package com.clmextract.export;

import com.clmextract.config.AppConfig;
import com.clmextract.config.BoTypeConfig;
import com.clmextract.metadata.BoMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExportOrchestratorTest {

    @TempDir
    Path tempDir;

    // ----------------------------------------------------------------
    // Stub DataSource that tracks getBoTypes() calls
    // ----------------------------------------------------------------

    private static class StubDataSource implements DataSource {
        private final List<String> boTypeNames;
        private final Map<String, BoMetadata> metadataMap = new HashMap<>();
        boolean getBoTypesCalled = false;

        StubDataSource(List<String> boTypeNames) {
            this.boTypeNames = boTypeNames;
        }

        void putMetadata(String boType, BoMetadata metadata) {
            metadataMap.put(boType, metadata);
        }

        @Override
        public void login() {}

        @Override
        public void logout() {}

        @Override
        public List<String> getBoTypes() {
            getBoTypesCalled = true;
            return boTypeNames;
        }

        @Override
        public BoMetadata getMetadata(String boType) {
            if (metadataMap.containsKey(boType)) {
                return metadataMap.get(boType);
            }
            BoMetadata m = new BoMetadata();
            m.setBoName(boType);
            m.setBoUsageType("Contract");
            m.setComponents(new ArrayList<>());
            return m;
        }

        @Override
        public List<Long> getTrackingNumbers(String boType) {
            return Collections.emptyList();
        }

        @Override
        public BundleResponse fetchBatch(List<Long> trackingIds, List<String> fieldPaths, BoMetadata metadata) {
            BundleResponse r = new BundleResponse();
            r.setRecords(new ArrayList<>());
            return r;
        }
    }

    // ----------------------------------------------------------------
    // Helper: build an ExportOrchestrator with the given config
    // ----------------------------------------------------------------

    private ExportOrchestrator buildOrchestrator(AppConfig config) {
        // Pass null for EndpointRegistry — it's only used in createDataSource() for
        // online mode, which we never exercise here (resolveBoTypes is called directly).
        return new ExportOrchestrator(config, null);
    }

    // ----------------------------------------------------------------
    // Test 1: Explicit mode — two named BOs, getBoTypes() never called
    // ----------------------------------------------------------------

    @Test
    void testExplicitMode_returnsTwoBoTypes_andDoesNotCallDiscovery() {
        AppConfig config = new AppConfig();
        config.setOutputRoot(tempDir.toString());

        BoTypeConfig nafbo = new BoTypeConfig();
        nafbo.setName("NAFBO");
        BoTypeConfig gpebo = new BoTypeConfig();
        gpebo.setName("GPEBO");
        config.setBoTypes(Arrays.asList(nafbo, gpebo));

        StubDataSource stub = new StubDataSource(Collections.singletonList("ShouldNotAppear"));
        ExportOrchestrator orchestrator = buildOrchestrator(config);

        List<BoTypeConfig> result = orchestrator.resolveBoTypes(stub);

        assertEquals(2, result.size());
        assertEquals("NAFBO", result.get(0).getName());
        assertEquals("GPEBO", result.get(1).getName());
        assertFalse(stub.getBoTypesCalled, "getBoTypes() must NOT be called in explicit mode");
    }

    // ----------------------------------------------------------------
    // Test 2: Discovery-all — empty boTypes, stub returns 3 names
    // ----------------------------------------------------------------

    @Test
    void testDiscoveryAll_emptyBoTypes_returnsAllDiscovered() {
        AppConfig config = new AppConfig();
        config.setOutputRoot(tempDir.toString());
        config.setBoTypes(new ArrayList<>());
        config.setBoUsageTypeFilter(null);

        StubDataSource stub = new StubDataSource(Arrays.asList("BOA", "BOB", "BOC"));
        ExportOrchestrator orchestrator = buildOrchestrator(config);

        List<BoTypeConfig> result = orchestrator.resolveBoTypes(stub);

        assertEquals(3, result.size());
        assertEquals("BOA", result.get(0).getName());
        assertEquals("BOB", result.get(1).getName());
        assertEquals("BOC", result.get(2).getName());
        assertTrue(stub.getBoTypesCalled, "getBoTypes() must be called in discovery mode");
    }

    // ----------------------------------------------------------------
    // Test 3: Empty discovery — stub returns empty list, no exception
    // ----------------------------------------------------------------

    @Test
    void testDiscoveryAll_emptyDiscovery_returnsEmptyList() {
        AppConfig config = new AppConfig();
        config.setOutputRoot(tempDir.toString());
        config.setBoTypes(new ArrayList<>());

        StubDataSource stub = new StubDataSource(new ArrayList<>());
        ExportOrchestrator orchestrator = buildOrchestrator(config);

        List<BoTypeConfig> result = orchestrator.resolveBoTypes(stub);

        assertNotNull(result);
        assertTrue(result.isEmpty(), "Empty discovery should return an empty list");
    }

    // ----------------------------------------------------------------
    // Test 4: Blank-name entry triggers discovery, not explicit mode
    // ----------------------------------------------------------------

    @Test
    void testBlankNameEntry_triggersDiscovery() {
        AppConfig config = new AppConfig();
        config.setOutputRoot(tempDir.toString());

        BoTypeConfig blankEntry = new BoTypeConfig();
        blankEntry.setName("");
        config.setBoTypes(Arrays.asList(blankEntry));

        StubDataSource stub = new StubDataSource(Arrays.asList("BOA"));
        ExportOrchestrator orchestrator = buildOrchestrator(config);

        List<BoTypeConfig> result = orchestrator.resolveBoTypes(stub);

        assertEquals(1, result.size());
        assertEquals("BOA", result.get(0).getName());
        assertTrue(stub.getBoTypesCalled, "Blank name should trigger discovery mode");
    }

    // ----------------------------------------------------------------
    // Test 5: Mode 3 — keep only BOs matching usageType filter
    // ----------------------------------------------------------------

    @Test
    void testDiscoveryFiltered_keepMatchingUsageType() {
        AppConfig config = new AppConfig();
        config.setOutputRoot(tempDir.toString());
        config.setBoTypes(new ArrayList<>());
        config.setBoUsageTypeFilter("Contract");

        StubDataSource stub = new StubDataSource(Arrays.asList("BOA", "BOB", "BOC"));

        BoMetadata mA = new BoMetadata();
        mA.setBoUsageType("Contract");
        stub.putMetadata("BOA", mA);

        BoMetadata mB = new BoMetadata();
        mB.setBoUsageType("Directory");
        stub.putMetadata("BOB", mB);

        BoMetadata mC = new BoMetadata();
        mC.setBoUsageType("Contract");
        stub.putMetadata("BOC", mC);

        ExportOrchestrator orchestrator = buildOrchestrator(config);
        List<BoTypeConfig> result = orchestrator.resolveBoTypes(stub);

        assertEquals(2, result.size());
        assertEquals("BOA", result.get(0).getName());
        assertEquals("BOC", result.get(1).getName());
    }

    // ----------------------------------------------------------------
    // Test 6: Mode 3 — null usageType excludes the BO
    // ----------------------------------------------------------------

    @Test
    void testDiscoveryFiltered_nullUsageType_excluded() {
        AppConfig config = new AppConfig();
        config.setOutputRoot(tempDir.toString());
        config.setBoTypes(new ArrayList<>());
        config.setBoUsageTypeFilter("Contract");

        StubDataSource stub = new StubDataSource(Arrays.asList("BOA", "BOB"));

        BoMetadata mA = new BoMetadata();
        mA.setBoUsageType(null);
        stub.putMetadata("BOA", mA);

        BoMetadata mB = new BoMetadata();
        mB.setBoUsageType("Contract");
        stub.putMetadata("BOB", mB);

        ExportOrchestrator orchestrator = buildOrchestrator(config);
        List<BoTypeConfig> result = orchestrator.resolveBoTypes(stub);

        assertEquals(1, result.size());
        assertEquals("BOB", result.get(0).getName());
    }

    // ----------------------------------------------------------------
    // Test 7: Mode 3 — no BOs match filter, returns empty list
    // ----------------------------------------------------------------

    @Test
    void testDiscoveryFiltered_noMatch_returnsEmpty() {
        AppConfig config = new AppConfig();
        config.setOutputRoot(tempDir.toString());
        config.setBoTypes(new ArrayList<>());
        config.setBoUsageTypeFilter("Contract");

        StubDataSource stub = new StubDataSource(Arrays.asList("BOA", "BOB"));

        BoMetadata mA = new BoMetadata();
        mA.setBoUsageType("Directory");
        stub.putMetadata("BOA", mA);

        BoMetadata mB = new BoMetadata();
        mB.setBoUsageType("Directory");
        stub.putMetadata("BOB", mB);

        ExportOrchestrator orchestrator = buildOrchestrator(config);
        List<BoTypeConfig> result = orchestrator.resolveBoTypes(stub);

        assertNotNull(result);
        assertTrue(result.isEmpty(), "No matching usageType should return an empty list");
    }

    // ----------------------------------------------------------------
    // Test 8: run() produces exactly one Manifest_*.csv in outputDir
    // ----------------------------------------------------------------

    @Test
    void testRun_producesManifestCsv() {
        AppConfig config = new AppConfig();
        config.setOutputRoot(tempDir.toString());
        config.setExportFolderName("MetaData");
        config.setBoTypes(new ArrayList<>());
        config.setBoUsageTypeFilter(null);

        StubDataSource stub = new StubDataSource(new ArrayList<>());

        // Subclass ExportOrchestrator so we can inject StubDataSource
        // instead of going through the real createDataSource() path.
        ExportOrchestrator orchestrator = new ExportOrchestrator(config, null) {
            @Override
            protected DataSource createDataSource() {
                return stub;
            }
        };

        orchestrator.run();

        Path outputDir = tempDir.resolve("MetaData");
        File[] manifestFiles = outputDir.toFile().listFiles(
                f -> f.getName().startsWith("Manifest_") && f.getName().endsWith(".csv"));

        assertNotNull(manifestFiles, "Output directory must exist and be listable");
        assertEquals(1, manifestFiles.length, "Exactly one Manifest_*.csv file must be created");
    }
}
