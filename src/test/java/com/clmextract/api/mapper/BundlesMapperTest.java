package com.clmextract.api.mapper;

import com.clmextract.api.dto.BundleFieldDto;
import com.clmextract.export.BundleComponent;
import com.clmextract.export.BundleRecord;
import com.clmextract.export.BundleResponse;
import com.clmextract.metadata.BoMetadata;
import com.clmextract.metadata.ComponentMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BundlesMapperTest {

    @Test
    void testMapSingleCardinalityRecord() {
        BoMetadata metadata = buildMetadata("single");
        List<List<BundleFieldDto>> rawRecords = List.of(
                buildSingleCardRecord(12345L)
        );

        BundlesMapper mapper = new BundlesMapper();
        BundleResponse response = mapper.map(rawRecords, metadata, null);

        assertEquals("TestBO", response.getBoName());
        assertEquals(1, response.getRecords().size());

        BundleRecord record = response.getRecords().get(0);
        assertEquals(12345L, record.getTrackingId());
        assertEquals(1, record.getComponents().size());

        BundleComponent comp = record.getComponents().get(0);
        assertEquals("ReqInfo", comp.getComponentInternalName());
        assertTrue(comp.isSingleCardinality());
        assertFalse(comp.isMultipleCardinality());
        assertEquals("12345", comp.getFields().get("trackingNumber"));
        assertEquals("Test Contract", comp.getFields().get("contractName"));
    }

    @Test
    void testMapMultipleRecords() {
        BoMetadata metadata = buildMetadata("single");
        List<List<BundleFieldDto>> rawRecords = List.of(
                buildSingleCardRecord(111L),
                buildSingleCardRecord(222L)
        );

        BundlesMapper mapper = new BundlesMapper();
        BundleResponse response = mapper.map(rawRecords, metadata, null);

        assertEquals(2, response.getRecords().size());
        assertEquals(111L, response.getRecords().get(0).getTrackingId());
        assertEquals(222L, response.getRecords().get(1).getTrackingId());
    }

    @Test
    void testMapMultiCardinalityComponent() {
        BoMetadata metadata = buildMetadataWithMultiComponent();

        // One record with fields from both single and multi-cardinality components
        List<BundleFieldDto> fields = new ArrayList<>();
        // Single-cardinality fields
        fields.add(makeField("trackingNumber", "5000",
                "MCP:/TestData|5000/ReqInfo|5000/trackingNumber"));
        fields.add(makeField("contractName", "Multi Test",
                "MCP:/TestData|5000/ReqInfo|5000/contractName"));
        // Multi-cardinality fields - instance 1001
        fields.add(makeField("serverFileName", "file1.pdf",
                "MCP:/TestData|5000/ReqAttachment|1001/serverFileName"));
        fields.add(makeField("fileSize", "1024",
                "MCP:/TestData|5000/ReqAttachment|1001/fileSize"));
        // Multi-cardinality fields - instance 1002
        fields.add(makeField("serverFileName", "file2.docx",
                "MCP:/TestData|5000/ReqAttachment|1002/serverFileName"));
        fields.add(makeField("fileSize", "2048",
                "MCP:/TestData|5000/ReqAttachment|1002/fileSize"));

        List<List<BundleFieldDto>> rawRecords = List.of(fields);

        BundlesMapper mapper = new BundlesMapper();
        BundleResponse response = mapper.map(rawRecords, metadata, null);

        assertEquals(1, response.getRecords().size());
        BundleRecord record = response.getRecords().get(0);
        assertEquals(5000L, record.getTrackingId());
        assertEquals(2, record.getComponents().size());

        // Single component
        BundleComponent singleComp = record.getComponents().get(0);
        assertEquals("ReqInfo", singleComp.getComponentInternalName());
        assertTrue(singleComp.isSingleCardinality());
        assertEquals("Multi Test", singleComp.getFields().get("contractName"));

        // Multi component
        BundleComponent multiComp = record.getComponents().get(1);
        assertEquals("ReqAttachment", multiComp.getComponentInternalName());
        assertTrue(multiComp.isMultipleCardinality());
        assertEquals(2, multiComp.getRows().size());
        assertEquals("file1.pdf", multiComp.getRows().get(0).get("serverFileName"));
        assertEquals("1024", multiComp.getRows().get(0).get("fileSize"));
        assertEquals("file2.docx", multiComp.getRows().get(1).get("serverFileName"));
        assertEquals("2048", multiComp.getRows().get(1).get("fileSize"));
    }

    @Test
    void testMapEmptyRecords() {
        BoMetadata metadata = buildMetadata("single");
        List<List<BundleFieldDto>> rawRecords = List.of();

        BundlesMapper mapper = new BundlesMapper();
        BundleResponse response = mapper.map(rawRecords, metadata, null);

        assertEquals("TestBO", response.getBoName());
        assertTrue(response.getRecords().isEmpty());
    }

    @Test
    void testMapFieldsWithNoInstancePath() {
        BoMetadata metadata = buildMetadata("single");

        List<BundleFieldDto> fields = new ArrayList<>();
        fields.add(makeField("trackingNumber", "999",
                "MCP:/TestData|999/ReqInfo|999/trackingNumber"));
        // Field with no instance path — should be skipped during component grouping
        BundleFieldDto orphan = new BundleFieldDto();
        orphan.setName("orphanField");
        orphan.setValue("orphanValue");
        fields.add(orphan);

        List<List<BundleFieldDto>> rawRecords = List.of(fields);

        BundlesMapper mapper = new BundlesMapper();
        BundleResponse response = mapper.map(rawRecords, metadata, null);

        BundleRecord record = response.getRecords().get(0);
        assertEquals(1, record.getComponents().size());
        assertNull(record.getComponents().get(0).getFields().get("orphanField"));
    }

    @Test
    void testMapUnknownComponentDefaultsToSingle() {
        // Metadata has no components — unknown component should default to single cardinality
        BoMetadata metadata = new BoMetadata();
        metadata.setBoName("EmptyBO");
        metadata.setComponents(List.of());

        List<BundleFieldDto> fields = new ArrayList<>();
        fields.add(makeField("trackingNumber", "777",
                "MCP:/Data|777/UnknownComp|777/trackingNumber"));
        fields.add(makeField("someField", "someValue",
                "MCP:/Data|777/UnknownComp|777/someField"));

        List<List<BundleFieldDto>> rawRecords = List.of(fields);

        BundlesMapper mapper = new BundlesMapper();
        BundleResponse response = mapper.map(rawRecords, metadata, null);

        BundleRecord record = response.getRecords().get(0);
        assertEquals(1, record.getComponents().size());
        BundleComponent comp = record.getComponents().get(0);
        assertEquals("UnknownComp", comp.getComponentInternalName());
        assertTrue(comp.isSingleCardinality());
        assertEquals("someValue", comp.getFields().get("someField"));
    }

    @Test
    void testPositionalTrackingIdAssignment() {
        BoMetadata metadata = buildMetadata("single");

        // Two records with trackingNumber "999" in the response fields
        List<BundleFieldDto> fields1 = new ArrayList<>();
        fields1.add(makeField("trackingNumber", "999",
                "MCP:/TestData|999/ReqInfo|999/trackingNumber"));
        fields1.add(makeField("contractName", "Contract A",
                "MCP:/TestData|999/ReqInfo|999/contractName"));

        List<BundleFieldDto> fields2 = new ArrayList<>();
        fields2.add(makeField("trackingNumber", "999",
                "MCP:/TestData|999/ReqInfo|999/trackingNumber"));
        fields2.add(makeField("contractName", "Contract B",
                "MCP:/TestData|999/ReqInfo|999/contractName"));

        List<List<BundleFieldDto>> rawRecords = List.of(fields1, fields2);
        List<Long> requestTrackingIds = List.of(100L, 200L);

        BundlesMapper mapper = new BundlesMapper();
        BundleResponse response = mapper.map(rawRecords, metadata, requestTrackingIds);

        assertEquals(2, response.getRecords().size());
        assertEquals(100L, response.getRecords().get(0).getTrackingId());
        assertEquals(200L, response.getRecords().get(1).getTrackingId());
    }

    @Test
    void testNullRequestTrackingIdsFallsBackToResponseField() {
        BoMetadata metadata = buildMetadata("single");

        List<BundleFieldDto> fields = new ArrayList<>();
        fields.add(makeField("trackingNumber", "555",
                "MCP:/TestData|555/ReqInfo|555/trackingNumber"));
        fields.add(makeField("contractName", "Fallback Contract",
                "MCP:/TestData|555/ReqInfo|555/contractName"));

        List<List<BundleFieldDto>> rawRecords = List.of(fields);

        BundlesMapper mapper = new BundlesMapper();
        BundleResponse response = mapper.map(rawRecords, metadata, null);

        assertEquals(1, response.getRecords().size());
        assertEquals(555L, response.getRecords().get(0).getTrackingId());
    }

    @Test
    void testSizeMismatchFallsBackToResponseField() {
        BoMetadata metadata = buildMetadata("single");

        List<BundleFieldDto> fields1 = new ArrayList<>();
        fields1.add(makeField("trackingNumber", "111",
                "MCP:/TestData|111/ReqInfo|111/trackingNumber"));
        fields1.add(makeField("contractName", "Contract One",
                "MCP:/TestData|111/ReqInfo|111/contractName"));

        List<BundleFieldDto> fields2 = new ArrayList<>();
        fields2.add(makeField("trackingNumber", "222",
                "MCP:/TestData|222/ReqInfo|222/trackingNumber"));
        fields2.add(makeField("contractName", "Contract Two",
                "MCP:/TestData|222/ReqInfo|222/contractName"));

        List<List<BundleFieldDto>> rawRecords = List.of(fields1, fields2);
        // Size 1 mismatches rawRecords size 2
        List<Long> requestTrackingIds = List.of(999L);

        BundlesMapper mapper = new BundlesMapper();
        BundleResponse response = mapper.map(rawRecords, metadata, requestTrackingIds);

        assertEquals(2, response.getRecords().size());
        assertEquals(111L, response.getRecords().get(0).getTrackingId());
        assertEquals(222L, response.getRecords().get(1).getTrackingId());
    }

    // --- Helper methods ---

    private BoMetadata buildMetadata(String cardinality) {
        BoMetadata metadata = new BoMetadata();
        metadata.setBoName("TestBO");

        ComponentMetadata comp = new ComponentMetadata();
        comp.setInternalName("ReqInfo");
        comp.setDisplayName("Info");
        comp.setCardinality(cardinality);
        comp.setFields(List.of());

        metadata.setComponents(List.of(comp));
        return metadata;
    }

    private BoMetadata buildMetadataWithMultiComponent() {
        BoMetadata metadata = new BoMetadata();
        metadata.setBoName("TestBO");

        ComponentMetadata singleComp = new ComponentMetadata();
        singleComp.setInternalName("ReqInfo");
        singleComp.setDisplayName("Info");
        singleComp.setCardinality("single");
        singleComp.setFields(List.of());

        ComponentMetadata multiComp = new ComponentMetadata();
        multiComp.setInternalName("ReqAttachment");
        multiComp.setDisplayName("Attachments");
        multiComp.setCardinality("multiple");
        multiComp.setFields(List.of());

        metadata.setComponents(Arrays.asList(singleComp, multiComp));
        return metadata;
    }

    private List<BundleFieldDto> buildSingleCardRecord(long trackingId) {
        List<BundleFieldDto> fields = new ArrayList<>();
        fields.add(makeField("trackingNumber", String.valueOf(trackingId),
                "MCP:/TestData|" + trackingId + "/ReqInfo|" + trackingId + "/trackingNumber"));
        fields.add(makeField("contractName", "Test Contract",
                "MCP:/TestData|" + trackingId + "/ReqInfo|" + trackingId + "/contractName"));
        return fields;
    }

    private BundleFieldDto makeField(String name, String value, String instancePath) {
        BundleFieldDto field = new BundleFieldDto();
        field.setName(name);
        field.setValue(value);
        field.setInstancePath(instancePath);
        return field;
    }
}
