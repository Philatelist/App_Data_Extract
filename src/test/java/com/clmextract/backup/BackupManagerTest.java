package com.clmextract.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BackupManagerTest {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @TempDir
    Path tempDir;

    @Test
    void testBackupCreatesTimestampedDirectory() throws IOException {
        Path exportDir = tempDir.resolve("MetaData");
        Files.createDirectories(exportDir);
        Files.writeString(exportDir.resolve("file1.csv"), "data1");
        Files.writeString(exportDir.resolve("file2.csv"), "data2");

        BackupManager manager = new BackupManager(tempDir.toString(), "MetaData", 30);
        manager.backupCurrentExports();

        // Backup directory should exist under backups/
        Path backupsDir = tempDir.resolve("backups");
        assertTrue(Files.exists(backupsDir));

        List<Path> backupSubDirs = listDirectories(backupsDir);
        assertEquals(1, backupSubDirs.size());

        // Backup should contain the files
        Path backupDir = backupSubDirs.get(0);
        assertTrue(Files.exists(backupDir.resolve("file1.csv")));
        assertTrue(Files.exists(backupDir.resolve("file2.csv")));
        assertEquals("data1", Files.readString(backupDir.resolve("file1.csv")));
        assertEquals("data2", Files.readString(backupDir.resolve("file2.csv")));

        // Export directory should be cleared
        List<Path> remaining = listFiles(exportDir);
        assertEquals(0, remaining.size());
    }

    @Test
    void testBackupEmptyExportFolderIsNoOp() throws IOException {
        Path exportDir = tempDir.resolve("MetaData");
        Files.createDirectories(exportDir);

        BackupManager manager = new BackupManager(tempDir.toString(), "MetaData", 30);
        manager.backupCurrentExports();

        // No backups directory should be created
        Path backupsDir = tempDir.resolve("backups");
        assertFalse(Files.exists(backupsDir));
    }

    @Test
    void testBackupNonExistentExportFolderIsNoOp() {
        BackupManager manager = new BackupManager(tempDir.toString(), "MetaData", 30);
        // Should not throw
        manager.backupCurrentExports();

        Path backupsDir = tempDir.resolve("backups");
        assertFalse(Files.exists(backupsDir));
    }

    @Test
    void testRetentionDeletesOldBackups() throws IOException {
        Path backupsDir = tempDir.resolve("backups");
        Files.createDirectories(backupsDir);

        // Create an old backup (40 days ago)
        LocalDateTime oldTime = LocalDateTime.now().minusDays(40);
        Path oldBackup = backupsDir.resolve(oldTime.format(TIMESTAMP_FORMAT));
        Files.createDirectories(oldBackup);
        Files.writeString(oldBackup.resolve("old.csv"), "old data");

        // Create a recent backup (1 day ago)
        LocalDateTime recentTime = LocalDateTime.now().minusDays(1);
        Path recentBackup = backupsDir.resolve(recentTime.format(TIMESTAMP_FORMAT));
        Files.createDirectories(recentBackup);
        Files.writeString(recentBackup.resolve("recent.csv"), "recent data");

        BackupManager manager = new BackupManager(tempDir.toString(), "MetaData", 30);
        manager.enforceRetention();

        // Old backup should be deleted
        assertFalse(Files.exists(oldBackup));

        // Recent backup should be preserved
        assertTrue(Files.exists(recentBackup));
        assertTrue(Files.exists(recentBackup.resolve("recent.csv")));
    }

    @Test
    void testRetentionZeroDeletesAll() throws IOException {
        Path backupsDir = tempDir.resolve("backups");
        Files.createDirectories(backupsDir);

        // Create two backups
        LocalDateTime time1 = LocalDateTime.now().minusDays(1);
        Path backup1 = backupsDir.resolve(time1.format(TIMESTAMP_FORMAT));
        Files.createDirectories(backup1);
        Files.writeString(backup1.resolve("data.csv"), "data");

        LocalDateTime time2 = LocalDateTime.now().minusHours(1);
        Path backup2 = backupsDir.resolve(time2.format(TIMESTAMP_FORMAT));
        Files.createDirectories(backup2);
        Files.writeString(backup2.resolve("data.csv"), "data");

        BackupManager manager = new BackupManager(tempDir.toString(), "MetaData", 0);
        manager.enforceRetention();

        // Both should be deleted
        assertFalse(Files.exists(backup1));
        assertFalse(Files.exists(backup2));
    }

    @Test
    void testRetentionPreservesRecentBackups() throws IOException {
        Path backupsDir = tempDir.resolve("backups");
        Files.createDirectories(backupsDir);

        LocalDateTime recentTime = LocalDateTime.now().minusDays(5);
        Path recentBackup = backupsDir.resolve(recentTime.format(TIMESTAMP_FORMAT));
        Files.createDirectories(recentBackup);
        Files.writeString(recentBackup.resolve("data.csv"), "data");

        BackupManager manager = new BackupManager(tempDir.toString(), "MetaData", 30);
        manager.enforceRetention();

        assertTrue(Files.exists(recentBackup));
    }

    @Test
    void testCreateOutputDirectories() {
        BackupManager manager = new BackupManager(tempDir.toString(), "MetaData", 30);
        manager.createOutputDirectories();

        assertTrue(Files.exists(tempDir.resolve("MetaData")));
        assertTrue(Files.exists(tempDir.resolve("backups")));
        assertTrue(Files.exists(tempDir.resolve("logs")));
        assertTrue(Files.exists(tempDir.resolve("downloads")));
    }

    @Test
    void testCreateOutputDirectoriesIdempotent() {
        BackupManager manager = new BackupManager(tempDir.toString(), "MetaData", 30);
        manager.createOutputDirectories();
        // Should not throw when called again
        manager.createOutputDirectories();

        assertTrue(Files.exists(tempDir.resolve("MetaData")));
    }

    private List<Path> listDirectories(Path dir) throws IOException {
        List<Path> dirs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    dirs.add(entry);
                }
            }
        }
        return dirs;
    }

    private List<Path> listFiles(Path dir) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                files.add(entry);
            }
        }
        return files;
    }
}
