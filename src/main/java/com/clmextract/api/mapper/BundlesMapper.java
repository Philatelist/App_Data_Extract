package com.clmextract.api.mapper;

import com.clmextract.api.InstancePathUtil;
import com.clmextract.api.InstancePathUtil.ParsedPath;
import com.clmextract.api.dto.BundleFieldDto;
import com.clmextract.export.BundleComponent;
import com.clmextract.export.BundleRecord;
import com.clmextract.export.BundleResponse;
import com.clmextract.metadata.BoMetadata;
import com.clmextract.metadata.ComponentMetadata;
import com.clmextract.metadata.FieldMetadata;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BundlesMapper {

    private static final Pattern DECIMAL_ENTITY = Pattern.compile("&#(\\d+);");
    private static final Pattern HEX_ENTITY = Pattern.compile("&#x([0-9a-fA-F]+);", Pattern.CASE_INSENSITIVE);

    private final char delimiter;
    private final String delimiterSubstituteChar;
    private final boolean yesNoTranslationEnabled;
    private final String yesNoTrueValue;
    private final String yesNoFalseValue;

    public BundlesMapper() {
        this(',', null, false, "YES", "NO");
    }

    public BundlesMapper(char delimiter, String delimiterSubstituteChar) {
        this(delimiter, delimiterSubstituteChar, false, "YES", "NO");
    }

    public BundlesMapper(char delimiter, String delimiterSubstituteChar,
                         boolean yesNoTranslationEnabled, String yesNoTrueValue, String yesNoFalseValue) {
        this.delimiter = delimiter;
        this.delimiterSubstituteChar = delimiterSubstituteChar;
        this.yesNoTranslationEnabled = yesNoTranslationEnabled;
        this.yesNoTrueValue = yesNoTrueValue;
        this.yesNoFalseValue = yesNoFalseValue;
    }

    private String applyDelimiterReplacement(String value) {
        if (delimiterSubstituteChar == null || value == null) return value;
        return value.replace(String.valueOf(delimiter), delimiterSubstituteChar);
    }

    private String applyYesNoTranslation(String value, String fieldType) {
        if (!yesNoTranslationEnabled) return value;
        if (!"yesNoRadioButtons".equals(fieldType) && !"genericBoolean".equals(fieldType)) return value;
        if (value == null || value.isBlank()) return value;
        if ("true".equalsIgnoreCase(value)) return yesNoTrueValue;
        if ("false".equalsIgnoreCase(value)) return yesNoFalseValue;
        return value;
    }

    static String unescapeHtml(String value) {
        if (value == null) return null;
        String result = value.replace("\r\n", " ").replace("\r", " ").replace("\n", " ");
        if (!result.contains("&")) return result;
        result = result
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&nbsp;", "\u00A0");
        result = replaceNumericEntities(result, DECIMAL_ENTITY, 10);
        result = replaceNumericEntities(result, HEX_ENTITY, 16);
        return result;
    }

    private static String normalizeValue(String fieldName, String value) {
        if (value == null) return null;
        if ("serverFileName".equals(fieldName)) {
            return value.replace('\\', '/');
        }
        return value;
    }

    private static String replaceNumericEntities(String input, Pattern pattern, int radix) {
        Matcher m = pattern.matcher(input);
        if (!m.find()) return input;
        StringBuilder sb = new StringBuilder();
        m.reset();
        while (m.find()) {
            int codePoint = Integer.parseInt(m.group(1), radix);
            m.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
        }
        m.appendTail(sb);
        return sb.toString();
    }


    public BundleResponse map(List<List<BundleFieldDto>> rawRecords, BoMetadata metadata, List<Long> requestTrackingIds) {
        BundleResponse response = new BundleResponse();
        response.setBoName(metadata.getBoName());

        // Build a lookup: component internal name → cardinality from metadata
        Map<String, String> cardinalityByComponent = new HashMap<>();
        // Build a lookup: component internal name → field internal name → dataType
        Map<String, Map<String, String>> fieldTypeMap = new HashMap<>();
        if (metadata.getComponents() != null) {
            for (ComponentMetadata comp : metadata.getComponents()) {
                cardinalityByComponent.put(comp.getInternalName(), comp.getCardinality());
                if (comp.getFields() != null) {
                    Map<String, String> fieldTypes = new HashMap<>();
                    for (FieldMetadata field : comp.getFields()) {
                        fieldTypes.put(field.getInternalName(), field.getDataType());
                    }
                    fieldTypeMap.put(comp.getInternalName(), fieldTypes);
                }
            }
        }

        boolean usePositionalIds = requestTrackingIds != null && requestTrackingIds.size() == rawRecords.size();

        List<BundleRecord> records = new ArrayList<>();
        for (int i = 0; i < rawRecords.size(); i++) {
            List<BundleFieldDto> rawFields = rawRecords.get(i);
            Long positionalTrackingId = usePositionalIds ? requestTrackingIds.get(i) : null;
            BundleRecord record = mapRecord(rawFields, cardinalityByComponent, fieldTypeMap, positionalTrackingId);
            records.add(record);
        }

        response.setRecords(records);
        return response;
    }

    private BundleRecord mapRecord(List<BundleFieldDto> rawFields,
                                    Map<String, String> cardinalityByComponent,
                                    Map<String, Map<String, String>> fieldTypeMap,
                                    Long positionalTrackingId) {
        BundleRecord record = new BundleRecord();

        // Set tracking ID: prefer positional ID from request, fall back to response field
        if (positionalTrackingId != null) {
            record.setTrackingId(positionalTrackingId);
        } else {
            // Find trackingNumber to set the record's trackingId
            for (BundleFieldDto field : rawFields) {
                if ("trackingNumber".equals(field.getName())) {
                    String val = field.getValue();
                    if (val != null && !val.isBlank()) {
                        try {
                            record.setTrackingId(Long.parseLong(val.trim()));
                        } catch (NumberFormatException ignored) {
                            // leave as 0
                        }
                    }
                    break;
                }
            }
        }

        // Group fields by component name, and within multi-cardinality by instance ID
        // Key: component internal name
        // Value: map of instanceId → list of fields
        Map<String, Map<String, List<BundleFieldDto>>> groupedByComponent = new LinkedHashMap<>();

        for (BundleFieldDto field : rawFields) {
            ParsedPath parsed = InstancePathUtil.parse(field.getInstancePath());
            if (parsed.isEmpty() || parsed.component() == null) {
                continue;
            }

            String componentName = parsed.component();
            String instanceId = parsed.componentInstanceId() != null ? parsed.componentInstanceId() : "_default_";

            groupedByComponent
                    .computeIfAbsent(componentName, k -> new LinkedHashMap<>())
                    .computeIfAbsent(instanceId, k -> new ArrayList<>())
                    .add(field);
        }

        // Convert grouped fields into BundleComponents
        List<BundleComponent> components = new ArrayList<>();

        for (Map.Entry<String, Map<String, List<BundleFieldDto>>> entry : groupedByComponent.entrySet()) {
            String componentName = entry.getKey();
            Map<String, List<BundleFieldDto>> byInstanceId = entry.getValue();

            String cardinality = cardinalityByComponent.getOrDefault(componentName, "single");
            boolean isMultiple = "multiple".equalsIgnoreCase(cardinality);

            if (isMultiple) {
                // Multi-cardinality: each instance ID becomes a row
                BundleComponent comp = new BundleComponent();
                comp.setComponentInternalName(componentName);

                boolean isAttachment = "ReqAttachment".equals(componentName);
                List<Map<String, String>> rows = new ArrayList<>();
                for (List<BundleFieldDto> instanceFields : byInstanceId.values()) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (BundleFieldDto f : instanceFields) {
                        String fieldType = fieldTypeMap.getOrDefault(componentName, Map.of()).get(f.getName());
                        row.put(f.getName(), applyYesNoTranslation(applyDelimiterReplacement(normalizeValue(f.getName(), unescapeHtml(f.getValue()))), fieldType));
                    }
                    if (isAttachment) {
                        String filePath = row.get("serverFileName");
                        if (filePath != null) {
                            int lastSlash = filePath.lastIndexOf('/');
                            row.put("sftpFileName", lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath);
                        }
                    }
                    rows.add(row);
                }

                comp.setRows(rows);
                components.add(comp);
            } else {
                // Single-cardinality: flatten all fields into one map
                BundleComponent comp = new BundleComponent();
                comp.setComponentInternalName(componentName);

                Map<String, String> fields = new LinkedHashMap<>();
                for (List<BundleFieldDto> instanceFields : byInstanceId.values()) {
                    for (BundleFieldDto f : instanceFields) {
                        String fieldType = fieldTypeMap.getOrDefault(componentName, Map.of()).get(f.getName());
                        fields.put(f.getName(), applyYesNoTranslation(applyDelimiterReplacement(normalizeValue(f.getName(), unescapeHtml(f.getValue()))), fieldType));
                    }
                }

                comp.setFields(fields);
                components.add(comp);
            }
        }

        record.setComponents(components);
        return record;
    }
}
