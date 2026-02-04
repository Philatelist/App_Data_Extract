package com.clmextract.export;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BundleParserTest {

    @Test
    void testParseSampleBundles() throws IOException {
        String json = Files.readString(Path.of("inputs/samples/bundles.sample.json"));
        BundleParser parser = new BundleParser();
        BundleResponse response = parser.parse(json);

        assertEquals("ExampleBO", response.getBoName());
        assertNotNull(response.getRecords());
        assertEquals(1, response.getRecords().size());
    }

    @Test
    void testRecordTrackingId() throws IOException {
        String json = Files.readString(Path.of("inputs/samples/bundles.sample.json"));
        BundleParser parser = new BundleParser();
        BundleResponse response = parser.parse(json);

        BundleRecord record = response.getRecords().get(0);
        assertEquals(1051372L, record.getTrackingId());
    }

    @Test
    void testRecordComponents() throws IOException {
        String json = Files.readString(Path.of("inputs/samples/bundles.sample.json"));
        BundleParser parser = new BundleParser();
        BundleResponse response = parser.parse(json);

        BundleRecord record = response.getRecords().get(0);
        assertNotNull(record.getComponents());
        assertEquals(2, record.getComponents().size());
    }

    @Test
    void testSingleCardinalityFields() throws IOException {
        String json = Files.readString(Path.of("inputs/samples/bundles.sample.json"));
        BundleParser parser = new BundleParser();
        BundleResponse response = parser.parse(json);

        BundleRecord record = response.getRecords().get(0);
        BundleComponent summary = record.getComponents().get(0);

        assertEquals("ReqSummary", summary.getComponentInternalName());
        assertTrue(summary.isSingleCardinality());
        assertFalse(summary.isMultipleCardinality());

        Map<String, String> fields = summary.getFields();
        assertNotNull(fields);
        assertEquals("1051372", fields.get("trackingNumber"));
        assertEquals("EX-1051372", fields.get("contractNumber"));
    }

    @Test
    void testMultiCardinalityRows() throws IOException {
        String json = Files.readString(Path.of("inputs/samples/bundles.sample.json"));
        BundleParser parser = new BundleParser();
        BundleResponse response = parser.parse(json);

        BundleRecord record = response.getRecords().get(0);
        BundleComponent attachments = record.getComponents().get(1);

        assertEquals("ReqAttachments", attachments.getComponentInternalName());
        assertFalse(attachments.isSingleCardinality());
        assertTrue(attachments.isMultipleCardinality());

        List<Map<String, String>> rows = attachments.getRows();
        assertNotNull(rows);
        assertEquals(1, rows.size());
        assertEquals("/files/contract_1051372.pdf", rows.get(0).get("serverFileName"));
    }

    @Test
    void testInvalidJsonThrows() {
        BundleParser parser = new BundleParser();
        assertThrows(RuntimeException.class, () -> parser.parse("not valid json"));
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
