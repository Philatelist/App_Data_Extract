package com.clmextract.web.run;

import com.clmextract.config.AppConfig;
import com.clmextract.config.BoTypeConfig;
import com.clmextract.export.AttachmentDownloader;
import com.clmextract.export.BoPipeline;
import com.clmextract.export.BundleRecord;
import com.clmextract.export.BundleResponse;
import com.clmextract.export.DataSource;
import com.clmextract.export.ParentRecord;
import com.clmextract.metadata.BoMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the data-flow contract introduced by Slice 1 of spec 013:
 *
 * After the CSV export step, the accumulated tracking IDs (exportedIds) must be
 * the exact set forwarded to AttachmentDownloader for PDF and attachment downloads.
 * No additional call to DataSource.getTrackingNumbers() should occur during the
 * EXPORT_PDF or EXPORT_ATTACHMENTS steps.
 */
class RunExecutorAttachmentScopeTest {

    @TempDir
    Path tempDir;

    // ----------------------------------------------------------------
    // Tracking DataSource — counts how many times getTrackingNumbers is called
    // ----------------------------------------------------------------

    private static class TrackingCallCountDataSource implements DataSource {
        private final List<Long> ids;
        final AtomicInteger getTrackingNumbersCallCount = new AtomicInteger(0);
        final List<Long> downloadedPdfIds = new ArrayList<>();
        final List<Long> downloadedAttachmentIds = new ArrayList<>();

        TrackingCallCountDataSource(List<Long> ids) {
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
            getTrackingNumbersCallCount.incrementAndGet();
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

        /** Records which tracking IDs the downloader asked about — simulates attachment info lookup. */
        @Override
        public List<Map<String, Object>> getAttachmentInfo(String trackingNumber) {
            // Return empty — no actual files, just track the call was made
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
    // Test 1: BoPipeline.execute() returns IDs — downloader receives those IDs
    //
    // This test simulates what RunExecutor does internally:
    //   exportedIds.addAll(pipeline.execute(boTypeConfig, outputDir, dateFilter))
    //   downloader.downloadPdfs(exportedIds)
    //   downloader.downloadAttachments(exportedIds)
    //
    // We verify that getTrackingNumbers is called exactly once (during the CSV step)
    // and that AttachmentDownloader receives those exact IDs.
    // ----------------------------------------------------------------

    @Test
    void exportedIds_fromCsvStep_arePassedToDownloader_notRefetched() throws IOException {
        List<Long> expectedIds = Arrays.asList(111L, 222L, 333L);
        TrackingCallCountDataSource ds = new TrackingCallCountDataSource(expectedIds);
        AppConfig config = buildConfig();

        BoPipeline pipeline = new BoPipeline(config, ds);

        // Simulate EXPORT_CSV step
        List<Long> exportedIds = new ArrayList<>();
        exportedIds.addAll(pipeline.execute(boType("TESTBO"), tempDir, null));

        // Verify the pipeline fetched tracking IDs exactly once
        assertEquals(1, ds.getTrackingNumbersCallCount.get(),
                "getTrackingNumbers must be called exactly once during the CSV pipeline step");

        // Verify the accumulated IDs match what the stub returned
        assertEquals(expectedIds, exportedIds,
                "exportedIds must contain exactly the tracking IDs returned by getTrackingNumbers");

        // Simulate EXPORT_PDF step using exportedIds (not re-fetching from DataSource)
        AttachmentDownloader downloader = new AttachmentDownloader(ds, tempDir);
        int pdfCount = downloader.downloadPdfs(exportedIds);

        // The downloader calls getAttachmentInfo() per ID but NOT getTrackingNumbers()
        assertEquals(1, ds.getTrackingNumbersCallCount.get(),
                "getTrackingNumbers must NOT be called again during EXPORT_PDF step");

        // Simulate EXPORT_ATTACHMENTS step using exportedIds
        int attachmentCount = downloader.downloadAttachments(exportedIds);

        assertEquals(1, ds.getTrackingNumbersCallCount.get(),
                "getTrackingNumbers must NOT be called again during EXPORT_ATTACHMENTS step");

        // Both downloads should complete (with 0 actual files since stub returns empty attachments)
        assertEquals(0, pdfCount, "No PDFs expected when stub returns empty attachment info");
        assertEquals(0, attachmentCount, "No attachments expected when stub returns empty attachment info");
    }

    // ----------------------------------------------------------------
    // Test 2: Multiple BO types — IDs are accumulated across all BOs
    // ----------------------------------------------------------------

    @Test
    void exportedIds_areAccumulatedAcrossMultipleBos() throws IOException {
        List<Long> bo1Ids = Arrays.asList(1L, 2L, 3L);
        List<Long> bo2Ids = Arrays.asList(4L, 5L);

        // DataSource that returns different IDs based on boType
        DataSource multiBoDs = new DataSource() {
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
                return "BO1".equals(boType) ? bo1Ids : bo2Ids;
            }

            @Override
            public BundleResponse fetchBatch(List<Long> ids, List<String> fieldPaths, BoMetadata metadata) {
                BundleResponse r = new BundleResponse();
                r.setRecords(new ArrayList<>());
                return r;
            }
        };

        AppConfig config = buildConfig();
        BoPipeline pipeline = new BoPipeline(config, multiBoDs);

        // Simulate EXPORT_CSV loop over two BO types
        List<Long> exportedIds = new ArrayList<>();
        exportedIds.addAll(pipeline.execute(boType("BO1"), tempDir, null));
        exportedIds.addAll(pipeline.execute(boType("BO2"), tempDir, null));

        List<Long> allExpected = new ArrayList<>();
        allExpected.addAll(bo1Ids);
        allExpected.addAll(bo2Ids);

        assertEquals(allExpected, exportedIds,
                "exportedIds must accumulate IDs from all BO types processed in the CSV step");
    }

    // ----------------------------------------------------------------
    // Test 3: Empty BO result — exportedIds stays empty, downloader handles gracefully
    // ----------------------------------------------------------------

    @Test
    void exportedIds_emptyWhenNoTrackingIds_downloaderHandlesEmpty() throws IOException {
        TrackingCallCountDataSource ds = new TrackingCallCountDataSource(Collections.emptyList());
        AppConfig config = buildConfig();

        BoPipeline pipeline = new BoPipeline(config, ds);

        List<Long> exportedIds = new ArrayList<>();
        exportedIds.addAll(pipeline.execute(boType("EMPTYBO"), tempDir, null));

        assertTrue(exportedIds.isEmpty(), "exportedIds must be empty when data source has no tracking IDs");

        // Downloader should handle empty list without errors
        AttachmentDownloader downloader = new AttachmentDownloader(ds, tempDir);
        assertDoesNotThrow(() -> downloader.downloadPdfs(exportedIds),
                "downloadPdfs must not throw when given an empty ID list");
        assertDoesNotThrow(() -> downloader.downloadAttachments(exportedIds),
                "downloadAttachments must not throw when given an empty ID list");

        // getTrackingNumbers must not be called at downloader step
        assertEquals(1, ds.getTrackingNumbersCallCount.get(),
                "getTrackingNumbers must be called exactly once (CSV step only)");
    }
}
