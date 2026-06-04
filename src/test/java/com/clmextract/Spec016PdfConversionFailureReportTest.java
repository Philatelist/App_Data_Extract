package com.clmextract;

import com.clmextract.export.AttachmentDownloader;
import com.clmextract.export.BundleResponse;
import com.clmextract.export.DataSource;
import com.clmextract.export.PdfConverter;
import com.clmextract.metadata.BoMetadata;
import com.clmextract.packaging.ZipPackager;
import com.clmextract.web.run.RunExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance-level tests for spec 016: PDF Conversion Failure Report.
 *
 * Covers:
 *   §2.1 — Consolidated failure report (AC1–AC4)
 *   §2.2 — ZIP packaging always runs (AC5–AC6)
 *   §2.3 — Remove "Signed PDFs" step (AC7)
 */
@Tag("spec:016-pdf-conversion-failure-report")
@Tag("regression")
class Spec016PdfConversionFailureReportTest {

    @TempDir
    Path tmpDir;

    // ----------------------------------------------------------------
    // Reset PdfConverter static flags before each test so LibreOffice
    // detection state does not bleed between tests.
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
    // Stub DataSource — normal operation
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
    // Stub DataSource that throws from downloadAttachmentsZip (AC6)
    // ----------------------------------------------------------------

    private static class ThrowingZipDataSource implements DataSource {

        private final List<Map<String, Object>> info;

        ThrowingZipDataSource(List<Map<String, Object>> info) {
            this.info = info;
        }

        @Override
        public List<Map<String, Object>> getAttachmentInfo(String trackingNumber) {
            return info;
        }

