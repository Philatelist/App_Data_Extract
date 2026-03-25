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
import java.util.List;
import java.util.Map;

public class ReportFetcher {

    private static final Logger logger = LogManager.getLogger(ReportFetcher.class);

    private final RequestExecutor requestExecutor;
    private final EndpointRegistry endpointRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReportFetcher(RequestExecutor requestExecutor, EndpointRegistry endpointRegistry) {
        this.requestExecutor = requestExecutor;
        this.endpointRegistry = endpointRegistry;
    }

    public List<ReportResult> fetch(List<String> reportNames, String sessionId) {
        List<ReportResult> results = new ArrayList<>();
        if (reportNames == null || reportNames.isEmpty()) {
            return results;
        }

        // Step 1: fetch all templates
        List<Map<String, Object>> templates = fetchTemplates(sessionId);
        logger.info("Fetched {} report templates", templates.size());

        // Log all available templates to help diagnose name matching
        for (Map<String, Object> t : templates) {
            logger.info("Available template: name='{}' displayName='{}' templateId='{}'",
                    t.get("name"), t.get("displayName"), t.get("templateId"));
        }

        // Step 2: for each configured report name, find matching template and run report
        for (String reportName : reportNames) {
            String trimmed = reportName.trim();
            Map<String, Object> match = findTemplate(templates, trimmed);
            if (match == null) {
                logger.warn("Report template not found: '{}' — skipping", trimmed);
                continue;
            }

            String templateId = (String) match.get("templateId");
            String displayName = match.containsKey("displayName")
                    ? (String) match.get("displayName")
                    : trimmed;

            logger.info("Selected template for '{}': name='{}' displayName='{}' templateId='{}'",
                    trimmed, match.get("name"), displayName, templateId);
            logger.info("Running report '{}' (templateId: {})", displayName, templateId);
            String csvContent = runReport(templateId, sessionId);
            results.add(new ReportResult(displayName, csvContent));
            logger.info("Report '{}' fetched ({} chars)", displayName, csvContent.length());
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchTemplates(String sessionId) {
        EndpointDefinition endpoint = endpointRegistry.getEndpoint(EndpointRegistry.GET_REPORT_TEMPLATES);
        Map<String, String> headers = new HashMap<>();
        headers.put("session_id", sessionId);
        String response = requestExecutor.execute(endpoint, headers, null);
        try {
            return objectMapper.readValue(response, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse templates response: " + e.getMessage(), e);
        }
    }

    private String runReport(String templateId, String sessionId) {
        EndpointDefinition endpoint = endpointRegistry.getEndpoint(EndpointRegistry.CREATE_REPORT);
        Map<String, String> headers = new HashMap<>();
        headers.put("session_id", sessionId);
        headers.put("templateId", templateId);
        return requestExecutor.execute(endpoint, headers, null);
    }

    private Map<String, Object> findTemplate(List<Map<String, Object>> templates, String name) {
        // Match by displayName (case-insensitive), then by name (with or without .xml extension)
        for (Map<String, Object> t : templates) {
            String displayName = (String) t.getOrDefault("displayName", "");
            if (name.equalsIgnoreCase(displayName)) {
                return t;
            }
        }
        for (Map<String, Object> t : templates) {
            String tName = (String) t.getOrDefault("name", "");
            // strip .xml extension for comparison
            String tNameBase = tName.endsWith(".xml") ? tName.substring(0, tName.length() - 4) : tName;
            if (name.equalsIgnoreCase(tName) || name.equalsIgnoreCase(tNameBase)) {
                return t;
            }
        }
        return null;
    }
}
