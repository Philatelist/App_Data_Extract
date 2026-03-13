package com.clmextract.export;

import com.clmextract.metadata.BoMetadata;
import com.clmextract.metadata.MetadataParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class OfflineDataSource implements DataSource {

    private static final Logger logger = LogManager.getLogger(OfflineDataSource.class);

    private final Path samplesDir;
    private final MetadataParser metadataParser;
    private final BundleParser bundleParser;

    private BoMetadata cachedMetadata;
    private BundleResponse cachedBundles;

    public OfflineDataSource(String samplesDir) {
        this.samplesDir = Path.of(samplesDir);
        this.metadataParser = new MetadataParser();
        this.bundleParser = new BundleParser();
    }

    @Override
    public void login() {
        logger.info("Offline mode: login is a no-op");
    }

    @Override
    public void logout() {
        logger.info("Offline mode: logout is a no-op");
    }

    @Override
    public List<String> getBoTypes() {
        logger.info("Offline mode: discovering BO types from sample metadata");
        BoMetadata metadata = loadMetadata();
        return List.of(metadata.getBoName());
    }

    @Override
    public BoMetadata getMetadata(String boType) {
        logger.info("Offline mode: loading metadata from sample file for BO type: {}", boType);
        return loadMetadata();
    }

    @Override
    public List<Long> getTrackingNumbers(String boType) {
        logger.info("Offline mode: extracting tracking numbers from sample bundles for BO type: {}", boType);
        BundleResponse bundles = loadBundles(loadMetadata(), null);
        if (bundles.getRecords() == null) {
            return List.of();
        }
        return bundles.getRecords().stream()
                .map(BundleRecord::getTrackingId)
                .collect(Collectors.toList());
    }

    @Override
    public BundleResponse fetchBatch(List<Long> trackingIds, List<String> fieldPaths,
                                     BoMetadata metadata) {
        logger.info("Offline mode: returning sample bundle data ({} requested IDs)", trackingIds.size());
        BundleResponse bundles = loadBundles(metadata, trackingIds);
        if (bundles.getRecords() == null) {
            return bundles;
        }
        // Filter to only requested tracking IDs
        List<BundleRecord> filtered = bundles.getRecords().stream()
                .filter(r -> trackingIds.contains(r.getTrackingId()))
                .collect(Collectors.toList());
        BundleResponse result = new BundleResponse();
        result.setBoName(bundles.getBoName());
        result.setRecords(filtered);
        return result;
    }

    private BoMetadata loadMetadata() {
        if (cachedMetadata != null) {
            return cachedMetadata;
        }
        Path metadataFile = samplesDir.resolve("BOMetaDataResponse.example.json");
        try {
            String json = Files.readString(metadataFile);
            cachedMetadata = metadataParser.parse(json);
            return cachedMetadata;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read sample metadata: " + metadataFile, e);
        }
    }

    private BundleResponse loadBundles(BoMetadata metadata, List<Long> requestTrackingIds) {
        Path bundlesFile = samplesDir.resolve("BundlesResponse.example.json");
        try {
            String json = Files.readString(bundlesFile);
            cachedBundles = bundleParser.parse(json, metadata, requestTrackingIds);
            return cachedBundles;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read sample bundles: " + bundlesFile, e);
        }
    }
}
