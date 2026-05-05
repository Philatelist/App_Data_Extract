package com.clmextract.config;

import java.util.ArrayList;
import java.util.List;

public class BoTypeConfig {

    private String name;
    private String localizedName;
    private String filenameTemplate;
    private List<AdditionalColumnConfig> additionalColumns = new ArrayList<>();

    public BoTypeConfig() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocalizedName() {
        return localizedName;
    }

    public void setLocalizedName(String localizedName) {
        this.localizedName = localizedName;
    }

    public String getFilenameTemplate() {
        return filenameTemplate;
    }

    public void setFilenameTemplate(String filenameTemplate) {
        this.filenameTemplate = filenameTemplate;
    }

    public List<AdditionalColumnConfig> getAdditionalColumns() {
        return additionalColumns;
    }

    public void setAdditionalColumns(List<AdditionalColumnConfig> additionalColumns) {
        this.additionalColumns = additionalColumns != null ? additionalColumns : new ArrayList<>();
    }
}
