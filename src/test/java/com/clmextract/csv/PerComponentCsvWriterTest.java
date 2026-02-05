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

class PerComponentCsvWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void testCorrectFilesCreated() throws IOException {
        BoMetadata metadata = makeMetadata();
        ColumnResolver resolver = new ColumnResolver(metadata.getComponents(), "NonExistentBO");
        FilenameResolver fnResolver = new FilenameResolver("01012026", "120000");

        PerComponentCsvWriter writer = new PerComponentCsvWriter(
                metadata, resolver, fnResolver,
                "{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv",
                tempDir, ',');

        writer.writeHeaders();
        writer.close();

        assertTrue(Files.exists(tempDir.resolve("ExampleBO_Summary_01012026_120000.csv")));
        assertTrue(Files.exists(tempDir.resolve("ExampleBO_Attachments_01012026_120000.csv")));
    }

    @Test
    void testTrackingNumberFirstColumn() throws IOException {
        BoMetadata metadata = makeMetadata();
        ColumnResolver resolver = new ColumnResolver(metadata.getComponents(), "NonExistentBO");
        FilenameResolver fnResolver = new FilenameResolver("01012026", "120000");

        PerComponentCsvWriter writer = new PerComponentCsvWriter(
                metadata, resolver, fnResolver,
                "{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv",
                tempDir, ',');

        writer.writeHeaders();
        writer.close();

        String summaryContent = Files.readString(
                tempDir.resolve("ExampleBO_Summary_01012026_120000.csv"));
        String firstLine = summaryContent.split("\n")[0];
        assertTrue(firstLine.startsWith("\"Tracking #\""));
    }

    @Test
    void testHeadersMatch() throws IOException {
        BoMetadata metadata = makeMetadata();
        ColumnResolver resolver = new ColumnResolver(metadata.getComponents(), "NonExistentBO");
        FilenameResolver fnResolver = new FilenameResolver("01012026", "120000");

        PerComponentCsvWriter writer = new PerComponentCsvWriter(
                metadata, resolver, fnResolver,
                "{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv",
                tempDir, ',');

        writer.writeHeaders();
        writer.close();

        String summaryContent = Files.readString(
                tempDir.resolve("ExampleBO_Summary_01012026_120000.csv"));
        String firstLine = summaryContent.split("\n")[0];
        assertTrue(firstLine.contains("\"Summary.Tracking #\""));
        assertTrue(firstLine.contains("\"Summary.Contract Number\""));
    }

    @Test
    void testSingleCardinalityDataRows() throws IOException {
        BoMetadata metadata = makeMetadata();
        ColumnResolver resolver = new ColumnResolver(metadata.getComponents(), "NonExistentBO");
        FilenameResolver fnResolver = new FilenameResolver("01012026", "120000");

        PerComponentCsvWriter writer = new PerComponentCsvWriter(
                metadata, resolver, fnResolver,
                "{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv",
                tempDir, ',');

        writer.writeHeaders();
        writer.writeRecords(List.of(makeBundleRecord()));
        writer.close();

        String content = Files.readString(
                tempDir.resolve("ExampleBO_Summary_01012026_120000.csv"));
        String[] lines = content.trim().split("\n");
        assertEquals(2, lines.length); // header + 1 data row
        assertTrue(lines[1].contains("\"1051372\""));
        assertTrue(lines[1].contains("\"EX-1051372\""));
    }

    @Test
    void testMultiCardinalityRowsExpanded() throws IOException {
        BoMetadata metadata = makeMetadata();
        ColumnResolver resolver = new ColumnResolver(metadata.getComponents(), "NonExistentBO");
        FilenameResolver fnResolver = new FilenameResolver("01012026", "120000");

        PerComponentCsvWriter writer = new PerComponentCsvWriter(
                metadata, resolver, fnResolver,
                "{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv",
                tempDir, ',');

        // Create record with multiple attachment rows
        BundleRecord record = new BundleRecord();
        record.setTrackingId(1001L);

        BundleComponent summary = new BundleComponent();
        summary.setComponentInternalName("ReqSummary");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("trackingNumber", "1001");
        fields.put("contractNumber", "EX-1001");
        summary.setFields(fields);

        BundleComponent attachments = new BundleComponent();
        attachments.setComponentInternalName("ReqAttachments");
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(Map.of("serverFileName", "/files/doc1.pdf"));
        rows.add(Map.of("serverFileName", "/files/doc2.pdf"));
        rows.add(Map.of("serverFileName", "/files/doc3.pdf"));
        attachments.setRows(rows);

        record.setComponents(Arrays.asList(summary, attachments));

        writer.writeHeaders();
        writer.writeRecords(List.of(record));
        writer.close();

        String content = Files.readString(
                tempDir.resolve("ExampleBO_Attachments_01012026_120000.csv"));
        String[] lines = content.trim().split("\n");
        assertEquals(4, lines.length); // header + 3 data rows
        // Each row should have the tracking ID
        assertTrue(lines[1].contains("\"1001\""));
        assertTrue(lines[2].contains("\"1001\""));
        assertTrue(lines[3].contains("\"1001\""));
        assertTrue(lines[1].contains("/files/doc1.pdf"));
        assertTrue(lines[2].contains("/files/doc2.pdf"));
        assertTrue(lines[3].contains("/files/doc3.pdf"));
    }

    @Test
    void testMultipleBatchesAppend() throws IOException {
        BoMetadata metadata = makeMetadata();
        ColumnResolver resolver = new ColumnResolver(metadata.getComponents(), "NonExistentBO");
        FilenameResolver fnResolver = new FilenameResolver("01012026", "120000");

        PerComponentCsvWriter writer = new PerComponentCsvWriter(
                metadata, resolver, fnResolver,
                "{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv",
                tempDir, ',');

        writer.writeHeaders();

        // Write two batches
        writer.writeRecords(List.of(makeBundleRecord()));

        BundleRecord record2 = new BundleRecord();
        record2.setTrackingId(2000L);
        BundleComponent summary2 = new BundleComponent();
        summary2.setComponentInternalName("ReqSummary");
        summary2.setFields(Map.of("trackingNumber", "2000", "contractNumber", "EX-2000"));
        BundleComponent attach2 = new BundleComponent();
        attach2.setComponentInternalName("ReqAttachments");
        attach2.setRows(List.of(Map.of("serverFileName", "/files/other.pdf")));
        record2.setComponents(Arrays.asList(summary2, attach2));

        writer.writeRecords(List.of(record2));
        writer.close();

        String content = Files.readString(
                tempDir.resolve("ExampleBO_Summary_01012026_120000.csv"));
        String[] lines = content.trim().split("\n");
        assertEquals(3, lines.length); // header + 2 data rows
    }

    private BoMetadata makeMetadata() {
        BoMetadata metadata = new BoMetadata();
        metadata.setBoName("ExampleBO");
        metadata.setBoUsageType("Contract");

        ComponentMetadata summary = new ComponentMetadata();
        summary.setInternalName("ReqSummary");
        summary.setDisplayName("Summary");
        summary.setCardinality("single");
        summary.setInstancePath("MCPDef:/ExampleData/ReqSummary/");

        FieldMetadata trackingField = new FieldMetadata();
        trackingField.setInternalName("trackingNumber");
        trackingField.setDisplayName("Tracking #");
        trackingField.setDataType("string");
        trackingField.setInstancePath("MCPDef:/ExampleData/ReqSummary/trackingNumber");

        FieldMetadata contractField = new FieldMetadata();
        contractField.setInternalName("contractNumber");
        contractField.setDisplayName("Contract Number");
        contractField.setDataType("string");
        contractField.setInstancePath("MCPDef:/ExampleData/ReqSummary/contractNumber");

        summary.setFields(Arrays.asList(trackingField, contractField));

        ComponentMetadata attachments = new ComponentMetadata();
        attachments.setInternalName("ReqAttachments");
        attachments.setDisplayName("Attachments");
        attachments.setCardinality("multiple");
        attachments.setInstancePath("MCPDef:/ExampleData/ReqAttachments/");

        FieldMetadata fileField = new FieldMetadata();
        fileField.setInternalName("serverFileName");
        fileField.setDisplayName("File Path");
        fileField.setDataType("string");
        fileField.setInstancePath("MCPDef:/ExampleData/ReqAttachments/serverFileName");

        attachments.setFields(List.of(fileField));

        metadata.setComponents(Arrays.asList(summary, attachments));
        return metadata;
    }

    private BundleRecord makeBundleRecord() {
        BundleRecord record = new BundleRecord();
        record.setTrackingId(1051372L);

        BundleComponent summary = new BundleComponent();
        summary.setComponentInternalName("ReqSummary");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("trackingNumber", "1051372");
        fields.put("contractNumber", "EX-1051372");
        summary.setFields(fields);

        BundleComponent attachments = new BundleComponent();
        attachments.setComponentInternalName("ReqAttachments");
        attachments.setRows(List.of(Map.of("serverFileName", "/files/contract_1051372.pdf")));

        record.setComponents(Arrays.asList(summary, attachments));
        return record;
    }
}
