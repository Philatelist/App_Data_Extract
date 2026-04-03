package com.clmextract.export;

import com.clmextract.config.AppConfig;
import com.clmextract.config.BoTypeConfig;
import com.clmextract.metadata.BoMetadata;
import com.clmextract.metadata.ComponentMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoPipelineTest {

    @TempDir
    Path tempDir;

    // ----------------------------------------------------------------
    // Minimal stub DataSource
    // ----------------------------------------------------------------

    private static class StubDataSource implements DataSource {
        private final List<Long> trackingIds;
        private final List<ParentRecord> parentRecords;

        StubDataSource(List<Long> trackingIds, List<ParentRecord> parentRecords) {
            this.trackingIds = trackingIds;
            this.parentRecords = parentRecords;
        }

        @Override public void login() {}
        @Override public void logout() {}

        @Override
        public List<String> getBoTypes() { return Collections.emptyList(); }

        @Override
        public BoMetadata getMetadata(String boType) {
            BoMetadata m = new BoMetadata();
            m.setBoName(boType);
            m.setComponents(new ArrayList<>());
            return m;
        }

        @Override
        public List<Long> getTrackingNumbers(String boType) {
            return trackingIds;
        }

        @Override
        public BundleResponse fetchBatch(List<Long> ids, List<String> fieldPaths, BoMetadata metadata) {
            BundleResponse r = new BundleResponse();
            r.setRecords(new ArrayList<>());
            return r;
        }

        @Override
        public List<ParentRecord> fetchBundleParents(List<Long> ids, int batchSize) {
            return parentRecords;
        }
    }

    // ----------------------------------------------------------------
    // Helper: minimal AppConfig with output wired to tempDir
    // ----------------------------------------------------------------

    private AppConfig buildConfig() throws IOException {
        Files.createDirectories(tempDir.resolve("MetaData"));
        Files.createDirectories(tempDir.resolve("downloads"));

        AppConfig config = new AppConfig();
        config.setOutputRoot(tempDir.toString());
        config.setBatchSize(100);
        return config;
    }

    private static ParentRecord record(String trackingId, String currentTask,
                                       String bundleType, String bundleCategory, String parentId) {
        ParentRecord r = new ParentRecord();
        r.setTrackingId(trackingId);
        r.setCurrentTask(currentTask);
        r.setBundleType(bundleType);
        r.setBundleCategory(bundleCategory);
        r.setParentId(parentId);
        return r;
    }

    // ----------------------------------------------------------------
    // Test 1: generateParentCsv=true — RelationshipMapping file created
    // ----------------------------------------------------------------

    @Test
    void testGenerateParentCsv_createsRelationshipMappingFile() throws IOException {
        AppConfig config = buildConfig();
        config.setGenerateParentCsv(true);
        config.setParentFilenameTemplate("{BO}_RelationshipMapping_{DDMMYYYY}_{HHMMSS}.csv");

        List<ParentRecord> parents = Arrays.asList(
                record("1001", "Stage A", "Master", "CatX", ""),
                record("1002", "Stage B", "Sub",    "CatY", "1001")
        );

        StubDataSource stub = new StubDataSource(Arrays.asList(1001L, 1002L), parents);
        BoPipeline pipeline = new BoPipeline(config, stub);

        BoTypeConfig boType = new BoTypeConfig();
        boType.setName("TESTBO");
        pipeline.execute(boType);

        File[] files = tempDir.resolve("MetaData").toFile().listFiles(
                f -> f.getName().startsWith("TESTBO_RelationshipMapping") && f.getName().endsWith(".csv"));
        assertNotNull(files, "RelationshipMapping file should be created");
        assertEquals(1, files.length, "Exactly one RelationshipMapping file expected");

        String content = new String(Files.readAllBytes(files[0].toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("Tracking #"), "Header row must be present");
        assertTrue(content.contains("1001"), "Record 1001 must be present");
        assertTrue(content.contains("Stage A"), "Current task 'Stage A' must be present");
        assertTrue(content.contains("1002"), "Record 1002 must be present");
        assertTrue(content.contains("Stage B"), "Current task 'Stage B' must be present");
    }

    // ----------------------------------------------------------------
    // Test 2: generateParentCsv=false — RelationshipMapping file NOT created
    // ----------------------------------------------------------------

    @Test
    void testGenerateParentCsv_false_noRelationshipMappingFile() throws IOException {
        AppConfig config = buildConfig();
        config.setGenerateParentCsv(false);

        StubDataSource stub = new StubDataSource(
                Arrays.asList(1001L),
                Arrays.asList(record("1001", "Stage A", "Master", "CatX", ""))
        );
        BoPipeline pipeline = new BoPipeline(config, stub);

        BoTypeConfig boType = new BoTypeConfig();
        boType.setName("TESTBO");
        pipeline.execute(boType);

        File[] files = tempDir.resolve("MetaData").toFile().listFiles(
                f -> f.getName().contains("RelationshipMapping"));
        assertTrue(files == null || files.length == 0,
                "RelationshipMapping file must NOT be created when generateParentCsv=false");
    }

    // ----------------------------------------------------------------
    // Test 3: generateParentCsv=true, empty records — file created with header only
    // ----------------------------------------------------------------

    @Test
    void testGenerateParentCsv_emptyRecords_headerOnly() throws IOException {
        AppConfig config = buildConfig();
        config.setGenerateParentCsv(true);
        config.setParentFilenameTemplate("{BO}_RelationshipMapping_{DDMMYYYY}_{HHMMSS}.csv");

        StubDataSource stub = new StubDataSource(Arrays.asList(1001L), new ArrayList<>());
        BoPipeline pipeline = new BoPipeline(config, stub);

        BoTypeConfig boType = new BoTypeConfig();
        boType.setName("TESTBO");
        pipeline.execute(boType);

        File[] files = tempDir.resolve("MetaData").toFile().listFiles(
                f -> f.getName().startsWith("TESTBO_RelationshipMapping") && f.getName().endsWith(".csv"));
        assertNotNull(files);
        assertEquals(1, files.length);

        String content = new String(Files.readAllBytes(files[0].toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("Tracking #"), "Header must still be written for empty records");
        String[] lines = content.split("\n");
        boolean hasDataRow = false;
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) {
                hasDataRow = true;
                break;
            }
        }
        assertFalse(hasDataRow, "No data rows expected when parent records are empty");
    }

    // ----------------------------------------------------------------
    // Test 4a: fetchBundleParents throws — pipeline completes, no crash, no file
    // ----------------------------------------------------------------

    @Test
    void testFetchBundleParentsThrows_pipelineCompletes_noFile() throws IOException {
        AppConfig config = buildConfig();
        config.setGenerateParentCsv(true);
        config.setParentFilenameTemplate("{BO}_RelationshipMapping_{DDMMYYYY}_{HHMMSS}.csv");

        DataSource stub = new DataSource() {
            @Override public void login() {}
            @Override public void logout() {}
            @Override public List<String> getBoTypes() { return Collections.emptyList(); }
            @Override public BoMetadata getMetadata(String boType) {
                BoMetadata m = new BoMetadata(); m.setBoName(boType); m.setComponents(new ArrayList<>()); return m;
            }
            @Override public List<Long> getTrackingNumbers(String boType) { return Arrays.asList(1001L); }
            @Override public BundleResponse fetchBatch(List<Long> ids, List<String> fieldPaths, BoMetadata metadata) {
                BundleResponse r = new BundleResponse(); r.setRecords(new ArrayList<>()); return r;
            }
            @Override public List<ParentRecord> fetchBundleParents(List<Long> ids, int batchSize) {
                throw new RuntimeException("getBundleParent API error");
            }
        };

        BoPipeline pipeline = new BoPipeline(config, stub);
        BoTypeConfig boType = new BoTypeConfig();
        boType.setName("TESTBO");

        // Must not throw
        assertDoesNotThrow(() -> pipeline.execute(boType));

        File[] files = tempDir.resolve("MetaData").toFile().listFiles(
                f -> f.getName().contains("RelationshipMapping"));
        assertTrue(files == null || files.length == 0,
                "No RelationshipMapping file expected when API throws");
    }

    // ----------------------------------------------------------------
    // Test 4: no tracking IDs — pipeline exits early, no RelationshipMapping
    // ----------------------------------------------------------------

    @Test
    void testNoTrackingIds_pipelineExitsEarly_noRelationshipMappingFile() throws IOException {
        AppConfig config = buildConfig();
        config.setGenerateParentCsv(true);
        config.setParentFilenameTemplate("{BO}_RelationshipMapping_{DDMMYYYY}_{HHMMSS}.csv");

        StubDataSource stub = new StubDataSource(new ArrayList<>(), new ArrayList<>());
        BoPipeline pipeline = new BoPipeline(config, stub);

        BoTypeConfig boType = new BoTypeConfig();
        boType.setName("TESTBO");
        pipeline.execute(boType);

        File[] files = tempDir.resolve("MetaData").toFile().listFiles(
                f -> f.getName().contains("RelationshipMapping"));
        assertTrue(files == null || files.length == 0,
                "No RelationshipMapping file when no tracking IDs");
    }

    // ================================================================
    // Skip-components tests
    // ================================================================

    /**
     * A DataSource stub that returns a pre-built BoMetadata so tests can
     * inspect which components survived after the pipeline filtering step.
     * It captures the metadata reference so the test can inspect it after
     * execute() returns.
     */
    private static class CapturingDataSource implements DataSource {
        final BoMetadata metadata;

        CapturingDataSource(BoMetadata metadata) {
            this.metadata = metadata;
        }

        @Override public void login() {}
        @Override public void logout() {}
        @Override public List<String> getBoTypes() { return Collections.emptyList(); }

        @Override
        public BoMetadata getMetadata(String boType) {
            return metadata;
        }

        @Override
        public List<Long> getTrackingNumbers(String boType) {
            // Return empty so pipeline exits before writing any files (simplifies tests)
            return Collections.emptyList();
        }

        @Override
        public BundleResponse fetchBatch(List<Long> ids, List<String> fieldPaths, BoMetadata metadata) {
            BundleResponse r = new BundleResponse();
            r.setRecords(new ArrayList<>());
            return r;
        }

        @Override
        public List<ParentRecord> fetchBundleParents(List<Long> ids, int batchSize) {
            return Collections.emptyList();
        }
    }

    /** Build a ComponentMetadata with the given displayName and internalName. */
    private static ComponentMetadata component(String displayName, String internalName) {
        ComponentMetadata c = new ComponentMetadata();
        c.setDisplayName(displayName);
        c.setInternalName(internalName);
        c.setFields(new ArrayList<>());
        return c;
    }

    /** Build a BoMetadata with the supplied components. */
    private static BoMetadata boMetadata(ComponentMetadata... components) {
        BoMetadata m = new BoMetadata();
        m.setBoName("TESTBO");
        m.setComponents(new ArrayList<>(Arrays.asList(components)));
        return m;
    }

    // ----------------------------------------------------------------
    // skipComponents test 1: filter by displayName (exact, case-sensitive entry)
    // ----------------------------------------------------------------

    @Test
    void skipComponents_byDisplayName_removesComponent() throws IOException {
        AppConfig config = buildConfig();
        config.setSkipComponents(Arrays.asList("BundleProperties"));

        BoMetadata metadata = boMetadata(
                component("BundleProperties", "bundle_props"),
                component("BasicInfo", "basic_info")
        );

        CapturingDataSource ds = new CapturingDataSource(metadata);
        BoPipeline pipeline = new BoPipeline(config, ds);

        BoTypeConfig boTypeConfig = new BoTypeConfig();
        boTypeConfig.setName("TESTBO");
        pipeline.execute(boTypeConfig);

        List<ComponentMetadata> remaining = metadata.getComponents();
        assertEquals(1, remaining.size(), "Only one component should remain");
        assertEquals("BasicInfo", remaining.get(0).getDisplayName());
    }

    // ----------------------------------------------------------------
    // skipComponents test 2: case-insensitive matching
    // ----------------------------------------------------------------

    @Test
    void skipComponents_caseInsensitive_removesComponent() throws IOException {
        AppConfig config = buildConfig();
        config.setSkipComponents(Arrays.asList("bundleproperties"));

        BoMetadata metadata = boMetadata(
                component("BundleProperties", "bundle_props"),
                component("BasicInfo", "basic_info")
        );

        CapturingDataSource ds = new CapturingDataSource(metadata);
        BoPipeline pipeline = new BoPipeline(config, ds);

        BoTypeConfig boTypeConfig = new BoTypeConfig();
        boTypeConfig.setName("TESTBO");
        pipeline.execute(boTypeConfig);

        List<ComponentMetadata> remaining = metadata.getComponents();
        assertEquals(1, remaining.size(), "Component should be skipped case-insensitively");
        assertEquals("BasicInfo", remaining.get(0).getDisplayName());
    }

    // ----------------------------------------------------------------
    // skipComponents test 3: whitespace trimming
    // ----------------------------------------------------------------

    @Test
    void skipComponents_whitespaceTrimed_removesComponent() throws IOException {
        AppConfig config = buildConfig();
        config.setSkipComponents(Arrays.asList("  BundleProperties  "));

        BoMetadata metadata = boMetadata(
                component("BundleProperties", "bundle_props"),
                component("BasicInfo", "basic_info")
        );

        CapturingDataSource ds = new CapturingDataSource(metadata);
        BoPipeline pipeline = new BoPipeline(config, ds);

        BoTypeConfig boTypeConfig = new BoTypeConfig();
        boTypeConfig.setName("TESTBO");
        pipeline.execute(boTypeConfig);

        List<ComponentMetadata> remaining = metadata.getComponents();
        assertEquals(1, remaining.size(), "Component should be skipped despite surrounding whitespace in config entry");
        assertEquals("BasicInfo", remaining.get(0).getDisplayName());
    }

    // ----------------------------------------------------------------
    // skipComponents test 4: partial name does NOT match (exact match only)
    // ----------------------------------------------------------------

    @Test
    void skipComponents_partialName_doesNotMatch() throws IOException {
        AppConfig config = buildConfig();
        config.setSkipComponents(Arrays.asList("Bundle"));

        BoMetadata metadata = boMetadata(
                component("BundleProperties", "bundle_props"),
                component("BasicInfo", "basic_info")
        );

        CapturingDataSource ds = new CapturingDataSource(metadata);
        BoPipeline pipeline = new BoPipeline(config, ds);

        BoTypeConfig boTypeConfig = new BoTypeConfig();
        boTypeConfig.setName("TESTBO");
        pipeline.execute(boTypeConfig);

        List<ComponentMetadata> remaining = metadata.getComponents();
        assertEquals(2, remaining.size(), "Partial match should NOT skip the component");
    }

    // ----------------------------------------------------------------
    // skipComponents test 5: empty skipComponents — nothing removed
    // ----------------------------------------------------------------

    @Test
    void skipComponents_empty_removesNothing() throws IOException {
        AppConfig config = buildConfig();
        config.setSkipComponents(Collections.emptyList());

        BoMetadata metadata = boMetadata(
                component("BundleProperties", "bundle_props"),
                component("BasicInfo", "basic_info")
        );

        CapturingDataSource ds = new CapturingDataSource(metadata);
        BoPipeline pipeline = new BoPipeline(config, ds);

        BoTypeConfig boTypeConfig = new BoTypeConfig();
        boTypeConfig.setName("TESTBO");
        pipeline.execute(boTypeConfig);

        List<ComponentMetadata> remaining = metadata.getComponents();
        assertEquals(2, remaining.size(), "All components should remain when skipComponents is empty");
    }

    // ----------------------------------------------------------------
    // skipComponents test 6: match by internalName (not displayName)
    // ----------------------------------------------------------------

    @Test
    void skipComponents_byInternalName_removesComponent() throws IOException {
        AppConfig config = buildConfig();
        config.setSkipComponents(Arrays.asList("bundle_props"));

        BoMetadata metadata = boMetadata(
                component("BundleProperties", "bundle_props"),
                component("BasicInfo", "basic_info")
        );

        CapturingDataSource ds = new CapturingDataSource(metadata);
        BoPipeline pipeline = new BoPipeline(config, ds);

        BoTypeConfig boTypeConfig = new BoTypeConfig();
        boTypeConfig.setName("TESTBO");
        pipeline.execute(boTypeConfig);

        List<ComponentMetadata> remaining = metadata.getComponents();
        assertEquals(1, remaining.size(), "Component should be skipped when internalName matches");
        assertEquals("BasicInfo", remaining.get(0).getDisplayName());
    }
}
