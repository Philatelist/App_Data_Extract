package com.clmextract.export;

import com.clmextract.config.AppConfig;
import com.clmextract.config.BoTypeConfig;
import com.clmextract.metadata.BoMetadata;
import com.clmextract.web.run.DateFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that BoPipeline.execute(BoTypeConfig, Path, DateFilter) returns
 * the tracking IDs that were resolved during Step 2 of the pipeline.
 */
class BoPipelineReturnValueTest {

    @TempDir
    Path tempDir;

    // ----------------------------------------------------------------
    // Stub DataSource with a fixed tracking-ID list
    // ----------------------------------------------------------------

    private static class FixedTrackingDataSource implements DataSource {
        private final List<Long> ids;
        int getTrackingNumbersCallCount = 0;

        FixedTrackingDataSource(List<Long> ids) {
            this.ids = ids;
        }

        @Override public void login() {}
        @Override public void logout() {}
        @Override public List<String> getBoTypes() { return Collections.emptyList(); }

        @Override
        public BoMetadata getMetadata(String boType) {
            BoMetadata m = new BoMetadata();
            m.setBoName(boType);
            m.setComponents(new ArrayList<>());
            return m;
        }

        @Override
        public List<Long> getTrackingNumbers(String boType) {
            getTrackingNumbersCallCount++;
            return ids;
        }

        @Override
        public BundleResponse fetchBatch(List<Long> trackingIds, List<String> fieldPaths, BoMetadata metadata) {
            BundleResponse r = new BundleResponse();
            r.setRecords(new ArrayList<>());
            return r;
        }

        @Override
        public List<ParentRecord> fetchBundleParents(List<Long> ids, int batchSize) {
            return Collections.emptyList();
        }
    }

    private AppConfig buildConfig() throws IOException {
        Files.createDirectories(tempDir);
        AppConfig config = new AppConfig();
        config.setOutputRoot(tempDir.toString());
        config.setBatchSize(100);
        return config;
    }

    private BoTypeConfig boType(String name) {
        BoTypeConfig bt = new BoTypeConfig();
        bt.setName(name);
        return bt;
    }

    // ----------------------------------------------------------------
    // Test 1: normal case — returns the fixed tracking IDs
    // ----------------------------------------------------------------

    @Test
    void execute_returnsTrackingIdsResolvedDuringStep2() throws IOException {
        List<Long> expected = Arrays.asList(101L, 202L, 303L);
        FixedTrackingDataSource ds = new FixedTrackingDataSource(expected);
        AppConfig config = buildConfig();

        BoPipeline pipeline = new BoPipeline(config, ds);

        List<Long> result = pipeline.execute(boType("TESTBO"), tempDir, null);

        assertEquals(expected, result, "Returned list must match the tracking IDs from getTrackingNumbers()");
    }

    // ----------------------------------------------------------------
    // Test 2: empty tracking IDs — returns empty list (early exit path)
    // ----------------------------------------------------------------

    @Test
    void execute_emptyTrackingIds_returnsEmptyList() throws IOException {
        FixedTrackingDataSource ds = new FixedTrackingDataSource(Collections.emptyList());
        AppConfig config = buildConfig();

        BoPipeline pipeline = new BoPipeline(config, ds);

        List<Long> result = pipeline.execute(boType("TESTBO"), tempDir, null);

        assertNotNull(result, "Result must not be null even when no tracking IDs exist");
        assertTrue(result.isEmpty(), "Result must be empty when no tracking IDs returned by data source");
    }

    // ----------------------------------------------------------------
    // Test 3: single tracking ID — returned correctly
    // ----------------------------------------------------------------

    @Test
    void execute_singleTrackingId_returnsSingleElementList() throws IOException {
        List<Long> expected = Collections.singletonList(999L);
        FixedTrackingDataSource ds = new FixedTrackingDataSource(expected);
        AppConfig config = buildConfig();

        BoPipeline pipeline = new BoPipeline(config, ds);

        List<Long> result = pipeline.execute(boType("MYBO"), tempDir, null);

        assertEquals(1, result.size(), "Exactly one tracking ID expected");
        assertEquals(999L, result.get(0), "The returned tracking ID must match");
    }

    // ----------------------------------------------------------------
    // Test 4: fetchBundleParents throws — still returns tracking IDs
    // ----------------------------------------------------------------

    @Test
    void execute_bundleParentsFails_stillReturnsTrackingIds() throws IOException {
        List<Long> expected = Arrays.asList(1L, 2L, 3L);
        AppConfig config = buildConfig();
        config.setGenerateParentCsv(true); // ensure we enter the parent fetch path
        config.setParentFilenameTemplate("{BO}_Mapping_{DDMMYYYY}_{HHMMSS}.csv");

        DataSource throwingDs = new DataSource() {
            @Override public void login() {}
            @Override public void logout() {}
            @Override public List<String> getBoTypes() { return Collections.emptyList(); }
            @Override public BoMetadata getMetadata(String boType) {
                BoMetadata m = new BoMetadata();
                m.setBoName(boType);
                m.setComponents(new ArrayList<>());
                return m;
            }
            @Override public List<Long> getTrackingNumbers(String boType) {
                return expected;
            }
            @Override public BundleResponse fetchBatch(List<Long> ids, List<String> fieldPaths, BoMetadata metadata) {
                BundleResponse r = new BundleResponse();
                r.setRecords(new ArrayList<>());
                return r;
            }
            @Override public List<ParentRecord> fetchBundleParents(List<Long> ids, int batchSize) {
                throw new RuntimeException("Simulated bundle parent API failure");
            }
        };

        BoPipeline pipeline = new BoPipeline(config, throwingDs);

        List<Long> result = assertDoesNotThrow(
                () -> pipeline.execute(boType("TESTBO"), tempDir, null),
                "Pipeline must not propagate exceptions from fetchBundleParents");

        assertEquals(expected, result,
                "Tracking IDs must be returned even when fetchBundleParents throws");
    }

    // ----------------------------------------------------------------
    // Test 5: DateFilter-based routing — still returns tracking IDs
    // ----------------------------------------------------------------

    @Test
    void execute_withDateFilter_returnsTrackingIds() throws IOException {
        List<Long> expected = Arrays.asList(500L, 600L);
        AppConfig config = buildConfig();

        DataSource dateFilterDs = new DataSource() {
            @Override public void login() {}
            @Override public void logout() {}
            @Override public List<String> getBoTypes() { return Collections.emptyList(); }
            @Override public BoMetadata getMetadata(String boType) {
                BoMetadata m = new BoMetadata();
                m.setBoName(boType);
                m.setComponents(new ArrayList<>());
                return m;
            }
            @Override public List<Long> getTrackingNumbers(String boType) { return Collections.emptyList(); }
            @Override public List<Long> getTrackingNumbersAfterDate(String boType, String dateTime) {
                return expected;
            }
            @Override public BundleResponse fetchBatch(List<Long> ids, List<String> fieldPaths, BoMetadata metadata) {
                BundleResponse r = new BundleResponse();
                r.setRecords(new ArrayList<>());
                return r;
            }
        };

        DateFilter filter = new DateFilter("createDate", "2024-01-01T00:00:00", false, 0);

        BoPipeline pipeline = new BoPipeline(config, dateFilterDs);

        List<Long> result = pipeline.execute(boType("TESTBO"), tempDir, filter);

        assertEquals(expected, result,
                "Tracking IDs from date-filtered query must be returned");
    }
}
