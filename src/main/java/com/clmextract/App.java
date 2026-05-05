package com.clmextract;

import com.clmextract.config.AppConfig;
import com.clmextract.config.ConfigLoader;
import com.clmextract.config.ConfigValidationException;
import com.clmextract.endpoint.EndpointRegistry;
import com.clmextract.endpoint.EndpointResolutionException;
import com.clmextract.export.ExportOrchestrator;
import com.clmextract.logging.LogSetup;
import com.clmextract.web.WebServer;

public class App {

    public static void main(String[] args) {
        String configPath = null;
        boolean serveMode = false;
        int port = 8082;

        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i])) {
                if (i + 1 < args.length) {
                    configPath = args[i + 1];
                    i++;
                }
            } else if ("--serve".equals(args[i])) {
                serveMode = true;
            } else if ("--port".equals(args[i])) {
                if (i + 1 < args.length) {
                    port = Integer.parseInt(args[i + 1]);
                    i++;
                }
            }
        }

        if (serveMode) {
            if (configPath == null) {
                System.out.println("Usage: java -jar clm-extract.jar --serve --config <config.yml> [--port <port>]");
                System.exit(1);
            }

            System.out.println("=== CLM Data Extract - Web UI Mode ===");
            System.out.println("  Server URL: http://localhost:" + port);
            System.out.println("  Config:     " + configPath);
            System.out.println("=======================================");

            WebServer.start(configPath, port);
            return;
        }

        if (configPath == null) {
            System.out.println("Usage: java -jar clm-extract.jar --config <config.yml>");
            System.exit(1);
        }

        AppConfig config;
        try {
            config = ConfigLoader.load(configPath);
        } catch (ConfigValidationException e) {
            System.out.println("Configuration error: " + e.getMessage());
            System.exit(1);
            return; // unreachable, but satisfies compiler
        }

        String boTypesInfo = config.getBoTypes().isEmpty()
                ? "all (auto-discover)"
                : String.valueOf(config.getBoTypes().size());

        System.out.println("=== CLM Data Extract - Configuration ===");
        System.out.println("  Server URL:   " + config.getBaseUrl());
        System.out.println("  Username:     " + config.getUsername());
        System.out.println("  BO types:     " + boTypesInfo);
        System.out.println("  CSV mode:     " + config.getCsvMode());
        System.out.println("  Output root:  " + config.getOutputRoot());
        System.out.println("  Batch size:   " + config.getBatchSize());
        System.out.println("  Offline mode: " + config.isOfflineMode());
        System.out.println("========================================");

        // Configure logging before any library code triggers Log4j2 auto-initialization
        LogSetup.configure(config.getOutputRoot());

        EndpointRegistry endpointRegistry;
        try {
            endpointRegistry = new EndpointRegistry(config.getEndpointsFile());
            endpointRegistry.load();
        } catch (EndpointResolutionException e) {
            System.out.println("Endpoint loading error: " + e.getMessage());
            System.exit(1);
            return;
        }

        System.out.println("=== Endpoints ===");
        System.out.println("  Base path:  " + endpointRegistry.getBasePath());
        System.out.println("  Total:      " + endpointRegistry.getAllEndpoints().size());
        System.out.println("  LOGIN:      " + endpointRegistry.getEndpoint(EndpointRegistry.LOGIN).getPath());
        System.out.println("  LOGOUT:     " + endpointRegistry.getEndpoint(EndpointRegistry.LOGOUT).getPath());
        System.out.println("  METADATA:   " + endpointRegistry.getEndpoint(EndpointRegistry.GET_BO_METADATA).getPath());
        System.out.println("  TRACKING:   " + endpointRegistry.getEndpoint(EndpointRegistry.GET_TRACKING_NUMBERS).getPath());
        System.out.println("  BUNDLES:    " + endpointRegistry.getEndpoint(EndpointRegistry.BUNDLES).getPath());
        try {
            System.out.println("  BO_TYPES:   " + endpointRegistry.getEndpoint(EndpointRegistry.GET_BO_TYPES).getPath());
        } catch (EndpointResolutionException e) {
            System.out.println("  BO_TYPES:   (not available)");
        }
        System.out.println("=================");

        ExportOrchestrator orchestrator = new ExportOrchestrator(config, endpointRegistry);
        try {
            orchestrator.run();
            System.exit(0);
        } catch (Exception e) {
            System.out.println("Run failed: " + e.getMessage());
            System.exit(2);
        }
    }
}
