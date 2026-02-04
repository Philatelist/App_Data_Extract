package com.clmextract.config;

public class BoTypeConfig {

    private String name;
    private String trackingFilter;
    private String filenameTemplate;

    public BoTypeConfig() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTrackingFilter() {
        return trackingFilter;
    }

    public void setTrackingFilter(String trackingFilter) {
        this.trackingFilter = trackingFilter;
    }

    public String getFilenameTemplate() {
        return filenameTemplate;
    }

    public void setFilenameTemplate(String filenameTemplate) {
        this.filenameTemplate = filenameTemplate;
    }
}
