package com.clmextract.config;

import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigLoader {

    private static final Set<String> VALID_CSV_MODES = Set.of(
            "per-component", "merged-single", "single-only"
    );

    public static AppConfig load(String configPath) {
        Map<String, Object> root = parseYaml(configPath);

        AppConfig config = new AppConfig();

        // --- server section ---
        Map<String, Object> server = getMap(root, "server");
        if (server != null) {
            config.setBaseUrl(getStringOrDefault(server, "url", null));
            config.setUsername(getStringOrDefault(server, "username", null));
            config.setPassword(getStringOrDefault(server, "password", null));
        }

        // --- retry section ---
        Map<String, Object> retry = getMap(root, "retry");
        if (retry != null) {
            config.setRetryMaxAttempts(getIntOrDefault(retry, "maxAttempts", 3));
            config.setRetryBaseDelayMs(getLongOrDefault(retry, "baseDelayMs", 1000L));
        }

        // --- boTypes ---
        List<Map<String, Object>> boTypesList = getListOfMaps(root, "boTypes");
        if (boTypesList != null) {
            List<BoTypeConfig> boTypes = new ArrayList<>();
            for (Map<String, Object> entry : boTypesList) {
                BoTypeConfig bt = new BoTypeConfig();
                bt.setName(getStringOrDefault(entry, "name", null));
                bt.setTrackingFilter(getStringOrDefault(root, "trackingFilter", null));
                bt.setFilenameTemplate(getStringOrDefault(entry, "filenameTemplate", null));
                boTypes.add(bt);
            }
            config.setBoTypes(boTypes);
        }

        // --- simple top-level fields ---
        config.setEndpointsFile(getStringOrDefault(root, "endpointsFile", "inputs/endpoints.yml"));
        config.setCsvMode(getStringOrDefault(root, "csvMode", "per-component"));
        config.setFilenameTemplate(getStringOrDefault(root, "filenameTemplate",
                "{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv"));
        config.setDownloadsFilenameTemplate(getStringOrDefault(root, "downloadsFilenameTemplate",
                "{BO}_AttachmentsToDownload_{DDMMYYYY}_{HHMMSS}.csv"));
        config.setOutputRoot(getStringOrDefault(root, "outputRoot", null));
        config.setExportFolderName(getStringOrDefault(root, "exportFolderName", "MetaData"));
        config.setBatchSize(getIntOrDefault(root, "batchSize", 100));
        config.setBackupRetentionDays(getIntOrDefault(root, "backupRetentionDays", 30));
        config.setOfflineMode(getBooleanOrDefault(root, "offlineMode", false));

        // --- delimiter ---
        String delimiterStr = getStringOrDefault(root, "delimiter", ",");
        config.setDelimiter(delimiterStr.isEmpty() ? ',' : delimiterStr.charAt(0));

        // --- validation ---
        validate(config);

        return config;
    }

    // ---------------------------------------------------------------
    // YAML parsing
    // ---------------------------------------------------------------

    private static Map<String, Object> parseYaml(String configPath) {
        try (InputStream in = new FileInputStream(configPath)) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(in);
            if (root == null) {
                root = Map.of();
            }
            return root;
        } catch (FileNotFoundException e) {
            throw new ConfigValidationException("Cannot read config file: " + configPath);
        } catch (IOException e) {
            throw new ConfigValidationException("Cannot read config file: " + configPath);
        }
    }

    // ---------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------

    private static void validate(AppConfig config) {
        requireNonEmpty(config.getBaseUrl(), "server.url is required");
        requireNonEmpty(config.getUsername(), "server.username is required");
        requireNonEmpty(config.getPassword(), "server.password is required");
        requireNonEmpty(config.getOutputRoot(), "outputRoot is required");

        if (!VALID_CSV_MODES.contains(config.getCsvMode())) {
            throw new ConfigValidationException(
                    "csvMode must be one of: per-component, merged-single, single-only");
        }

        if (config.getBatchSize() <= 0) {
            throw new ConfigValidationException("batchSize must be greater than 0");
        }

        if (config.getBackupRetentionDays() < 0) {
            throw new ConfigValidationException("backupRetentionDays must be >= 0");
        }

        List<BoTypeConfig> boTypes = config.getBoTypes();
        for (int i = 0; i < boTypes.size(); i++) {
            if (isNullOrEmpty(boTypes.get(i).getName())) {
                throw new ConfigValidationException("boTypes[" + i + "].name is required");
            }
        }
    }

    private static void requireNonEmpty(String value, String message) {
        if (isNullOrEmpty(value)) {
            throw new ConfigValidationException(message);
        }
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }

    // ---------------------------------------------------------------
    // Helper accessors
    // ---------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> getListOfMaps(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<Map<String, Object>>) value;
        }
        return null;
    }

    private static String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value != null) {
            return value.toString();
        }
        return defaultValue;
    }

    private static int getIntOrDefault(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private static long getLongOrDefault(Map<String, Object> map, String key, long defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }

    private static boolean getBooleanOrDefault(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
}
