package com.clmextract.web.api;

import com.clmextract.config.AppConfig;
import com.clmextract.config.ConfigLoader;
import com.clmextract.config.DateFormatConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigController {

    private final String configPath;
    private final ObjectMapper objectMapper;

    public ConfigController(String configPath, ObjectMapper objectMapper) {
        this.configPath = configPath;
        this.objectMapper = objectMapper;
    }

    public void getConfig(Context ctx) throws Exception {
        String role = ctx.sessionAttribute("role");
        if (!"ADMIN".equals(role)) {
            ctx.status(403).result("Forbidden");
            return;
        }

        AppConfig config = ConfigLoader.loadRaw(configPath);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("serverUrl", config.getBaseUrl());
        response.put("serverUsername", config.getUsername());
        response.put("serverPassword", config.getPassword());
        response.put("endpointsFile", config.getEndpointsFile());
        response.put("batchSize", config.getBatchSize());
        response.put("retryMaxAttempts", config.getRetryMaxAttempts());
        response.put("retryBaseDelayMs", config.getRetryBaseDelayMs());
        response.put("offlineMode", config.isOfflineMode());

        List<Map<String, Object>> boTypesList = config.getBoTypes().stream()
                .filter(bt -> bt.getName() != null && !bt.getName().isEmpty())
                .map(bt -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", bt.getName());
                    m.put("localizedName", bt.getLocalizedName() != null ? bt.getLocalizedName() : "");
                    return m;
                })
                .collect(Collectors.toList());
        response.put("boTypes", boTypesList);
        response.put("boUsageTypeFilter", config.getBoUsageTypeFilter());
        response.put("trackingFilter", config.getTrackingFilter());
        response.put("outputRoot", config.getOutputRoot());
        response.put("exportFolderName", config.getExportFolderName());
        response.put("backupRetentionDays", config.getBackupRetentionDays());
        response.put("csvMode", config.getCsvMode());
        response.put("delimiter", String.valueOf(config.getDelimiter()));
        response.put("filenameTemplate", config.getFilenameTemplate());
        response.put("downloadsFilenameTemplate", config.getDownloadsFilenameTemplate());
        response.put("generateSummaryCsv", config.isGenerateSummaryCsv());
        response.put("summaryFilenameTemplate", config.getSummaryFilenameTemplate());
        response.put("generateParentCsv", config.isGenerateParentCsv());
        response.put("parentFilenameTemplate", config.getParentFilenameTemplate());
        response.put("skipColumns", config.getSkipColumns());
        response.put("skipComponents", config.getSkipComponents());

        List<Map<String, Object>> addCols = config.getAdditionalColumns().stream().map(ac -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("header", ac.getHeader());
            m.put("position", ac.getPosition());
            return m;
        }).collect(Collectors.toList());
        response.put("additionalColumns", addCols);

        response.put("delimiterReplacementEnabled", config.isDelimiterReplacementEnabled());
        response.put("delimiterSubstituteChar", config.getDelimiterSubstituteChar());
        response.put("yesNoTranslationEnabled", config.isYesNoTranslationEnabled());
        response.put("yesNoTrueValue", config.getYesNoTrueValue());
        response.put("yesNoFalseValue", config.getYesNoFalseValue());

        DateFormatConfig df = config.getDateFormat();
        Map<String, Object> dateFormatMap = new LinkedHashMap<>();
        if (df != null) {
            dateFormatMap.put("inputFormats", df.getInputFormats());
            dateFormatMap.put("outputFormat", df.getOutputFormat());
            dateFormatMap.put("inputDateTimeFormats", df.getInputDateTimeFormats());
            dateFormatMap.put("outputDateTimeFormat", df.getOutputDateTimeFormat());
        } else {
            dateFormatMap.put("inputFormats", List.of());
            dateFormatMap.put("outputFormat", "");
            dateFormatMap.put("inputDateTimeFormats", List.of());
            dateFormatMap.put("outputDateTimeFormat", "");
        }
        response.put("dateFormat", dateFormatMap);

        AppConfig.SftpConfig sftpCfg = config.getSftp();
        Map<String, Object> sftp = new LinkedHashMap<>();
        sftp.put("host", sftpCfg.getHost() != null ? sftpCfg.getHost() : "");
        sftp.put("port", sftpCfg.getPort());
        sftp.put("username", sftpCfg.getUsername() != null ? sftpCfg.getUsername() : "");
        sftp.put("password", sftpCfg.getPassword() != null ? sftpCfg.getPassword() : "");
        response.put("sftp", sftp);
        response.put("enableZipPackaging", config.isEnableZipPackaging());
        response.put("enableSftpUpload", config.isEnableSftpUpload());
        response.put("adminEmails", config.getAdminEmails());

        ctx.contentType("application/json");
        ctx.result(objectMapper.writeValueAsString(response));
    }

    List<Map<String, String>> validateBody(JsonNode body) {
        List<Map<String, String>> errors = new ArrayList<>();

        // Required non-blank string fields
        for (String field : List.of("serverUrl", "endpointsFile", "outputRoot")) {
            JsonNode node = body.get(field);
            if (node == null || node.asText("").isBlank()) {
                errors.add(Map.of("field", field, "message", field + " is required"));
            }
        }

        // Integer >= 1: batchSize, retryMaxAttempts
        for (String field : List.of("batchSize", "retryMaxAttempts")) {
            JsonNode node = body.get(field);
            if (node == null || !node.isNumber() || node.asInt() < 1) {
                errors.add(Map.of("field", field, "message", field + " must be an integer >= 1"));
            }
        }

        // Long >= 0: retryBaseDelayMs, backupRetentionDays
        for (String field : List.of("retryBaseDelayMs", "backupRetentionDays")) {
            JsonNode node = body.get(field);
            if (node == null || !node.isNumber() || node.asLong() < 0) {
                errors.add(Map.of("field", field, "message", field + " must be a number >= 0"));
            }
        }

        // delimiter: exactly 1 character
        JsonNode delimiterNode = body.get("delimiter");
        if (delimiterNode == null || delimiterNode.asText("").length() != 1) {
            errors.add(Map.of("field", "delimiter", "message", "delimiter must be exactly 1 character"));
        }

        // csvMode: one of allowed values
        Set<String> validCsvModes = Set.of("per-component", "merged-single", "single-only");
        JsonNode csvModeNode = body.get("csvMode");
        if (csvModeNode == null || !validCsvModes.contains(csvModeNode.asText(""))) {
            errors.add(Map.of("field", "csvMode", "message", "csvMode must be one of: per-component, merged-single, single-only"));
        }

        // delimiterReplacementEnabled + delimiterSubstituteChar
        JsonNode delimRepEnabled = body.get("delimiterReplacementEnabled");
        if (delimRepEnabled != null && delimRepEnabled.asBoolean(false)) {
            JsonNode substituteChar = body.get("delimiterSubstituteChar");
            if (substituteChar == null || substituteChar.asText("").isBlank()) {
                errors.add(Map.of("field", "delimiterSubstituteChar", "message", "delimiterSubstituteChar is required when delimiterReplacementEnabled is true"));
            }
        }

        return errors;
    }

    public void putConfig(Context ctx) throws Exception {
        String role = ctx.sessionAttribute("role");
        if (!"ADMIN".equals(role)) {
            ctx.status(403).result("Forbidden");
            return;
        }

        JsonNode body = objectMapper.readTree(ctx.body());
        List<Map<String, String>> errors = validateBody(body);

        if (!errors.isEmpty()) {
            ctx.status(400).contentType("application/json")
                    .result(objectMapper.writeValueAsString(Map.of("errors", errors)));
            return;
        }

        // Load current raw config
        AppConfig config = ConfigLoader.loadRaw(configPath);

        // Update all fields from request JSON
        config.setBaseUrl(body.get("serverUrl").asText());
        JsonNode serverUsernameNode = body.get("serverUsername");
        if (serverUsernameNode != null) {
            config.setUsername(serverUsernameNode.asText(config.getUsername()));
        }
        JsonNode serverPasswordNode = body.get("serverPassword");
        if (serverPasswordNode != null) {
            config.setPassword(serverPasswordNode.asText(config.getPassword()));
        }
        config.setEndpointsFile(body.get("endpointsFile").asText());
        config.setOutputRoot(body.get("outputRoot").asText());
        config.setBatchSize(body.get("batchSize").asInt());
        config.setRetryMaxAttempts(body.get("retryMaxAttempts").asInt());
        config.setRetryBaseDelayMs(body.get("retryBaseDelayMs").asLong());
        config.setBackupRetentionDays((int) body.get("backupRetentionDays").asLong());
        config.setDelimiter(body.get("delimiter").asText().charAt(0));
        config.setCsvMode(body.get("csvMode").asText());

        JsonNode exportFolderNode = body.get("exportFolderName");
        if (exportFolderNode != null) config.setExportFolderName(exportFolderNode.asText(config.getExportFolderName()));

        JsonNode offlineModeNode = body.get("offlineMode");
        if (offlineModeNode != null) config.setOfflineMode(offlineModeNode.asBoolean(false));

        JsonNode filenameTemplateNode = body.get("filenameTemplate");
        if (filenameTemplateNode != null) config.setFilenameTemplate(filenameTemplateNode.asText(config.getFilenameTemplate()));

        JsonNode downloadsFilenameTemplateNode = body.get("downloadsFilenameTemplate");
        if (downloadsFilenameTemplateNode != null) config.setDownloadsFilenameTemplate(downloadsFilenameTemplateNode.asText(config.getDownloadsFilenameTemplate()));

        JsonNode generateParentCsvNode = body.get("generateParentCsv");
        if (generateParentCsvNode != null) config.setGenerateParentCsv(generateParentCsvNode.asBoolean(false));

        JsonNode parentFilenameTemplateNode = body.get("parentFilenameTemplate");
        if (parentFilenameTemplateNode != null) config.setParentFilenameTemplate(parentFilenameTemplateNode.asText(config.getParentFilenameTemplate()));

        JsonNode generateSummaryCsvNode = body.get("generateSummaryCsv");
        if (generateSummaryCsvNode != null) config.setGenerateSummaryCsv(generateSummaryCsvNode.asBoolean(false));

        JsonNode summaryFilenameTemplateNode = body.get("summaryFilenameTemplate");
        if (summaryFilenameTemplateNode != null) config.setSummaryFilenameTemplate(summaryFilenameTemplateNode.asText(config.getSummaryFilenameTemplate()));

        JsonNode boUsageTypeFilterNode = body.get("boUsageTypeFilter");
        if (boUsageTypeFilterNode != null) config.setBoUsageTypeFilter(boUsageTypeFilterNode.isNull() ? null : boUsageTypeFilterNode.asText());

        JsonNode trackingFilterNode = body.get("trackingFilter");
        if (trackingFilterNode != null) config.setTrackingFilter(trackingFilterNode.isNull() ? null : trackingFilterNode.asText());

        JsonNode delimRepEnabledNode = body.get("delimiterReplacementEnabled");
        if (delimRepEnabledNode != null) config.setDelimiterReplacementEnabled(delimRepEnabledNode.asBoolean(false));

        JsonNode delimSubstNode = body.get("delimiterSubstituteChar");
        if (delimSubstNode != null) config.setDelimiterSubstituteChar(delimSubstNode.isNull() ? null : delimSubstNode.asText());

        JsonNode yesNoEnabledNode = body.get("yesNoTranslationEnabled");
        if (yesNoEnabledNode != null) config.setYesNoTranslationEnabled(yesNoEnabledNode.asBoolean(false));

        JsonNode yesNoTrueNode = body.get("yesNoTrueValue");
        if (yesNoTrueNode != null) config.setYesNoTrueValue(yesNoTrueNode.asText(config.getYesNoTrueValue()));

        JsonNode yesNoFalseNode = body.get("yesNoFalseValue");
        if (yesNoFalseNode != null) config.setYesNoFalseValue(yesNoFalseNode.asText(config.getYesNoFalseValue()));

        // boTypes
        JsonNode boTypesNode = body.get("boTypes");
        if (boTypesNode != null && boTypesNode.isArray()) {
            List<com.clmextract.config.BoTypeConfig> boTypes = new ArrayList<>();
            for (JsonNode btNode : boTypesNode) {
                String btName = btNode.has("name") ? btNode.get("name").asText("") : btNode.asText("");
                if (btName.isEmpty()) continue;
                com.clmextract.config.BoTypeConfig bt = new com.clmextract.config.BoTypeConfig();
                bt.setName(btName);
                String localizedName = btNode.has("localizedName") ? btNode.get("localizedName").asText("") : "";
                if (!localizedName.isEmpty()) bt.setLocalizedName(localizedName);
                boTypes.add(bt);
            }
            config.setBoTypes(boTypes);
        }

        // skipColumns
        JsonNode skipColumnsNode = body.get("skipColumns");
        if (skipColumnsNode != null && skipColumnsNode.isArray()) {
            List<String> skipColumns = new ArrayList<>();
            for (JsonNode n : skipColumnsNode) skipColumns.add(n.asText());
            config.setSkipColumns(skipColumns);
        }

        // skipComponents
        JsonNode skipComponentsNode = body.get("skipComponents");
        if (skipComponentsNode != null && skipComponentsNode.isArray()) {
            List<String> skipComponents = new ArrayList<>();
            for (JsonNode n : skipComponentsNode) skipComponents.add(n.asText());
            config.setSkipComponents(skipComponents);
        }

        // additionalColumns
        JsonNode addColsNode = body.get("additionalColumns");
        if (addColsNode != null && addColsNode.isArray()) {
            List<com.clmextract.config.AdditionalColumnConfig> addCols = new ArrayList<>();
            for (JsonNode colNode : addColsNode) {
                JsonNode headerNode = colNode.get("header");
                if (headerNode != null && !headerNode.asText("").isBlank()) {
                    com.clmextract.config.AdditionalColumnConfig col = new com.clmextract.config.AdditionalColumnConfig();
                    col.setHeader(headerNode.asText());
                    JsonNode posNode = colNode.get("position");
                    col.setPosition(posNode != null ? posNode.asInt(1) : 1);
                    addCols.add(col);
                }
            }
            config.setAdditionalColumns(addCols);
        }

        // dateFormat
        JsonNode dateFormatNode = body.get("dateFormat");
        if (dateFormatNode != null && dateFormatNode.isObject()) {
            DateFormatConfig dfConfig = config.getDateFormat() != null ? config.getDateFormat() : new DateFormatConfig();
            JsonNode inputFormatsNode = dateFormatNode.get("inputFormats");
            if (inputFormatsNode != null && inputFormatsNode.isArray()) {
                List<String> formats = new ArrayList<>();
                for (JsonNode n : inputFormatsNode) formats.add(n.asText());
                dfConfig.setInputFormats(formats);
            }
            JsonNode outputFormatNode = dateFormatNode.get("outputFormat");
            if (outputFormatNode != null) dfConfig.setOutputFormat(outputFormatNode.asText(""));
            JsonNode inputDtFormatsNode = dateFormatNode.get("inputDateTimeFormats");
            if (inputDtFormatsNode != null && inputDtFormatsNode.isArray()) {
                List<String> formats = new ArrayList<>();
                for (JsonNode n : inputDtFormatsNode) formats.add(n.asText());
                dfConfig.setInputDateTimeFormats(formats);
            }
            JsonNode outputDtFormatNode = dateFormatNode.get("outputDateTimeFormat");
            if (outputDtFormatNode != null) dfConfig.setOutputDateTimeFormat(outputDtFormatNode.asText(""));
            config.setDateFormat(dfConfig);
        }

        // sftp
        JsonNode sftpNode = body.get("sftp");
        if (sftpNode != null && sftpNode.isObject()) {
            AppConfig.SftpConfig sftpConfig = config.getSftp() != null ? config.getSftp() : new AppConfig.SftpConfig();
            JsonNode hostNode = sftpNode.get("host");
            if (hostNode != null) sftpConfig.setHost(hostNode.asText(""));
            JsonNode portNode = sftpNode.get("port");
            if (portNode != null) sftpConfig.setPort(portNode.asInt(22));
            JsonNode sftpUsernameNode = sftpNode.get("username");
            if (sftpUsernameNode != null) sftpConfig.setUsername(sftpUsernameNode.asText(""));
            JsonNode sftpPasswordNode = sftpNode.get("password");
            if (sftpPasswordNode != null) sftpConfig.setPassword(sftpPasswordNode.asText(""));
            config.setSftp(sftpConfig);
        }

        JsonNode enableZipNode = body.get("enableZipPackaging");
        if (enableZipNode != null) config.setEnableZipPackaging(enableZipNode.asBoolean(true));

        JsonNode enableSftpNode = body.get("enableSftpUpload");
        if (enableSftpNode != null) config.setEnableSftpUpload(enableSftpNode.asBoolean(true));

        JsonNode adminEmailsNode = body.get("adminEmails");
        if (adminEmailsNode != null && adminEmailsNode.isArray()) {
            List<String> adminEmails = new ArrayList<>();
            for (JsonNode n : adminEmailsNode) {
                String email = n.asText("").trim();
                if (!email.isEmpty()) adminEmails.add(email);
            }
            config.setAdminEmails(adminEmails);
        }

        // Build YAML map and write to file
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> serverMap = new LinkedHashMap<>();
        serverMap.put("url", config.getBaseUrl());
        serverMap.put("username", config.getUsername());
        serverMap.put("password", config.getPassword());
        root.put("server", serverMap);

        root.put("endpointsFile", config.getEndpointsFile());
        root.put("batchSize", config.getBatchSize());
        root.put("outputRoot", config.getOutputRoot());
        root.put("exportFolderName", config.getExportFolderName());
        root.put("backupRetentionDays", config.getBackupRetentionDays());
        root.put("offlineMode", config.isOfflineMode());
        root.put("csvMode", config.getCsvMode());
        root.put("delimiter", String.valueOf(config.getDelimiter()));
        root.put("filenameTemplate", config.getFilenameTemplate());
        root.put("downloadsFilenameTemplate", config.getDownloadsFilenameTemplate());
        root.put("generateParentCsv", config.isGenerateParentCsv());
        root.put("parentFilenameTemplate", config.getParentFilenameTemplate());
        root.put("generateSummaryCsv", config.isGenerateSummaryCsv());
        root.put("summaryFilenameTemplate", config.getSummaryFilenameTemplate());

        if (config.getBoUsageTypeFilter() != null) root.put("boUsageTypeFilter", config.getBoUsageTypeFilter());
        if (config.getTrackingFilter() != null) root.put("trackingFilter", config.getTrackingFilter());

        // boTypes as List<Map<String,Object>>
        List<Map<String, Object>> boTypesYaml = new ArrayList<>();
        if (body.has("boTypes") && body.get("boTypes").isArray()) {
            for (JsonNode bt : body.get("boTypes")) {
                Map<String, Object> btMap = new LinkedHashMap<>();
                btMap.put("name", bt.has("name") ? bt.get("name").asText("") : bt.asText(""));
                String localizedName = bt.has("localizedName") ? bt.get("localizedName").asText("") : "";
                if (!localizedName.isEmpty()) btMap.put("localizedName", localizedName);
                if (!btMap.get("name").toString().isEmpty()) boTypesYaml.add(btMap);
            }
        }
        root.put("boTypes", boTypesYaml);

        root.put("skipColumns", config.getSkipColumns());
        root.put("skipComponents", config.getSkipComponents());

        Map<String, Object> retryMap = new LinkedHashMap<>();
        retryMap.put("maxAttempts", config.getRetryMaxAttempts());
        retryMap.put("baseDelayMs", config.getRetryBaseDelayMs());
        root.put("retry", retryMap);

        // delimiterReplacement
        Map<String, Object> delimRepMap = new LinkedHashMap<>();
        delimRepMap.put("enabled", config.isDelimiterReplacementEnabled());
        delimRepMap.put("substituteChar", config.getDelimiterSubstituteChar());
        root.put("delimiterReplacement", delimRepMap);

        // additionalColumns
        List<Map<String, Object>> addColsMaps = new ArrayList<>();
        for (com.clmextract.config.AdditionalColumnConfig ac : config.getAdditionalColumns()) {
            Map<String, Object> acMap = new LinkedHashMap<>();
            acMap.put("header", ac.getHeader());
            acMap.put("position", ac.getPosition());
            addColsMaps.add(acMap);
        }
        root.put("additionalColumns", addColsMaps);

        root.put("yesNoTranslationEnabled", config.isYesNoTranslationEnabled());
        root.put("yesNoTrueValue", config.getYesNoTrueValue());
        root.put("yesNoFalseValue", config.getYesNoFalseValue());

        // dateFormat
        Map<String, Object> dateFormatMap = new LinkedHashMap<>();
        DateFormatConfig df = config.getDateFormat();
        if (df != null) {
            dateFormatMap.put("inputFormats", df.getInputFormats() != null ? df.getInputFormats() : List.of());
            dateFormatMap.put("outputFormat", df.getOutputFormat() != null ? df.getOutputFormat() : "");
            dateFormatMap.put("inputDateTimeFormats", df.getInputDateTimeFormats() != null ? df.getInputDateTimeFormats() : List.of());
            dateFormatMap.put("outputDateTimeFormat", df.getOutputDateTimeFormat() != null ? df.getOutputDateTimeFormat() : "");
        } else {
            dateFormatMap.put("inputFormats", List.of());
            dateFormatMap.put("outputFormat", "");
            dateFormatMap.put("inputDateTimeFormats", List.of());
            dateFormatMap.put("outputDateTimeFormat", "");
        }
        root.put("dateFormat", dateFormatMap);

        // sftp
        Map<String, Object> sftpMap = new LinkedHashMap<>();
        sftpMap.put("host", config.getSftp().getHost());
        sftpMap.put("port", config.getSftp().getPort());
        sftpMap.put("username", config.getSftp().getUsername());
        sftpMap.put("password", config.getSftp().getPassword());
        root.put("sftp", sftpMap);
        root.put("enableZipPackaging", config.isEnableZipPackaging());
        root.put("enableSftpUpload", config.isEnableSftpUpload());
        root.put("adminEmails", config.getAdminEmails());

        String yaml = new Yaml().dump(root);
        Files.writeString(Path.of(configPath), yaml);

        ctx.status(200).result("OK");
    }
}
