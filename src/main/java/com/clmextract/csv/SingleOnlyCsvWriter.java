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

public class SingleOnlyCsvWriter implements CsvExportWriter {

    private static final Logger logger = LogManager.getLogger(SingleOnlyCsvWriter.class);

    private final BoMetadata metadata;
    private final ColumnResolver columnResolver;
    private final FilenameResolver filenameResolver;
    private final String filenameTemplate;
    private final Path outputDir;
    private final char delimiter;

    private ICSVWriter writer;
    private List<MergedColumnEntry> mergedColumns;

    public SingleOnlyCsvWriter(BoMetadata metadata, ColumnResolver columnResolver,
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
        mergedColumns = new ArrayList<>();
        for (ComponentMetadata comp : metadata.getComponents()) {
            if (comp.isSingleCardinality()) {
                List<ColumnResolver.ResolvedColumn> columns = columnResolver.resolveColumns(comp);
                for (ColumnResolver.ResolvedColumn col : columns) {
                    mergedColumns.add(new MergedColumnEntry(comp.getInternalName(), col));
                }
            }
            // Multi-cardinality components are skipped entirely
        }

        if (mergedColumns.isEmpty()) {
            logger.info("No single-cardinality components found, no CSV will be written");
            return;
        }

        String filename = filenameResolver.resolve(
                filenameTemplate, metadata.getBoName(), "SingleOnly");
        Path filePath = outputDir.resolve(filename);
        try {
            writer = new CSVWriterBuilder(new FileWriter(filePath.toString()))
                    .withSeparator(delimiter)
                    .build();

            String[] header = new String[mergedColumns.size() + 1];
            header[0] = columnResolver.resolveTrackingHeader();
            for (int i = 0; i < mergedColumns.size(); i++) {
                header[i + 1] = mergedColumns.get(i).column.getHeader();
            }
            writer.writeNext(header, false);
            logger.info("Opened single-only CSV: {}", filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open CSV: " + filePath, e);
        }
    }

    @Override
    public void writeRecords(List<BundleRecord> records) {
        if (writer == null || mergedColumns == null) {
            return;
        }

        for (BundleRecord record : records) {
            String trackingId = String.valueOf(record.getTrackingId());
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
            writer.writeNext(row, false);
        }
    }

    @Override
    public void close() throws IOException {
        if (writer != null) {
            writer.close();
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
