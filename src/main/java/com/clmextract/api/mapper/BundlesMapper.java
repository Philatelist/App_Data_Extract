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
    // fieldName → (rawValue → mappedValue)
    private final Map<String, Map<String, String>> fieldValueMappings;

    public BundlesMapper() {
        this(',', null, false, "YES", "NO", null);
    }

    public BundlesMapper(char delimiter, String delimiterSubstituteChar) {
        this(delimiter, delimiterSubstituteChar, false, "YES", "NO", null);
    }

    public BundlesMapper(char delimiter, String delimiterSubstituteChar,
                         boolean yesNoTranslationEnabled, String yesNoTrueValue, String yesNoFalseValue,
                         Map<String, Map<String, String>> fieldValueMappings) {
        this.delimiter = delimiter;
        this.delimiterSubstituteChar = delimiterSubstituteChar;
        this.yesNoTranslationEnabled = yesNoTranslationEnabled;
        this.yesNoTrueValue = yesNoTrueValue != null ? yesNoTrueValue : "YES";
        this.yesNoFalseValue = yesNoFalseValue != null ? yesNoFalseValue : "NO";
        // Normalize to lowercase keys for case-insensitive lookup
        this.fieldValueMappings = normalizeMappings(fieldValueMappings);
    }

    private static Map<String, Map<String, String>> normalizeMappings(Map<String, Map<String, String>> raw) {
        if (raw == null || raw.isEmpty()) return Map.of();
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> fieldEntry : raw.entrySet()) {
            Map<String, String> normalizedValues = new LinkedHashMap<>();
            if (fieldEntry.getValue() != null) {
                for (Map.Entry<String, String> valEntry : fieldEntry.getValue().entrySet()) {
                    normalizedValues.put(valEntry.getKey().toLowerCase(), valEntry.getValue());
                }
            }
            result.put(fieldEntry.getKey().toLowerCase(), normalizedValues);
        }
        return result;
    }

    private String applyDelimiterReplacement(String value) {
        if (delimiterSubstituteChar == null || value == null) return value;
        return value.replace(String.valueOf(delimiter), delimiterSubstituteChar);
    }

    private String applyFieldValueMapping(String fieldName, String value) {
        if (value == null || fieldValueMappings.isEmpty()) return value;
        Map<String, String> mappings = fieldValueMappings.get(fieldName.toLowerCase());
        if (mappings == null) return value;
        return mappings.getOrDefault(value.toLowerCase(), value);
    }

    private String applyYesNoTranslation(String value, String fieldDataType, Set<String> yesNoFields, String fieldName) {
        if (!yesNoTranslationEnabled || value == null) return value;
        // Match if bundle field DataType is "boolean", or if metadata tagged this field as yesNoRadioButtons
        boolean isYesNo = "boolean".equalsIgnoreCase(fieldDataType)
                || (yesNoFields != null && yesNoFields.contains(fieldName));
        if (!isYesNo) return value;
        if ("true".equalsIgnoreCase(value.trim())) return yesNoTrueValue;
        if ("false".equalsIgnoreCase(value.trim())) return yesNoFalseValue;
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
        // Build a set of field internal names that require yes/no translation
        Set<String> yesNoFields = new HashSet<>();
        if (metadata.getComponents() != null) {
            for (ComponentMetadata comp : metadata.getComponents()) {
                cardinalityByComponent.put(comp.getInternalName(), comp.getCardinality());
                if (yesNoTranslationEnabled && comp.getFields() != null) {
                    for (FieldMetadata field : comp.getFields()) {
                        if ("yesNoRadioButtons".equals(field.getDataType())) {
                            yesNoFields.add(field.getInternalName());
                        }
                    }
                }
            }
        }

        boolean usePositionalIds = requestTrackingIds != null && requestTrackingIds.size() == rawRecords.size();

        List<BundleRecord> records = new ArrayList<>();
        for (int i = 0; i < rawRecords.size(); i++) {
            List<BundleFieldDto> rawFields = rawRecords.get(i);
            Long positionalTrackingId = usePositionalIds ? requestTrackingIds.get(i) : null;
            BundleRecord record = mapRecord(rawFields, cardinalityByComponent, positionalTrackingId, yesNoFields);
            records.add(record);
        }

        response.setRecords(records);
        return response;
    }

    private BundleRecord mapRecord(List<BundleFieldDto> rawFields,
                                    Map<String, String> cardinalityByComponent,
                                    Long positionalTrackingId,
                                    Set<String> yesNoFields) {
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
                        String val = applyDelimiterReplacement(normalizeValue(f.getName(), unescapeHtml(f.getValue())));
                        val = applyYesNoTranslation(val, f.getDataType(), yesNoFields, f.getName());
                        val = applyFieldValueMapping(f.getName(), val);
                        row.put(f.getName(), val);
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
                        String val = applyDelimiterReplacement(normalizeValue(f.getName(), unescapeHtml(f.getValue())));
                        val = applyYesNoTranslation(val, f.getDataType(), yesNoFields, f.getName());
                        val = applyFieldValueMapping(f.getName(), val);
                        fields.put(f.getName(), val);
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
