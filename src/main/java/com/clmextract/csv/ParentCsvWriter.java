package com.clmextract.csv;

import com.clmextract.export.ParentRecord;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ParentCsvWriter implements Closeable {

    private static final Logger logger = LogManager.getLogger(ParentCsvWriter.class);

    private static final String[] HEADER = {
            "Tracking #", "Current Task", "Bundle Type", "Bundle Category", "ParentID"
    };

    private final Path filePath;
    private final char delimiter;
    private ICSVWriter writer;

    public ParentCsvWriter(Path filePath, char delimiter) {
        this.filePath = filePath;
        this.delimiter = delimiter;
    }

    public void open() {
        try {
            writer = new CSVWriterBuilder(new FileWriter(filePath.toString()))
                    .withSeparator(delimiter)
                    .withQuoteChar(ICSVWriter.NO_QUOTE_CHARACTER)
                    .build();
            writer.writeNext(HEADER);
            logger.info("Opened bundle parent CSV: {}", filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open bundle parent CSV: " + filePath, e);
        }
    }

    public void writeRecords(List<ParentRecord> records) {
        if (writer == null) return;
        for (ParentRecord r : records) {
            writer.writeNext(new String[]{
                    r.getTrackingId(),
                    r.getCurrentTask(),
                    r.getBundleType(),
                    r.getBundleCategory(),
                    r.getParentId()
            });
        }
    }

    @Override
    public void close() throws IOException {
        if (writer != null) {
            writer.close();
        }
    }
}
