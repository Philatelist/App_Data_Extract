package com.clmextract.export;

import com.clmextract.config.AppConfig;
import com.clmextract.config.BoTypeConfig;
import com.clmextract.csv.ColumnResolver;
import com.clmextract.csv.CsvExportWriter;
import com.clmextract.csv.CsvWriterFactory;
import com.clmextract.csv.DownloadsCsvWriter;
import com.clmextract.csv.FilenameResolver;
import com.clmextract.metadata.BoMetadata;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class BoPipeline {

    private static final Logger logger = LogManager.getLogger(BoPipeline.class);

    private final AppConfig config;
    private final DataSource dataSource;

    public BoPipeline(AppConfig config, DataSource dataSource) {
        this.config = config;
        this.dataSource = dataSource;
    }

    public void execute(BoTypeConfig boTypeConfig) {
        String boType = boTypeConfig.getName();
        logger.info("=== Starting pipeline for BO type: {} ===", boType);

        // Step 1: Metadata
        BoMetadata metadata = dataSource.getMetadata(boType);
        logger.info("Metadata loaded: {} components", metadata.getComponents().size());

        // Step 2: Tracking numbers
        List<Long> trackingIds = dataSource.getTrackingNumbers(boType);
        if (boTypeConfig.getTrackingFilter() != null) {
            trackingIds = TrackingFilter.apply(trackingIds, boTypeConfig.getTrackingFilter());
        }
        logger.info("Tracking IDs to process: {}", trackingIds.size());

        if (trackingIds.isEmpty()) {
            logger.info("No tracking IDs to process for BO type: {}", boType);
            return;
        }

        // Step 3: Resolve columns
        ColumnResolver columnResolver = new ColumnResolver(metadata.getComponents(), metadata.getBoName());
        List<String> fieldPaths = columnResolver.resolveFieldPaths();

        // Step 4: Determine filename template (per-BO override or global)
        String template = boTypeConfig.getFilenameTemplate() != null
                ? boTypeConfig.getFilenameTemplate()
                : config.getFilenameTemplate();

        // Step 5: Open CSV writers
        FilenameResolver filenameResolver = new FilenameResolver();
        Path outputDir = Path.of(config.getOutputRoot(), config.getExportFolderName());

        CsvExportWriter csvWriter = CsvWriterFactory.create(
                config.getCsvMode(), metadata, columnResolver, filenameResolver,
                template, outputDir, config.getDelimiter());

        // Step 5b: Open downloads CSV writer
        Path downloadsDir = Path.of(config.getOutputRoot(), "downloads");
        DownloadsCsvWriter downloadsCsvWriter = new DownloadsCsvWriter(
                metadata, filenameResolver,
                config.getDownloadsFilenameTemplate(), downloadsDir, config.getDelimiter());

        try {
            csvWriter.writeHeaders();
            downloadsCsvWriter.open();

            // Step 6: Batched fetch + incremental write
            List<List<Long>> batches = BatchProcessor.split(trackingIds, config.getBatchSize());
            logger.info("Processing {} batches (batch size: {})", batches.size(), config.getBatchSize());

            for (int i = 0; i < batches.size(); i++) {
                List<Long> batch = batches.get(i);
                logger.info("Processing batch {}/{} ({} IDs)", i + 1, batches.size(), batch.size());

                BundleResponse bundleResponse = dataSource.fetchBatch(batch, fieldPaths, metadata);

                if (bundleResponse.getRecords() != null) {
                    csvWriter.writeRecords(bundleResponse.getRecords());
                    downloadsCsvWriter.writeRecords(bundleResponse.getRecords());
                }
            }
        } finally {
            try {
                csvWriter.close();
            } catch (IOException e) {
                logger.warn("Error closing CSV writers: {}", e.getMessage());
            }
            try {
                downloadsCsvWriter.close();
            } catch (IOException e) {
                logger.warn("Error closing downloads CSV writer: {}", e.getMessage());
            }
        }

        // Step 7: State cleanup
        logger.info("=== Completed pipeline for BO type: {} ===", boType);
    }
}
