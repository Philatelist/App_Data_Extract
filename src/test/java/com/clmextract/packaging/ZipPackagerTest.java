package com.clmextract.packaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class ZipPackagerTest {

    @TempDir
    Path tmpDir;

    // -------------------------------------------------------------------------
    // Test 1: single file produces one part with correct ZIP content
    // -------------------------------------------------------------------------
    @Test
    void singleFile_producesOnePart() throws IOException {
        Path exportDir = tmpDir.resolve("export");
        Files.createDirectories(exportDir);
        Files.writeString(exportDir.resolve("hello.txt"), "hello world");

        ZipPackager packager = new ZipPackager();
        List<Path> parts = packager.pack(exportDir, "run001");

        // Exactly one part returned
        assertEquals(1, parts.size());

        // Part file is placed in parent (tmpDir), not inside exportDir
        Path expectedPart = tmpDir.resolve("run001.zip.001");
        assertTrue(Files.exists(expectedPart), "Expected part file does not exist: " + expectedPart);
        assertEquals(expectedPart, parts.get(0));

        // ZIP contains the entry with correct content
        try (ZipFile zf = new ZipFile(expectedPart.toFile())) {
            List<String> names = zf.stream()
                    .map(ZipEntry::getName)
                    .collect(Collectors.toList());
            assertEquals(List.of("hello.txt"), names);

            ZipEntry entry = zf.getEntry("hello.txt");
            assertNotNull(entry);
            String content = new String(zf.getInputStream(entry).readAllBytes());
            assertEquals("hello world", content);
        }

        // No ZIP files were written inside exportDir
        long zipFilesInExportDir = Files.list(exportDir)
                .filter(p -> p.getFileName().toString().contains(".zip"))
                .count();
        assertEquals(0, zipFilesInExportDir, "No zip parts should be inside exportDir");
    }

    // -------------------------------------------------------------------------
    // Test 2: empty directory returns empty list and creates no files
    // -------------------------------------------------------------------------
    @Test
    void emptyDir_returnsEmptyList() throws IOException {
        Path exportDir = tmpDir.resolve("export");
        Files.createDirectories(exportDir);

        ZipPackager packager = new ZipPackager();
        List<Path> parts = packager.pack(exportDir, "run002");

        assertTrue(parts.isEmpty(), "Expected empty list for empty directory");

        // Only the exportDir itself should exist under tmpDir — no part files
        List<Path> tmpContents = Files.list(tmpDir).collect(Collectors.toList());
        assertEquals(1, tmpContents.size(), "Only exportDir should exist in tmpDir");
        assertEquals(exportDir, tmpContents.get(0));
    }

    // -------------------------------------------------------------------------
    // Test 3: split at boundary — 9-byte threshold, two 5-byte files split
    //
    // Split condition: bytesWrittenInCurrentPart > 0
    //                  && bytesWrittenInCurrentPart + nextFileSize > partSizeBytes
    //
    // After first file: bytesWritten = 5
    // Second file:      5 > 0 && 5+5=10 > 9  =>  true  =>  new part opened
    // -------------------------------------------------------------------------
    @Test
    void splitAtExactly200MB_boundary() throws IOException {
        Path exportDir = tmpDir.resolve("export");
        Files.createDirectories(exportDir);
        Files.writeString(exportDir.resolve("a.txt"), "12345");  // 5 bytes
        Files.writeString(exportDir.resolve("b.txt"), "67890");  // 5 bytes

        // Threshold of 9 bytes: first file (5 bytes) fits, second would push to 10 > 9
        ZipPackager packager = new ZipPackager(9);
        List<Path> parts = packager.pack(exportDir, "split");

        assertEquals(2, parts.size(), "Expected 2 parts for threshold-9 with two 5-byte files");

        Path part1 = tmpDir.resolve("split.zip.001");
        Path part2 = tmpDir.resolve("split.zip.002");
        assertTrue(Files.exists(part1), "Part 1 does not exist");
        assertTrue(Files.exists(part2), "Part 2 does not exist");

        // Each part should contain exactly one entry, and together they hold both files
        Set<String> part1Entries = zipEntryNames(part1);
        Set<String> part2Entries = zipEntryNames(part2);

        assertEquals(1, part1Entries.size(), "Part 1 should contain exactly one entry");
        assertEquals(1, part2Entries.size(), "Part 2 should contain exactly one entry");

        Set<String> allEntries = new java.util.HashSet<>(part1Entries);
        allEntries.addAll(part2Entries);
        assertEquals(Set.of("a.txt", "b.txt"), allEntries, "Both files must appear across the two parts");
    }

    // -------------------------------------------------------------------------
    // Test 4: part naming is zero-padded to 3 digits
    // -------------------------------------------------------------------------
    @Test
    void partNaming_isZeroPadded() throws IOException {
        Path exportDir = tmpDir.resolve("export");
        Files.createDirectories(exportDir);
        // Each file is 1 byte; threshold of 1 means split occurs after every file
        // because: bytesWritten(1) > 0  &&  1+1=2 > 1  =>  new part
        Files.writeString(exportDir.resolve("x.txt"), "A");
        Files.writeString(exportDir.resolve("y.txt"), "B");
        Files.writeString(exportDir.resolve("z.txt"), "C");

        ZipPackager packager = new ZipPackager(1);
        List<Path> parts = packager.pack(exportDir, "named");

        assertEquals(3, parts.size(), "Expected 3 parts with 1-byte threshold");

        // Collect returned file names (order may vary by filesystem)
        Set<String> partNames = parts.stream()
                .map(p -> p.getFileName().toString())
                .collect(Collectors.toSet());

        assertTrue(partNames.contains("named.zip.001"), "Missing named.zip.001");
        assertTrue(partNames.contains("named.zip.002"), "Missing named.zip.002");
        assertTrue(partNames.contains("named.zip.003"), "Missing named.zip.003");

        // Verify physical files all exist
        for (Path part : parts) {
            assertTrue(Files.exists(part), "Part file does not exist: " + part);
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------
    private Set<String> zipEntryNames(Path zipPath) throws IOException {
        try (ZipFile zf = new ZipFile(zipPath.toFile())) {
            return zf.stream()
                    .map(ZipEntry::getName)
                    .collect(Collectors.toSet());
        }
    }
}
