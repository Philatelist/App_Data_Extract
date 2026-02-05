package com.clmextract.export;

import com.clmextract.metadata.BoMetadata;

import java.util.List;

public interface DataSource {

    void login();

    void logout();

    List<String> getBoTypes();

    BoMetadata getMetadata(String boType);

    List<Long> getTrackingNumbers(String boType);

    BundleResponse fetchBatch(List<Long> trackingIds, List<String> fieldPaths, BoMetadata metadata);
}
