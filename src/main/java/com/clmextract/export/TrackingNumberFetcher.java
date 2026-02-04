package com.clmextract.export;

import com.clmextract.endpoint.EndpointDefinition;
import com.clmextract.endpoint.EndpointRegistry;
import com.clmextract.http.RequestExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TrackingNumberFetcher {

    private static final Logger logger = LogManager.getLogger(TrackingNumberFetcher.class);

    private final ObjectMapper objectMapper;

    public TrackingNumberFetcher() {
        this.objectMapper = new ObjectMapper();
    }

    public List<Long> fetch(String boType, RequestExecutor requestExecutor,
                            EndpointRegistry endpointRegistry, String sessionId) {
        logger.info("Fetching tracking numbers for BO type: {}", boType);

        EndpointDefinition endpoint = endpointRegistry.getEndpoint(EndpointRegistry.GET_TRACKING_NUMBERS);

        Map<String, String> headers = new HashMap<>();
        headers.put("session_id", sessionId);
        headers.put("boUsageType", boType);

        String response = requestExecutor.execute(endpoint, headers, null);
        return parse(response);
    }

    public List<Long> parse(String json) {
        try {
            List<String> stringIds = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            List<Long> ids = stringIds.stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            logger.info("Parsed {} tracking numbers", ids.size());
            return ids;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse tracking numbers: " + e.getMessage(), e);
        }
    }
}
