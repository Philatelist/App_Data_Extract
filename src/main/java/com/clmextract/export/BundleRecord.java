package com.clmextract.export;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BundleRecord {

    private long trackingId;
    private List<BundleComponent> components;

    public long getTrackingId() {
        return trackingId;
    }

    public void setTrackingId(long trackingId) {
        this.trackingId = trackingId;
    }

    public List<BundleComponent> getComponents() {
        return components;
    }

    public void setComponents(List<BundleComponent> components) {
        this.components = components;
    }
}
