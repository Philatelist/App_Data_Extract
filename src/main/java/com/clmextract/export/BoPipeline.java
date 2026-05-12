package com.clmextract.export;

import com.clmextract.config.AppConfig;
import com.clmextract.config.BoTypeConfig;
import com.clmextract.csv.ColumnResolver;
import com.clmextract.csv.CsvExportWriter;
import com.clmextract.csv.CsvWriterFactory;
import com.clmextract.csv.DownloadsCsvWriter;
import com.clmextract.csv.FilenameResolver;
import com.clmextract.csv.ParentCsvWriter;
import com.clmextract.csv.SummaryCsvWriter;
import com.clmextract.metadata.BoMetadata;
import com.clmextract.web.run.DateFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        // Step 1b: Skip components filtering
        if (!config.getSkipComponents().isEmpty()) {
            Set<String> skipSet = new HashSet<>();
            for (String entry : config.getSkipComponents()) {
                skipSet.add(entry.trim().toLowerCase());
            }
            List<com.clmextract.metadata.ComponentMetadata> retained = new ArrayList<>();
            int skippedCount = 0;
            for (com.clmextract.metadata.ComponentMetadata component : metadata.getComponents()) {
                String normDisplay = component.getDisplayName() != null ? component.getDisplayName().trim().toLowerCase() : "";
                String normInternal = component.getInternalName() != null ? component.getInternalName().trim().toLowerCase() : "";
                if (skipSet.contains(normDisplay) || skipSet.contains(normInternal)) {
                    logger.info("Skipping component \"{}\" for BO type \"{}\" (in skipComponents list)",
                            component.getDisplayName(), boType);
                    skippedCount++;
                } else {
                    retained.add(component);
                }
            }
            metadata.setComponents(retained);
            if (skippedCount > 0) {
                logger.info("Filtered {} component(s) for BO type: {}", skippedCount, boType);
            }
        }

        // Step 2: Tracking numbers
        List<Long> trackingIds = dataSource.getTrackingNumbers(boType);
        if (config.getTrackingFilter() != null) {
            trackingIds = TrackingFilter.apply(trackingIds, config.getTrackingFilter());
        }
        logger.info("Tracking IDs to process: {}", trackingIds.size());

        if (trackingIds.isEmpty()) {
            logger.info("No tracking IDs to process for BO type: {}", boType);
            return;
        }

        // Step 3: Resolve columns
        ColumnResolver columnResolver = new ColumnResolver(metadata.getComponents(), metadata.getBoName(),
                config.getSkipColumns(), config.getAdditionalColumns());
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
                template, outputDir, config.getDelimiter(), config.getDateFormat());

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

        // Step 7: Bundle parent CSV and/or summary — fetch once if either is needed
        if (config.isGenerateParentCsv() || summaryCsvWriter != null) {
            List<ParentRecord> parentRecords;
            try {
                parentRecords = dataSource.fetchBundleParents(trackingIds, config.getBatchSize());
            } catch (Exception e) {
                logger.warn("BO type {}: failed to fetch bundle parents, skipping RelationshipMapping and summary. Reason: {}",
                        boType, e.getMessage());
                logger.info("=== Completed pipeline for BO type: {} ===", boType);
                return;
            }

            if (config.isGenerateParentCsv()) {
                String parentFilename = filenameResolver.resolve(
                        config.getParentFilenameTemplate(), boType, null);
                Path parentFilePath = outputDir.resolve(parentFilename);
                ParentCsvWriter parentCsvWriter = new ParentCsvWriter(parentFilePath, config.getDelimiter());
                parentCsvWriter.open();
                try {
                    parentCsvWriter.writeRecords(parentRecords);
                    logger.info("Written {} parent record(s) to {}", parentRecords.size(), parentFilePath);
                } finally {
                    try {
                        parentCsvWriter.close();
                    } catch (IOException e) {
                        logger.warn("Error closing parent CSV writer: {}", e.getMessage());
                    }
                }
            }

            if (summaryCsvWriter != null) {
                summaryCsvWriter.writeBoSummary(metadata.getBoDisplayName(), parentRecords);
                logger.info("Written summary for BO type: {}", boType);
            }
        }

        logger.info("=== Completed pipeline for BO type: {} ===", boType);
    }

    public List<Long> execute(BoTypeConfig boTypeConfig, Path outputDir, DateFilter dateFilter) {
        String boType = boTypeConfig.getName();
        logger.info("=== Starting pipeline for BO type: {} ===", boType);

        // Step 1: Metadata
        BoMetadata metadata = dataSource.getMetadata(boType);
        logger.info("Metadata loaded: {} components", metadata.getComponents().size());

        // Step 1b: Skip components filtering
        if (!config.getSkipComponents().isEmpty()) {
            Set<String> skipSet = new HashSet<>();
            for (String entry : config.getSkipComponents()) {
                skipSet.add(entry.trim().toLowerCase());
            }
            List<com.clmextract.metadata.ComponentMetadata> retained = new ArrayList<>();
            int skippedCount = 0;
            for (com.clmextract.metadata.ComponentMetadata component : metadata.getComponents()) {
                String normDisplay = component.getDisplayName() != null ? component.getDisplayName().trim().toLowerCase() : "";
                String normInternal = component.getInternalName() != null ? component.getInternalName().trim().toLowerCase() : "";
                if (skipSet.contains(normDisplay) || skipSet.contains(normInternal)) {
                    logger.info("Skipping component \"{}\" for BO type \"{}\" (in skipComponents list)",
                            component.getDisplayName(), boType);
                    skippedCount++;
                } else {
                    retained.add(component);
                }
            }
            metadata.setComponents(retained);
            if (skippedCount > 0) {
                logger.info("Filtered {} component(s) for BO type: {}", skippedCount, boType);
            }
        }

        // Step 2: Tracking numbers — DateFilter routing (null = no filter)
        List<Long> trackingIds;
        if (dateFilter != null && "createDate".equals(dateFilter.getDateField())
                && dateFilter.getDateFrom() != null && !dateFilter.getDateFrom().isBlank()) {
            trackingIds = dataSource.getTrackingNumbersAfterDate(boType, dateFilter.getDateFrom());
        } else if (dateFilter != null && dateFilter.isModifiedWithinPeriod()) {
            trackingIds = dataSource.getTrackingNumbersInFlight(boType, dateFilter.getDaysBeforeToday());
        } else {
            trackingIds = dataSource.getTrackingNumbers(boType);
        }
        if (config.getTrackingFilter() != null) {
            trackingIds = TrackingFilter.apply(trackingIds, config.getTrackingFilter());
        }
        logger.info("Tracking IDs to process: {}", trackingIds.size());

        if (trackingIds.isEmpty()) {
            logger.info("No tracking IDs to process for BO type: {}", boType);
            return List.of();
        }

        // Step 3: Resolve columns
        ColumnResolver columnResolver = new ColumnResolver(metadata.getComponents(), metadata.getBoName(),
                config.getSkipColumns(), config.getAdditionalColumns());
        List<String> fieldPaths = columnResolver.resolveFieldPaths();

        // Step 4: Determine filename template (per-BO override or global)
        String template = boTypeConfig.getFilenameTemplate() != null
                ? boTypeConfig.getFilenameTemplate()
                : config.getFilenameTemplate();

        // Step 5: Open CSV writers using the supplied outputDir
        FilenameResolver filenameResolver = new FilenameResolver();

        CsvExportWriter csvWriter = CsvWriterFactory.create(
                config.getCsvMode(), metadata, columnResolver, filenameResolver,
                template, outputDir, config.getDelimiter(), config.getDateFormat());

        // Step 5b: Open downloads CSV writer (downloads go alongside CSVs)
        DownloadsCsvWriter downloadsCsvWriter = new DownloadsCsvWriter(
                metadata, filenameResolver,
                config.getDownloadsFilenameTemplate(), outputDir, config.getDelimiter());

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

        // Step 7: Bundle parent CSV and/or summary
        if (config.isGenerateParentCsv() || summaryCsvWriter != null) {
            List<ParentRecord> parentRecords;
            try {
                parentRecords = dataSource.fetchBundleParents(trackingIds, config.getBatchSize());
            } catch (Exception e) {
                logger.warn("BO type {}: failed to fetch bundle parents. Reason: {}", boType, e.getMessage());
                logger.info("=== Completed pipeline for BO type: {} ===", boType);
                return trackingIds;
            }

            if (config.isGenerateParentCsv()) {
                String parentFilename = filenameResolver.resolve(
                        config.getParentFilenameTemplate(), boType, null);
                Path parentFilePath = outputDir.resolve(parentFilename);
                ParentCsvWriter parentCsvWriter = new ParentCsvWriter(parentFilePath, config.getDelimiter());
                parentCsvWriter.open();
                try {
                    parentCsvWriter.writeRecords(parentRecords);
                    logger.info("Written {} parent record(s) to {}", parentRecords.size(), parentFilePath);
                } finally {
                    try {
                        parentCsvWriter.close();
                    } catch (IOException e) {
                        logger.warn("Error closing parent CSV writer: {}", e.getMessage());
                    }
                }
            }

            if (summaryCsvWriter != null) {
                summaryCsvWriter.writeBoSummary(metadata.getBoDisplayName(), parentRecords);
                logger.info("Written summary for BO type: {}", boType);
            }
        }

        logger.info("=== Completed pipeline for BO type: {} ===", boType);
        return trackingIds;
    }

    private SummaryCsvWriter summaryCsvWriter;

    public void setSummaryCsvWriter(SummaryCsvWriter summaryCsvWriter) {
        this.summaryCsvWriter = summaryCsvWriter;
    }
}
