package com.clmextract.export;

public class ReportResult {

    private final String displayName;
    private final String csvContent;

    public ReportResult(String displayName, String csvContent) {
        this.displayName = displayName;
        this.csvContent = csvContent;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCsvContent() {
        return csvContent;
    }
}
