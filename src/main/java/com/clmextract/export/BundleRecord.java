package com.clmextract.export;

import java.util.List;

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
