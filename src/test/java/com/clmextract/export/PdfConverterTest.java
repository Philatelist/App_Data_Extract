package com.clmextract.export;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.jodconverter.core.office.OfficeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PdfConverterTest {

    @TempDir
    Path tempDir;

    // -----------------------------------------------------------------------
    // In-memory log appender
    // -----------------------------------------------------------------------

    static final class CapturingAppender extends AbstractAppender {
        private final List<LogEvent> events = Collections.synchronizedList(new ArrayList<>());

        CapturingAppender(String name) {
            super(name, null, null, true, null);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        List<LogEvent> getEvents() {
            return Collections.unmodifiableList(events);
        }

        void clear() {
            events.clear();
        }
    }

    private CapturingAppender capturingAppender;

    @BeforeEach
    void setUp() throws Exception {
        // Reset static state so each test starts clean
        resetStaticFlag("libreOfficeWarnLogged");
        resetStaticFlag("libreOfficeUnavailable");

        // Install in-memory log appender
        capturingAppender = new CapturingAppender("PdfConverterCapture");
        capturingAppender.start();

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        config.addAppender(capturingAppender);

        LoggerConfig loggerConfig = config.getLoggerConfig("com.clmextract");
        loggerConfig.addAppender(capturingAppender, Level.WARN, null);
        ctx.updateLoggers();
    }

    @AfterEach
    void tearDown() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("com.clmextract");
        loggerConfig.removeAppender("PdfConverterCapture");
        ctx.updateLoggers();
        capturingAppender.stop();
    }

    /** Reset a private static AtomicBoolean field on PdfConverter to false. */
    private static void resetStaticFlag(String fieldName) throws Exception {
        Field field = PdfConverter.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((AtomicBoolean) field.get(null)).set(false);
    }

    private long countWarnMessages(String fragment) {
        return capturingAppender.getEvents().stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .filter(e -> e.getMessage().getFormattedMessage().contains(fragment))
                .count();
    }

    // -----------------------------------------------------------------------
    // Test 1: .pdf pass-through
    // -----------------------------------------------------------------------

    /**
     * When the input file already has a .pdf extension, convert() must copy it
     * to the output path and return true — without starting LibreOffice.
     * open() is never called so the test is independent of LibreOffice presence.
     */
    @Test
    void pdfPassThrough_copiesFileAndReturnsTrue() throws IOException {
        Path inputPdf = tempDir.resolve("input.pdf");
        byte[] content = "PDF content bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(inputPdf, content);

        Path outputPdf = tempDir.resolve("output.pdf");

        // Do NOT call open() — PDF pass-through must not require LibreOffice
        PdfConverter converter = new PdfConverter();
        ConversionResult result = converter.convert(inputPdf, outputPdf);

        assertTrue(result.success(), "convert() must return true for .pdf input");
        assertTrue(Files.exists(outputPdf), "Output file must be created");
        assertArrayEquals(content, Files.readAllBytes(outputPdf),
                "Output file must contain the same bytes as the input");
    }

    /**
     * Extension matching must be case-insensitive (.PDF, .Pdf, etc.).
     */
    @Test
    void pdfPassThrough_caseInsensitiveExtension() throws IOException {
        Path inputPdf = tempDir.resolve("document.PDF");
        byte[] content = "upper case extension".getBytes(StandardCharsets.UTF_8);
        Files.write(inputPdf, content);

        Path outputPdf = tempDir.resolve("output.pdf");

        PdfConverter converter = new PdfConverter();
        ConversionResult result = converter.convert(inputPdf, outputPdf);

        assertTrue(result.success(), "convert() must return true for .PDF (uppercase) input");
        assertArrayEquals(content, Files.readAllBytes(outputPdf));
    }

    // -----------------------------------------------------------------------
    // Test 2: Conversion failure returns false
    // -----------------------------------------------------------------------

    /**
     * When open() is called but LibreOffice is absent (start() throws),
     * convert() on a non-PDF file must return false.
     */
    @Test
    void conversionFailure_returnsFalse() throws IOException {
        Path inputDocx = tempDir.resolve("report.docx");
        Files.write(inputDocx, "fake docx content".getBytes(StandardCharsets.UTF_8));
        Path outputPdf = tempDir.resolve("report.pdf");

        PdfConverter converter = new PdfConverter();

        // open() will throw (OfficeException or NullPointerException depending on LO install state)
        // because LibreOffice is not installed in CI.
        try {
            converter.open();
            // If somehow open() succeeded (LO installed), close and skip gracefully
            converter.close();
        } catch (Exception expected) {
            // LibreOffice not installed — the natural CI state.
            // converter remains in "no manager" state.
        }

        ConversionResult result = converter.convert(inputDocx, outputPdf);

        assertFalse(result.success(), "convert() must return false when LibreOffice is unavailable");
    }

    // -----------------------------------------------------------------------
    // Test 3: WARN logged exactly once even across multiple convert() calls
    // -----------------------------------------------------------------------

    /**
     * When LibreOffice is absent, the WARN about unavailability must be emitted
     * exactly once regardless of how many times convert() is called.
     */
    @Test
    void libreOfficeAbsent_warnLoggedExactlyOnce() throws IOException {
        Path inputDocx1 = tempDir.resolve("file1.docx");
        Path inputDocx2 = tempDir.resolve("file2.docx");
        Path inputDocx3 = tempDir.resolve("file3.docx");
        Files.write(inputDocx1, "content1".getBytes(StandardCharsets.UTF_8));
        Files.write(inputDocx2, "content2".getBytes(StandardCharsets.UTF_8));
        Files.write(inputDocx3, "content3".getBytes(StandardCharsets.UTF_8));

        PdfConverter converter = new PdfConverter();

        // Do not call open() so converter == null, triggering the unavailability path
        converter.convert(inputDocx1, tempDir.resolve("out1.pdf"));
        converter.convert(inputDocx2, tempDir.resolve("out2.pdf"));
        converter.convert(inputDocx3, tempDir.resolve("out3.pdf"));

        long warnCount = countWarnMessages("LibreOffice is unavailable");
        assertEquals(1, warnCount,
                "WARN about LibreOffice unavailability must be logged exactly once");
    }

    // -----------------------------------------------------------------------
    // Test 4: AutoCloseable lifecycle
    // -----------------------------------------------------------------------

    /**
     * close() on a PdfConverter whose open() was never called must not throw.
     */
    @Test
    void close_withoutOpen_isIdempotent() {
        PdfConverter converter = new PdfConverter();
        assertDoesNotThrow(converter::close, "close() without open() must not throw");
    }

    /**
     * A second close() after the first is also safe (idempotent).
     */
    @Test
    void close_calledTwice_isIdempotent() {
        PdfConverter converter = new PdfConverter();
        assertDoesNotThrow(converter::close, "First close() must not throw");
        assertDoesNotThrow(converter::close, "Second close() must not throw");
    }

    /**
     * open() followed by close() must not throw when LibreOffice is absent
     * (open() may throw OfficeException; close() on the partial state must still be safe).
     */
    @Test
    void openThenClose_withLibreOfficeAbsent_doesNotThrow() {
        PdfConverter converter = new PdfConverter();
        try {
            converter.open();
        } catch (Exception ignored) {
            // Expected when LibreOffice is not installed (may be OfficeException or NullPointerException)
        }
        // close() must be safe regardless of whether open() succeeded
        assertDoesNotThrow(converter::close, "close() after failed open() must not throw");
    }

    /**
     * try-with-resources usage compiles and runs without exception.
     */
    @Test
    void tryWithResources_doesNotThrow() {
        assertDoesNotThrow(() -> {
            try (PdfConverter converter = new PdfConverter()) {
                // no-op — just verify AutoCloseable works
            }
        });
    }

    // -----------------------------------------------------------------------
    // Test 5: Failure result carries a non-empty reason
    // -----------------------------------------------------------------------

    /**
     * When LibreOffice is absent and a .docx is submitted, the result must have
     * success() == false and a non-blank reason().
     */
    @Test
    void convert_failure_returnsNonEmptyReason() throws IOException {
        Path inputDocx = tempDir.resolve("report.docx");
        Files.write(inputDocx, "fake docx content".getBytes(StandardCharsets.UTF_8));
        Path outputPdf = tempDir.resolve("report.pdf");

        PdfConverter converter = new PdfConverter();

        // open() will throw when LibreOffice is absent — leave converter in no-manager state
        try {
            converter.open();
            converter.close();
        } catch (Exception ignored) {
            // Expected in CI without LibreOffice
        }

        ConversionResult result = converter.convert(inputDocx, outputPdf);

        assertFalse(result.success(), "result must indicate failure");
        assertFalse(result.reason().isBlank(), "failure reason must not be blank");
    }

    // -----------------------------------------------------------------------
    // Test 6: PDF pass-through carries an empty reason
    // -----------------------------------------------------------------------

    /**
     * When the input is already a .pdf, the result must have
     * success() == true and an empty reason().
     */
    @Test
    void pdfPassthrough_returnsEmptyReason() throws IOException {
        Path inputPdf = tempDir.resolve("document.pdf");
        Files.write(inputPdf, "PDF content".getBytes(StandardCharsets.UTF_8));
        Path outputPdf = tempDir.resolve("output.pdf");

        PdfConverter converter = new PdfConverter();
        ConversionResult result = converter.convert(inputPdf, outputPdf);

        assertTrue(result.success(), "result must indicate success for PDF pass-through");
        assertTrue(result.reason().isEmpty(), "reason must be empty on success");
    }
}
