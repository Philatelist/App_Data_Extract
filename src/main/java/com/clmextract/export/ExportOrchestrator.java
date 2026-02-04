package com.clmextract.export;

import com.clmextract.backup.BackupManager;
import com.clmextract.config.AppConfig;
import com.clmextract.config.BoTypeConfig;
import com.clmextract.endpoint.EndpointRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

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

        DataSource dataSource = createDataSource();
        dataSource.login();
        try {
            List<BoTypeConfig> boTypes = resolveBoTypes(dataSource);
            logger.info("BO types to process: {}", boTypes.size());

            BoPipeline pipeline = new BoPipeline(config, dataSource);

            for (int i = 0; i < boTypes.size(); i++) {
                BoTypeConfig boType = boTypes.get(i);
                logger.info("Processing BO type {}/{}: {}", i + 1, boTypes.size(), boType.getName());
                pipeline.execute(boType);
            }

            logger.info("All BO types processed successfully");
        } finally {
            dataSource.logout();
            backupManager.enforceRetention();
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

    private List<BoTypeConfig> resolveBoTypes(DataSource dataSource) {
        if (!config.getBoTypes().isEmpty()) {
            return config.getBoTypes();
        }

        logger.info("No BO types configured, discovering...");
        List<String> boTypeNames = dataSource.getBoTypes();

        List<BoTypeConfig> discovered = new ArrayList<>();
        for (String name : boTypeNames) {
            BoTypeConfig btc = new BoTypeConfig();
            btc.setName(name);
            discovered.add(btc);
        }
        logger.info("Discovered {} BO types: {}", discovered.size(), boTypeNames);
        return discovered;
    }
}
