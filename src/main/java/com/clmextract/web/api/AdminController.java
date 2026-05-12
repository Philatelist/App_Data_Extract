package com.clmextract.web.api;

import com.clmextract.config.AppConfig;
import com.clmextract.config.BoTypeConfig;
import com.clmextract.config.ConfigLoader;
import com.clmextract.endpoint.EndpointRegistry;
import com.clmextract.export.ApiDataSource;
import com.clmextract.metadata.BoMetadata;
import com.clmextract.metadata.ComponentMetadata;
import com.clmextract.metadata.FieldMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class AdminController {

    private static final Logger logger = LogManager.getLogger(AdminController.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String configPath;
    private final AppConfig config;

    public AdminController(String configPath, AppConfig config) {
        this.configPath = configPath;
        this.config = config;
    }

    public void getBoTypes(Context ctx) throws Exception {
        String role = ctx.sessionAttribute("role");
        if (!"ADMIN".equals(role)) {
            ctx.status(403).result("Forbidden");
            return;
        }

        String clmSessionId = ctx.sessionAttribute("clmSessionId");
        if (clmSessionId == null || clmSessionId.isBlank()) {
            ctx.status(401).contentType("application/json")
               .result(mapper.writeValueAsString(Map.of("error", "CLM session expired. Please log in again.")));
            return;
        }

        // Build ApiDataSource using admin's CLM session
        AppConfig liveConfig = ConfigLoader.loadRaw(configPath);
        EndpointRegistry registry = new EndpointRegistry(liveConfig.getEndpointsFile());
        registry.load();
        ApiDataSource dataSource = new ApiDataSource(liveConfig, registry);
        dataSource.injectSessionId(clmSessionId);

        // Step 1: Fetch BO type names
        List<String> boTypeNames = dataSource.getBoTypes();

        // Step 2: Fetch display names + usage type in parallel (5 threads, 30 s timeout each)
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(5, Math.max(1, boTypeNames.size())));
        Map<String, BoMetadata> metadataMap = new ConcurrentHashMap<>();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (String name : boTypeNames) {
                futures.add(pool.submit(() -> {
                    try {
                        BoMetadata meta = dataSource.getMetadata(name);
                        if (meta != null) metadataMap.put(name, meta);
                    } catch (Exception e) {
                        logger.warn("Failed to fetch metadata for BO '{}': {}", name, e.getMessage());
                    }
                }));
            }
            for (Future<?> f : futures) {
                try { f.get(30, TimeUnit.SECONDS); }
                catch (TimeoutException e) { logger.warn("Metadata fetch timed out for a BO type"); f.cancel(true); }
                catch (Exception e) { logger.warn("Metadata fetch failed: {}", e.getMessage()); }
            }
        } finally {
            pool.shutdownNow();
        }

        // Step 3: Load current config to determine checked/localizedName state
        AppConfig currentConfig = ConfigLoader.loadRaw(configPath);
        Map<String, BoTypeConfig> configuredBoMap = currentConfig.getBoTypes().stream()
                .filter(bt -> bt.getName() != null && !bt.getName().isBlank())
                .collect(Collectors.toMap(BoTypeConfig::getName, bt -> bt, (a, b) -> a));

        // Step 4: Build response
        List<Map<String, Object>> result = buildBoTypeResponse(boTypeNames, metadataMap, configuredBoMap);

        ctx.contentType("application/json").result(mapper.writeValueAsString(result));
    }

    public void getBoMetadata(Context ctx) throws Exception {
        String role = ctx.sessionAttribute("role");
        if (!"ADMIN".equals(role)) { ctx.status(403).result("Forbidden"); return; }

        String clmSessionId = ctx.sessionAttribute("clmSessionId");
        if (clmSessionId == null || clmSessionId.isBlank()) {
            ctx.status(401).contentType("application/json")
               .result(mapper.writeValueAsString(Map.of("error", "CLM session expired.")));
            return;
        }

        String boType = ctx.pathParam("boType");

        AppConfig liveConfig = ConfigLoader.loadRaw(configPath);
        EndpointRegistry registry = new EndpointRegistry(liveConfig.getEndpointsFile());
        registry.load();
        ApiDataSource dataSource = new ApiDataSource(liveConfig, registry);
        dataSource.injectSessionId(clmSessionId);

        BoMetadata metadata = dataSource.getMetadata(boType);

        ctx.contentType("application/json").result(mapper.writeValueAsString(buildMetadataResponse(metadata)));
    }

    public void getColumns(Context ctx) throws Exception {
        String role = ctx.sessionAttribute("role");
        if (!"ADMIN".equals(role)) { ctx.status(403).result("Forbidden"); return; }

        String boType = ctx.pathParam("boType");
        Path columnFile = resolveColumnFile(boType);

        if (!Files.exists(columnFile)) {
            ctx.contentType("application/json")
               .result(mapper.writeValueAsString(Map.of("fieldPaths", (Object) null)));
            return;
        }

        List<String> paths = Files.readAllLines(columnFile).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        ctx.contentType("application/json")
           .result(mapper.writeValueAsString(Map.of("fieldPaths", paths)));
    }

    public void putColumns(Context ctx) throws Exception {
        String role = ctx.sessionAttribute("role");
        if (!"ADMIN".equals(role)) { ctx.status(403).result("Forbidden"); return; }

        String boType = ctx.pathParam("boType");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        Object fieldPathsObj = body != null ? body.get("fieldPaths") : null;
        if (!(fieldPathsObj instanceof List)) {
            ctx.status(400).result("fieldPaths array is required");
            return;
        }
        @SuppressWarnings("unchecked")
        List<String> fieldPaths = (List<String>) fieldPathsObj;

        Path columnFile = resolveColumnFile(boType);
        Files.createDirectories(columnFile.getParent());
        String content = String.join("\n", fieldPaths);
        Files.writeString(columnFile, content);

        ctx.status(200).result("OK");
    }

    /**
     * Returns the Path for a BO type's column file.
     * Protected to allow overriding in tests.
     */
    protected Path resolveColumnFile(String boType) {
        return Path.of("config", "columns", boType + ".csv");
    }

    /**
     * Converts a BoMetadata object into the JSON-serialisable map structure.
     * Extracted as a static helper to allow unit testing without a Javalin Context.
     */
    static Map<String, Object> buildMetadataResponse(BoMetadata metadata) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("boName", metadata.getBoName());
        response.put("boDisplayName", metadata.getBoDisplayName());

        List<Map<String, Object>> components = new ArrayList<>();
        if (metadata.getComponents() != null) {
            for (ComponentMetadata comp : metadata.getComponents()) {
                Map<String, Object> compMap = new LinkedHashMap<>();
                compMap.put("internalName", comp.getInternalName());
                compMap.put("displayName", comp.getDisplayName());
                compMap.put("cardinality", comp.getCardinality());

                List<Map<String, Object>> fields = new ArrayList<>();
                if (comp.getFields() != null) {
                    for (FieldMetadata field : comp.getFields()) {
                        Map<String, Object> fieldMap = new LinkedHashMap<>();
                        fieldMap.put("internalName", field.getInternalName());
                        fieldMap.put("displayName", field.getDisplayName());
                        String raw = field.getInstancePath();
                        String instancePath = (raw != null) ? raw.replace("MCPDef:/", "") : "";
                        fieldMap.put("instancePath", instancePath);
                        fieldMap.put("dataType", field.getDataType());
                        fields.add(fieldMap);
                    }
                }
                compMap.put("fields", fields);
                components.add(compMap);
            }
        }
        response.put("components", components);
        return response;
    }

    /**
     * Builds the BO type response list from the discovered names, metadata, and current config.
     * Extracted to allow unit testing without a Javalin Context.
     */
    static List<Map<String, Object>> buildBoTypeResponse(
            List<String> boTypeNames,
            Map<String, BoMetadata> metadataMap,
            Map<String, BoTypeConfig> configuredBoMap) {

        List<Map<String, Object>> result = new ArrayList<>();
        for (String name : boTypeNames) {
            BoMetadata meta = metadataMap.get(name);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("internalName", name);
            entry.put("displayName", meta != null ? meta.getBoDisplayName() : name);
            entry.put("usageType", meta != null && meta.getBoUsageType() != null ? meta.getBoUsageType() : "");
            entry.put("checked", configuredBoMap.containsKey(name));
            BoTypeConfig existing = configuredBoMap.get(name);
            entry.put("localizedName", existing != null && existing.getLocalizedName() != null ? existing.getLocalizedName() : "");
            result.add(entry);
        }
        return result;
    }
}
