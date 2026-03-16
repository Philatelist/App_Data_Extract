package com.clmextract.config;

import java.util.ArrayList;
import java.util.List;

public class AppConfig {

    private String baseUrl;
    private String username;
    private String password;
    private String endpointsFile = "inputs/endpoints.yml";
    private List<BoTypeConfig> boTypes = new ArrayList<>();
    private String csvMode = "per-component";
    private char delimiter = ',';
    private String filenameTemplate = "{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv";
    private String downloadsFilenameTemplate = "{BO}_AttachmentsToDownload_{DDMMYYYY}_{HHMMSS}.csv";
    private String outputRoot;
    private String exportFolderName = "MetaData";
    private int batchSize = 100;
    private int backupRetentionDays = 30;
    private boolean offlineMode = false;
    private int retryMaxAttempts = 3;
    private long retryBaseDelayMs = 1000;
    private String boUsageTypeFilter = null;
    private List<String> skipColumns = new ArrayList<>();

    public AppConfig() {
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEndpointsFile() {
        return endpointsFile;
    }

    public void setEndpointsFile(String endpointsFile) {
        this.endpointsFile = endpointsFile;
    }

    public List<BoTypeConfig> getBoTypes() {
        return boTypes;
    }

    public void setBoTypes(List<BoTypeConfig> boTypes) {
        this.boTypes = boTypes;
    }

    public String getCsvMode() {
        return csvMode;
    }

    public void setCsvMode(String csvMode) {
        this.csvMode = csvMode;
    }

    public char getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(char delimiter) {
        this.delimiter = delimiter;
    }

    public String getFilenameTemplate() {
        return filenameTemplate;
    }

    public void setFilenameTemplate(String filenameTemplate) {
        this.filenameTemplate = filenameTemplate;
    }

    public String getDownloadsFilenameTemplate() {
        return downloadsFilenameTemplate;
    }

    public void setDownloadsFilenameTemplate(String downloadsFilenameTemplate) {
        this.downloadsFilenameTemplate = downloadsFilenameTemplate;
    }

    public String getOutputRoot() {
        return outputRoot;
    }

    public void setOutputRoot(String outputRoot) {
        this.outputRoot = outputRoot;
    }

    public String getExportFolderName() {
        return exportFolderName;
    }

    public void setExportFolderName(String exportFolderName) {
        this.exportFolderName = exportFolderName;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getBackupRetentionDays() {
        return backupRetentionDays;
    }

    public void setBackupRetentionDays(int backupRetentionDays) {
        this.backupRetentionDays = backupRetentionDays;
    }

    public boolean isOfflineMode() {
        return offlineMode;
    }

    public void setOfflineMode(boolean offlineMode) {
        this.offlineMode = offlineMode;
    }

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public void setRetryMaxAttempts(int retryMaxAttempts) {
        this.retryMaxAttempts = retryMaxAttempts;
    }

    public long getRetryBaseDelayMs() {
        return retryBaseDelayMs;
    }

    public void setRetryBaseDelayMs(long retryBaseDelayMs) {
        this.retryBaseDelayMs = retryBaseDelayMs;
    }

    public String getBoUsageTypeFilter() {
        return boUsageTypeFilter;
    }

    public void setBoUsageTypeFilter(String boUsageTypeFilter) {
        this.boUsageTypeFilter = boUsageTypeFilter;
    }

    public List<String> getSkipColumns() {
        return skipColumns;
    }

    public void setSkipColumns(List<String> skipColumns) {
        this.skipColumns = skipColumns != null ? skipColumns : new ArrayList<>();
    }
}
