package com.clmextract.export;

import com.clmextract.backup.BackupManager;
import com.clmextract.config.AppConfig;
import com.clmextract.config.BoTypeConfig;
import com.clmextract.csv.FilenameResolver;
import com.clmextract.csv.SummaryCsvWriter;
import com.clmextract.endpoint.EndpointRegistry;
import com.clmextract.metadata.BoMetadata;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ExportOrchestrator {

    private static final Logger logger = LogManager.getLogger(ExportOrchestrator.class);

    private final AppConfig config;
    private final EndpointRegistry endpointRegistry;
    private final BackupManager backupManager;

    public ExportOrchestrator(AppConfig config, EndpointRegistry endpointRegistry) {
        this.config = config;
        this.endpointRegistry = endpointRegistry;
        this.backupManager = new BackupManager(
                config.getOutputRoot(), config.getExportFolderName(), config.getBackupRetentionDays());
    }

    public void run() {
        logger.info("Starting CLM Data Extract run");

        backupManager.backupCurrentExports();
        backupManager.createOutputDirectories();

        FilenameResolver filenameResolver = new FilenameResolver();
        Path outputDir = Path.of(config.getOutputRoot(), config.getExportFolderName());

        SummaryCsvWriter summaryCsvWriter = null;
        if (config.isGenerateSummaryCsv()) {
            String summaryFilename = filenameResolver.resolve(config.getSummaryFilenameTemplate(), null, null);
            summaryCsvWriter = new SummaryCsvWriter(outputDir.resolve(summaryFilename), config.getDelimiter());
        }

        DataSource dataSource = createDataSource();
        dataSource.login();
        try {
            List<BoTypeConfig> boTypes = resolveBoTypes(dataSource);
            logger.info("BO types to process: {}", boTypes.size());

            BoPipeline pipeline = new BoPipeline(config, dataSource);
            pipeline.setSummaryCsvWriter(summaryCsvWriter);

            for (int i = 0; i < boTypes.size(); i++) {
                BoTypeConfig boType = boTypes.get(i);
                logger.info("Processing BO type {}/{}: {}", i + 1, boTypes.size(), boType.getName());
                pipeline.execute(boType);
            }

            logger.info("All BO types processed successfully");
        } finally {
            dataSource.logout();
            backupManager.enforceRetention();
            if (summaryCsvWriter != null) {
                try {
                    summaryCsvWriter.close();
                } catch (IOException e) {
                    logger.warn("Error closing summary CSV writer: {}", e.getMessage());
                }
            }
            logger.info("CLM Data Extract run finished");
        }
    }

    private DataSource createDataSource() {
        if (config.isOfflineMode()) {
            logger.info("Running in OFFLINE mode using sample data files");
            return new OfflineDataSource("inputs/samples");
        }
        return new ApiDataSource(config, endpointRegistry);
    }

    List<BoTypeConfig> resolveBoTypes(DataSource dataSource) {
        // Filter config boTypes to non-blank entries
        List<BoTypeConfig> explicit = config.getBoTypes().stream()
                .filter(bt -> bt.getName() != null && !bt.getName().isBlank())
                .collect(Collectors.toList());

        if (!explicit.isEmpty()) {
            // Explicit mode
            List<String> names = explicit.stream().map(BoTypeConfig::getName).collect(Collectors.toList());
            logger.info("Explicit boTypes configured: processing {} BO type(s): {}", explicit.size(), names);
            return explicit;
        }

        // Discovery mode
        logger.info("No explicit boTypes configured. Discovering all BO types from server.");
        List<String> boTypeNames = dataSource.getBoTypes();
        logger.info("Discovered {} BO type(s) from /BOTypes: {}", boTypeNames.size(), boTypeNames);

        if (boTypeNames.isEmpty()) {
            logger.warn("No BO types found on server. Nothing to export.");
            return Collections.emptyList();
        }

        // Mode 3: filter by usageType
        String usageTypeFilter = config.getBoUsageTypeFilter();
        if (usageTypeFilter != null && !usageTypeFilter.isBlank()) {
            logger.info("No explicit boTypes configured. Discovering BO types with usageType filter: {}.", usageTypeFilter);
            List<BoTypeConfig> retained = new ArrayList<>();
            for (String name : boTypeNames) {
                BoMetadata metadata;
                try {
                    metadata = dataSource.getMetadata(name);
                } catch (Exception e) {
                    logger.warn("BO type {}: failed to fetch metadata, skipping. Reason: {}", name, e.getMessage());
                    continue;
                }
                String usageType = metadata.getBoUsageType();
                if (usageType == null) {
                    logger.warn("BO type {}: no usageType found in metadata — excluded.", name);
                    continue;
                }
                if (usageType.equals(usageTypeFilter)) {
                    BoTypeConfig btc = new BoTypeConfig();
                    btc.setName(name);
                    retained.add(btc);
                }
            }
            List<String> retainedNames = retained.stream().map(BoTypeConfig::getName).collect(Collectors.toList());
            logger.info("Retained {} of {} BO type(s) after usageType filter '{}': {}.",
                    retained.size(), boTypeNames.size(), usageTypeFilter, retainedNames);
            if (retained.isEmpty()) {
                logger.warn("No BO types to process. Exiting.");
            }
            return retained;
        }

        // Mode 2: no filter — return all discovered
        List<BoTypeConfig> discovered = new ArrayList<>();
        for (String name : boTypeNames) {
            BoTypeConfig btc = new BoTypeConfig();
            btc.setName(name);
            discovered.add(btc);
        }
        return discovered;
    }
}
