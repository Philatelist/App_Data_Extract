package com.clmextract.export;

import com.clmextract.api.dto.BundleFieldDto;
import com.clmextract.api.mapper.BundlesMapper;
import com.clmextract.config.AppConfig;
import com.clmextract.metadata.BoMetadata;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class BundleParser {

    private static final Logger logger = LogManager.getLogger(BundleParser.class);

    private final ObjectMapper objectMapper;
    private final BundlesMapper bundlesMapper;

    public BundleParser() {
        this.objectMapper = new ObjectMapper();
        this.bundlesMapper = new BundlesMapper();
    }

    public BundleParser(AppConfig config) {
        this.objectMapper = new ObjectMapper();
        this.bundlesMapper = new BundlesMapper(
                config.getDelimiter(),
                config.isDelimiterReplacementEnabled() ? config.getDelimiterSubstituteChar() : null,
                config.isYesNoTranslationEnabled(),
                config.getYesNoTrueValue(),
                config.getYesNoFalseValue(),
                config.getFieldValueMappings()
        );
    }

    public BundleResponse parse(String json, BoMetadata metadata, List<Long> requestTrackingIds) {
        try {
            List<List<BundleFieldDto>> rawRecords = objectMapper.readValue(json,
                    new TypeReference<List<List<BundleFieldDto>>>() {});
            BundleResponse response = bundlesMapper.map(rawRecords, metadata, requestTrackingIds);
            int recordCount = response.getRecords() != null ? response.getRecords().size() : 0;
            logger.debug("Parsed bundle response: {} records", recordCount);
            return response;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse bundles response: " + e.getMessage(), e);
        }
    }
}