        @Override
        public InputStream downloadAttachmentsZip(String trackingNumber) {
            // Return a broken InputStream that will cause an IOException during copy
            return new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("Simulated download failure for " + trackingNumber);
                }
            };
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
    // ZIP and attachment-info helpers
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
    private static byte[] twoEntryZip(String name1, byte[] c1,
                                      String name2, byte[] c2) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(name1));
            zos.write(c1);
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(name2));
            zos.write(c2);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    /** Builds a CLM-format attachment-info entry. */
    private static Map<String, Object> infoEntry(String fileName, String version) {
        Map<String, Object> fnProp = new HashMap<>();
        fnProp.put("Name", "fileName");
        fnProp.put("Value", fileName);
        Map<String, Object> verProp = new HashMap<>();
        verProp.put("Name", "fileVersion");
        verProp.put("Value", version);
        Map<String, Object> element = new HashMap<>();
        element.put("Property", List.of(fnProp, verProp));
        return element;
    }

    // ================================================================
    // §2.1 AC1 — Given convertAttachmentsToPdf=ON and at least one
    // attachment fails conversion, pdf_conversion_failures.txt is present
    // in the output folder.
    // ================================================================

    @Test
    void ac1_conversionFailure_failureReportCreated() throws IOException {
        // .docx will fail conversion because LibreOffice is absent in the test environment
        long trackingId = 10001L;
        byte[] content = "DOCX_BYTES".getBytes(StandardCharsets.UTF_8);
        byte[] zip = singleEntryZip("brief.docx", content);

        StubDataSource stub = new StubDataSource();
        stub.putInfo("10001", List.of(infoEntry("brief.docx", "1")));
        stub.putZip("10001", zip);

        AttachmentDownloader downloader = new AttachmentDownloader(stub, tmpDir, true);
        downloader.downloadAttachments(List.of(trackingId));

        Path report = tmpDir.resolve("pdf_conversion_failures.txt");
        assertTrue(Files.exists(report),
                "AC1: pdf_conversion_failures.txt must be created when at least one conversion fails");
    }

    // ================================================================
    // §2.1 AC2 — The report lists every failed file with original name,
    // saved name, and a human-readable reason.
    // ================================================================

    @Test
    void ac2_failureReportContainsOriginalNameSavedNameAndReason() throws IOException {
        long trackingId = 10002L;
        String originalName = "contract.docx";
        byte[] content = "DOCX_BYTES".getBytes(StandardCharsets.UTF_8);
        byte[] zip = singleEntryZip(originalName, content);

        StubDataSource stub = new StubDataSource();
        stub.putInfo("10002", List.of(infoEntry(originalName, "3")));
        stub.putZip("10002", zip);

        AttachmentDownloader downloader = new AttachmentDownloader(stub, tmpDir, true);
        downloader.downloadAttachments(List.of(trackingId));

        Path report = tmpDir.resolve("pdf_conversion_failures.txt");
        assertTrue(Files.exists(report), "AC2 prerequisite: failure report must exist");

        String reportText = Files.readString(report, StandardCharsets.UTF_8);

        // Original file name must appear
        assertTrue(reportText.contains(originalName),
                "AC2: report must contain the original file name");

        // Saved-as file name: trackingId-sanitizedBase-version.ext
        // sanitizeForFilename replaces spaces with _ but "contract" has no special chars
        String expectedSavedAs = "10002-contract-3.docx";
        assertTrue(reportText.contains(expectedSavedAs),
                "AC2: report must contain the saved-as file name (" + expectedSavedAs + ")");

        // Reason line must be present and non-empty
        int reasonIdx = reportText.indexOf("Reason   :");
        assertTrue(reasonIdx >= 0, "AC2: report must contain a 'Reason   :' line");
        String afterReason = reportText.substring(reasonIdx + "Reason   :".length()).trim();
        assertFalse(afterReason.isEmpty(),
                "AC2: reason must not be blank");
    }

    // ================================================================
    // §2.1 AC3 — Given convertAttachmentsToPdf=ON and all attachments
    // convert successfully, pdf_conversion_failures.txt is NOT created.
    // ================================================================

    @Test
    void ac3_allConversionsSucceed_noFailureReport() throws IOException {
        // .pdf input uses the PdfConverter pass-through path — always succeeds, no LibreOffice needed
        long trackingId = 10003L;
        byte[] content = "PDF_BYTES".getBytes(StandardCharsets.UTF_8);
        byte[] zip = singleEntryZip("signed.pdf", content);

        StubDataSource stub = new StubDataSource();
        stub.putInfo("10003", List.of(infoEntry("signed.pdf", "1")));
        stub.putZip("10003", zip);

        AttachmentDownloader downloader = new AttachmentDownloader(stub, tmpDir, true);
        downloader.downloadAttachments(List.of(trackingId));

        Path report = tmpDir.resolve("pdf_conversion_failures.txt");
        assertFalse(Files.exists(report),
                "AC3: pdf_conversion_failures.txt must NOT be created when all attachments convert successfully");
    }

    // ================================================================
    // §2.1 AC4 — Given multiple attachments fail, all failures appear
    // in ONE file — no per-file companion .txt files exist.
    // ================================================================

    @Test
    void ac4_multipleFailures_allInOneSingleReport_noPerFileCompanionTxtFiles() throws IOException {
        long id1 = 10004L;
        long id2 = 10005L;
        byte[] content = "CONTENT".getBytes(StandardCharsets.UTF_8);

        StubDataSource stub = new StubDataSource();
        stub.putInfo("10004", List.of(infoEntry("alpha.docx", "1")));
        stub.putZip("10004", singleEntryZip("alpha.docx", content));
        stub.putInfo("10005", List.of(infoEntry("beta.docx", "2")));
        stub.putZip("10005", singleEntryZip("beta.docx", content));

        AttachmentDownloader downloader = new AttachmentDownloader(stub, tmpDir, true);
        downloader.downloadAttachments(List.of(id1, id2));

        // Exactly one consolidated report
        Path report = tmpDir.resolve("pdf_conversion_failures.txt");
        assertTrue(Files.exists(report),
                "AC4: single pdf_conversion_failures.txt must be created for multiple failures");

        String reportText = Files.readString(report, StandardCharsets.UTF_8);

        // Both original names must appear in the single report
        assertTrue(reportText.contains("alpha.docx"),
                "AC4: report must include the first failed file's original name");
        assertTrue(reportText.contains("beta.docx"),
                "AC4: report must include the second failed file's original name");

        // No per-file companion .txt files must exist alongside the originals
        assertFalse(Files.exists(tmpDir.resolve("10004-alpha-1.txt")),
                "AC4: no per-file companion .txt must exist for the first failed file");
        assertFalse(Files.exists(tmpDir.resolve("10005-beta-2.txt")),
                "AC4: no per-file companion .txt must exist for the second failed file");

        // There must be exactly one .txt file in the output directory
        long txtCount = Files.list(tmpDir)
                .filter(p -> p.getFileName().toString().endsWith(".txt"))
                .count();
        assertEquals(1, txtCount,
                "AC4: exactly one .txt file (the consolidated report) must exist; got: " + txtCount);
    }

    // ================================================================
    // §2.2 AC5 — Given some attachments failed PDF conversion (saved as
    // originals), the packaging step still produces a ZIP containing all
    // CSV and attachment files.
    // ================================================================

    @Test
    void ac5_partialConversionFailure_zipPackagingStillProducesArchive() throws IOException {
        // Set up an output directory with a mix of files:
        //   - a CSV (from EXPORT_CSV step)
        //   - a successfully-converted PDF (pass-through)
        //   - a fallback original .docx (conversion failed, saved as original)
        Path exportDir = tmpDir.resolve("run20260101_120000");
        Files.createDirectories(exportDir);

        Path csvFile = exportDir.resolve("Contracts_export.csv");
        Files.writeString(csvFile, "id,name\n1,Alpha\n");

        Path pdfFile = exportDir.resolve("9001-signed-1.pdf");
        Files.write(pdfFile, "PDF_BYTES".getBytes(StandardCharsets.UTF_8));

        Path docxFile = exportDir.resolve("9002-brief-1.docx");
        Files.write(docxFile, "DOCX_BYTES".getBytes(StandardCharsets.UTF_8));

        // Also include the failure report (written by AttachmentDownloader on conversion failure)
        Path failureReport = exportDir.resolve("pdf_conversion_failures.txt");
        Files.writeString(failureReport,
                "PDF Conversion Failures\n=======================\nbrief.docx\n  Saved as : 9002-brief-1.docx\n  Reason   : LibreOffice is not installed.\n\nTotal: 1 file(s) could not be converted to PDF.\n");

        ZipPackager packager = new ZipPackager();
        List<Path> parts = packager.pack(exportDir, "run20260101_120000");

        // A ZIP must be produced
        assertFalse(parts.isEmpty(),
                "AC5: ZipPackager must produce at least one ZIP part even when some attachments failed conversion");

        // The archive must contain all four files
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(parts.get(0).toFile())) {
            java.util.Set<String> entries = zf.stream()
                    .map(java.util.zip.ZipEntry::getName)
                    .collect(java.util.stream.Collectors.toSet());

            assertTrue(entries.contains(csvFile.getFileName().toString()),
                    "AC5: ZIP must contain the CSV file");
            assertTrue(entries.contains(pdfFile.getFileName().toString()),
                    "AC5: ZIP must contain the successfully converted PDF attachment");
            assertTrue(entries.contains(docxFile.getFileName().toString()),
                    "AC5: ZIP must contain the original-format fallback attachment (.docx)");
        }
    }

    // ================================================================
    // §2.2 AC6 — Given the attachment download step throws an exception,
    // the packaging step still runs and produces a ZIP of the CSV output.
    // ================================================================

    @Test
    void ac6_attachmentDownloadException_packagingStillProducesZipOfCsvOutput() throws IOException {
        // Simulate: EXPORT_CSV completed and wrote a CSV; then EXPORT_ATTACHMENTS threw.
        // The packaging step must still be able to produce a ZIP from whatever is in outputDir.
        Path exportDir = tmpDir.resolve("run20260101_130000");
        Files.createDirectories(exportDir);

        // CSV written before the attachment step failed
        Path csvFile = exportDir.resolve("Contracts_export.csv");
        Files.writeString(csvFile, "id,name\n1,Alpha\n2,Beta\n");

        // Verify the throwing DataSource actually causes downloadAttachments to return early
        // (not crash fatally) — the .docx info is present but the ZIP stream throws
        List<Map<String, Object>> infoList = List.of(infoEntry("report.docx", "1"));
        ThrowingZipDataSource throwingDs = new ThrowingZipDataSource(infoList);

        AttachmentDownloader downloader = new AttachmentDownloader(throwingDs, exportDir, false);
        // downloadAttachments must not propagate the IOException to the caller —
        // AttachmentDownloader catches it and logs a warning, returning 0
        int count = assertDoesNotThrow(
                () -> downloader.downloadAttachments(List.of(99001L)),
                "AC6: downloadAttachments must not throw when the ZIP download fails; it logs and continues");
        assertEquals(0, count,
                "AC6: when ZIP download fails, no files are written and count is 0");

        // PACKAGING step: even after a failed attachment step, the CSV is present —
        // ZipPackager must produce a ZIP containing at least the CSV file
        ZipPackager packager = new ZipPackager();
        List<Path> parts = packager.pack(exportDir, "run20260101_130000");

        assertFalse(parts.isEmpty(),
                "AC6: ZipPackager must produce at least one ZIP part when outputDir contains CSV files");

        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(parts.get(0).toFile())) {
            java.util.Set<String> entries = zf.stream()
                    .map(java.util.zip.ZipEntry::getName)
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(entries.contains(csvFile.getFileName().toString()),
                    "AC6: ZIP must contain the CSV file produced before the attachment step failed");
        }
    }

    // ================================================================
    // §2.3 AC7 — No step with key EXPORT_PDF or label "Signed PDFs"
    // appears in the run step state.
    // ================================================================

    @Test
    void ac7_exportPdfStep_isNotInRunExecutorStepsList() {
        // STEP_EXPORT_PDF is declared as a constant (for backward compatibility / API consumers)
        // but must NOT be present in the STEPS list that drives the run progress display.
        // We verify this by inspecting RunExecutor.STEPS via reflection.
        try {
            Field stepsField = RunExecutor.class.getDeclaredField("STEPS");
            stepsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<String> steps = (List<String>) stepsField.get(null);

            assertFalse(steps.contains(RunExecutor.STEP_EXPORT_PDF),
                    "AC7: EXPORT_PDF must NOT be in RunExecutor.STEPS — it must not appear in run progress state");
            assertFalse(steps.contains("EXPORT_PDF"),
                    "AC7: 'EXPORT_PDF' key must not appear in the STEPS list by any means");

            // Belt-and-suspenders: verify none of the step keys references "Signed PDF" or similar
            for (String step : steps) {
                assertFalse(step.toUpperCase().contains("PDF"),
                        "AC7: no step key in STEPS may reference PDF (got: " + step + ")");
            }
        } catch (Exception e) {
            fail("Could not inspect RunExecutor.STEPS via reflection: " + e.getMessage());
        }
    }

    // ================================================================
    // §2.3 AC7 (supplemental) — Run step map initialized from STEPS does
    // not contain the EXPORT_PDF key at any point.
    // ================================================================

    @Test
    void ac7_runStepMap_doesNotContainExportPdfKey() throws IOException, InterruptedException {
        // Build a minimal RunExecutor, start a run, and verify the step map that
        // gets recorded in state never includes EXPORT_PDF.
        Path configFile = tmpDir.resolve("config-ac7.yml");
        String yaml = "server:\n  url: https://test.example.com\n  username: u\n  password: p\n"
                + "endpointsFile: inputs/endpoints.yml\nbatchSize: 10\noutputRoot: "
                + tmpDir.toAbsolutePath().toString().replace("\\", "/")
                + "\ndelimiter: \",\"\ncsvMode: per-component\nenableZipPackaging: false\n"
                + "enableSftpUpload: false\n";
        Files.writeString(configFile, yaml);

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.clmextract.web.state.StateStore stateStore =
                new com.clmextract.web.state.StateStore(
                        tmpDir.resolve("state-ac7.json"), mapper);

        RunExecutor executor = new RunExecutor(configFile.toString(), stateStore);
        executor.startRun(Collections.emptyList(), "/test", null, null);

        // Wait for the run to complete (up to 15 seconds)
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            com.clmextract.web.state.UiState.RunState run = stateStore.read().getCurrentRun();
            if (run != null && run.getCompletedAt() != null) break;
            Thread.sleep(200);
        }

        com.clmextract.web.state.UiState.RunState run = stateStore.read().getCurrentRun();
        assertNotNull(run, "Run must have completed");

        Map<String, String> stepMap = run.getSteps();
        assertFalse(stepMap.containsKey(RunExecutor.STEP_EXPORT_PDF),
                "AC7: EXPORT_PDF must not appear as a key in the run step map at any time");
        assertFalse(stepMap.containsKey("EXPORT_PDF"),
                "AC7: 'EXPORT_PDF' must not appear as a key in the run step map");
    }
}
