package com.clmextract.csv;

import com.clmextract.export.BundleComponent;
import com.clmextract.export.BundleRecord;
import com.clmextract.metadata.BoMetadata;
import com.clmextract.metadata.ComponentMetadata;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MergedSingleCsvWriter implements CsvExportWriter {

    private static final Logger logger = LogManager.getLogger(MergedSingleCsvWriter.class);

    private final BoMetadata metadata;
    private final ColumnResolver columnResolver;
    private final FilenameResolver filenameResolver;
    private final String filenameTemplate;
    private final Path outputDir;
    private final char delimiter;

    private ICSVWriter mergedWriter;
    private List<MergedColumnEntry> mergedColumns;
    private final Map<String, ICSVWriter> multiWriters = new LinkedHashMap<>();
    private final Map<String, List<ColumnResolver.ResolvedColumn>> multiComponentColumns = new LinkedHashMap<>();

    public MergedSingleCsvWriter(BoMetadata metadata, ColumnResolver columnResolver,
                                 FilenameResolver filenameResolver, String filenameTemplate,
                                 Path outputDir, char delimiter) {
        this.metadata = metadata;
        this.columnResolver = columnResolver;
        this.filenameResolver = filenameResolver;
        this.filenameTemplate = filenameTemplate;
        this.outputDir = outputDir;
        this.delimiter = delimiter;
    }

    @Override
    public void writeHeaders() {
        // Build merged columns from all single-cardinality components
        mergedColumns = new ArrayList<>();
        for (ComponentMetadata comp : metadata.getComponents()) {
            if (comp.isSingleCardinality()) {
                List<ColumnResolver.ResolvedColumn> columns = columnResolver.resolveColumns(comp);
                for (ColumnResolver.ResolvedColumn col : columns) {
                    mergedColumns.add(new MergedColumnEntry(comp.getInternalName(), col));
                }
            }
        }

        // Open merged single CSV if there are single-cardinality columns
        if (!mergedColumns.isEmpty()) {
            String filename = filenameResolver.resolve(
                    filenameTemplate, metadata.getBoName(), "Merged");
            Path filePath = outputDir.resolve(filename);
            try {
                mergedWriter = new CSVWriterBuilder(new FileWriter(filePath.toString()))
                        .withSeparator(delimiter)
                        .build();

                String[] header = new String[mergedColumns.size() + 1];
                header[0] = columnResolver.resolveTrackingHeader();
                for (int i = 0; i < mergedColumns.size(); i++) {
                    header[i + 1] = mergedColumns.get(i).column.getHeader();
                }
                mergedWriter.writeNext(header, false);
                logger.info("Opened merged single CSV: {}", filePath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to open merged CSV: " + filePath, e);
            }
        }

        // Open separate CSVs for multi-cardinality components
        for (ComponentMetadata comp : metadata.getComponents()) {
            if (comp.isMultipleCardinality()) {
                List<ColumnResolver.ResolvedColumn> columns = columnResolver.resolveColumns(comp);
                multiComponentColumns.put(comp.getInternalName(), columns);

                String filename = filenameResolver.resolve(
                        filenameTemplate, metadata.getBoName(), comp.getDisplayName());
                Path filePath = outputDir.resolve(filename);
                try {
                    ICSVWriter writer = new CSVWriterBuilder(new FileWriter(filePath.toString()))
                            .withSeparator(delimiter)
                            .build();
                    multiWriters.put(comp.getInternalName(), writer);

                    String[] header = new String[columns.size() + 1];
                    header[0] = columnResolver.resolveTrackingHeader();
                    for (int i = 0; i < columns.size(); i++) {
                        header[i + 1] = columns.get(i).getHeader();
                    }
                    writer.writeNext(header, false);
                    logger.info("Opened multi-cardinality CSV for {}: {}", comp.getDisplayName(), filePath);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to open CSV: " + filePath, e);
                }
            }
        }
    }

    @Override
    public void writeRecords(List<BundleRecord> records) {
        for (BundleRecord record : records) {
            String trackingId = String.valueOf(record.getTrackingId());

            // Build merged single row
            if (mergedWriter != null && mergedColumns != null) {
                String[] row = new String[mergedColumns.size() + 1];
                row[0] = trackingId;

                Map<String, BundleComponent> componentMap = new LinkedHashMap<>();
                for (BundleComponent comp : record.getComponents()) {
                    componentMap.put(comp.getComponentInternalName(), comp);
                }

                for (int i = 0; i < mergedColumns.size(); i++) {
                    MergedColumnEntry entry = mergedColumns.get(i);
                    BundleComponent comp = componentMap.get(entry.componentInternalName);
                    if (comp != null && comp.getFields() != null) {
                        row[i + 1] = comp.getFields().getOrDefault(
                                entry.column.getFieldInternalName(), "");
                    } else {
                        row[i + 1] = "";
                    }
                }
                mergedWriter.writeNext(row, false);
            }

            // Write multi-cardinality rows
            for (BundleComponent comp : record.getComponents()) {
                ICSVWriter writer = multiWriters.get(comp.getComponentInternalName());
                List<ColumnResolver.ResolvedColumn> columns =
                        multiComponentColumns.get(comp.getComponentInternalName());

                if (writer == null || columns == null || !comp.isMultipleCardinality()) {
                    continue;
                }

                if (comp.getRows() != null) {
                    for (Map<String, String> rowData : comp.getRows()) {
                        String[] row = new String[columns.size() + 1];
                        row[0] = trackingId;
                        for (int i = 0; i < columns.size(); i++) {
                            row[i + 1] = rowData.getOrDefault(
                                    columns.get(i).getFieldInternalName(), "");
                        }
                        writer.writeNext(row, false);
                    }
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        List<IOException> errors = new ArrayList<>();
        if (mergedWriter != null) {
            try {
                mergedWriter.close();
            } catch (IOException e) {
                errors.add(e);
            }
        }
        for (ICSVWriter writer : multiWriters.values()) {
            try {
                writer.close();
            } catch (IOException e) {
                errors.add(e);
            }
        }
        multiWriters.clear();
        if (!errors.isEmpty()) {
            throw errors.get(0);
        }
    }

    private static class MergedColumnEntry {
        final String componentInternalName;
        final ColumnResolver.ResolvedColumn column;

        MergedColumnEntry(String componentInternalName, ColumnResolver.ResolvedColumn column) {
            this.componentInternalName = componentInternalName;
            this.column = column;
        }
    }
}
