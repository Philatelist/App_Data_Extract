package com.clmextract.csv;

import com.clmextract.export.BundleComponent;
import com.clmextract.export.BundleRecord;
import com.clmextract.metadata.BoMetadata;
import com.clmextract.metadata.ComponentMetadata;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class DownloadsCsvWriter implements Closeable {

    private static final Logger logger = LogManager.getLogger(DownloadsCsvWriter.class);

    private final BoMetadata metadata;
    private final FilenameResolver filenameResolver;
    private final String downloadsFilenameTemplate;
    private final Path downloadsDir;
    private final char delimiter;

    private ICSVWriter writer;
    private String attachmentsComponentName;
    private String serverFileNameField;

    public DownloadsCsvWriter(BoMetadata metadata, FilenameResolver filenameResolver,
                              String downloadsFilenameTemplate, Path downloadsDir, char delimiter) {
        this.metadata = metadata;
        this.filenameResolver = filenameResolver;
        this.downloadsFilenameTemplate = downloadsFilenameTemplate;
        this.downloadsDir = downloadsDir;
        this.delimiter = delimiter;
        resolveAttachmentsComponent();
    }

    private void resolveAttachmentsComponent() {
        for (ComponentMetadata comp : metadata.getComponents()) {
            if (comp.isMultipleCardinality()
                    && comp.getInternalName().toLowerCase().contains("attachment")) {
                attachmentsComponentName = comp.getInternalName();
                // Look for serverFileName field
                comp.getFields().stream()
                        .filter(f -> "serverFileName".equals(f.getInternalName()))
                        .findFirst()
                        .ifPresent(f -> serverFileNameField = f.getInternalName());
                if (serverFileNameField == null && !comp.getFields().isEmpty()) {
                    // Fallback: use first field if no serverFileName
                    serverFileNameField = comp.getFields().get(0).getInternalName();
                }
                logger.debug("Attachments component: {}, field: {}",
                        attachmentsComponentName, serverFileNameField);
                return;
            }
        }
        logger.debug("No attachments component found for BO: {}", metadata.getBoName());
    }

    public void open() {
        String filename = filenameResolver.resolve(
                downloadsFilenameTemplate, metadata.getBoUsageType(), null);
        Path filePath = downloadsDir.resolve(filename);

        try {
            writer = new CSVWriterBuilder(new FileWriter(filePath.toString()))
                    .withSeparator(delimiter)
                    .build();
            logger.info("Opened downloads CSV: {}", filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open downloads CSV: " + filePath, e);
        }
    }

    public void writeRecords(List<BundleRecord> records) {
        if (writer == null || attachmentsComponentName == null || serverFileNameField == null) {
            return;
        }

        for (BundleRecord record : records) {
            for (BundleComponent comp : record.getComponents()) {
                if (!attachmentsComponentName.equals(comp.getComponentInternalName())) {
                    continue;
                }
                if (comp.getRows() == null) {
                    continue;
                }
                for (Map<String, String> row : comp.getRows()) {
                    String filePath = row.get(serverFileNameField);
                    if (filePath != null && !filePath.isEmpty()) {
                        writer.writeNext(new String[]{filePath});
                    }
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (writer != null) {
            writer.close();
        }
    }
}
