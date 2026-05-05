package com.clmextract.config;

import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigLoader {

    private static final Set<String> VALID_CSV_MODES = Set.of(
            "per-component", "merged-single", "single-only"
    );

    private static final Set<String> VALID_BO_USAGE_TYPE_FILTERS = Set.of("Directory", "NonContract", "Contract");

    public static AppConfig loadRaw(String configPath) {
        return parse(parseYaml(configPath));
    }

    public static AppConfig load(String configPath) {
        Map<String, Object> root = parseYaml(configPath);
        AppConfig config = parse(root);
        validate(config);
        return config;
    }

    private static AppConfig parse(Map<String, Object> root) {
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
                bt.setLocalizedName(getStringOrDefault(entry, "localizedName", null));
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
        config.setBoUsageTypeFilter(getStringOrDefault(root, "boUsageTypeFilter", null));
        config.setTrackingFilter(getStringOrDefault(root, "trackingFilter", null));
        config.setReports(parseCommaSeparatedOrList(root, "reports"));
        config.setSkipColumns(getStringList(root, "skipColumns"));
        config.setSkipComponents(getStringList(root, "skipComponents"));
        config.setGenerateParentCsv(getBooleanOrDefault(root, "generateParentCsv", false));
        config.setParentFilenameTemplate(getStringOrDefault(root, "parentFilenameTemplate",
                "{BO}_BundleParent_{DDMMYYYY}_{HHMMSS}.csv"));
        config.setGenerateSummaryCsv(getBooleanOrDefault(root, "generateSummaryCsv", false));
        config.setSummaryFilenameTemplate(getStringOrDefault(root, "summaryFilenameTemplate",
                "Summary_{DDMMYYYY}_{HHMMSS}.csv"));

        // --- delimiter ---
        String delimiterStr = getStringOrDefault(root, "delimiter", ",");
        config.setDelimiter(delimiterStr.isEmpty() ? ',' : delimiterStr.charAt(0));

        // --- delimiterReplacement section ---
        Map<String, Object> delimiterReplacement = getMap(root, "delimiterReplacement");
        if (delimiterReplacement != null) {
            config.setDelimiterReplacementEnabled(getBooleanOrDefault(delimiterReplacement, "enabled", false));
            config.setDelimiterSubstituteChar(getStringOrDefault(delimiterReplacement, "substituteChar", null));
        }

        // --- additionalColumns ---
        List<Map<String, Object>> addColsList = getListOfMaps(root, "additionalColumns");
        if (addColsList != null) {
            List<AdditionalColumnConfig> addCols = new ArrayList<>();
            for (Map<String, Object> colEntry : addColsList) {
                String header = getStringOrDefault(colEntry, "header", null);
                if (header != null) {
                    AdditionalColumnConfig col = new AdditionalColumnConfig();
                    col.setHeader(header);
                    col.setPosition(getIntOrDefault(colEntry, "position", 1));
                    addCols.add(col);
                }
            }
            config.setAdditionalColumns(addCols);
        }

        // --- dateFormat section ---
        Map<String, Object> dateFormat = getMap(root, "dateFormat");
        if (dateFormat != null) {
            DateFormatConfig dateFormatConfig = new DateFormatConfig();
            dateFormatConfig.setInputFormats(getStringListOrDefault(dateFormat, "inputFormats", null));
            dateFormatConfig.setOutputFormat(getStringOrDefault(dateFormat, "outputFormat", null));
            dateFormatConfig.setInputDateTimeFormats(getStringListOrDefault(dateFormat, "inputDateTimeFormats", null));
            dateFormatConfig.setOutputDateTimeFormat(getStringOrDefault(dateFormat, "outputDateTimeFormat", null));
            config.setDateFormat(dateFormatConfig);
        }

        // --- sftp section ---
        Map<String, Object> sftp = getMap(root, "sftp");
        if (sftp != null) {
            AppConfig.SftpConfig sftpConfig = new AppConfig.SftpConfig();
            sftpConfig.setHost(getStringOrDefault(sftp, "host", ""));
            sftpConfig.setPort(getIntOrDefault(sftp, "port", 22));
            sftpConfig.setUsername(getStringOrDefault(sftp, "username", ""));
            sftpConfig.setPassword(getStringOrDefault(sftp, "password", ""));
            config.setSftp(sftpConfig);
        }

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

        String boUsageTypeFilter = config.getBoUsageTypeFilter();
        if (boUsageTypeFilter != null && !boUsageTypeFilter.isEmpty()
                && !VALID_BO_USAGE_TYPE_FILTERS.contains(boUsageTypeFilter)) {
            throw new ConfigValidationException(
                    "Invalid boUsageTypeFilter value: '" + boUsageTypeFilter
                            + "'. Allowed values: Directory, NonContract, Contract.");
        }

        if (config.isDelimiterReplacementEnabled()) {
            if (isNullOrEmpty(config.getDelimiterSubstituteChar())) {
                throw new ConfigValidationException(
                    "delimiterReplacement.substituteChar is required when replacement is enabled");
            }
            char delim = config.getDelimiter();
            for (char c : config.getDelimiterSubstituteChar().toCharArray()) {
                if (c == delim) {
                    throw new ConfigValidationException(
                        "delimiterReplacement.substituteChar must not contain the delimiter character ('" + delim + "')");
                }
            }
        }

        DateFormatConfig dateFormat = config.getDateFormat();
        if (dateFormat != null) {
            boolean hasInput = dateFormat.getInputFormats() != null && !dateFormat.getInputFormats().isEmpty();
            boolean hasOutput = dateFormat.getOutputFormat() != null && !dateFormat.getOutputFormat().isEmpty();
            if (hasInput && !hasOutput) {
                throw new ConfigValidationException("dateFormat.outputFormat is required when dateFormat.inputFormats is set");
            }
            if (!hasInput && hasOutput) {
                throw new ConfigValidationException("dateFormat.inputFormats is required when dateFormat.outputFormat is set");
            }

            boolean hasInputDt = dateFormat.getInputDateTimeFormats() != null && !dateFormat.getInputDateTimeFormats().isEmpty();
            boolean hasOutputDt = dateFormat.getOutputDateTimeFormat() != null && !dateFormat.getOutputDateTimeFormat().isEmpty();
            if (hasInputDt && !hasOutputDt) {
                throw new ConfigValidationException("dateFormat.outputDateTimeFormat is required when dateFormat.inputDateTimeFormats is set");
            }
            if (!hasInputDt && hasOutputDt) {
                throw new ConfigValidationException("dateFormat.inputDateTimeFormats is required when dateFormat.outputDateTimeFormat is set");
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

    @SuppressWarnings("unchecked")
    private static List<String> getStringListOrDefault(Map<String, Object> map, String key, List<String> defaultValue) {
        Object value = map.get(key);
        if (value instanceof List) {
            return ((List<?>) value).stream().map(Object::toString).collect(Collectors.toList());
        }
        if (value != null) {
            return Collections.singletonList(value.toString());
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

    // Accepts either a YAML list or a comma-separated string
    private static List<String> parseCommaSeparatedOrList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return getStringList(map, key);
        }
        if (value != null) {
            List<String> result = new ArrayList<>();
            for (String part : value.toString().split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) result.add(trimmed);
            }
            return result;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static List<String> getStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item != null) result.add(item.toString());
            }
            return result;
        }
        return new ArrayList<>();
    }

    private static boolean getBooleanOrDefault(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
}
