package com.clmextract.export;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AttachmentDownloader {

    private static final Logger LOG = LogManager.getLogger(AttachmentDownloader.class);

    private final DataSource dataSource;
    private final Path outputDir;
    private final boolean convertToPdf;

    public AttachmentDownloader(DataSource dataSource, Path outputDir, boolean convertToPdf) {
        this.dataSource = dataSource;
        this.outputDir = outputDir;
        this.convertToPdf = convertToPdf;
    }

    /**
     * Downloads attachments for all tracking IDs into outputDir.
     * When convertToPdf is true, attempts to convert each attachment to PDF via PdfConverter.
     *
     * @return count of output files written
     */
    public int downloadAttachments(List<Long> trackingIds) {
        LOG.info("downloadAttachments: {} tracking ID(s) to scan", trackingIds.size());
        int count = 0;
        for (Long trackingId : trackingIds) {
            count += processTrackingId(trackingId);
        }
        return count;
    }

    private int processTrackingId(Long trackingId) {
        // Step 1: Get attachment metadata
        List<Map<String, Object>> rawInfo = dataSource.getAttachmentInfo(String.valueOf(trackingId));
        List<AttachmentMeta> metaList = parseAttachmentMeta(rawInfo);

        if (metaList.isEmpty()) {
            LOG.debug("No attachment info returned for tracking ID {}", trackingId);
            return 0;
        }

        // Step 3: Download ZIP to temp file
        Path tempZip = outputDir.resolve(trackingId + "_attachments_tmp.zip");
        try (InputStream zipStream = dataSource.downloadAttachmentsZip(String.valueOf(trackingId))) {
            Files.copy(zipStream, tempZip, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.warn("Failed to download attachments ZIP for tracking ID {}: {}", trackingId, e.getMessage());
            return 0;
        }

        // Step 4: Check for empty ZIP
        try {
            if (Files.size(tempZip) == 0) {
                LOG.warn("Downloaded attachments ZIP is empty for tracking ID {}", trackingId);
                Files.deleteIfExists(tempZip);
                return 0;
            }
        } catch (IOException e) {
            LOG.warn("Could not check size of temp ZIP for tracking ID {}: {}", trackingId, e.getMessage());
            return 0;
        }

        // Step 5: Process ZIP entries
        int count = 0;
        try (PdfConverter pdfConverter = new PdfConverter()) {
            if (convertToPdf) {
                try {
                    pdfConverter.open();
                } catch (Exception e) {
                    LOG.warn("PdfConverter.open() failed for tracking ID {}: {}", trackingId, e.getMessage());
                    // converter remains in closed/unavailable state; convert() will return false for non-PDFs
                }
            }

            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(tempZip))) {
                ZipEntry entry;
                int entryIndex = 0;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        zis.closeEntry();
                        continue;
                    }

                    String entryName = entry.getName();

                    // Step 5a: Path-traversal guard
                    if (entryName.contains("..")) {
                        LOG.warn("Rejecting ZIP entry with path traversal sequence: {}", entryName);
                        zis.closeEntry();
                        entryIndex++;
                        continue;
                    }

                    // Step 5b: Find matching meta
                    String plainName = entryName.contains("/")
                            ? entryName.substring(entryName.lastIndexOf('/') + 1)
                            : entryName;
                    AttachmentMeta matchedMeta = null;
                    for (AttachmentMeta meta : metaList) {
                        if (meta.fileName().equals(entryName) || meta.fileName().equals(plainName)) {
                            matchedMeta = meta;
                            break;
                        }
                    }

                    // Step 5c: Determine base name and extension
                    String baseName;
                    String originalExt;
                    int dotIdx = plainName.lastIndexOf('.');
                    if (dotIdx > 0 && dotIdx < plainName.length() - 1) {
                        baseName = plainName.substring(0, dotIdx);
                        originalExt = plainName.substring(dotIdx + 1);
                    } else {
                        baseName = plainName;
                        originalExt = "";
                    }

                    // Step 5d: Sanitize components
                    String fileVersion = matchedMeta != null ? matchedMeta.fileVersion() : "1";
                    String sanitizedId = sanitizeForFilename(String.valueOf(trackingId));
                    String sanitizedBase = sanitizeForFilename(baseName);
                    String sanitizedVersion = sanitizeForFilename(fileVersion);

                    // Step 5e: Extract to temp file
                    Path tempExtract = outputDir.resolve(trackingId + "_tmp_" + entryIndex);
                    // Preserve original extension on the temp file so PdfConverter pass-through works
                    if (!originalExt.isEmpty()) {
                        tempExtract = outputDir.resolve(trackingId + "_tmp_" + entryIndex + "." + originalExt);
                    }
                    try {
                        Files.copy(zis, tempExtract, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        LOG.warn("Failed to extract ZIP entry {} for tracking ID {}: {}", entryName, trackingId, e.getMessage());
                        zis.closeEntry();
                        entryIndex++;
                        continue;
                    }

                    if (convertToPdf) {
                        // Step 5f: Convert to PDF
                        Path finalPdfPath = outputDir.resolve(
                                sanitizedId + "-" + sanitizedBase + "-" + sanitizedVersion + ".pdf");
                        boolean converted = pdfConverter.convert(tempExtract, finalPdfPath);
                        if (converted) {
                            deleteQuietly(tempExtract);
                            count++;
                        } else {
                            // Rename to original extension and write companion .txt
                            String originalName = sanitizedId + "-" + sanitizedBase + "-" + sanitizedVersion
                                    + (originalExt.isEmpty() ? "" : "." + originalExt);
                            Path originalPath = outputDir.resolve(originalName);
                            try {
                                Files.move(tempExtract, originalPath, StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                LOG.warn("Failed to rename temp extract to {}: {}", originalPath, e.getMessage());
                                deleteQuietly(tempExtract);
                                zis.closeEntry();
                                entryIndex++;
                                continue;
                            }
                            // Write companion .txt
                            String companionName = sanitizedId + "-" + sanitizedBase + "-" + sanitizedVersion + ".txt";
                            Path companionPath = outputDir.resolve(companionName);
                            try {
                                Files.writeString(companionPath,
                                        "PDF conversion failed for: " + entryName + System.lineSeparator()
                                                + "Original file saved as: " + originalName + System.lineSeparator(),
                                        StandardCharsets.UTF_8);
                            } catch (IOException e) {
                                LOG.warn("Failed to write companion .txt for {}: {}", entryName, e.getMessage());
                            }
                            count++;
                        }
                    } else {
                        // Step 5g: No convert — rename to final path
                        String finalName = sanitizedId + "-" + sanitizedBase + "-" + sanitizedVersion
                                + (originalExt.isEmpty() ? "" : "." + originalExt);
                        Path finalPath = outputDir.resolve(finalName);
                        try {
                            Files.move(tempExtract, finalPath, StandardCopyOption.REPLACE_EXISTING);
                            count++;
                        } catch (IOException e) {
                            LOG.warn("Failed to rename temp extract to {}: {}", finalPath, e.getMessage());
                            deleteQuietly(tempExtract);
                        }
                    }

                    zis.closeEntry();
                    entryIndex++;
                }
            } catch (IOException e) {
                LOG.warn("Error reading attachments ZIP for tracking ID {}: {}", trackingId, e.getMessage());
            }
        }

        // Step 6: Delete temp ZIP
        deleteQuietly(tempZip);
        return count;
    }

    /**
     * Parses the CLM attachment info response structure.
     * Each element has a "Property" key whose value is a List<Map<String,Object>>,
     * where each property map has "Name" and "Value" keys.
     */
    private List<AttachmentMeta> parseAttachmentMeta(List<Map<String, Object>> rawInfo) {
        List<AttachmentMeta> result = new ArrayList<>();
        for (Map<String, Object> element : rawInfo) {
            Object propObj = element.get("Property");
            if (!(propObj instanceof List<?> propList)) {
                continue;
            }
            String fileName = null;
            String fileVersion = null;
            for (Object item : propList) {
                if (!(item instanceof Map<?, ?> propMap)) continue;
                Object nameObj = propMap.get("Name");
                Object valueObj = propMap.get("Value");
                if (nameObj == null) continue;
                String name = nameObj.toString();
                String value = valueObj != null ? valueObj.toString() : "";
                if ("fileName".equals(name)) {
                    fileName = value;
                } else if ("fileVersion".equals(name)) {
                    fileVersion = value;
                }
            }
            if (fileName != null && !fileName.isBlank()) {
                result.add(new AttachmentMeta(fileName, fileVersion != null ? fileVersion : "1"));
            }
        }
        return result;
    }

    /**
     * Sanitizes a string for use as a filename component.
     * - Replaces illegal chars (/ \ : * ? " < > | and null byte) with _
     * - Replaces tabs and newlines with space; collapses runs of spaces; trims
     * - Truncates to 100 characters
     * - Returns "unknown" if blank after all transformations
     */
    static String sanitizeForFilename(String input) {
        if (input == null) return "unknown";
        // Replace illegal chars with _
        String s = input.replaceAll("[/\\\\:*?\"<>| ]", "_");
        // Replace tabs and newlines with space
        s = s.replaceAll("[\t\n\r]", " ");
        // Collapse runs of spaces to single space
        s = s.replaceAll(" {2,}", " ");
        // Trim
        s = s.trim();
        // Truncate to 100 characters
        if (s.length() > 100) {
            s = s.substring(0, 100);
        }
        return s.isBlank() ? "unknown" : s;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.debug("Could not delete temp file {}: {}", path, e.getMessage());
        }
    }

    /**
     * Metadata for a single attachment extracted from the CLM attachment info response.
     */
    record AttachmentMeta(String fileName, String fileVersion) {}
}
