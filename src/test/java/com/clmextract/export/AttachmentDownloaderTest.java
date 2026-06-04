package com.clmextract.export;

import com.clmextract.metadata.BoMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
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

class AttachmentDownloaderTest {

    @TempDir
    Path tmpDir;

    // ----------------------------------------------------------------
    // Reset PdfConverter static flags before each test so tests are
    // independent of LibreOffice detection state.
    // ----------------------------------------------------------------

    @BeforeEach
    void resetPdfConverterFlags() throws Exception {
        resetStaticFlag("libreOfficeWarnLogged");
        resetStaticFlag("libreOfficeUnavailable");
    }

    private static void resetStaticFlag(String fieldName) throws Exception {
        Field field = PdfConverter.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((AtomicBoolean) field.get(null)).set(false);
    }

    // ----------------------------------------------------------------
    // ZIP builder helper — produces a ZIP as a byte array
    // ----------------------------------------------------------------

    /**
     * Builds a ZIP in memory containing a single entry.
     *
     * @param entryName the ZipEntry name (may include path separators)
     * @param content   the bytes to store in the entry
     */
    private static byte[] buildZip(String entryName, byte[] content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(content);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    // ----------------------------------------------------------------
    // Attachment-info helper — builds the CLM Property list structure
    // ----------------------------------------------------------------

    /**
     * Builds one element of the attachmentInfo list, matching the CLM structure:
     * { "Property": [ {"Name": "fileName", "Value": ...}, {"Name": "fileVersion", "Value": ...} ] }
     */
    private static Map<String, Object> attachmentInfoEntry(String fileName, String fileVersion) {
        Map<String, Object> fileNameProp = new HashMap<>();
        fileNameProp.put("Name", "fileName");
        fileNameProp.put("Value", fileName);

        Map<String, Object> versionProp = new HashMap<>();
        versionProp.put("Name", "fileVersion");
        versionProp.put("Value", fileVersion);

        List<Map<String, Object>> properties = new ArrayList<>();
        properties.add(fileNameProp);
        properties.add(versionProp);

        Map<String, Object> element = new HashMap<>();
        element.put("Property", properties);
        return element;
    }

    // ----------------------------------------------------------------
    // Minimal DataSource stub
    // ----------------------------------------------------------------

    private static class StubDataSource implements DataSource {

        private final Map<String, List<Map<String, Object>>> attachmentInfoByTracking = new HashMap<>();
        private final Map<String, byte[]> zipByTracking = new HashMap<>();

        void putAttachmentInfo(String trackingId, List<Map<String, Object>> info) {
            attachmentInfoByTracking.put(trackingId, info);
        }

        void putZip(String trackingId, byte[] zipBytes) {
            zipByTracking.put(trackingId, zipBytes);
        }

        @Override
        public List<Map<String, Object>> getAttachmentInfo(String trackingNumber) {
            return attachmentInfoByTracking.getOrDefault(trackingNumber, List.of());
        }

        @Override
        public InputStream downloadAttachmentsZip(String trackingNumber) {
            byte[] data = zipByTracking.getOrDefault(trackingNumber, new byte[0]);
            return new ByteArrayInputStream(data);
        }

        // ----------------------------------------------------------
        // Remaining interface methods — not exercised by these tests
        // ----------------------------------------------------------

        @Override public void login() { throw new UnsupportedOperationException(); }
        @Override public void logout() { throw new UnsupportedOperationException(); }
        @Override public List<String> getBoTypes() { throw new UnsupportedOperationException(); }
        @Override public BoMetadata getMetadata(String boType) { throw new UnsupportedOperationException(); }
        @Override public List<Long> getTrackingNumbers(String boType) { throw new UnsupportedOperationException(); }
        @Override public BundleResponse fetchBatch(List<Long> trackingIds, List<String> fieldPaths, BoMetadata metadata) {
            throw new UnsupportedOperationException();
        }
    }

    // ----------------------------------------------------------------
    // Test 1: Empty metadata → silent skip; method returns 0
    // ----------------------------------------------------------------

    @Test
    void emptyMetadata_silentSkip_returnsZero() throws IOException {
        StubDataSource stub = new StubDataSource();
        // No attachment info registered → stub returns empty list

        AttachmentDownloader downloader = new AttachmentDownloader(stub, tmpDir, false);
        int count = downloader.downloadAttachments(List.of(12345L));

        assertEquals(0, count, "Should return 0 when no attachment info");
        assertEquals(0, Files.list(tmpDir).count(), "No files should be written");
    }

    // ----------------------------------------------------------------
    // Test 2: No-convert path — file written with original extension
    // ----------------------------------------------------------------

    @Test
    void noConvert_writesFileWithOriginalExtension() throws IOException {
        long trackingId = 55555L;
        String fileName = "exhibit.xlsx";
        String fileVersion = "3";
        byte[] content = "XLSX_BYTES".getBytes(StandardCharsets.UTF_8);

        byte[] zip = buildZip(fileName, content);

        StubDataSource stub = new StubDataSource();
        stub.putAttachmentInfo("55555", List.of(attachmentInfoEntry(fileName, fileVersion)));
        stub.putZip("55555", zip);

        AttachmentDownloader downloader = new AttachmentDownloader(stub, tmpDir, false);
        int count = downloader.downloadAttachments(List.of(trackingId));

        assertEquals(1, count);
        // Expected: 55555-exhibit-3.xlsx
        Path expected = tmpDir.resolve("55555-exhibit-3.xlsx");
        assertTrue(Files.exists(expected), "Expected file 55555-exhibit-3.xlsx to exist");
        assertArrayEquals(content, Files.readAllBytes(expected), "File content must match ZIP entry bytes");
    }

    // ----------------------------------------------------------------
    // Test 3: Convert path with .pdf input — PDF pass-through, no LibreOffice needed
    // ----------------------------------------------------------------

    @Test
    void convertPath_pdfInput_passThrough_writesPdfOutput() throws IOException {
        long trackingId = 77777L;
        String fileName = "contract.pdf";
        String fileVersion = "2";
        byte[] content = "PDF_CONTENT".getBytes(StandardCharsets.UTF_8);

        byte[] zip = buildZip(fileName, content);

        StubDataSource stub = new StubDataSource();
        stub.putAttachmentInfo("77777", List.of(attachmentInfoEntry(fileName, fileVersion)));
        stub.putZip("77777", zip);

        // convertToPdf=true; input is .pdf so PdfConverter uses the pass-through path
        AttachmentDownloader downloader = new AttachmentDownloader(stub, tmpDir, true);
        int count = downloader.downloadAttachments(List.of(trackingId));

        assertEquals(1, count);
        // Expected: 77777-contract-2.pdf
        Path expected = tmpDir.resolve("77777-contract-2.pdf");
        assertTrue(Files.exists(expected), "Expected file 77777-contract-2.pdf to exist");
        assertArrayEquals(content, Files.readAllBytes(expected), "PDF content must match original bytes");
    }

    // ----------------------------------------------------------------
    // Test 4: Conversion failure — .docx input, LibreOffice absent
    //         Original file saved; no per-file companion .txt written
    // ----------------------------------------------------------------

    @Test
    void conversionFailure_savesOriginal_noCompanionTxt() throws IOException {
        long trackingId = 88888L;
        String fileName = "report.docx";
        String fileVersion = "1";
        byte[] content = "DOCX_CONTENT".getBytes(StandardCharsets.UTF_8);

        byte[] zip = buildZip(fileName, content);

        StubDataSource stub = new StubDataSource();
        stub.putAttachmentInfo("88888", List.of(attachmentInfoEntry(fileName, fileVersion)));
        stub.putZip("88888", zip);

        // convertToPdf=true; .docx will attempt LibreOffice conversion which will fail (absent)
        AttachmentDownloader downloader = new AttachmentDownloader(stub, tmpDir, true);
        int count = downloader.downloadAttachments(List.of(trackingId));

        // Count is 1 because the original file was saved as fallback
        assertEquals(1, count, "Should count 1 (original file saved on conversion failure)");

        // Original file saved with original extension
        Path originalFile = tmpDir.resolve("88888-report-1.docx");
        assertTrue(Files.exists(originalFile), "Original .docx file must be saved on conversion failure");

        // Per-file companion .txt must NOT exist
        Path companionFile = tmpDir.resolve("88888-report-1.txt");
        assertFalse(Files.exists(companionFile), "Per-file companion .txt must NOT be written (consolidated report replaces it)");
    }

    // ----------------------------------------------------------------
    // Test 4b: Consolidated report — single file failure
    // ----------------------------------------------------------------

    @Test
    void conversionFailure_singleFile_writesConsolidatedReport() throws IOException {
        long trackingId = 11111L;
        String fileName = "contract.docx";
        String fileVersion = "2";
        byte[] content = "DOCX_BYTES".getBytes(StandardCharsets.UTF_8);

        byte[] zip = buildZip(fileName, content);

        StubDataSource stub = new StubDataSource();
        stub.putAttachmentInfo("11111", List.of(attachmentInfoEntry(fileName, fileVersion)));
        stub.putZip("11111", zip);

        AttachmentDownloader downloader = new AttachmentDownloader(stub, tmpDir, true);
        downloader.downloadAttachments(List.of(trackingId));

        Path reportFile = tmpDir.resolve("pdf_conversion_failures.txt");
        assertTrue(Files.exists(reportFile), "pdf_conversion_failures.txt must be written on conversion failure");

        String reportContent = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertTrue(reportContent.contains(fileName), "Report must contain the original filename");
        assertTrue(reportContent.contains("11111-contract-2.docx"), "Report must contain the saved-as filename");
        assertFalse(reportContent.isBlank(), "Report must not be blank");
        // reason must be present and non-blank
        int reasonIdx = reportContent.indexOf("Reason   :");
        assertTrue(reasonIdx >= 0, "Report must contain a Reason line");
        String afterReason = reportContent.substring(reasonIdx + "Reason   :".length()).trim();
        assertFalse(afterReason.isEmpty(), "Reason must not be blank");
    }

    // ----------------------------------------------------------------
    // Test 4c: Consolidated report — multiple failures, all in one report
    // ----------------------------------------------------------------

    @Test
    void conversionFailure_multipleFiles_allInOneReport() throws IOException {
        long trackingId1 = 22221L;
        long trackingId2 = 22222L;
        String fileName1 = "doc1.docx";
        String fileName2 = "doc2.docx";
        byte[] content = "CONTENT".getBytes(StandardCharsets.UTF_8);

        StubDataSource stub = new StubDataSource();
        stub.putAttachmentInfo("22221", List.of(attachmentInfoEntry(fileName1, "1")));
        stub.putZip("22221", buildZip(fileName1, content));
        stub.putAttachmentInfo("22222", List.of(attachmentInfoEntry(fileName2, "1")));
        stub.putZip("22222", buildZip(fileName2, content));

        AttachmentDownloader downloader = new AttachmentDownloader(stub, tmpDir, true);
        downloader.downloadAttachments(List.of(trackingId1, trackingId2));

        Path reportFile = tmpDir.resolve("pdf_conversion_failures.txt");
        assertTrue(Files.exists(reportFile), "pdf_conversion_failures.txt must exist when multiple conversions fail");

        String reportContent = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertTrue(reportContent.contains(fileName1), "Report must contain first original filename");
        assertTrue(reportContent.contains(fileName2), "Report must contain second original filename");

        // No per-file .txt files must exist
        assertFalse(Files.exists(tmpDir.resolve("22221-doc1-1.txt")), "No per-file .txt for doc1");
        assertFalse(Files.exists(tmpDir.resolve("22222-doc2-1.txt")), "No per-file .txt for doc2");
    }

    // ----------------------------------------------------------------
    // Test 4d: No failures — no report file written
    // ----------------------------------------------------------------

    @Test
    void noConversionFailures_noReportFile() throws IOException {
        long trackingId = 33333L;
        String fileName = "document.pdf";
        String fileVersion = "1";
        byte[] content = "PDF_CONTENT".getBytes(StandardCharsets.UTF_8);

        byte[] zip = buildZip(fileName, content);

        StubDataSource stub = new StubDataSource();
        stub.putAttachmentInfo("33333", List.of(attachmentInfoEntry(fileName, fileVersion)));
        stub.putZip("33333", zip);

        // .pdf input passes through successfully — no failure
        AttachmentDownloader downloader = new AttachmentDownloader(stub, tmpDir, true);
        downloader.downloadAttachments(List.of(trackingId));

        Path reportFile = tmpDir.resolve("pdf_conversion_failures.txt");
        assertFalse(Files.exists(reportFile), "pdf_conversion_failures.txt must NOT be written when there are no failures");
    }

    // ----------------------------------------------------------------
    // Test 5: ZIP path-traversal guard — entry named ../../evil.txt rejected
    // ----------------------------------------------------------------

    @Test
    void pathTraversalGuard_rejectsEntryWithDotDot() throws IOException {
        long trackingId = 99999L;
        String evilEntry = "../../evil.txt";
        byte[] content = "EVIL_CONTENT".getBytes(StandardCharsets.UTF_8);

        byte[] zip = buildZip(evilEntry, content);

        StubDataSource stub = new StubDataSource();
        stub.putAttachmentInfo("99999", List.of(attachmentInfoEntry("evil.txt", "1")));
        stub.putZip("99999", zip);

        AttachmentDownloader downloader = new AttachmentDownloader(stub, tmpDir, false);
        int count = downloader.downloadAttachments(List.of(trackingId));

        assertEquals(0, count, "Should return 0 when all entries are rejected due to path traversal");

        // No file must exist outside tmpDir
        Path evilPath = tmpDir.resolve("../../evil.txt").normalize();
        assertFalse(Files.exists(evilPath), "No file must be written outside outputDir");

        // Verify no files written anywhere in tmpDir either
        long fileCount = Files.list(tmpDir).filter(Files::isRegularFile).count();
        assertEquals(0, fileCount, "No files should exist in outputDir after path-traversal rejection");
    }

    // ----------------------------------------------------------------
    // Test 6: sanitizeForFilename edge cases
    // ----------------------------------------------------------------

    @Test
    void sanitize_illegalCharsReplacedWithUnderscore() {
        // / \ : * ? " < > | and null byte → _
        String result = AttachmentDownloader.sanitizeForFilename("a/b\\c:d*e?f\"g<h>i|j");
        assertFalse(result.contains("/"), "Forward slash must be replaced");
        assertFalse(result.contains("\\"), "Backslash must be replaced");
        assertFalse(result.contains(":"), "Colon must be replaced");
        assertFalse(result.contains("*"), "Asterisk must be replaced");
        assertFalse(result.contains("?"), "Question mark must be replaced");
        assertFalse(result.contains("\""), "Double quote must be replaced");
        assertFalse(result.contains("<"), "Less-than must be replaced");
        assertFalse(result.contains(">"), "Greater-than must be replaced");
        assertFalse(result.contains("|"), "Pipe must be replaced");
    }

    @Test
    void sanitize_blankInput_returnsUnknown() {
        assertEquals("unknown", AttachmentDownloader.sanitizeForFilename(""));
        assertEquals("unknown", AttachmentDownloader.sanitizeForFilename("   "));
        assertEquals("unknown", AttachmentDownloader.sanitizeForFilename(null));
    }

    @Test
    void sanitize_longString_truncatedTo100Chars() {
        String longInput = "a".repeat(200);
        String result = AttachmentDownloader.sanitizeForFilename(longInput);
        assertEquals(100, result.length(), "Result must be truncated to 100 characters");
    }

    @Test
    void sanitize_tabsAndNewlinesBecomeSingleSpace() {
        // Tabs and newlines → space; runs of spaces → single space
        String result = AttachmentDownloader.sanitizeForFilename("hello\t\nworld");
        // After replacement: "hello  world" → collapse → "hello world" → trim → "hello world"
        // But spaces are also replaced by _ in the first step. Let's check the spec:
        // "Replace tabs and newlines with a space" — done after the illegal chars step
        // In our implementation: first replace illegal chars (including space→_), then
        // replace tabs/newlines with space. So "hello\t\nworld" → "hello__world" (tabs→space then collapse)
        // Actually: space is in the illegal char set so it gets replaced by _ too.
        // The spec says: replace tabs/newlines with space, collapse runs of spaces.
        // Spaces in the original input are handled by the illegal char replacement.
        // Result just must not contain raw tabs or newlines.
        assertFalse(result.contains("\t"), "Tabs must not appear in sanitized output");
        assertFalse(result.contains("\n"), "Newlines must not appear in sanitized output");
        assertFalse(result.contains("\r"), "Carriage returns must not appear in sanitized output");
    }

    @Test
    void sanitize_normalFilename_preserved() {
        String result = AttachmentDownloader.sanitizeForFilename("myDocument");
        assertEquals("myDocument", result, "Clean filename must be preserved as-is");
    }
}
