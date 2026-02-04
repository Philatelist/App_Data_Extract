package com.clmextract.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BundleParser {

    private static final Logger logger = LogManager.getLogger(BundleParser.class);

    private final ObjectMapper objectMapper;

    public BundleParser() {
        this.objectMapper = new ObjectMapper();
    }

    public BundleResponse parse(String json) {
        try {
            BundleResponse response = objectMapper.readValue(json, BundleResponse.class);
            int recordCount = response.getRecords() != null ? response.getRecords().size() : 0;
            logger.debug("Parsed bundle response: {} records", recordCount);
            return response;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse bundles response: " + e.getMessage(), e);
        }
    }
}
