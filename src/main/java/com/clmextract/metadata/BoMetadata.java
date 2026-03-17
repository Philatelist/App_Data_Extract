package com.clmextract.metadata;

import java.util.List;

public class BoMetadata {

    private String boName;
    private String boDisplayName;
    private String boUsageType;
    private List<ComponentMetadata> components;

    public String getBoName() {
        return boName;
    }

    public void setBoName(String boName) {
        this.boName = boName;
    }

    public String getBoDisplayName() {
        return boDisplayName != null ? boDisplayName : boName;
    }

    public void setBoDisplayName(String boDisplayName) {
        this.boDisplayName = boDisplayName;
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
