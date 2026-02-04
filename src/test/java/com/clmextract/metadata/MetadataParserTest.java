package com.clmextract.metadata;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MetadataParserTest {

    @Test
    void testParseSampleMetadata() throws IOException {
        String json = Files.readString(Path.of("inputs/samples/boMetaData.sample.json"));
        MetadataParser parser = new MetadataParser();
        BoMetadata metadata = parser.parse(json);

        assertEquals("ExampleBO", metadata.getBoName());
        assertEquals("Contract", metadata.getBoUsageType());
        assertNotNull(metadata.getComponents());
        assertEquals(2, metadata.getComponents().size());
    }

    @Test
    void testSingleCardinalityComponent() throws IOException {
        String json = Files.readString(Path.of("inputs/samples/boMetaData.sample.json"));
        MetadataParser parser = new MetadataParser();
        BoMetadata metadata = parser.parse(json);

        ComponentMetadata summary = metadata.getComponents().get(0);
        assertEquals("ReqSummary", summary.getInternalName());
        assertEquals("Summary", summary.getDisplayName());
        assertEquals("single", summary.getCardinality());
        assertTrue(summary.isSingleCardinality());
        assertFalse(summary.isMultipleCardinality());
        assertEquals("MCPDef:/ExampleData/ReqSummary/", summary.getInstancePath());

        List<FieldMetadata> fields = summary.getFields();
        assertNotNull(fields);
        assertEquals(2, fields.size());

        FieldMetadata trackingField = fields.get(0);
        assertEquals("trackingNumber", trackingField.getInternalName());
        assertEquals("Tracking #", trackingField.getDisplayName());
        assertEquals("string", trackingField.getDataType());
        assertEquals("MCPDef:/ExampleData/ReqSummary/trackingNumber", trackingField.getInstancePath());

        FieldMetadata contractField = fields.get(1);
        assertEquals("contractNumber", contractField.getInternalName());
        assertEquals("Contract Number", contractField.getDisplayName());
        assertEquals("string", contractField.getDataType());
        assertEquals("MCPDef:/ExampleData/ReqSummary/contractNumber", contractField.getInstancePath());
    }

    @Test
    void testMultipleCardinalityComponent() throws IOException {
        String json = Files.readString(Path.of("inputs/samples/boMetaData.sample.json"));
        MetadataParser parser = new MetadataParser();
        BoMetadata metadata = parser.parse(json);

        ComponentMetadata attachments = metadata.getComponents().get(1);
        assertEquals("ReqAttachments", attachments.getInternalName());
        assertEquals("Attachments", attachments.getDisplayName());
        assertEquals("multiple", attachments.getCardinality());
        assertFalse(attachments.isSingleCardinality());
        assertTrue(attachments.isMultipleCardinality());
        assertEquals("MCPDef:/ExampleData/ReqAttachments/", attachments.getInstancePath());

        List<FieldMetadata> fields = attachments.getFields();
        assertNotNull(fields);
        assertEquals(1, fields.size());

        FieldMetadata fileField = fields.get(0);
        assertEquals("serverFileName", fileField.getInternalName());
        assertEquals("File Path", fileField.getDisplayName());
        assertEquals("string", fileField.getDataType());
        assertEquals("MCPDef:/ExampleData/ReqAttachments/serverFileName", fileField.getInstancePath());
    }

    @Test
    void testFieldInstancePaths() throws IOException {
        String json = Files.readString(Path.of("inputs/samples/boMetaData.sample.json"));
        MetadataParser parser = new MetadataParser();
        BoMetadata metadata = parser.parse(json);

        // Verify all field instance paths are present (used for bundles API fieldPaths)
        for (ComponentMetadata comp : metadata.getComponents()) {
            for (FieldMetadata field : comp.getFields()) {
                assertNotNull(field.getInstancePath());
                assertTrue(field.getInstancePath().startsWith("MCPDef:/"));
            }
        }
    }

    @Test
    void testInvalidJsonThrows() {
        MetadataParser parser = new MetadataParser();
        assertThrows(RuntimeException.class, () -> parser.parse("not valid json"));
    }
}
