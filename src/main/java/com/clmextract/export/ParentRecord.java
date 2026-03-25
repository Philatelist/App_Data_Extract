package com.clmextract.export;

public class ParentRecord {

    private String trackingId;
    private String currentTask;
    private String bundleType;
    private String bundleCategory;
    private String parentId;

    public String getTrackingId() { return trackingId; }
    public void setTrackingId(String trackingId) { this.trackingId = trackingId; }

    public String getCurrentTask() { return currentTask; }
    public void setCurrentTask(String currentTask) { this.currentTask = currentTask; }

    public String getBundleType() { return bundleType; }
    public void setBundleType(String bundleType) { this.bundleType = bundleType; }

    public String getBundleCategory() { return bundleCategory; }
    public void setBundleCategory(String bundleCategory) { this.bundleCategory = bundleCategory; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
}
