package com.clmextract.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ComponentMetadata {

    private String internalName;
    private String displayName;
    private String cardinality;
    private String instancePath;
    private List<FieldMetadata> fields;

    public String getInternalName() {
        return internalName;
    }

    public void setInternalName(String internalName) {
        this.internalName = internalName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getCardinality() {
        return cardinality;
    }

    public void setCardinality(String cardinality) {
        this.cardinality = cardinality;
    }

    public String getInstancePath() {
        return instancePath;
    }

    public void setInstancePath(String instancePath) {
        this.instancePath = instancePath;
    }

    public List<FieldMetadata> getFields() {
        return fields;
    }

    public void setFields(List<FieldMetadata> fields) {
        this.fields = fields;
    }

    public boolean isSingleCardinality() {
        return "single".equalsIgnoreCase(cardinality);
    }

    public boolean isMultipleCardinality() {
        return "multiple".equalsIgnoreCase(cardinality);
    }
}
