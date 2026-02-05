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
        String json = Files.readString(Path.of("inputs/samples/BOMetaDataResponse.example.json"));
        MetadataParser parser = new MetadataParser();
        BoMetadata metadata = parser.parse(json);

        assertEquals("NAFBO", metadata.getBoName());
        assertEquals("Contract", metadata.getBoUsageType());
        assertNotNull(metadata.getComponents());
        assertEquals(20, metadata.getComponents().size());
    }

    @Test
    void testSingleCardinalityComponent() throws IOException {
        String json = Files.readString(Path.of("inputs/samples/BOMetaDataResponse.example.json"));
        MetadataParser parser = new MetadataParser();
        BoMetadata metadata = parser.parse(json);

        ComponentMetadata summary = metadata.getComponents().get(0);
        assertEquals("ReqNAFInfo", summary.getInternalName());
        assertEquals("Summary", summary.getDisplayName());
        assertEquals("single", summary.getCardinality());
        assertTrue(summary.isSingleCardinality());
        assertFalse(summary.isMultipleCardinality());
        assertEquals("MCPDef:/NAFData/ReqNAFInfo/", summary.getInstancePath());

        List<FieldMetadata> fields = summary.getFields();
        assertNotNull(fields);
        assertEquals(3, fields.size());

        // Verify trackingNumber field exists (may not be first; componentId comes first)
        FieldMetadata trackingField = fields.stream()
                .filter(f -> "trackingNumber".equals(f.getInternalName()))
                .findFirst()
                .orElseThrow();
        assertEquals("Tracking #", trackingField.getDisplayName());
        assertEquals("int", trackingField.getDataType());
        assertEquals("MCPDef:/NAFData/ReqNAFInfo/trackingNumber", trackingField.getInstancePath());
    }

    @Test
    void testMultipleCardinalityComponent() throws IOException {
        String json = Files.readString(Path.of("inputs/samples/BOMetaDataResponse.example.json"));
        MetadataParser parser = new MetadataParser();
        BoMetadata metadata = parser.parse(json);

        // Find ReqAttachment component
        ComponentMetadata attachments = metadata.getComponents().stream()
                .filter(c -> "ReqAttachment".equals(c.getInternalName()))
                .findFirst()
                .orElseThrow();

        assertEquals("ReqAttachment", attachments.getInternalName());
        assertEquals("Attachments", attachments.getDisplayName());
        assertEquals("multiple", attachments.getCardinality());
        assertFalse(attachments.isSingleCardinality());
        assertTrue(attachments.isMultipleCardinality());
        assertEquals("MCPDef:/NAFData/ReqAttachment/", attachments.getInstancePath());

        List<FieldMetadata> fields = attachments.getFields();
        assertNotNull(fields);
        assertTrue(fields.size() >= 1);

        // fileVersion should be one of the fields
        boolean hasFileVersion = fields.stream()
                .anyMatch(f -> "fileVersion".equals(f.getInternalName()));
        assertTrue(hasFileVersion);
    }

    @Test
    void testFieldInstancePaths() throws IOException {
        String json = Files.readString(Path.of("inputs/samples/BOMetaDataResponse.example.json"));
        MetadataParser parser = new MetadataParser();
        BoMetadata metadata = parser.parse(json);

        // Verify all field instance paths are present (used for bundles API fieldPaths)
        for (ComponentMetadata comp : metadata.getComponents()) {
            for (FieldMetadata field : comp.getFields()) {
                assertNotNull(field.getInstancePath(),
                        "Field " + field.getInternalName() + " in " + comp.getInternalName()
                                + " should have an instancePath");
                assertTrue(field.getInstancePath().startsWith("MCPDef:/"),
                        "Field instancePath should start with MCPDef:/ but was: " + field.getInstancePath());
            }
        }
    }

    @Test
    void testInvalidJsonThrows() {
        MetadataParser parser = new MetadataParser();
        assertThrows(RuntimeException.class, () -> parser.parse("not valid json"));
    }
}
