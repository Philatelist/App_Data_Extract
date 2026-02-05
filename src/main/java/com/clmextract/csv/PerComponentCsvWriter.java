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
            List<ColumnResolver.ResolvedColumn> columns = columnResolver.resolveColumns(comp);
            if (columns.isEmpty()) {
                continue;
            }
            componentColumns.put(comp.getInternalName(), columns);

            String filename = filenameResolver.resolve(
                    filenameTemplate, metadata.getBoName(), comp.getDisplayName());
            Path filePath = outputDir.resolve(filename);

            try {
                ICSVWriter writer = new CSVWriterBuilder(new FileWriter(filePath.toString()))
                        .withSeparator(delimiter)
                        .withQuoteChar(ICSVWriter.NO_QUOTE_CHARACTER)
                        .build();
                writers.put(comp.getInternalName(), writer);

                // Build header: Tracking # + column headers
                String[] header = new String[columns.size() + 1];
                header[0] = "Summary.Tracking #";
                for (int i = 0; i < columns.size(); i++) {
                    header[i + 1] = columns.get(i).getHeader();
                }
                writer.writeNext(header);

                logger.info("Opened CSV for component {}: {}", comp.getDisplayName(), filePath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to open CSV file: " + filePath, e);
            }
        }
    }

    @Override
    public void writeRecords(List<BundleRecord> records) {
        for (BundleRecord record : records) {
            for (BundleComponent comp : record.getComponents()) {
                ICSVWriter writer = writers.get(comp.getComponentInternalName());
                List<ColumnResolver.ResolvedColumn> columns =
                        componentColumns.get(comp.getComponentInternalName());

                if (writer == null || columns == null) {
                    continue;
                }

                String trackingId = String.valueOf(record.getTrackingId());

                if (comp.isSingleCardinality()) {
                    String[] row = new String[columns.size() + 1];
                    row[0] = trackingId;
                    Map<String, String> fields = comp.getFields();
                    for (int i = 0; i < columns.size(); i++) {
                        String fieldName = columns.get(i).getFieldInternalName();
                        row[i + 1] = fields != null ? fields.getOrDefault(fieldName, "") : "";
                    }
                    writer.writeNext(row);
                } else if (comp.isMultipleCardinality()) {
                    List<Map<String, String>> rows = comp.getRows();
                    if (rows != null) {
                        for (Map<String, String> rowData : rows) {
                            String[] row = new String[columns.size() + 1];
                            row[0] = trackingId;
                            for (int i = 0; i < columns.size(); i++) {
                                String fieldName = columns.get(i).getFieldInternalName();
                                row[i + 1] = rowData.getOrDefault(fieldName, "");
                            }
                            writer.writeNext(row);
                        }
                    }
                }
            }
        }
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
