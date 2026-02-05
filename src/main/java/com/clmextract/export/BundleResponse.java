package com.clmextract.export;

import java.util.List;

public class BundleResponse {

    private String boName;
    private List<BundleRecord> records;

    public String getBoName() {
        return boName;
    }

    public void setBoName(String boName) {
        this.boName = boName;
    }

    public List<BundleRecord> getRecords() {
        return records;
    }

    public void setRecords(List<BundleRecord> records) {
        this.records = records;
    }
}
