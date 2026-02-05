package com.clmextract.export;

import com.clmextract.config.AppConfig;
import com.clmextract.endpoint.EndpointDefinition;
import com.clmextract.endpoint.EndpointRegistry;
import com.clmextract.http.RequestExecutor;
import com.clmextract.metadata.BoMetadata;
import com.clmextract.metadata.MetadataParser;
import com.clmextract.session.SessionManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiDataSource implements DataSource {

    private static final Logger logger = LogManager.getLogger(ApiDataSource.class);

    private final AppConfig config;
    private final RequestExecutor requestExecutor;
    private final EndpointRegistry endpointRegistry;
    private final SessionManager sessionManager;
    private final MetadataParser metadataParser;
    private final TrackingNumberFetcher trackingNumberFetcher;
    private final BatchProcessor batchProcessor;

    public ApiDataSource(AppConfig config, EndpointRegistry endpointRegistry) {
        this.config = config;
        this.endpointRegistry = endpointRegistry;
        this.requestExecutor = new RequestExecutor(config);
        this.sessionManager = new SessionManager(config, endpointRegistry, requestExecutor);
        this.requestExecutor.setReLoginHandler(sessionManager);
        this.metadataParser = new MetadataParser();
        this.trackingNumberFetcher = new TrackingNumberFetcher();
        this.batchProcessor = new BatchProcessor(requestExecutor, endpointRegistry);
    }

    @Override
    public void login() {
        sessionManager.ensureLoggedIn();
    }

    @Override
    public void logout() {
        sessionManager.logout();
    }

    @Override
    public List<String> getBoTypes() {
        logger.info("Discovering BO types via API...");
        try {
            EndpointDefinition endpoint = endpointRegistry.getEndpoint(EndpointRegistry.GET_BO_TYPES);
            Map<String, String> headers = new HashMap<>();
            headers.put("session_id", sessionManager.getSessionId());

            String response = requestExecutor.execute(endpoint, headers, null);
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(response, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to discover BO types: " + e.getMessage(), e);
        }
    }

    @Override
    public BoMetadata getMetadata(String boType) {
        return metadataParser.fetch(boType, requestExecutor, endpointRegistry,
                sessionManager.getSessionId());
    }

    @Override
    public List<Long> getTrackingNumbers(String boType) {
        return trackingNumberFetcher.fetch(boType, requestExecutor, endpointRegistry,
                sessionManager.getSessionId());
    }

    @Override
    public BundleResponse fetchBatch(List<Long> trackingIds, List<String> fieldPaths,
                                     BoMetadata metadata) {
        return batchProcessor.fetchBatch(trackingIds, fieldPaths, sessionManager.getSessionId(),
                metadata);
    }
}
