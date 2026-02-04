package com.clmextract.csv;

import com.clmextract.metadata.ComponentMetadata;
import com.clmextract.metadata.FieldMetadata;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ColumnResolver {

    private static final Logger logger = LogManager.getLogger(ColumnResolver.class);

    private final List<ComponentMetadata> components;
    private final String boType;
    private List<String> orderFilePaths;
    private Map<String, String> displayNameOverrides;

    public ColumnResolver(List<ComponentMetadata> components, String boType) {
        this.components = components;
        this.boType = boType;
        loadOrderFile();
        loadOverridesFile();
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
        Path overridesFile = Path.of("config", "overrides", boType + ".csv");
        if (Files.exists(overridesFile)) {
            try {
                List<String> lines = Files.readAllLines(overridesFile);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;
                    int commaIdx = trimmed.indexOf(',');
                    if (commaIdx > 0 && commaIdx < trimmed.length() - 1) {
                        String fieldPath = trimmed.substring(0, commaIdx).trim();
                        String newName = trimmed.substring(commaIdx + 1).trim();
                        displayNameOverrides.put(fieldPath, newName);
                    }
                }
                logger.info("Loaded display name overrides for {}: {} entries",
                        boType, displayNameOverrides.size());
            } catch (IOException e) {
                logger.warn("Failed to read overrides file: {}", e.getMessage());
            }
        }
    }

    public List<String> resolveFieldPaths() {
        List<String> paths = new ArrayList<>();
        for (ComponentMetadata comp : components) {
            for (FieldMetadata field : comp.getFields()) {
                paths.add(field.getInstancePath());
            }
        }

        if (orderFilePaths != null) {
            List<String> ordered = new ArrayList<>();
            for (String orderPath : orderFilePaths) {
                if (paths.contains(orderPath)) {
                    ordered.add(orderPath);
                }
            }
            return ordered;
        }

        return paths;
    }

    public List<ResolvedColumn> resolveColumns(ComponentMetadata component) {
        List<ResolvedColumn> columns = new ArrayList<>();
        List<FieldMetadata> fields = component.getFields();

        if (orderFilePaths != null) {
            for (String orderPath : orderFilePaths) {
                for (FieldMetadata field : fields) {
                    if (orderPath.equals(field.getInstancePath())) {
                        columns.add(buildColumn(component, field));
                        break;
                    }
                }
            }
        } else {
            for (FieldMetadata field : fields) {
                columns.add(buildColumn(component, field));
            }
        }

        return columns;
    }

    private ResolvedColumn buildColumn(ComponentMetadata component, FieldMetadata field) {
        String displayName = field.getDisplayName();
        if (displayNameOverrides.containsKey(field.getInstancePath())) {
            displayName = displayNameOverrides.get(field.getInstancePath());
        }
        String header = component.getDisplayName() + "." + displayName;
        return new ResolvedColumn(header, field.getInternalName(), field.getInstancePath());
    }

    public static class ResolvedColumn {
        private final String header;
        private final String fieldInternalName;
        private final String instancePath;

        public ResolvedColumn(String header, String fieldInternalName, String instancePath) {
            this.header = header;
            this.fieldInternalName = fieldInternalName;
            this.instancePath = instancePath;
        }

        public String getHeader() {
            return header;
        }

        public String getFieldInternalName() {
            return fieldInternalName;
        }

        public String getInstancePath() {
            return instancePath;
        }
    }
}
