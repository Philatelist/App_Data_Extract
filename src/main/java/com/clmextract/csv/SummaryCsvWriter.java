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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SummaryCsvWriter implements Closeable {

    private static final Logger logger = LogManager.getLogger(SummaryCsvWriter.class);

    private final ICSVWriter writer;

    public SummaryCsvWriter(Path filePath, char delimiter) {
        try {
            this.writer = new CSVWriterBuilder(new FileWriter(filePath.toString()))
                    .withSeparator(delimiter)
                    .withQuoteChar(ICSVWriter.NO_QUOTE_CHARACTER)
                    .build();
            writer.writeNext(new String[]{"Business Object", "Current Task", "Count of Records"});
            logger.info("Opened summary CSV: {}", filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open summary CSV: " + filePath, e);
        }
    }

    public void writeBoSummary(String boName, List<ParentRecord> parentRecords) {
        // Count records per current task, preserving insertion order (first seen)
        Map<String, Integer> taskCounts = new LinkedHashMap<>();
        for (ParentRecord record : parentRecords) {
            String task = record.getCurrentTask();
            if (task == null || task.trim().isEmpty()) task = "";
            taskCounts.merge(task, 1, Integer::sum);
        }

        // Sort by count descending
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(taskCounts.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        int total = parentRecords.size();
        boolean firstRow = true;
        for (Map.Entry<String, Integer> entry : entries) {
            writer.writeNext(new String[]{
                    firstRow ? boName : "",
                    entry.getKey(),
                    String.valueOf(entry.getValue())
            });
            firstRow = false;
        }

        // Total row
        writer.writeNext(new String[]{"Total", "", String.valueOf(total)});

        // Blank separator row
        writer.writeNext(new String[]{"", "", ""});
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
