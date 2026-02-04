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

class MergedSingleCsvWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void testSingleComponentsMergedIntoOneFile() throws IOException {
        BoMetadata metadata = makeMetadata();
        ColumnResolver resolver = new ColumnResolver(metadata.getComponents(), "NonExistentBO");
        FilenameResolver fnResolver = new FilenameResolver("01012026", "120000");

        MergedSingleCsvWriter writer = new MergedSingleCsvWriter(
                metadata, resolver, fnResolver,
                "{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv",
                tempDir, ',');

        writer.writeHeaders();
        writer.close();

        // Merged single file should exist (Component placeholder = "Merged")
        assertTrue(Files.exists(tempDir.resolve("Contract_Merged_01012026_120000.csv")));
    }

    @Test
    void testMultiComponentsSeparateFiles() throws IOException {
        BoMetadata metadata = makeMetadata();
        ColumnResolver resolver = new ColumnResolver(metadata.getComponents(), "NonExistentBO");
        FilenameResolver fnResolver = new FilenameResolver("01012026", "120000");

        MergedSingleCsvWriter writer = new MergedSingleCsvWriter(
                metadata, resolver, fnResolver,
                "{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv",
                tempDir, ',');

        writer.writeHeaders();
        writer.close();

        // Multi-cardinality component gets its own file
        assertTrue(Files.exists(tempDir.resolve("Contract_Attachments_01012026_120000.csv")));
    }

    @Test
    void testMergedHeadersContainAllSingleColumns() throws IOException {
        BoMetadata metadata = makeMetadataWithTwoSingleComponents();
        ColumnResolver resolver = new ColumnResolver(metadata.getComponents(), "NonExistentBO");
        FilenameResolver fnResolver = new FilenameResolver("01012026", "120000");

        MergedSingleCsvWriter writer = new MergedSingleCsvWriter(
                metadata, resolver, fnResolver,
                "{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv",
                tempDir, ',');

        writer.writeHeaders();
        writer.close();

        String content = Files.readString(tempDir.resolve("Contract_Merged_01012026_120000.csv"));
        String firstLine = content.split("\n")[0];
        assertTrue(firstLine.contains("\"Tracking #\""));
        assertTrue(firstLine.contains("\"Summary.Contract Number\""));
        assertTrue(firstLine.contains("\"Details.Description\""));
    }

    @Test
    void testMergedDataRows() throws IOException {
        BoMetadata metadata = makeMetadata();
        ColumnResolver resolver = new ColumnResolver(metadata.getComponents(), "NonExistentBO");
        FilenameResolver fnResolver = new FilenameResolver("01012026", "120000");

        MergedSingleCsvWriter writer = new MergedSingleCsvWriter(
                metadata, resolver, fnResolver,
                "{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv",
                tempDir, ',');

        writer.writeHeaders();
        writer.writeRecords(List.of(makeBundleRecord()));
        writer.close();

        String content = Files.readString(tempDir.resolve("Contract_Merged_01012026_120000.csv"));
        String[] lines = content.trim().split("\n");
        assertEquals(2, lines.length); // header + 1 data row
        assertTrue(lines[1].contains("\"1051372\""));
        assertTrue(lines[1].contains("\"EX-1051372\""));
    }

    @Test
    void testMultiCardinalityDataInSeparateFile() throws IOException {
        BoMetadata metadata = makeMetadata();
        ColumnResolver resolver = new ColumnResolver(metadata.getComponents(), "NonExistentBO");
        FilenameResolver fnResolver = new FilenameResolver("01012026", "120000");

        MergedSingleCsvWriter writer = new MergedSingleCsvWriter(
                metadata, resolver, fnResolver,
                "{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv",
                tempDir, ',');

        writer.writeHeaders();
        writer.writeRecords(List.of(makeBundleRecord()));
        writer.close();

        String content = Files.readString(tempDir.resolve("Contract_Attachments_01012026_120000.csv"));
        String[] lines = content.trim().split("\n");
        assertEquals(2, lines.length); // header + 1 attachment row
        assertTrue(lines[1].contains("/files/contract_1051372.pdf"));
    }

    private BoMetadata makeMetadata() {
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

        FieldMetadata contractField = new FieldMetadata();
        contractField.setInternalName("contractNumber");
        contractField.setDisplayName("Contract Number");
        contractField.setInstancePath("MCPDef:/BO/ReqSummary/contractNumber");

        summary.setFields(Arrays.asList(trackingField, contractField));

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

    private BoMetadata makeMetadataWithTwoSingleComponents() {
        BoMetadata metadata = new BoMetadata();
        metadata.setBoName("ExampleBO");
        metadata.setBoUsageType("Contract");

        ComponentMetadata summary = new ComponentMetadata();
        summary.setInternalName("ReqSummary");
        summary.setDisplayName("Summary");
        summary.setCardinality("single");

        FieldMetadata contractField = new FieldMetadata();
        contractField.setInternalName("contractNumber");
        contractField.setDisplayName("Contract Number");
        contractField.setInstancePath("MCPDef:/BO/ReqSummary/contractNumber");
        summary.setFields(List.of(contractField));

        ComponentMetadata details = new ComponentMetadata();
        details.setInternalName("ReqDetails");
        details.setDisplayName("Details");
        details.setCardinality("single");

        FieldMetadata descField = new FieldMetadata();
        descField.setInternalName("description");
        descField.setDisplayName("Description");
        descField.setInstancePath("MCPDef:/BO/ReqDetails/description");
        details.setFields(List.of(descField));

        metadata.setComponents(Arrays.asList(summary, details));
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
