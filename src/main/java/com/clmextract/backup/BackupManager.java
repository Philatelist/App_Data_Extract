package com.clmextract.backup;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public class BackupManager {

    private static final Logger logger = LogManager.getLogger(BackupManager.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Path outputRoot;
    private final String exportFolderName;
    private final int retentionDays;

    public BackupManager(String outputRoot, String exportFolderName, int retentionDays) {
        this.outputRoot = Path.of(outputRoot);
        this.exportFolderName = exportFolderName;
        this.retentionDays = retentionDays;
    }

    public void backupCurrentExports() {
        Path exportDir = outputRoot.resolve(exportFolderName);
        if (!Files.exists(exportDir)) {
            logger.debug("No existing export directory to back up: {}", exportDir);
            return;
        }

        try {
            boolean hasFiles = false;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(exportDir)) {
                hasFiles = stream.iterator().hasNext();
            }

            if (!hasFiles) {
                logger.debug("Export directory is empty, nothing to back up");
                return;
            }

            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            Path backupDir = outputRoot.resolve("backups").resolve(timestamp);
            Files.createDirectories(backupDir);

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(exportDir)) {
                for (Path file : stream) {
                    Path target = backupDir.resolve(file.getFileName());
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            // Clear the export folder
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(exportDir)) {
                for (Path file : stream) {
                    Files.deleteIfExists(file);
                }
            }

            logger.info("Backed up exports to: {}", backupDir);
        } catch (IOException e) {
            logger.warn("Backup failed (non-fatal): {}", e.getMessage());
        }
    }

    public void enforceRetention() {
        Path backupsDir = outputRoot.resolve("backups");
        if (!Files.exists(backupsDir)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupsDir)) {
            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

            for (Path backupSubDir : stream) {
                if (!Files.isDirectory(backupSubDir)) {
                    continue;
                }

                String dirName = backupSubDir.getFileName().toString();
                try {
                    LocalDateTime backupTime = LocalDateTime.parse(dirName, TIMESTAMP_FORMAT);
                    Instant backupInstant = backupTime.atZone(ZoneId.systemDefault()).toInstant();

                    if (retentionDays == 0 || backupInstant.isBefore(cutoff)) {
                        deleteDirectory(backupSubDir);
                        logger.info("Deleted old backup: {}", dirName);
                    }
                } catch (DateTimeParseException e) {
                    logger.debug("Skipping non-backup directory: {}", dirName);
                }
            }
        } catch (IOException e) {
            logger.warn("Retention cleanup failed (non-fatal): {}", e.getMessage());
        }
    }

    public void createOutputDirectories() {
        try {
            Files.createDirectories(outputRoot.resolve(exportFolderName));
            Files.createDirectories(outputRoot.resolve("backups"));
            Files.createDirectories(outputRoot.resolve("logs"));
            Files.createDirectories(outputRoot.resolve("downloads"));
            logger.info("Output directories ready at: {}", outputRoot);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create output directories: " + e.getMessage(), e);
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    deleteDirectory(entry);
                } else {
                    Files.delete(entry);
                }
            }
        }
        Files.delete(dir);
    }
}
