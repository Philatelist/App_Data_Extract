package com.clmextract.web.api;

import com.clmextract.config.BoTypeConfig;
import com.clmextract.metadata.BoMetadata;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AdminController.buildBoTypeResponse().
 *
 * Javalin Context is not mocked — the core business logic is extracted into
 * the static helper method buildBoTypeResponse(), which is tested directly
 * using hand-rolled stub data.
 */
class AdminControllerBoTypesTest {

    // -----------------------------------------------------------------------
    // Stub builders
    // -----------------------------------------------------------------------

    private static BoMetadata stubMeta(String displayName, String usageType) {
        BoMetadata m = new BoMetadata();
        m.setBoDisplayName(displayName);
        m.setBoUsageType(usageType);
        return m;
    }

    private static BoTypeConfig stubConfig(String name, String localizedName) {
        BoTypeConfig bt = new BoTypeConfig();
        bt.setName(name);
        bt.setLocalizedName(localizedName);
        return bt;
    }

    private static Map<String, BoTypeConfig> configMap(BoTypeConfig... entries) {
        Map<String, BoTypeConfig> map = new LinkedHashMap<>();
        for (BoTypeConfig bt : entries) {
            map.put(bt.getName(), bt);
        }
        return map;
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    void result_containsCorrectFields_forCheckedBo() {
        List<String> names = List.of("ContractA");
        Map<String, BoMetadata> meta = Map.of("ContractA", stubMeta("Contract Alpha", "Contract"));
        Map<String, BoTypeConfig> cfg = configMap(stubConfig("ContractA", "My Contract A"));

        List<Map<String, Object>> result = AdminController.buildBoTypeResponse(names, meta, cfg);

        assertEquals(1, result.size());
        Map<String, Object> entry = result.get(0);
        assertEquals("ContractA", entry.get("internalName"));
        assertEquals("Contract Alpha", entry.get("displayName"));
        assertEquals("Contract", entry.get("usageType"));
        assertEquals(true, entry.get("checked"));
        assertEquals("My Contract A", entry.get("localizedName"));
    }

    @Test
    void result_checked_isFalse_whenBoNotInConfig() {
        List<String> names = List.of("UnknownBo");
        Map<String, BoMetadata> meta = Map.of("UnknownBo", stubMeta("Unknown BO", "Directory"));
        Map<String, BoTypeConfig> cfg = Collections.emptyMap();

        List<Map<String, Object>> result = AdminController.buildBoTypeResponse(names, meta, cfg);

        assertEquals(1, result.size());
        assertEquals(false, result.get(0).get("checked"));
    }

    @Test
    void result_checked_isTrue_whenBoIsInConfig() {
        List<String> names = List.of("ConfiguredBo");
        Map<String, BoMetadata> meta = Map.of("ConfiguredBo", stubMeta("Configured BO", "NonContract"));
        Map<String, BoTypeConfig> cfg = configMap(stubConfig("ConfiguredBo", null));

        List<Map<String, Object>> result = AdminController.buildBoTypeResponse(names, meta, cfg);

        assertEquals(true, result.get(0).get("checked"));
    }

    @Test
    void result_displayName_fallsBackToInternalName_whenMetadataMissing() {
        List<String> names = List.of("OrphanBo");
        Map<String, BoMetadata> meta = Collections.emptyMap(); // no metadata for OrphanBo
        Map<String, BoTypeConfig> cfg = Collections.emptyMap();

        List<Map<String, Object>> result = AdminController.buildBoTypeResponse(names, meta, cfg);

        assertEquals(1, result.size());
        assertEquals("OrphanBo", result.get(0).get("displayName"));
        assertEquals("OrphanBo", result.get(0).get("internalName"));
    }

    @Test
    void result_usageType_isEmptyString_whenMetadataMissing() {
        List<String> names = List.of("NoMetaBo");
        Map<String, BoMetadata> meta = Collections.emptyMap();
        Map<String, BoTypeConfig> cfg = Collections.emptyMap();

        List<Map<String, Object>> result = AdminController.buildBoTypeResponse(names, meta, cfg);

        assertEquals("", result.get(0).get("usageType"));
    }

    @Test
    void result_usageType_isEmptyString_whenMetadataUsageTypeIsNull() {
        List<String> names = List.of("NullUsageBo");
        Map<String, BoMetadata> meta = Map.of("NullUsageBo", stubMeta("Null Usage BO", null));
        Map<String, BoTypeConfig> cfg = Collections.emptyMap();

        List<Map<String, Object>> result = AdminController.buildBoTypeResponse(names, meta, cfg);

        assertEquals("", result.get(0).get("usageType"));
    }

    @Test
    void result_localizedName_isEmptyString_whenNotSetInConfig() {
        List<String> names = List.of("ConfiguredNoLocalized");
        Map<String, BoMetadata> meta = Map.of("ConfiguredNoLocalized", stubMeta("Display", "Contract"));
        Map<String, BoTypeConfig> cfg = configMap(stubConfig("ConfiguredNoLocalized", null));

        List<Map<String, Object>> result = AdminController.buildBoTypeResponse(names, meta, cfg);

        assertEquals("", result.get(0).get("localizedName"));
    }

    @Test
    void result_localizedName_isEmptyString_whenBoNotInConfig() {
        List<String> names = List.of("Unconfigured");
        Map<String, BoMetadata> meta = Map.of("Unconfigured", stubMeta("Unconfigured BO", "Directory"));
        Map<String, BoTypeConfig> cfg = Collections.emptyMap();

        List<Map<String, Object>> result = AdminController.buildBoTypeResponse(names, meta, cfg);

        assertEquals("", result.get(0).get("localizedName"));
    }

    @Test
    void result_preservesOrderOfInputNames() {
        List<String> names = List.of("ZBo", "ABo", "MBo");
        Map<String, BoMetadata> meta = Collections.emptyMap();
        Map<String, BoTypeConfig> cfg = Collections.emptyMap();

        List<Map<String, Object>> result = AdminController.buildBoTypeResponse(names, meta, cfg);

        assertEquals(3, result.size());
        assertEquals("ZBo", result.get(0).get("internalName"));
        assertEquals("ABo", result.get(1).get("internalName"));
        assertEquals("MBo", result.get(2).get("internalName"));
    }

    @Test
    void result_mixedCheckedAndUnchecked_withMultipleBos() {
        List<String> names = List.of("BoA", "BoB", "BoC");
        Map<String, BoMetadata> meta = new HashMap<>();
        meta.put("BoA", stubMeta("Business Object A", "Contract"));
        meta.put("BoC", stubMeta("Business Object C", "Directory"));
        // BoB has no metadata (simulates a fetch failure)
        Map<String, BoTypeConfig> cfg = configMap(
                stubConfig("BoA", "Localized A"),
                stubConfig("BoC", null)
        );

        List<Map<String, Object>> result = AdminController.buildBoTypeResponse(names, meta, cfg);

        assertEquals(3, result.size());

        // BoA: in config, has metadata
        assertEquals("BoA", result.get(0).get("internalName"));
        assertEquals("Business Object A", result.get(0).get("displayName"));
        assertEquals("Contract", result.get(0).get("usageType"));
        assertEquals(true, result.get(0).get("checked"));
        assertEquals("Localized A", result.get(0).get("localizedName"));

        // BoB: not in config, no metadata
        assertEquals("BoB", result.get(1).get("internalName"));
        assertEquals("BoB", result.get(1).get("displayName")); // falls back to internalName
        assertEquals("", result.get(1).get("usageType"));
        assertEquals(false, result.get(1).get("checked"));
        assertEquals("", result.get(1).get("localizedName"));

        // BoC: in config (no localized), has metadata
        assertEquals("BoC", result.get(2).get("internalName"));
        assertEquals("Business Object C", result.get(2).get("displayName"));
        assertEquals("Directory", result.get(2).get("usageType"));
        assertEquals(true, result.get(2).get("checked"));
        assertEquals("", result.get(2).get("localizedName"));
    }

    @Test
    void result_isEmpty_whenNoBoTypesDiscovered() {
        List<Map<String, Object>> result = AdminController.buildBoTypeResponse(
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap()
        );
        assertTrue(result.isEmpty());
    }
}
