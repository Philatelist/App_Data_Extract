package com.clmextract.export;

import com.clmextract.metadata.BoMetadata;
import com.clmextract.metadata.MetadataParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BundleParserTest {

    private static final List<Long> TRACKING_IDS = List.of(16016628L, 17011747L);

    private static BoMetadata metadata;

    @BeforeAll
    static void loadMetadata() throws IOException {
        String metaJson = Files.readString(Path.of("inputs/samples/BOMetaDataResponse.example.json"));
        metadata = new MetadataParser().parse(metaJson);
    }

    @Test
    void testParseSampleBundles() throws IOException {
        String json = Files.readString(Path.of("inputs/samples/BundlesResponse.example.json"));
        BundleParser parser = new BundleParser();
        BundleResponse response = parser.parse(json, metadata, TRACKING_IDS);

        assertEquals("NAFBO", response.getBoName());
        assertNotNull(response.getRecords());
        assertEquals(2, response.getRecords().size());
    }

    @Test
    void testRecordTrackingId() throws IOException {
        String json = Files.readString(Path.of("inputs/samples/BundlesResponse.example.json"));
        BundleParser parser = new BundleParser();
        BundleResponse response = parser.parse(json, metadata, TRACKING_IDS);

        BundleRecord record1 = response.getRecords().get(0);
        assertEquals(16016628L, record1.getTrackingId());

        BundleRecord record2 = response.getRecords().get(1);
        assertEquals(17011747L, record2.getTrackingId());
    }

    @Test
    void testRecordComponents() throws IOException {
        String json = Files.readString(Path.of("inputs/samples/BundlesResponse.example.json"));
        BundleParser parser = new BundleParser();
        BundleResponse response = parser.parse(json, metadata, TRACKING_IDS);

        BundleRecord record = response.getRecords().get(0);
        assertNotNull(record.getComponents());
        // The example fixture contains fields from multiple components
        assertEquals(7, record.getComponents().size());
    }

    @Test
    void testSingleCardinalityFields() throws IOException {
        String json = Files.readString(Path.of("inputs/samples/BundlesResponse.example.json"));
        BundleParser parser = new BundleParser();
        BundleResponse response = parser.parse(json, metadata, TRACKING_IDS);

        BundleRecord record = response.getRecords().get(0);
        BundleComponent nafInfo = record.getComponents().get(0);

        assertEquals("ReqNAFInfo", nafInfo.getComponentInternalName());
        assertTrue(nafInfo.isSingleCardinality());
        assertFalse(nafInfo.isMultipleCardinality());

        Map<String, String> fields = nafInfo.getFields();
        assertNotNull(fields);
        assertEquals("16016628", fields.get("trackingNumber"));
        assertEquals("1SY-16016628", fields.get("name"));
        assertEquals("Inactive", fields.get("contractStatus"));
    }

    @Test
    void testSecondRecordFields() throws IOException {
        String json = Files.readString(Path.of("inputs/samples/BundlesResponse.example.json"));
        BundleParser parser = new BundleParser();
        BundleResponse response = parser.parse(json, metadata, TRACKING_IDS);

        BundleRecord record = response.getRecords().get(1);
        BundleComponent nafInfo = record.getComponents().get(0);

        assertEquals("ReqNAFInfo", nafInfo.getComponentInternalName());
        assertEquals("17011747", nafInfo.getFields().get("trackingNumber"));
        assertEquals("AME-17011747-Extension", nafInfo.getFields().get("name"));
    }

    @Test
    void testTrackingIdsAssignedFromRequestPositionally() throws IOException {
        String json = Files.readString(Path.of("inputs/samples/BundlesResponse.example.json"));
        BundleParser parser = new BundleParser();
        BundleResponse response = parser.parse(json, metadata, TRACKING_IDS);

        List<BundleRecord> records = response.getRecords();
        assertEquals(2, records.size());
        assertEquals(16016628L, records.get(0).getTrackingId(),
                "First record should get first request tracking ID positionally");
        assertEquals(17011747L, records.get(1).getTrackingId(),
                "Second record should get second request tracking ID positionally");
    }

    @Test
    void testInvalidJsonThrows() {
        BundleParser parser = new BundleParser();
        assertThrows(RuntimeException.class, () -> parser.parse("not valid json", metadata, null));
    }

    @Test
    void testBatchSplit() {
        List<Long> ids = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L);

        List<List<Long>> batches = BatchProcessor.split(ids, 3);
        assertEquals(3, batches.size());
        assertEquals(Arrays.asList(1L, 2L, 3L), batches.get(0));
        assertEquals(Arrays.asList(4L, 5L, 6L), batches.get(1));
        assertEquals(Arrays.asList(7L), batches.get(2));
    }

    @Test
    void testBatchSplitExactFit() {
        List<Long> ids = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L);

        List<List<Long>> batches = BatchProcessor.split(ids, 3);
        assertEquals(2, batches.size());
        assertEquals(3, batches.get(0).size());
        assertEquals(3, batches.get(1).size());
    }

    @Test
    void testBatchSplitSingleBatch() {
        List<Long> ids = Arrays.asList(1L, 2L, 3L);

        List<List<Long>> batches = BatchProcessor.split(ids, 10);
        assertEquals(1, batches.size());
        assertEquals(3, batches.get(0).size());
    }

    @Test
    void testBatchSplitEmpty() {
        List<Long> ids = List.of();

        List<List<Long>> batches = BatchProcessor.split(ids, 10);
        assertEquals(0, batches.size());
    }
}
