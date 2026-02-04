package com.clmextract.export;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BundleComponent {

    private String componentInternalName;
    private Map<String, String> fields;
    private List<Map<String, String>> rows;

    public String getComponentInternalName() {
        return componentInternalName;
    }

    public void setComponentInternalName(String componentInternalName) {
        this.componentInternalName = componentInternalName;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public void setFields(Map<String, String> fields) {
        this.fields = fields;
    }

    public List<Map<String, String>> getRows() {
        return rows;
    }

    public void setRows(List<Map<String, String>> rows) {
        this.rows = rows;
    }

    public boolean isSingleCardinality() {
        return fields != null;
    }

    public boolean isMultipleCardinality() {
        return rows != null;
    }
}
