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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DownloadsCsvWriter implements Closeable {

    private static final Logger logger = LogManager.getLogger(DownloadsCsvWriter.class);

    private final BoMetadata metadata;
    private final FilenameResolver filenameResolver;
    private final String downloadsFilenameTemplate;
    private final Path downloadsDir;
    private final char delimiter;

    private ICSVWriter writer;
    private final Set<String> attachmentsComponentNames = new HashSet<>();
    private static final String SERVER_FILE_NAME = "serverFileName";

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
            if (!comp.isMultipleCardinality()) continue;

            String name = comp.getInternalName();
            if ("ReqAttachment".equals(name) || "ReqContractAttachment".equals(name)) {
                attachmentsComponentNames.add(name);
            }
        }

        logger.debug("Attachments components enabled: {}, field: {}",
                attachmentsComponentNames, SERVER_FILE_NAME);

        if (attachmentsComponentNames.isEmpty()) {
            logger.debug("No attachment components (ReqAttachment/ReqContractAttachment) found for BO: {}", metadata.getBoName());
        }
    }

    public void open() {
        String filename = filenameResolver.resolve(
                downloadsFilenameTemplate, metadata.getBoName(), null);
        Path filePath = downloadsDir.resolve(filename);

        try {
            writer = new CSVWriterBuilder(new FileWriter(filePath.toString()))
                    .withSeparator(delimiter)
                    .withQuoteChar(ICSVWriter.NO_QUOTE_CHARACTER)
                    .build();
            logger.info("Opened downloads CSV: {}", filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open downloads CSV: " + filePath, e);
        }
    }

    public void writeRecords(List<BundleRecord> records) {
        if (writer == null || attachmentsComponentNames.isEmpty()) {
            return;
        }

        for (BundleRecord record : records) {
            for (BundleComponent comp : record.getComponents()) {
                if (!attachmentsComponentNames.contains(comp.getComponentInternalName())) {
                    continue;
                }
                if (comp.getRows() == null) {
                    continue;
                }
                for (Map<String, String> row : comp.getRows()) {
                    String filePath = row.get(SERVER_FILE_NAME);
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
