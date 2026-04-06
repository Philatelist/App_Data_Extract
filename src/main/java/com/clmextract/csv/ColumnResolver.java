package com.clmextract.csv;

import com.clmextract.config.AdditionalColumnConfig;
import com.clmextract.metadata.ComponentMetadata;
import com.clmextract.metadata.FieldMetadata;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ColumnResolver {

    private static final Logger logger = LogManager.getLogger(ColumnResolver.class);

    private final List<ComponentMetadata> components;
    private final String boType;
    private final Set<String> skipColumns;
    private final List<AdditionalColumnConfig> additionalColumns;
    private List<String> orderFilePaths;
    // Outer key: component internal name, inner key: parameter internal name, value: display name
    private Map<String, Map<String, String>> displayNameOverrides;

    public ColumnResolver(List<ComponentMetadata> components, String boType) {
        this(components, boType, List.of(), List.of());
    }

    public ColumnResolver(List<ComponentMetadata> components, String boType, List<String> skipColumns) {
        this(components, boType, skipColumns, List.of());
    }

    public ColumnResolver(List<ComponentMetadata> components, String boType, List<String> skipColumns,
                          List<AdditionalColumnConfig> additionalColumns) {
        this.components = components;
        this.boType = boType;
        this.skipColumns = new HashSet<>(skipColumns);
        this.additionalColumns = additionalColumns != null ? additionalColumns : List.of();
        loadOrderFile();
        loadOverridesFile();
    }

    public List<AdditionalColumnConfig> getAdditionalColumns() {
        return additionalColumns;
    }

    /**
     * Returns {@code true} for components that are written exactly as-is: no tracking-ID
     * column 0, no additional-column injection, and no field filtering beyond skipColumns.
     */
    public static boolean isExemptFromStandardColumns(String componentInternalName) {
        return "ReqSubClause".equals(componentInternalName);
    }

    /**
     * Injects additional columns into an existing column list at their configured positions.
     * Positions are 1-indexed (1 = first column after Tracking #). Columns with a higher
     * position are inserted first to avoid index shifting.
     */
    public static void injectAdditionalColumns(List<ResolvedColumn> columns,
                                               List<AdditionalColumnConfig> additionalColumns) {
        if (additionalColumns == null || additionalColumns.isEmpty()) return;
        List<AdditionalColumnConfig> sorted = new ArrayList<>(additionalColumns);
        sorted.sort((a, b) -> Integer.compare(b.getPosition(), a.getPosition()));
        for (AdditionalColumnConfig addCol : sorted) {
            int idx = Math.max(0, Math.min(addCol.getPosition() - 1, columns.size()));
            columns.add(idx, new ResolvedColumn(addCol.getHeader(), null, null, true));
        }
    }

    private void loadOrderFile() {
        Path orderFile = Path.of("config", "columns", boType + ".csv");
        if (Files.exists(orderFile)) {
            try {
                orderFilePaths = Files.readAllLines(orderFile).stream()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .collect(Collectors.toList());
                logger.info("Loaded column order file for {}: {} fields", boType, orderFilePaths.size());
            } catch (IOException e) {
                logger.warn("Failed to read column order file: {}", e.getMessage());
                orderFilePaths = null;
            }
        }
    }

    private void loadOverridesFile() {
        displayNameOverrides = new LinkedHashMap<>();
        Path overridesFile = Path.of("config", "overrides", "parameter-displaynames.csv");
        if (Files.exists(overridesFile)) {
            try {
                List<String> lines = Files.readAllLines(overridesFile);
                // Skip header row (first line: Component;Parameter;DisplayName;)
                for (int i = 1; i < lines.size(); i++) {
                    String trimmed = lines.get(i).trim();
                    if (trimmed.isEmpty()) continue;
                    String[] parts = trimmed.split(";");
                    if (parts.length >= 3) {
                        String componentName = parts[0].trim();
                        String parameterName = parts[1].trim();
                        String displayName = parts[2].trim();
                        if (!componentName.isEmpty() && !parameterName.isEmpty() && !displayName.isEmpty()) {
                            displayNameOverrides
                                    .computeIfAbsent(componentName, k -> new LinkedHashMap<>())
                                    .put(parameterName, displayName);
                        }
                    }
                }
                int totalEntries = displayNameOverrides.values().stream()
                        .mapToInt(Map::size)
                        .sum();
                logger.info("Loaded display name overrides from parameter-displaynames.csv: {} entries", totalEntries);
            } catch (IOException e) {
                logger.warn("Failed to read overrides file: {}", e.getMessage());
            }
        }
    }

    /**
     * Returns the header label for the first (tracking) column, e.g. "Basic Information.Tracking #".
     * Finds the component that owns the trackingNumber field; falls back to "Tracking #" if not found.
     */
    public String resolveTrackingHeader() {
        for (ComponentMetadata comp : components) {
            if (comp.getFields() == null) continue;
            for (FieldMetadata field : comp.getFields()) {
                if ("trackingNumber".equals(field.getInternalName())) {
                    String displayName = field.getDisplayName();
                    Map<String, String> overrides = displayNameOverrides.get(comp.getInternalName());
                    if (overrides != null && overrides.containsKey(field.getInternalName())) {
                        displayName = overrides.get(field.getInternalName());
                    }
                    return comp.getDisplayName() + "." + displayName;
                }
            }
        }
        return "Tracking #";
    }

    public List<String> resolveFieldPaths() {
        List<String> paths;
        if (orderFilePaths != null) {
            paths = new ArrayList<>(orderFilePaths);
        } else {
            paths = new ArrayList<>();
            for (ComponentMetadata comp : components) {
                for (FieldMetadata field : comp.getFields()) {
                    paths.add(field.getInstancePath().replace("MCPDef:/", ""));
                }
            }
        }

        return paths;
    }

    public List<ResolvedColumn> resolveColumns(ComponentMetadata component) {
        List<ResolvedColumn> columns = new ArrayList<>();
        List<FieldMetadata> fields = component.getFields();

        if (orderFilePaths != null) {
            for (String orderPath : orderFilePaths) {
                // Format: Module/Component/Parameter
                String[] segments = orderPath.split("/");
                if (segments.length < 3) continue;
                String componentName = segments[1];
                String parameterName = segments[2];

                if (!componentName.equals(component.getInternalName())) {
                    continue;
                }

                // Look for a matching field in the component's metadata
                FieldMetadata matchedField = null;
                for (FieldMetadata field : fields) {
                    if (parameterName.equals(field.getInternalName())) {
                        matchedField = field;
                        break;
                    }
                }

                if (matchedField != null) {
                    columns.add(buildColumn(component.getInternalName(), component.getDisplayName(), matchedField));
                } else {
                    // Field not found in metadata — build a column using the parameter name
                    // as both internal name and fallback display name, checking overrides first
                    String displayName = parameterName;
                    Map<String, String> componentOverrides = displayNameOverrides.get(component.getInternalName());
                    if (componentOverrides != null && componentOverrides.containsKey(parameterName)) {
                        displayName = componentOverrides.get(parameterName);
                    }
                    String header = component.getDisplayName() + "." + displayName;
                    columns.add(new ResolvedColumn(header, parameterName, orderPath));
                }
            }
        } else {
            for (FieldMetadata field : fields) {
                columns.add(buildColumn(component.getInternalName(), component.getDisplayName(), field));
            }
        }

        String compInternal = component.getInternalName();

        // ReqSubClause is exempt from all post-processing rules except skipColumns filtering.
        if ("ReqSubClause".equals(compInternal)) {
            if (!skipColumns.isEmpty()) {
                columns.removeIf(col -> {
                    String header = col.getHeader();
                    int dot = header.lastIndexOf('.');
                    String fieldName = dot >= 0 ? header.substring(dot + 1) : header;
                    return skipColumns.stream().anyMatch(skip -> skip.equalsIgnoreCase(fieldName));
                });
            }
            return columns;
        }

        // Always exclude the tracking number field — it is already the dedicated first column
        columns.removeIf(col -> "trackingNumber".equals(col.getFieldInternalName()));

        // serverFileName (File Path) must only appear in attachment components, always as the second column.
        // For all other components it is removed regardless of whether it came from metadata or order file.
        boolean isAttachment = "ReqAttachment".equals(compInternal) || "ReqContractAttachment".equals(compInternal);
        if (isAttachment) {
            // Remove from wherever it is, then re-insert at position 0 to guarantee second column
            columns.removeIf(col -> "serverFileName".equals(col.getFieldInternalName()));
            String header = component.getDisplayName() + ".File Path";
            columns.add(0, new ResolvedColumn(header, "serverFileName", "serverFileName"));
        } else {
            columns.removeIf(col -> "serverFileName".equals(col.getFieldInternalName()));
        }

        if (!skipColumns.isEmpty()) {
            columns.removeIf(col -> {
                String header = col.getHeader();
                int dot = header.lastIndexOf('.');
                String fieldName = dot >= 0 ? header.substring(dot + 1) : header;
                return skipColumns.stream().anyMatch(skip -> skip.equalsIgnoreCase(fieldName));
            });
        }

        if ("ReqAttachment".equals(compInternal)) {
            columns.add(new ResolvedColumn(component.getDisplayName() + ".SFTP File Name", "sftpFileName", "sftpFileName"));
        }

        // Copy all fields from ReqEmailConversation into ReqContractEmailWithConversation.
        // Each copied column is tagged with its source component so writers can look up
        // the field values from the correct BundleComponent at runtime.
        if ("ReqContractEmailWithConversation".equals(compInternal)) {
            for (ComponentMetadata otherComp : components) {
                if ("ReqEmailConversation".equals(otherComp.getInternalName())) {
                    String sourceDisplayName = otherComp.getDisplayName();
                    String targetDisplayName = component.getDisplayName();
                    List<ResolvedColumn> emailCols = resolveColumns(otherComp);
                    for (ResolvedColumn col : emailCols) {
                        String newHeader = col.getHeader();
                        if (sourceDisplayName != null && newHeader.startsWith(sourceDisplayName + ".")) {
                            newHeader = targetDisplayName + newHeader.substring(sourceDisplayName.length());
                        }
                        columns.add(new ResolvedColumn(newHeader, col.getFieldInternalName(),
                                col.getInstancePath(), col.isAdditional(), "ReqEmailConversation"));
                    }
                    break;
                }
            }
        }

        return columns;
    }

    private ResolvedColumn buildColumn(String componentInternalName, String componentDisplayName, FieldMetadata field) {
        String displayName = field.getDisplayName();
        Map<String, String> componentOverrides = displayNameOverrides.get(componentInternalName);
        if (componentOverrides != null && componentOverrides.containsKey(field.getInternalName())) {
            displayName = componentOverrides.get(field.getInternalName());
        }
        String header = componentDisplayName + "." + displayName;
        return new ResolvedColumn(header, field.getInternalName(), field.getInstancePath());
    }

    public static class ResolvedColumn {
        private final String header;
        private final String fieldInternalName;
        private final String instancePath;
        private final boolean additional;
        /** Non-null when this column's field values must be read from a different component
         *  than the one this column was added to (e.g. ReqEmailConversation fields copied
         *  into ReqContractEmailWithConversation). */
        private final String sourceComponentInternalName;

        public ResolvedColumn(String header, String fieldInternalName, String instancePath) {
            this(header, fieldInternalName, instancePath, false, null);
        }

        public ResolvedColumn(String header, String fieldInternalName, String instancePath, boolean additional) {
            this(header, fieldInternalName, instancePath, additional, null);
        }

        public ResolvedColumn(String header, String fieldInternalName, String instancePath,
                              boolean additional, String sourceComponentInternalName) {
            this.header = header;
            this.fieldInternalName = fieldInternalName;
            this.instancePath = instancePath;
            this.additional = additional;
            this.sourceComponentInternalName = sourceComponentInternalName;
        }

        public String getHeader() { return header; }
        public String getFieldInternalName() { return fieldInternalName; }
        public String getInstancePath() { return instancePath; }
        public boolean isAdditional() { return additional; }
        public String getSourceComponentInternalName() { return sourceComponentInternalName; }
    }
}
