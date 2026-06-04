package com.clmextract.export;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.office.LocalOfficeManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Converts documents to PDF using JODConverter / LibreOffice.
 *
 * <p>Lifecycle: call {@link #open()} once before use, {@link #close()} when done.
 * Implements {@link AutoCloseable} for try-with-resources usage.
 */
public class PdfConverter implements AutoCloseable {

    private static final Logger LOG = LogManager.getLogger(PdfConverter.class);

    /** Set to true the first time a LibreOffice unavailability warning is logged. */
    private static final AtomicBoolean libreOfficeWarnLogged = new AtomicBoolean(false);

    /** Set to true when LibreOffice is determined to be unavailable, suppressing further attempts. */
    private static final AtomicBoolean libreOfficeUnavailable = new AtomicBoolean(false);

    private LocalOfficeManager manager;
    private LocalConverter converter;

    /**
     * Starts a {@link LocalOfficeManager} via JODConverter and creates a {@link LocalConverter}.
     *
     * @throws OfficeException if the office manager cannot be started
     */
    public void open() throws OfficeException {
        manager = LocalOfficeManager.install();
        manager.start();
        converter = LocalConverter.make(manager);
    }

    /**
     * Stops the office manager if it was started (null-safe).
     */
    @Override
    public void close() {
        if (manager != null) {
            try {
                manager.stop();
            } catch (OfficeException e) {
                LOG.warn("Failed to stop LibreOffice office manager: {}", e.getMessage());
            }
            manager = null;
            converter = null;
        }
    }

    /**
     * Converts {@code inputFile} to PDF and writes the result to {@code outputPdfPath}.
     *
     * <ul>
     *   <li>If the input file already has a {@code .pdf} extension (case-insensitive), it is copied
     *       directly to {@code outputPdfPath} without invoking LibreOffice.</li>
     *   <li>Otherwise, the {@link LocalConverter} is used to convert via LibreOffice.</li>
     * </ul>
     *
     * @param inputFile     the source file to convert
     * @param outputPdfPath the destination path for the resulting PDF
     * @return a {@link ConversionResult} indicating success or failure with a reason
     */
    public ConversionResult convert(Path inputFile, Path outputPdfPath) {
        if (libreOfficeUnavailable.get()) {
            return ConversionResult.fail("LibreOffice is not installed on this host. Install LibreOffice to enable PDF conversion.");
        }

        String fileName = inputFile.getFileName().toString();
        String lowerName = fileName.toLowerCase();

        // Pass-through: input is already a PDF
        if (lowerName.endsWith(".pdf")) {
            try {
                Files.copy(inputFile, outputPdfPath, StandardCopyOption.REPLACE_EXISTING);
                return ConversionResult.ok();
            } catch (IOException e) {
                LOG.warn("Failed to copy PDF file {} to {}: {}", inputFile, outputPdfPath, e.getMessage());
                return ConversionResult.fail("Conversion failed unexpectedly: " + e.getMessage());
            }
        }

        // Conversion via LibreOffice
        if (converter == null) {
            logLibreOfficeUnavailable("PdfConverter.open() was never called or LibreOffice failed to start");
            return ConversionResult.fail("LibreOffice is not installed on this host. Install LibreOffice to enable PDF conversion.");
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("jod-");
            File outputFile = new File(tempDir.toFile(), outputPdfPath.getFileName().toString());
            converter.convert(inputFile.toFile())
                     .to(outputFile)
                     .execute();
            Files.move(outputFile.toPath(), outputPdfPath, StandardCopyOption.REPLACE_EXISTING);
            return ConversionResult.ok();
        } catch (OfficeException e) {
            LOG.warn("LibreOffice conversion failed for {}: {}", inputFile, e.getMessage());
            return ConversionResult.fail("Conversion failed: " + e.getMessage());
        } catch (IOException e) {
            LOG.warn("I/O error during conversion of {}: {}", inputFile, e.getMessage());
            return ConversionResult.fail("Conversion process timed out.");
        } catch (Exception e) {
            LOG.warn("Unexpected error during conversion of {}: {}", inputFile, e.getMessage());
            return ConversionResult.fail("Conversion failed unexpectedly: " + e.getMessage());
        } finally {
            if (tempDir != null) {
                deleteTempDir(tempDir);
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void logLibreOfficeUnavailable(String reason) {
        libreOfficeUnavailable.set(true);
        if (libreOfficeWarnLogged.compareAndSet(false, true)) {
            LOG.warn("LibreOffice is unavailable — PDF conversion disabled. Reason: {}", reason);
        }
    }

    private void deleteTempDir(Path dir) {
        try {
            File[] files = dir.toFile().listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            Files.deleteIfExists(dir);
        } catch (IOException e) {
            LOG.debug("Could not clean up temp directory {}: {}", dir, e.getMessage());
        }
    }
}
