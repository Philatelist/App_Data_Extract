package com.clmextract;

import com.clmextract.config.AppConfig;
import com.clmextract.export.AttachmentDownloader;
import com.clmextract.export.DataSource;
import com.clmextract.export.BundleResponse;
import com.clmextract.metadata.BoMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance-level tests for spec 015: Attachment Export and PDF Conversion.
 *
 * These tests cover gaps not addressed by the existing fine-grained unit tests
 * in AttachmentDownloaderTest, PdfConverterTest, RunExecutorTest, and ConfigLoaderTest.
 *
 * Spec sections covered:
 *   §2.1 — Attachment Download (temp ZIP deletion, multi-record accumulation)
 *   §2.2 — PDF Conversion: partial failure does not stop the run; both outputs are produced
 *   §2.3 — No-convert: files written with original extension (multi-record variant)
 *   §2.4 — AppConfig field defaults; deleteEmptyCsvFiles non-CSV guard; 0-byte CSV deletion
 *   §2.5 — ZIP contains CSVs + attachment files (delegated to ZipPackagerTest; tagged here)
 */
@Tag("spec:015-attachment-export-pdf-conversion")
class Spec015AttachmentExportPdfConversionTest {

    @TempDir
    Path tmpDir;

    // ----------------------------------------------------------------
    // Reset PdfConverter static flags before each test
    // ----------------------------------------------------------------

    @BeforeEach
    void resetPdfConverterFlags() throws Exception {
        resetPdfConverterFlag("libreOfficeWarnLogged");
        resetPdfConverterFlag("libreOfficeUnavailable");
    }

    private static void resetPdfConverterFlag(String fieldName) throws Exception {
        Class<?> cls = Class.forName("com.clmextract.export.PdfConverter");
        Field field = cls.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((AtomicBoolean) field.get(null)).set(false);
    }

    // ----------------------------------------------------------------
    // Stub DataSource shared by attachment tests
    // ----------------------------------------------------------------

    private static class StubDataSource implements DataSource {

        private final Map<String, List<Map<String, Object>>> infoByTracking = new HashMap<>();
        private final Map<String, byte[]> zipByTracking = new HashMap<>();

        void putInfo(String trackingId, List<Map<String, Object>> info) {
            infoByTracking.put(trackingId, info);
        }

        void putZip(String trackingId, byte[] zip) {
            zipByTracking.put(trackingId, zip);
        }

        @Override
        public List<Map<String, Object>> getAttachmentInfo(String trackingNumber) {
            return infoByTracking.getOrDefault(trackingNumber, List.of());
        }

        @Override
        public InputStream downloadAttachmentsZip(String trackingNumber) {
            byte[] data = zipByTracking.getOrDefault(trackingNumber, new byte[0]);
            return new ByteArrayInputStream(data);
        }

        @Override public void login() {}
        @Override public void logout() {}
        @Override public List<String> getBoTypes() { return List.of(); }
        @Override public BoMetadata getMetadata(String boType) { throw new UnsupportedOperationException(); }
        @Override public List<Long> getTrackingNumbers(String boType) { return List.of(); }
        @Override public BundleResponse fetchBatch(List<Long> ids, List<String> fields, BoMetadata meta) {
            throw new UnsupportedOperationException();
        }
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    /** Builds a single-entry ZIP in memory. */
    private static byte[] singleEntryZip(String entryName, byte[] content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    /** Builds a two-entry ZIP in memory. */
    private static byte[] twoEntryZip(String name1, byte[] content1,
                                      String name2, byte[] content2) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(name1));
            zos.write(content1);
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(name2));
            zos.write(content2);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    /** Builds a CLM-format attachment info entry with fileName + fileVersion properties. */
    private static Map<String, Object> infoEntry(String fileName, String version) {
        Map<String, Object> fileNameProp = Map.of("Name", "fileName", "Value", fileName);
        Map<String, Object> versionProp  = Map.of("Name", "fileVersion", "Value", version);
        return Map.of("Property", List.of(fileNameProp, versionProp));
    }

    // ================================================================
    // §2.1 — Attachment Download
    // ================================================================

