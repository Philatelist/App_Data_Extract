package com.clmextract.export;

import com.clmextract.config.AppConfig;
import com.clmextract.endpoint.EndpointDefinition;
import com.clmextract.endpoint.EndpointRegistry;
import com.clmextract.http.RequestExecutor;
import com.clmextract.metadata.BoMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BatchProcessor {

    private static final Logger logger = LogManager.getLogger(BatchProcessor.class);

    private final RequestExecutor requestExecutor;
    private final EndpointRegistry endpointRegistry;
    private final BundleParser bundleParser;
    private final ObjectMapper objectMapper;

    public BatchProcessor(RequestExecutor requestExecutor, EndpointRegistry endpointRegistry, AppConfig config) {
        this.requestExecutor = requestExecutor;
        this.endpointRegistry = endpointRegistry;
        this.bundleParser = new BundleParser(config);
        this.objectMapper = new ObjectMapper();
    }

    public static List<List<Long>> split(List<Long> trackingIds, int batchSize) {
        List<List<Long>> batches = new ArrayList<>();
        for (int i = 0; i < trackingIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, trackingIds.size());
            batches.add(new ArrayList<>(trackingIds.subList(i, end)));
        }
        return batches;
    }

    public BundleResponse fetchBatch(List<Long> batchIds, List<String> fieldPaths,
                                     String sessionId, BoMetadata metadata) {
        logger.info("Fetching batch of {} tracking IDs", batchIds.size());

        EndpointDefinition endpoint = endpointRegistry.getEndpoint(EndpointRegistry.BUNDLES);

        Map<String, String> headers = new HashMap<>();
        headers.put("session_id", sessionId);

        String body = buildRequestBody(batchIds, fieldPaths);
        String response = requestExecutor.execute(endpoint, headers, body);
        return bundleParser.parse(response, metadata, batchIds);
    }

    private String buildRequestBody(List<Long> trackingIds, List<String> fieldPaths) {
        try {
            Map<String, Object> bodyMap = new LinkedHashMap<>();
            bodyMap.put("trackingIds", trackingIds);
            bodyMap.put("fieldPaths", fieldPaths);
            return objectMapper.writeValueAsString(bodyMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build bundles request body: " + e.getMessage(), e);
        }
    }
}
