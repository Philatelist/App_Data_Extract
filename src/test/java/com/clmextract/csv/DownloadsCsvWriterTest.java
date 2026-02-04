package com.clmextract.csv;

import com.clmextract.export.BundleComponent;
import com.clmextract.export.BundleRecord;
import com.clmextract.metadata.BoMetadata;
import com.clmextract.metadata.ComponentMetadata;
import com.clmextract.metadata.FieldMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DownloadsCsvWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void testAttachmentPathsExtracted() throws IOException {
        BoMetadata metadata = makeMetadataWithAttachments();
        FilenameResolver fnResolver = new FilenameResolver("01012026", "120000");

        DownloadsCsvWriter writer = new DownloadsCsvWriter(
                metadata, fnResolver,
                "{BO}_downloads_{DDMMYYYY}_{HHMMSS}.csv",
                tempDir, ',');

        writer.open();
        writer.writeRecords(List.of(makeBundleRecordWithAttachments()));
        writer.close();

        Path file = tempDir.resolve("Contract_downloads_01012026_120000.csv");
        assertTrue(Files.exists(file));

        String content = Files.readString(file);
        assertTrue(content.contains("/files/contract_1051372.pdf"));
        assertTrue(content.contains("/files/contract_1051372_annex.pdf"));
    }

    @Test
    void testOneColumnOnly() throws IOException {
        BoMetadata metadata = makeMetadataWithAttachments();
        FilenameResolver fnResolver = new FilenameResolver("01012026", "120000");

        DownloadsCsvWriter writer = new DownloadsCsvWriter(
                metadata, fnResolver,
                "{BO}_downloads_{DDMMYYYY}_{HHMMSS}.csv",
                tempDir, ',');

        writer.open();
        writer.writeRecords(List.of(makeBundleRecordWithAttachments()));
        writer.close();

        Path file = tempDir.resolve("Contract_downloads_01012026_120000.csv");
        String content = Files.readString(file);
        String[] lines = content.trim().split("\n");

        for (String line : lines) {
            // Each line should be a single quoted value (no commas between columns)
            String trimmed = line.trim();
            // Remove surrounding quotes if present
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            // The inner content should not contain unquoted commas (only one column)
            assertFalse(trimmed.contains("\",\""), "Should have only one column per line");
        }

        assertEquals(2, lines.length); // 2 attachment rows
    }

    @Test
    void testEmptyFileForBoWithoutAttachments() throws IOException {
        BoMetadata metadata = makeMetadataWithoutAttachments();
        FilenameResolver fnResolver = new FilenameResolver("01012026", "120000");

        DownloadsCsvWriter writer = new DownloadsCsvWriter(
                metadata, fnResolver,
                "{BO}_downloads_{DDMMYYYY}_{HHMMSS}.csv",
                tempDir, ',');

        writer.open();
        writer.writeRecords(List.of(makeBundleRecordNoAttachments()));
        writer.close();

        Path file = tempDir.resolve("Contract_downloads_01012026_120000.csv");
        assertTrue(Files.exists(file));

        String content = Files.readString(file);
        assertTrue(content.trim().isEmpty(), "File should be empty when no attachments component");
    }

    @Test
    void testEmptyValuesSkipped() throws IOException {
        BoMetadata metadata = makeMetadataWithAttachments();
        FilenameResolver fnResolver = new FilenameResolver("01012026", "120000");

        DownloadsCsvWriter writer = new DownloadsCsvWriter(
                metadata, fnResolver,
                "{BO}_downloads_{DDMMYYYY}_{HHMMSS}.csv",
                tempDir, ',');

        writer.open();

        // Create a record with one empty and one non-empty attachment
        BundleRecord record = new BundleRecord();
        record.setTrackingId(1051372L);

        BundleComponent summary = new BundleComponent();
        summary.setComponentInternalName("ReqSummary");
        summary.setFields(Map.of("trackingNumber", "1051372"));

        BundleComponent attachments = new BundleComponent();
        attachments.setComponentInternalName("ReqAttachments");
        attachments.setRows(List.of(
                Map.of("serverFileName", "/files/valid.pdf"),
                Map.of("serverFileName", ""),
                Map.of("otherField", "noFileName")
        ));

        record.setComponents(Arrays.asList(summary, attachments));

        writer.writeRecords(List.of(record));
        writer.close();

        Path file = tempDir.resolve("Contract_downloads_01012026_120000.csv");
        String content = Files.readString(file);
        String[] lines = content.trim().split("\n");

        assertEquals(1, lines.length); // Only the valid path
        assertTrue(lines[0].contains("/files/valid.pdf"));
    }

    @Test
    void testMultipleRecordsBatchWritten() throws IOException {
        BoMetadata metadata = makeMetadataWithAttachments();
        FilenameResolver fnResolver = new FilenameResolver("01012026", "120000");

        DownloadsCsvWriter writer = new DownloadsCsvWriter(
                metadata, fnResolver,
                "{BO}_downloads_{DDMMYYYY}_{HHMMSS}.csv",
                tempDir, ',');

        writer.open();

        // First batch
        writer.writeRecords(List.of(makeBundleRecordWithAttachments()));
        // Second batch
        BundleRecord record2 = new BundleRecord();
        record2.setTrackingId(2000000L);

        BundleComponent summary2 = new BundleComponent();
        summary2.setComponentInternalName("ReqSummary");
        summary2.setFields(Map.of("trackingNumber", "2000000"));

        BundleComponent attachments2 = new BundleComponent();
        attachments2.setComponentInternalName("ReqAttachments");
        attachments2.setRows(List.of(Map.of("serverFileName", "/files/second_doc.pdf")));

        record2.setComponents(Arrays.asList(summary2, attachments2));
        writer.writeRecords(List.of(record2));

        writer.close();

        Path file = tempDir.resolve("Contract_downloads_01012026_120000.csv");
        String content = Files.readString(file);
        String[] lines = content.trim().split("\n");

        assertEquals(3, lines.length); // 2 from first record + 1 from second
        assertTrue(content.contains("/files/contract_1051372.pdf"));
        assertTrue(content.contains("/files/contract_1051372_annex.pdf"));
        assertTrue(content.contains("/files/second_doc.pdf"));
    }

    private BoMetadata makeMetadataWithAttachments() {
        BoMetadata metadata = new BoMetadata();
        metadata.setBoName("ExampleBO");
        metadata.setBoUsageType("Contract");

        ComponentMetadata summary = new ComponentMetadata();
        summary.setInternalName("ReqSummary");
        summary.setDisplayName("Summary");
        summary.setCardinality("single");

        FieldMetadata trackingField = new FieldMetadata();
        trackingField.setInternalName("trackingNumber");
        trackingField.setDisplayName("Tracking #");
        trackingField.setInstancePath("MCPDef:/BO/ReqSummary/trackingNumber");
        summary.setFields(List.of(trackingField));

        ComponentMetadata attachments = new ComponentMetadata();
        attachments.setInternalName("ReqAttachments");
        attachments.setDisplayName("Attachments");
        attachments.setCardinality("multiple");

        FieldMetadata fileField = new FieldMetadata();
        fileField.setInternalName("serverFileName");
        fileField.setDisplayName("File Path");
        fileField.setInstancePath("MCPDef:/BO/ReqAttachments/serverFileName");
        attachments.setFields(List.of(fileField));

        metadata.setComponents(Arrays.asList(summary, attachments));
        return metadata;
    }

    private BoMetadata makeMetadataWithoutAttachments() {
        BoMetadata metadata = new BoMetadata();
        metadata.setBoName("ExampleBO");
        metadata.setBoUsageType("Contract");

        ComponentMetadata summary = new ComponentMetadata();
        summary.setInternalName("ReqSummary");
        summary.setDisplayName("Summary");
        summary.setCardinality("single");

        FieldMetadata trackingField = new FieldMetadata();
        trackingField.setInternalName("trackingNumber");
        trackingField.setDisplayName("Tracking #");
        trackingField.setInstancePath("MCPDef:/BO/ReqSummary/trackingNumber");
        summary.setFields(List.of(trackingField));

        // Only single-cardinality components, no attachments
        metadata.setComponents(List.of(summary));
        return metadata;
    }

    private BundleRecord makeBundleRecordWithAttachments() {
        BundleRecord record = new BundleRecord();
        record.setTrackingId(1051372L);

        BundleComponent summary = new BundleComponent();
        summary.setComponentInternalName("ReqSummary");
        summary.setFields(Map.of("trackingNumber", "1051372"));

        BundleComponent attachments = new BundleComponent();
        attachments.setComponentInternalName("ReqAttachments");
        attachments.setRows(List.of(
                Map.of("serverFileName", "/files/contract_1051372.pdf"),
                Map.of("serverFileName", "/files/contract_1051372_annex.pdf")
        ));

        record.setComponents(Arrays.asList(summary, attachments));
        return record;
    }

    private BundleRecord makeBundleRecordNoAttachments() {
        BundleRecord record = new BundleRecord();
        record.setTrackingId(1051372L);

        BundleComponent summary = new BundleComponent();
        summary.setComponentInternalName("ReqSummary");
        summary.setFields(Map.of("trackingNumber", "1051372"));

        record.setComponents(List.of(summary));
        return record;
    }
}
