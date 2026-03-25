package com.clmextract.export;

import com.clmextract.endpoint.EndpointDefinition;
import com.clmextract.endpoint.EndpointRegistry;
import com.clmextract.http.RequestExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BundleParentFetcher {

    private static final Logger logger = LogManager.getLogger(BundleParentFetcher.class);

    private final RequestExecutor requestExecutor;
    private final EndpointRegistry endpointRegistry;
    private final ObjectMapper objectMapper;

    public BundleParentFetcher(RequestExecutor requestExecutor, EndpointRegistry endpointRegistry) {
        this.requestExecutor = requestExecutor;
        this.endpointRegistry = endpointRegistry;
        this.objectMapper = new ObjectMapper();
    }

    public List<ParentRecord> fetch(List<Long> trackingIds, String sessionId, int batchSize) {
        List<ParentRecord> all = new ArrayList<>();
        List<List<Long>> batches = BatchProcessor.split(trackingIds, batchSize);

        logger.info("Fetching bundle parents for {} tracking IDs in {} batch(es)", trackingIds.size(), batches.size());

        for (int i = 0; i < batches.size(); i++) {
            List<Long> batch = batches.get(i);
            logger.info("Fetching parent batch {}/{} ({} IDs)", i + 1, batches.size(), batch.size());
            all.addAll(fetchBatch(batch, sessionId));
        }

        return all;
    }

    private List<ParentRecord> fetchBatch(List<Long> batchIds, String sessionId) {
        EndpointDefinition endpoint = endpointRegistry.getEndpoint(EndpointRegistry.GET_BUNDLE_PARENT);

        Map<String, String> headers = new HashMap<>();
        headers.put("session_id", sessionId);

        String body = buildRequestBody(batchIds);
        String response = requestExecutor.execute(endpoint, headers, body);
        return parseResponse(response);
    }

    private String buildRequestBody(List<Long> trackingIds) {
        try {
            Map<String, Object> bodyMap = new LinkedHashMap<>();
            bodyMap.put("trackingIds", trackingIds);
            return objectMapper.writeValueAsString(bodyMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build getBundleParent request body: " + e.getMessage(), e);
        }
    }

    private List<ParentRecord> parseResponse(String response) {
        try {
            // Response format: { "trackingId": ["currentTask", "bundleType", "bundleCategory", "parentId"], ... }
            Map<String, List<String>> raw = objectMapper.readValue(
                    response, new TypeReference<Map<String, List<String>>>() {});
            List<ParentRecord> records = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : raw.entrySet()) {
                List<String> values = entry.getValue();
                ParentRecord r = new ParentRecord();
                r.setTrackingId(entry.getKey());
                r.setCurrentTask(get(values, 0));
                r.setBundleType(resolveBundleType(get(values, 1), get(values, 3)));
                r.setBundleCategory(get(values, 2));
                r.setParentId(get(values, 3));
                records.add(r);
            }
            return records;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse getBundleParent response: " + e.getMessage(), e);
        }
    }

    private static String resolveBundleType(String rawType, String parentId) {
        switch (rawType) {
            case "TaskAuthAmendWF":
                return "Amendment";
            case "WFAmendmentActivation":
                return "Original";
            case "RelatedWFCreate":
            case "WFCopy":
            case "WFCreate":
                return !parentId.isEmpty() ? "Sub" : "Master";
            case "TaskCompleted":
                return !parentId.isEmpty() ? "Original" : "";
            default:
                return rawType;
        }
    }

    private static String get(List<String> list, int index) {
        if (list == null || index >= list.size()) return "";
        String value = list.get(index);
        return value != null ? value : "";
    }
}
