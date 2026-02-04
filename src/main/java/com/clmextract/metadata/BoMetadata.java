package com.clmextract.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BoMetadata {

    private String boName;
    private String boUsageType;
    private List<ComponentMetadata> components;

    public String getBoName() {
        return boName;
    }

    public void setBoName(String boName) {
        this.boName = boName;
    }

    public String getBoUsageType() {
        return boUsageType;
    }

    public void setBoUsageType(String boUsageType) {
        this.boUsageType = boUsageType;
    }

    public List<ComponentMetadata> getComponents() {
        return components;
    }

    public void setComponents(List<ComponentMetadata> components) {
        this.components = components;
    }
}
