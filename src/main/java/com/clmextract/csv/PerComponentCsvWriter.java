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

public class PerComponentCsvWriter implements CsvExportWriter {

    private static final Logger logger = LogManager.getLogger(PerComponentCsvWriter.class);

    private final BoMetadata metadata;
    private final ColumnResolver columnResolver;
    private final FilenameResolver filenameResolver;
    private final String filenameTemplate;
    private final Path outputDir;
    private final char delimiter;

    private final Map<String, ICSVWriter> writers = new LinkedHashMap<>();
    private final Map<String, List<ColumnResolver.ResolvedColumn>> componentColumns = new LinkedHashMap<>();

    public PerComponentCsvWriter(BoMetadata metadata, ColumnResolver columnResolver,
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
        for (ComponentMetadata comp : metadata.getComponents()) {
            boolean exempt = ColumnResolver.isExemptFromStandardColumns(comp.getInternalName());
            List<ColumnResolver.ResolvedColumn> columns = columnResolver.resolveColumns(comp);
            if (!exempt) {
                ColumnResolver.injectAdditionalColumns(columns, columnResolver.getAdditionalColumns());
            }
            componentColumns.put(comp.getInternalName(), columns);

            String filename = filenameResolver.resolve(
                    filenameTemplate, metadata.getBoName(), comp.getDisplayName());
            Path filePath = outputDir.resolve(filename);

            try {
                ICSVWriter writer = new CSVWriterBuilder(new FileWriter(filePath.toString()))
                        .withSeparator(delimiter)
                        .build();
                writers.put(comp.getInternalName(), writer);

                String[] header;
                if (exempt) {
                    header = new String[columns.size()];
                    for (int i = 0; i < columns.size(); i++) {
                        header[i] = columns.get(i).getHeader();
                    }
                } else {
                    header = new String[columns.size() + 1];
                    header[0] = columnResolver.resolveTrackingHeader();
                    for (int i = 0; i < columns.size(); i++) {
                        header[i + 1] = columns.get(i).getHeader();
                    }
                }
                writer.writeNext(header, false);

                logger.info("Opened CSV for component {}: {}", comp.getDisplayName(), filePath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to open CSV file: " + filePath, e);
            }
        }
    }

    @Override
    public void writeRecords(List<BundleRecord> records) {
        for (BundleRecord record : records) {
            Map<String, BundleComponent> componentMap = new LinkedHashMap<>();
            for (BundleComponent c : record.getComponents()) {
                componentMap.put(c.getComponentInternalName(), c);
            }

            for (BundleComponent comp : record.getComponents()) {
                ICSVWriter writer = writers.get(comp.getComponentInternalName());
                List<ColumnResolver.ResolvedColumn> columns =
                        componentColumns.get(comp.getComponentInternalName());

                if (writer == null || columns == null) {
                    continue;
                }

                boolean exempt = ColumnResolver.isExemptFromStandardColumns(comp.getComponentInternalName());
                String trackingId = exempt ? null : String.valueOf(record.getTrackingId());

                if (comp.isSingleCardinality()) {
                    writer.writeNext(buildRow(columns, comp, componentMap, -1, trackingId), false);
                } else if (comp.isMultipleCardinality()) {
                    List<Map<String, String>> rows = comp.getRows();
                    if (rows != null) {
                        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
                            writer.writeNext(buildRow(columns, comp, componentMap, rowIdx, trackingId), false);
                        }
                    }
                }
            }
        }
    }

    private static String[] buildRow(List<ColumnResolver.ResolvedColumn> columns,
                                     BundleComponent primaryComp,
                                     Map<String, BundleComponent> componentMap,
                                     int rowIdx,
                                     String trackingId) {
        int offset = trackingId != null ? 1 : 0;
        String[] row = new String[columns.size() + offset];
        if (trackingId != null) {
            row[0] = trackingId;
        }
        for (int i = 0; i < columns.size(); i++) {
            row[i + offset] = resolveValue(columns.get(i), primaryComp, componentMap, rowIdx);
        }
        return row;
    }

    private static String resolveValue(ColumnResolver.ResolvedColumn col,
                                       BundleComponent primaryComp,
                                       Map<String, BundleComponent> componentMap,
                                       int rowIdx) {
        if (col.isAdditional()) {
            return "";
        }
        BundleComponent source = col.getSourceComponentInternalName() != null
                ? componentMap.get(col.getSourceComponentInternalName())
                : primaryComp;
        if (source == null) {
            return "";
        }
        if (rowIdx < 0) {
            if (source.isSingleCardinality()) {
                Map<String, String> fields = source.getFields();
                return fields != null ? fields.getOrDefault(col.getFieldInternalName(), "") : "";
            }
            List<Map<String, String>> srcRows = source.getRows();
            if (srcRows != null && !srcRows.isEmpty()) {
                return srcRows.get(0).getOrDefault(col.getFieldInternalName(), "");
            }
            return "";
        }
        if (source.isMultipleCardinality()) {
            List<Map<String, String>> srcRows = source.getRows();
            if (srcRows != null && rowIdx < srcRows.size()) {
                return srcRows.get(rowIdx).getOrDefault(col.getFieldInternalName(), "");
            }
            return "";
        }
        Map<String, String> fields = source.getFields();
        return fields != null ? fields.getOrDefault(col.getFieldInternalName(), "") : "";
    }

    @Override
    public void close() throws IOException {
        List<IOException> errors = new ArrayList<>();
        for (Map.Entry<String, ICSVWriter> entry : writers.entrySet()) {
            try {
                entry.getValue().close();
                logger.debug("Closed CSV for component: {}", entry.getKey());
            } catch (IOException e) {
                errors.add(e);
            }
        }
        writers.clear();
        if (!errors.isEmpty()) {
            throw errors.get(0);
        }
    }
}
