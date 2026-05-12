package com.clmextract.export;

import com.clmextract.metadata.BoMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AttachmentDownloaderTest {

    @TempDir
    Path tmpDir;

    // ----------------------------------------------------------------
    // Minimal DataSource stub — only overrides the two attachment methods
    // ----------------------------------------------------------------

    private static class StubDataSource implements DataSource {

        private final Map<String, List<Map<String, Object>>> attachmentsByTracking = new HashMap<>();
        private final Map<String, byte[]> downloadsByUrl = new HashMap<>();

        void putAttachments(String trackingNumber, List<Map<String, Object>> attachments) {
            attachmentsByTracking.put(trackingNumber, attachments);
        }

        void putDownload(String url, byte[] content) {
            downloadsByUrl.put(url, content);
        }

        @Override
        public List<Map<String, Object>> getAttachmentInfo(String trackingNumber) {
            return attachmentsByTracking.getOrDefault(trackingNumber, List.of());
        }

        @Override
        public byte[] downloadDocument(String encryptedUrl) {
            byte[] content = downloadsByUrl.get(encryptedUrl);
            if (content == null) {
                throw new IllegalStateException("Unexpected downloadDocument call for URL: " + encryptedUrl);
            }
            return content;
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

    // Helper to build an attachment map
    private static Map<String, Object> attachment(String fileName, String url, String category) {
        Map<String, Object> m = new HashMap<>();
        m.put("fileName", fileName);
        m.put("url", url);
        m.put("category", category);
        return m;
    }

    // ----------------------------------------------------------------
    // Test 1: signed PDF is downloaded and written as <id>.pdf
    // ----------------------------------------------------------------

    @Test
    void downloadPdfs_writesSignedPdfFile() throws IOException {
        StubDataSource stub = new StubDataSource();
        stub.putAttachments("12345", List.of(
                attachment("contract.pdf", "enc://abc", "SignedContract")
        ));
        stub.putDownload("enc://abc", "PDF_CONTENT".getBytes(StandardCharsets.UTF_8));

        AttachmentDownloader downloader = new AttachmentDownloader(stub, tmpDir);
        int count = downloader.downloadPdfs(List.of(12345L));

        assertEquals(1, count);

        Path expected = tmpDir.resolve("12345.pdf");
        assertTrue(Files.exists(expected), "Expected file 12345.pdf to exist");
        assertEquals("PDF_CONTENT",
                new String(Files.readAllBytes(expected), StandardCharsets.UTF_8));
    }

    // ----------------------------------------------------------------
    // Test 2: non-signed, non-blank-category PDF is skipped
    // ----------------------------------------------------------------

    @Test
    void downloadPdfs_skipsNonPdf() throws IOException {
        StubDataSource stub = new StubDataSource();
        stub.putAttachments("99", List.of(
                attachment("exhibit.docx", "enc://xyz", "Exhibit")
        ));

        AttachmentDownloader downloader = new AttachmentDownloader(stub, tmpDir);
        int count = downloader.downloadPdfs(List.of(99L));

        assertEquals(0, count);
        assertEquals(0, Files.list(tmpDir).count(), "No files should exist in tmpDir");
    }

    // ----------------------------------------------------------------
    // Test 3: downloadAttachments writes non-PDF, skips signed PDF
    // ----------------------------------------------------------------

    @Test
    void downloadAttachments_writesOtherFile() throws IOException {
        StubDataSource stub = new StubDataSource();
        stub.putAttachments("55555", List.of(
                attachment("exhibit.xlsx", "enc://u1", "Exhibit"),
                attachment("contract.pdf", "enc://u2", "SignedContract")  // must be skipped
        ));
        stub.putDownload("enc://u1", "XLSX_BYTES".getBytes(StandardCharsets.UTF_8));

        AttachmentDownloader downloader = new AttachmentDownloader(stub, tmpDir);
        int count = downloader.downloadAttachments(List.of(55555L));

        assertEquals(1, count);

        Path expected = tmpDir.resolve("55555_exhibit.xlsx");
        assertTrue(Files.exists(expected), "Expected file 55555_exhibit.xlsx to exist");
    }

    // ----------------------------------------------------------------
    // Test 4: second call is a no-op when file already exists
    // ----------------------------------------------------------------

    @Test
    void downloadPdfs_isIdempotent() throws IOException {
        StubDataSource stub = new StubDataSource();
        stub.putAttachments("77777", List.of(
                attachment("signed.pdf", "enc://dup", "Signed")
        ));
        stub.putDownload("enc://dup", "IDEMPOTENT".getBytes(StandardCharsets.UTF_8));

        AttachmentDownloader downloader = new AttachmentDownloader(stub, tmpDir);

        int firstCall = downloader.downloadPdfs(List.of(77777L));
        assertEquals(1, firstCall, "First call must write one file");

        int secondCall = downloader.downloadPdfs(List.of(77777L));
        assertEquals(0, secondCall, "Second call must skip already-existing file");

        long fileCount = Files.list(tmpDir).count();
        assertEquals(1, fileCount, "Exactly one file must exist after two calls");
    }
}
