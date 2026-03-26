package com.clmextract.csv;

import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class ManifestCsvWriter {

    private static final Logger logger = LogManager.getLogger(ManifestCsvWriter.class);

    private static final String[] HEADER = {"Filename", "SHA256", "SizeBytes", "GeneratedAt"};
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_SLEEP_MS = 500L;

    private final Path outputDir;
    private final Path manifestPath;
    private final char delimiter;

    public ManifestCsvWriter(Path outputDir, Path manifestPath, char delimiter) {
        this.outputDir = outputDir;
        this.manifestPath = manifestPath;
        this.delimiter = delimiter;
    }

    public void write() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                doWrite();
                return; // success
            } catch (IOException e) {
                logger.warn("Manifest write attempt {}/{} failed: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                try {
                    Files.deleteIfExists(manifestPath);
                } catch (IOException deleteEx) {
                    logger.warn("Could not delete partial manifest after attempt {}: {}", attempt, deleteEx.getMessage());
                }
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_SLEEP_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        logger.warn("Manifest generation failed after 3 attempts — skipping");
    }

    /**
     * Core write logic. Extracted to allow subclassing in tests.
     */
    protected void doWrite() throws IOException {
        List<Path> files;
        try (var stream = Files.list(outputDir)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.equals(manifestPath))
                    .collect(Collectors.toList());
        }

        try (ICSVWriter writer = new CSVWriterBuilder(new FileWriter(manifestPath.toString()))
                .withSeparator(delimiter)
                .withQuoteChar(ICSVWriter.NO_QUOTE_CHARACTER)
                .build()) {

            writer.writeNext(HEADER);

            for (Path file : files) {
                String filename = file.getFileName().toString();
                String sha256 = computeSha256(file);
                String sizeBytes = String.valueOf(Files.size(file));
                String generatedAt = formatLastModified(file);

                writer.writeNext(new String[]{filename, sha256, sizeBytes, generatedAt});
                logger.debug("Manifest entry: {} sha256={} size={}", filename, sha256, sizeBytes);
            }
        }

        logger.info("Manifest written to {} ({} entries)", manifestPath, files.size());
    }

    private String computeSha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(file));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String formatLastModified(Path file) throws IOException {
        LocalDateTime ldt = Files.getLastModifiedTime(file)
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        return ldt.format(TIMESTAMP_FORMAT);
    }
}
