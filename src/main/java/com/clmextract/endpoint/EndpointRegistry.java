package com.clmextract.endpoint;

import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EndpointRegistry {

    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String GET_BO_TYPES = "GET_BO_TYPES";
    public static final String GET_BO_METADATA = "GET_BO_METADATA";
    public static final String GET_TRACKING_NUMBERS = "GET_TRACKING_NUMBERS";
    public static final String BUNDLES = "BUNDLES";
    public static final String GET_BUNDLE_PARENT = "GET_BUNDLE_PARENT";
    public static final String GET_REPORT_TEMPLATES = "GET_REPORT_TEMPLATES";
    public static final String CREATE_REPORT = "CREATE_REPORT";

    private static final Map<String, String> OPERATION_NAME_MAP = new HashMap<>();

    static {
        OPERATION_NAME_MAP.put("login", LOGIN);
        OPERATION_NAME_MAP.put("logout", LOGOUT);
        OPERATION_NAME_MAP.put("getBoTypes", GET_BO_TYPES);
        OPERATION_NAME_MAP.put("getBoMetaData", GET_BO_METADATA);
        OPERATION_NAME_MAP.put("getTrackingNumbers", GET_TRACKING_NUMBERS);
        OPERATION_NAME_MAP.put("bundles", BUNDLES);
        OPERATION_NAME_MAP.put("getBundleParent", GET_BUNDLE_PARENT);
        OPERATION_NAME_MAP.put("reportsGetReportTemplates", GET_REPORT_TEMPLATES);
        OPERATION_NAME_MAP.put("reportsCreateReport", CREATE_REPORT);
    }

    private static final String[] REQUIRED_OPERATIONS = {
            LOGIN, LOGOUT, GET_BO_METADATA, GET_TRACKING_NUMBERS, BUNDLES
    };

    private final String endpointsFilePath;
    private String basePath;
    private final List<EndpointDefinition> allEndpoints = new ArrayList<>();
    private final Map<String, EndpointDefinition> operationMap = new HashMap<>();

    public EndpointRegistry(String endpointsFilePath) {
        this.endpointsFilePath = endpointsFilePath;
    }

    @SuppressWarnings("unchecked")
    public void load() {
        Map<String, Object> root;
        try (FileInputStream fis = new FileInputStream(endpointsFilePath)) {
            Yaml yaml = new Yaml();
            root = yaml.load(fis);
        } catch (IOException e) {
            throw new EndpointResolutionException("Cannot read endpoints file: " + endpointsFilePath);
        }

        basePath = (String) root.getOrDefault("basePath", "");

        List<Map<String, Object>> endpoints = (List<Map<String, Object>>) root.get("endpoints");
        if (endpoints == null) {
            throw new EndpointResolutionException("No endpoints defined in: " + endpointsFilePath);
        }

        for (Map<String, Object> entry : endpoints) {
            EndpointDefinition def = parseEndpoint(entry);
            allEndpoints.add(def);

            String operation = OPERATION_NAME_MAP.get(def.getName());
            if (operation != null) {
                operationMap.put(operation, def);
            }
        }

        for (String required : REQUIRED_OPERATIONS) {
            if (!operationMap.containsKey(required)) {
                String endpointName = OPERATION_NAME_MAP.entrySet().stream()
                        .filter(e -> e.getValue().equals(required))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(required);
                throw new EndpointResolutionException("Required endpoint not found: " + endpointName);
            }
        }
    }

    public EndpointDefinition getEndpoint(String operation) {
        EndpointDefinition def = operationMap.get(operation);
        if (def == null) {
            throw new EndpointResolutionException("Endpoint not resolved for operation: " + operation);
        }
        return def;
    }

    public List<EndpointDefinition> getAllEndpoints() {
        return allEndpoints;
    }

    public String getBasePath() {
        return basePath;
    }

    @SuppressWarnings("unchecked")
    private EndpointDefinition parseEndpoint(Map<String, Object> entry) {
        EndpointDefinition def = new EndpointDefinition();
        def.setName((String) entry.get("name"));
        def.setMethod((String) entry.get("method"));
        def.setPath((String) entry.get("path"));
        def.setAbsolutePath(Boolean.TRUE.equals(entry.get("absolutePath")));

        if (entry.containsKey("auth")) {
            Map<String, Object> authMap = (Map<String, Object>) entry.get("auth");
            AuthConfig auth = new AuthConfig();
            auth.setType((String) authMap.get("type"));
            auth.setHeaderName((String) authMap.getOrDefault("headerName", null));
            def.setAuth(auth);
        }

        if (entry.containsKey("requestHeaders")) {
            List<Map<String, Object>> headersList = (List<Map<String, Object>>) entry.get("requestHeaders");
            List<HeaderDef> headers = new ArrayList<>();
            for (Map<String, Object> hdr : headersList) {
                HeaderDef headerDef = new HeaderDef();
                headerDef.setName((String) hdr.get("name"));
                headerDef.setValue(hdr.get("value") != null ? hdr.get("value").toString() : null);
                headerDef.setValueFrom((String) hdr.getOrDefault("valueFrom", null));
                headers.add(headerDef);
            }
            def.setRequestHeaders(headers);
        }

        if (entry.containsKey("requestBody")) {
            Map<String, Object> bodyMap = (Map<String, Object>) entry.get("requestBody");
            BodyConfig body = new BodyConfig();
            body.setType((String) bodyMap.get("type"));
            body.setSchema(bodyMap.getOrDefault("schema", null));
            def.setRequestBody(body);
        }

        if (entry.containsKey("response")) {
            Map<String, Object> respMap = (Map<String, Object>) entry.get("response");
            ResponseConfig resp = new ResponseConfig();
            resp.setType((String) respMap.get("type"));
            resp.setSaveAs((String) respMap.getOrDefault("saveAs", null));
            def.setResponse(resp);
        }

        return def;
    }
}