    /**
     * §2.1 — After extraction the temp ZIP archive must not remain in the output folder.
     *
     * RED CHECK: AttachmentDownloader.processTrackingId() calls deleteQuietly(tempZip)
     * at the end of processing. The temp file is named "<trackingId>_attachments_tmp.zip".
     */
    @Test
    @Tag("regression")
    void tempZipDeletedAfterExtraction() throws IOException {
        long id = 12300L;
        StubDataSource stub = new StubDataSource();
        stub.putInfo("12300", List.of(infoEntry("file.txt", "1")));
        stub.putZip("12300", singleEntryZip("file.txt",
                "hello".getBytes(StandardCharsets.UTF_8)));

        AttachmentDownloader dl = new AttachmentDownloader(stub, tmpDir, false);
        dl.downloadAttachments(List.of(id));

        Path tempZip = tmpDir.resolve("12300_attachments_tmp.zip");
        assertFalse(Files.exists(tempZip),
                "Temp ZIP archive must be deleted after extraction (§2.1 AC2)");
    }

    /**
     * §2.1 — Given a record has no attachments, no files are written and no error is shown.
     * This is the "multiple records: one has attachments, one does not" accumulation case.
     * The total count equals only the files from the record that had attachments.
     *
     * RED CHECK: AttachmentDownloader.processTrackingId() returns 0 when metaList is empty
     * and the outer loop sums results across all IDs.
     */
    @Test
    @Tag("regression")
    void multipleRecords_emptyOneSkipped_countSumsCorrectly() throws IOException {
        long idWithAttachment = 40001L;
        long idWithoutAttachment = 40002L;

        StubDataSource stub = new StubDataSource();
        stub.putInfo("40001", List.of(infoEntry("report.xlsx", "2")));
        stub.putZip("40001", singleEntryZip("report.xlsx",
                "DATA".getBytes(StandardCharsets.UTF_8)));
        // 40002 has no info → silent skip

        AttachmentDownloader dl = new AttachmentDownloader(stub, tmpDir, false);
        int count = dl.downloadAttachments(List.of(idWithAttachment, idWithoutAttachment));

        assertEquals(1, count,
                "Count must be 1: only the record with attachments produces a file (§2.1 AC3)");
        assertTrue(Files.exists(tmpDir.resolve("40001-report-2.xlsx")),
                "Output file for record with attachment must exist");
        // No files for 40002
        long extraFiles = Files.list(tmpDir).filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().startsWith("40002"))
                .count();
        assertEquals(0, extraFiles,
                "No files must be written for the record without attachments (§2.1 AC3)");
    }

    /**
     * §2.1 — After processing multiple tracking IDs, no temp ZIPs remain in the output folder.
     *
     * RED CHECK: the deleteQuietly(tempZip) call at end of processTrackingId() covers each ID.
     */
    @Test
    @Tag("regression")
    void multipleRecords_noTempZipsRemain() throws IOException {
        StubDataSource stub = new StubDataSource();
        for (long id = 50001L; id <= 50003L; id++) {
            String sid = String.valueOf(id);
            stub.putInfo(sid, List.of(infoEntry("doc.pdf", "1")));
            stub.putZip(sid, singleEntryZip("doc.pdf",
                    "PDF".getBytes(StandardCharsets.UTF_8)));
        }

        AttachmentDownloader dl = new AttachmentDownloader(stub, tmpDir, false);
        dl.downloadAttachments(List.of(50001L, 50002L, 50003L));

        long tempZips = Files.list(tmpDir)
                .filter(p -> p.getFileName().toString().endsWith("_attachments_tmp.zip"))
                .count();
        assertEquals(0, tempZips,
                "No temp ZIPs must remain after all records are processed (§2.1 AC2)");
    }

    // ================================================================
    // §2.2 — PDF Conversion: partial failure does not stop the run
    // ================================================================

    /**
     * §2.2 — Given a ZIP with two entries (.pdf pass-through success + .docx conversion
     * failure), the run does not stop — both outputs are present.
     *
     * The .pdf entry uses the PdfConverter pass-through path (always succeeds, no LibreOffice).
     * The .docx entry triggers a conversion attempt that fails because LibreOffice is absent;
     * the original .docx file plus a companion .txt are written as fallback.
     *
     * Total count == 2 (one converted PDF + one unconverted original saved as fallback).
     *
     * RED CHECK:
     *   - AttachmentDownloader iterates all ZIP entries and does not abort on one failure.
     *   - PdfConverter.convert() for .pdf returns true (pass-through).
     *   - PdfConverter.convert() for .docx returns false (LibreOffice absent).
     *   - On false: original saved + companion .txt written; count incremented.
     */
    @Test
    @Tag("regression")
    void convertEnabled_partialFailure_runContinues_bothOutputsPresent() throws IOException {
        long id = 66600L;
        byte[] pdfBytes  = "PDF_DATA".getBytes(StandardCharsets.UTF_8);
        byte[] docxBytes = "DOCX_DATA".getBytes(StandardCharsets.UTF_8);

        byte[] zip = twoEntryZip(
                "contract.pdf",  pdfBytes,
                "exhibit.docx",  docxBytes);

        StubDataSource stub = new StubDataSource();
        stub.putInfo("66600", List.of(
                infoEntry("contract.pdf", "1"),
                infoEntry("exhibit.docx", "1")));
        stub.putZip("66600", zip);

        // convertToPdf=true; PDF pass-through succeeds, docx fails (no LibreOffice in CI)
        AttachmentDownloader dl = new AttachmentDownloader(stub, tmpDir, true);
        int count = dl.downloadAttachments(List.of(id));

        // Both a successful PDF and a failed-conversion fallback were written
        assertEquals(2, count,
                "Both successful conversion (pass-through) and conversion-failure fallback must be counted (§2.2)");

        // Successful PDF (pass-through)
        assertTrue(Files.exists(tmpDir.resolve("66600-contract-1.pdf")),
                "Pass-through PDF must exist (§2.2 AC1)");

        // Conversion-failure fallback: original file + companion .txt
        assertTrue(Files.exists(tmpDir.resolve("66600-exhibit-1.docx")),
                "Original .docx must be saved when conversion fails (§2.2 AC2)");
        assertTrue(Files.exists(tmpDir.resolve("66600-exhibit-1.txt")),
                "Companion .txt must be written when conversion fails (§2.2 AC2)");
        String companionText = Files.readString(tmpDir.resolve("66600-exhibit-1.txt"), StandardCharsets.UTF_8);
        assertFalse(companionText.isBlank(),
                "Companion .txt must contain a human-readable explanation (§2.2 AC2)");
    }

    // ================================================================
    // §2.3 — No PDF Conversion: original extensions preserved
    // ================================================================

    /**
     * §2.3 — Given convertAttachmentsToPdf=false, files are saved with their original
     * extensions even when multiple different formats are present in the same ZIP.
     *
     * RED CHECK: AttachmentDownloader non-convert branch appends originalExt to filename.
     */
    @Test
    @Tag("regression")
    void convertDisabled_multipleFormats_originalExtensionsPreserved() throws IOException {
        long id = 77700L;
        byte[] xlsxBytes = "XLSX".getBytes(StandardCharsets.UTF_8);
        byte[] pngBytes  = "PNG".getBytes(StandardCharsets.UTF_8);

        byte[] zip = twoEntryZip("data.xlsx", xlsxBytes, "logo.png", pngBytes);

        StubDataSource stub = new StubDataSource();
        stub.putInfo("77700", List.of(
                infoEntry("data.xlsx", "3"),
                infoEntry("logo.png",  "1")));
        stub.putZip("77700", zip);

        AttachmentDownloader dl = new AttachmentDownloader(stub, tmpDir, false);
        int count = dl.downloadAttachments(List.of(id));

        assertEquals(2, count, "Both files should be written (§2.3)");
        assertTrue(Files.exists(tmpDir.resolve("77700-data-3.xlsx")),
                "xlsx file must keep its original extension (§2.3 AC)");
        assertTrue(Files.exists(tmpDir.resolve("77700-logo-1.png")),
                "png file must keep its original extension (§2.3 AC)");
        // No .pdf output expected
        long pdfCount = Files.list(tmpDir)
                .filter(p -> p.getFileName().toString().endsWith(".pdf"))
                .count();
        assertEquals(0, pdfCount, "No PDF files must be produced when convertAttachmentsToPdf=false (§2.3 AC)");
    }

    // ================================================================
    // §2.4 — AppConfig defaults (no YAML needed)
    // ================================================================

    /**
     * §2.4 — convertAttachmentsToPdf defaults to false on a freshly constructed AppConfig.
     *
     * RED CHECK: AppConfig field initializer `private boolean convertAttachmentsToPdf = false`.
     */
    @Test
    @Tag("regression")
    void appConfig_convertAttachmentsToPdf_defaultFalse() {
        AppConfig config = new AppConfig();
        assertFalse(config.isConvertAttachmentsToPdf(),
                "convertAttachmentsToPdf must default to false (§2.4, tech-spec §2.2)");
    }

    /**
     * §2.4 — includeEmptyExportFiles defaults to true on a freshly constructed AppConfig.
     *
     * RED CHECK: AppConfig field initializer `private boolean includeEmptyExportFiles = true`.
     */
    @Test
    @Tag("regression")
    void appConfig_includeEmptyExportFiles_defaultTrue() {
        AppConfig config = new AppConfig();
        assertTrue(config.isIncludeEmptyExportFiles(),
                "includeEmptyExportFiles must default to true (§2.4, tech-spec §2.2)");
    }

    // ================================================================
    // §2.4 — deleteEmptyCsvFiles: non-CSV files are not touched
    // ================================================================

    /**
     * §2.4 — deleteEmptyCsvFiles must only delete *.csv files.
     * Other files in the same directory (PDF, TXT) must not be deleted even if they
     * contain only one line or are empty.
     *
     * RED CHECK: RunExecutor.deleteEmptyCsvFiles() filters with `.endsWith(".csv")`.
     */
    @Test
    @Tag("regression")
    void deleteEmptyCsvFiles_nonCsvFilesNotDeleted() throws Exception {
        Path csvDir = tmpDir.resolve("out");
        Files.createDirectories(csvDir);

        // Header-only CSV — should be deleted
        Path emptyCsv = csvDir.resolve("empty.csv");
        Files.writeString(emptyCsv, "col1,col2\n");

        // Non-CSV with header-only content — must NOT be deleted
        Path pdfFile = csvDir.resolve("attachment.pdf");
        Files.writeString(pdfFile, "single line content\n");

        Path txtFile = csvDir.resolve("companion.txt");
        Files.writeString(txtFile, "only one line\n");

        invokeDeleteEmptyCsvFiles(csvDir);

        assertFalse(Files.exists(emptyCsv),
                "Header-only CSV must be deleted (§2.4 AC3)");
        assertTrue(Files.exists(pdfFile),
                "PDF file must not be deleted by deleteEmptyCsvFiles (§2.4)");
        assertTrue(Files.exists(txtFile),
                "TXT file must not be deleted by deleteEmptyCsvFiles (§2.4)");
    }

    /**
     * §2.4 — A completely empty (0-byte) CSV file must also be deleted.
     *
     * Files.lines(path).count() on a 0-byte file returns 0, which is <= 1.
     *
     * RED CHECK: the `<= 1` comparison in RunExecutor.deleteEmptyCsvFiles().
     */
    @Test
    @Tag("regression")
    void deleteEmptyCsvFiles_zeroByteCsvIsDeleted() throws Exception {
        Path csvDir = tmpDir.resolve("out-zero");
        Files.createDirectories(csvDir);

        Path zeroCsv = csvDir.resolve("zero.csv");
        Files.createFile(zeroCsv);  // 0 bytes

        Path dataCsv = csvDir.resolve("data.csv");
        Files.writeString(dataCsv, "col1\nval1\n");

        invokeDeleteEmptyCsvFiles(csvDir);

        assertFalse(Files.exists(zeroCsv),
                "Zero-byte CSV must be deleted by deleteEmptyCsvFiles (§2.4 AC3)");
        assertTrue(Files.exists(dataCsv),
                "CSV with data rows must be preserved (§2.4 AC4)");
    }

    /**
     * §2.4 — A CSV with multiple data rows must never be deleted.
     *
     * RED CHECK: count > 1 means the file is preserved.
     */
    @Test
    @Tag("regression")
    void deleteEmptyCsvFiles_csvWithDataRows_preserved() throws Exception {
        Path csvDir = tmpDir.resolve("out-data");
        Files.createDirectories(csvDir);

        Path dataCsv = csvDir.resolve("contracts.csv");
        Files.writeString(dataCsv, "id,name\n1,Alpha\n2,Beta\n");

        invokeDeleteEmptyCsvFiles(csvDir);

        assertTrue(Files.exists(dataCsv),
                "CSV with data rows (line count > 1) must be preserved (§2.4 AC4)");
    }

    // ================================================================
    // §2.5 — Final archive packaging (ZipPackager produces correct output)
    // ================================================================

    /**
     * §2.5 — Given a run output directory containing both a CSV and an attachment PDF,
     * the ZipPackager wraps them together in a single archive part.
     *
     * RED CHECK: ZipPackager.pack() iterates all regular files in exportDir and adds them.
     */
    @Test
    @Tag("regression")
    void zipPackager_archiveContainsBothCsvAndAttachmentFile() throws IOException {
        Path exportDir = tmpDir.resolve("run20260603_120000");
        Files.createDirectories(exportDir);

        // CSV produced by EXPORT_CSV step
        Path csv = exportDir.resolve("Contracts_20260603_120000.csv");
        Files.writeString(csv, "id,name\n1,Alpha\n");

        // Attachment produced by EXPORT_ATTACHMENTS step
        Path attachment = exportDir.resolve("1001-contract-1.pdf");
        Files.write(attachment, "PDF_BYTES".getBytes(StandardCharsets.UTF_8));

        com.clmextract.packaging.ZipPackager packager = new com.clmextract.packaging.ZipPackager();
        List<Path> parts = packager.pack(exportDir, "run20260603_120000");

        assertEquals(1, parts.size(),
                "Total output is under 200 MB — exactly one archive part must be created (§2.5 AC2)");

        // Verify both files appear in the archive
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(parts.get(0).toFile())) {
            java.util.Set<String> entryNames = zf.stream()
                    .map(java.util.zip.ZipEntry::getName)
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(entryNames.stream().anyMatch(n -> n.endsWith(".csv")),
                    "Archive must contain the CSV file (§2.5 AC1)");
            assertTrue(entryNames.stream().anyMatch(n -> n.endsWith(".pdf")),
                    "Archive must contain the attachment PDF (§2.5 AC1)");
        }
    }

    // ================================================================
    // §2.5 — Multi-part split at boundary
    // ================================================================

    /**
     * §2.5 — Given total output > part-size threshold, multiple parts are created
     * with .zip.001 / .zip.002 naming.
     *
     * This uses a 9-byte threshold (same as ZipPackagerTest) to avoid needing 200 MB of data.
     *
     * RED CHECK: ZipPackager.pack(dir, baseName, partSizeBytes) splits when
     * bytesWritten + nextFileSize > partSizeBytes.
     */
    @Test
    @Tag("regression")
    void zipPackager_totalExceedsThreshold_multiplePartsCreated() throws IOException {
        Path exportDir = tmpDir.resolve("split-run");
        Files.createDirectories(exportDir);

        Files.write(exportDir.resolve("contracts.csv"),
                "id,name\n1,A\n".getBytes(StandardCharsets.UTF_8));  // ~12 bytes
        Files.write(exportDir.resolve("attachment.pdf"),
                "PDFDATA".getBytes(StandardCharsets.UTF_8));         // 7 bytes

        // Threshold of 9 bytes: first file fits, second pushes over threshold
        com.clmextract.packaging.ZipPackager packager = new com.clmextract.packaging.ZipPackager(9);
        List<Path> parts = packager.pack(exportDir, "split-run");

        assertTrue(parts.size() >= 2,
                "Output exceeding the threshold must produce multiple archive parts (§2.5 AC3)");

        // Verify .zip.001 and .zip.002 naming convention
        boolean has001 = parts.stream().anyMatch(p -> p.getFileName().toString().endsWith(".zip.001"));
        boolean has002 = parts.stream().anyMatch(p -> p.getFileName().toString().endsWith(".zip.002"));
        assertTrue(has001, "First part must use .zip.001 naming (§2.5 AC3)");
        assertTrue(has002, "Second part must use .zip.002 naming (§2.5 AC3)");
    }

    // ================================================================
    // Helper: invoke private RunExecutor.deleteEmptyCsvFiles(Path) via reflection
    // ================================================================

    /**
     * Creates a minimal RunExecutor and invokes deleteEmptyCsvFiles via reflection,
     * since the method is private and deliberately not part of the public API.
     */
    private void invokeDeleteEmptyCsvFiles(Path dir) throws Exception {
        // Build a minimal config YAML for RunExecutor
        Path configFile = tmpDir.resolve("config-reflect.yml");
        if (!Files.exists(configFile)) {
            String yaml = "server:\n  url: https://test.example.com\n  username: u\n  password: p\n"
                    + "endpointsFile: inputs/endpoints.yml\nbatchSize: 10\noutputRoot: "
                    + tmpDir.toAbsolutePath().toString().replace("\\", "/")
                    + "\ndelimiter: \",\"\ncsvMode: per-component\nenableZipPackaging: false\n"
                    + "enableSftpUpload: false\n";
            Files.writeString(configFile, yaml);
        }

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.clmextract.web.state.StateStore stateStore =
                new com.clmextract.web.state.StateStore(tmpDir.resolve("state-reflect.json"), mapper);

        com.clmextract.web.run.RunExecutor executor =
                new com.clmextract.web.run.RunExecutor(configFile.toString(), stateStore);

        Method method = com.clmextract.web.run.RunExecutor.class
                .getDeclaredMethod("deleteEmptyCsvFiles", Path.class);
        method.setAccessible(true);
        method.invoke(executor, dir);
    }
}
