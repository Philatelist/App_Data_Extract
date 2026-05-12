package com.clmextract.export;

import com.clmextract.metadata.BoMetadata;

import java.util.List;
import java.util.Map;

public interface DataSource {

    void login();

    void logout();

    List<String> getBoTypes();

    BoMetadata getMetadata(String boType);

    List<Long> getTrackingNumbers(String boType);

    default List<Long> getTrackingNumbersAfterDate(String boType, String dateTime) {
        return getTrackingNumbers(boType);
    }

    default List<Long> getTrackingNumbersInFlight(String boType, int daysBeforeToday) {
        return getTrackingNumbers(boType);
    }

    BundleResponse fetchBatch(List<Long> trackingIds, List<String> fieldPaths, BoMetadata metadata);

    default List<ParentRecord> fetchBundleParents(List<Long> trackingIds, int batchSize) {
        return List.of();
    }

    default List<ReportResult> fetchReports(List<String> reportNames) {
        return List.of();
    }

    default List<Map<String, Object>> getAttachmentInfo(String trackingNumber) {
        return List.of();
    }

    default byte[] downloadDocument(String encryptedUrl) {
        return new byte[0];
    }
}
