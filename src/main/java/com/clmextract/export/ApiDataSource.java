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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    private final BundleParentFetcher bundleParentFetcher;
    private final ReportFetcher reportFetcher;

    public ApiDataSource(AppConfig config, EndpointRegistry endpointRegistry) {
        this.config = config;
        this.endpointRegistry = endpointRegistry;
        this.requestExecutor = new RequestExecutor(config);
        this.sessionManager = new SessionManager(config, endpointRegistry, requestExecutor);
        this.requestExecutor.setReLoginHandler(sessionManager);
        this.metadataParser = new MetadataParser();
        this.trackingNumberFetcher = new TrackingNumberFetcher();
        this.batchProcessor = new BatchProcessor(requestExecutor, endpointRegistry, config);
        this.bundleParentFetcher = new BundleParentFetcher(requestExecutor, endpointRegistry);
        this.reportFetcher = new ReportFetcher(requestExecutor, endpointRegistry);
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
    public List<Long> getTrackingNumbersAfterDate(String boType, String dateTime) {
        try {
            // Convert ISO yyyy-MM-dd to CLM format dd-M-yyyy HH:mm:ss
            LocalDate date = LocalDate.parse(dateTime);
            String clmDate = date.atStartOfDay().format(DateTimeFormatter.ofPattern("dd-M-yyyy HH:mm:ss"));
            logger.info("getTrackingNumbersAfterDate: boType={}, dateFrom={} → CLM dateTime header='{}'",
                    boType, dateTime, clmDate);

            EndpointDefinition endpoint = endpointRegistry.getEndpoint(
                    EndpointRegistry.GET_TRACKING_NUMBERS_AFTER_DATE);
            Map<String, String> headers = new HashMap<>();
            headers.put("session_id", sessionManager.getSessionId());
            headers.put("boUsageType", boType);
            headers.put("dateTime", clmDate);

            String response = requestExecutor.execute(endpoint, headers, null);
            logger.info("getTrackingNumbersAfterDate: raw CLM response: {}", response);
            ObjectMapper objectMapper = new ObjectMapper();
            List<String> stringIds = objectMapper.readValue(response,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            logger.info("getTrackingNumbersAfterDate: parsed {} tracking number(s)", stringIds.size());
            return stringIds.stream()
                    .map(Long::parseLong)
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch tracking numbers after date: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Long> getTrackingNumbersInFlight(String boType, int daysBeforeToday) {
        try {
            EndpointDefinition endpoint = endpointRegistry.getEndpoint(
                    EndpointRegistry.GET_TRACKING_NUMBERS_IN_FLIGHT);
            Map<String, String> headers = new HashMap<>();
            headers.put("session_id", sessionManager.getSessionId());
            headers.put("boUsageType", boType);
            headers.put("daysBeforeToday", String.valueOf(daysBeforeToday));

            String response = requestExecutor.execute(endpoint, headers, null);
            ObjectMapper objectMapper = new ObjectMapper();
            List<String> stringIds = objectMapper.readValue(response,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            return stringIds.stream()
                    .map(Long::parseLong)
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch in-flight tracking numbers: " + e.getMessage(), e);
        }
    }

    @Override
    public BundleResponse fetchBatch(List<Long> trackingIds, List<String> fieldPaths,
                                     BoMetadata metadata) {
        return batchProcessor.fetchBatch(trackingIds, fieldPaths, sessionManager.getSessionId(),
                metadata);
    }

    @Override
    public List<ParentRecord> fetchBundleParents(List<Long> trackingIds, int batchSize) {
        return bundleParentFetcher.fetch(trackingIds, sessionManager.getSessionId(), batchSize);
    }

    @Override
    public List<ReportResult> fetchReports(List<String> reportNames) {
        return reportFetcher.fetch(reportNames, sessionManager.getSessionId());
    }

    /** Injects an already-authenticated CLM session ID, bypassing the normal login flow. */
    public void injectSessionId(String sessionId) {
        this.sessionManager.setSessionId(sessionId);
    }

    @Override
    public List<Map<String, Object>> getAttachmentInfo(String trackingNumber) {
        try {
            EndpointDefinition endpoint = endpointRegistry.getEndpoint(
                    EndpointRegistry.GET_ATTACHMENT_INFO);
            Map<String, String> headers = new HashMap<>();
            headers.put("session_id", sessionManager.getSessionId());
            headers.put("trackingNumber", trackingNumber);
            String response = requestExecutor.execute(endpoint, headers, null);
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(response, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            logger.warn("Failed to get attachment info for tracking number {}: {}", trackingNumber, e.getMessage());
            return List.of();
        }
    }

    @Override
    public byte[] downloadDocument(String encryptedUrl) {
        try {
            String fullUrl = config.getBaseUrl() + "/document?url="
                    + URLEncoder.encode(encryptedUrl, StandardCharsets.UTF_8);
            return requestExecutor.executeBytes(fullUrl, sessionManager.getSessionId());
        } catch (Exception e) {
            logger.warn("Failed to download document for url {}: {}", encryptedUrl, e.getMessage());
            return new byte[0];
        }
    }
}
