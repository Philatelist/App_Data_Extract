package com.clmextract.metadata;

import com.clmextract.api.dto.MetadataNodeDto;
import com.clmextract.api.mapper.MetadataMapper;
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

public class MetadataParser {

    private static final Logger logger = LogManager.getLogger(MetadataParser.class);

    private final ObjectMapper objectMapper;
    private final MetadataMapper metadataMapper;

    public MetadataParser() {
        this.objectMapper = new ObjectMapper();
        this.metadataMapper = new MetadataMapper();
    }

    public BoMetadata fetch(String boType, RequestExecutor requestExecutor,
                            EndpointRegistry endpointRegistry, String sessionId) {
        logger.info("Fetching metadata for BO type: {}", boType);

        EndpointDefinition endpoint = endpointRegistry.getEndpoint(EndpointRegistry.GET_BO_METADATA);

        Map<String, String> headers = new HashMap<>();
        headers.put("session_id", sessionId);
        headers.put("boUsageType", boType);

        String response = requestExecutor.execute(endpoint, headers, null);
        return parse(response);
    }

    public BoMetadata parse(String json) {
        try {
            List<MetadataNodeDto> nodes = objectMapper.readValue(json,
                    new TypeReference<List<MetadataNodeDto>>() {});
            BoMetadata metadata = metadataMapper.map(nodes);
            logger.info("Parsed metadata: boName={}, components={}",
                    metadata.getBoName(),
                    metadata.getComponents() != null ? metadata.getComponents().size() : 0);
            return metadata;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse BO metadata: " + e.getMessage(), e);
        }
    }
}
