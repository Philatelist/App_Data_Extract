package com.clmextract.export;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class AttachmentDownloader {

    private static final Logger LOG = LogManager.getLogger(AttachmentDownloader.class);

    private final DataSource dataSource;
    private final Path outputDir;

    public AttachmentDownloader(DataSource dataSource, Path outputDir) {
        this.dataSource = dataSource;
        this.outputDir = outputDir;
    }

    /**
     * Downloads signed PDFs for all tracking IDs into outputDir as {@code <id>.pdf}.
     *
     * @return count of files written
     */
    public int downloadPdfs(List<Long> trackingIds) {
        LOG.info("downloadPdfs: {} tracking ID(s) to scan", trackingIds.size());
        int count = 0;
        for (Long trackingId : trackingIds) {
            List<Map<String, Object>> attachments = dataSource.getAttachmentInfo(
                    String.valueOf(trackingId));
            if (attachments.isEmpty()) {
                LOG.debug("No attachment info returned for tracking ID {}", trackingId);
            } else {
                LOG.info("Tracking ID {}: {} attachment(s) found", trackingId, attachments.size());
            }
            for (Map<String, Object> attachment : attachments) {
                if (!isSignedPdf(attachment)) {
                    continue;
                }
                String url = extractString(attachment, "url", "");
                if (url.isBlank()) {
                    continue;
                }
                Path dest = outputDir.resolve(trackingId + ".pdf");
                if (Files.exists(dest)) {
                    LOG.info("Skipping already-downloaded file: {}", dest);
                    continue;
                }
                byte[] bytes = dataSource.downloadDocument(url);
                if (bytes.length == 0) {
                    LOG.warn("Empty response downloading signed PDF for tracking ID {}", trackingId);
                    continue;
                }
                try {
                    Files.write(dest, bytes);
                    LOG.info("Downloaded {} ({} bytes)", dest, bytes.length);
                    count++;
                } catch (Exception e) {
                    LOG.warn("Failed to write signed PDF {}: {}", dest, e.getMessage());
                }
            }
        }
        return count;
    }

    /**
     * Downloads all non-PDF attachments for all tracking IDs into outputDir as
     * {@code <id>_<fileName>}.
     *
     * @return count of files written
     */
    public int downloadAttachments(List<Long> trackingIds) {
        LOG.info("downloadAttachments: {} tracking ID(s) to scan", trackingIds.size());
        int count = 0;
        for (Long trackingId : trackingIds) {
            List<Map<String, Object>> attachments = dataSource.getAttachmentInfo(
                    String.valueOf(trackingId));
            if (attachments.isEmpty()) {
                LOG.debug("No attachment info returned for tracking ID {}", trackingId);
            }
            for (Map<String, Object> attachment : attachments) {
                String attCategory = extractString(attachment, "category", "");
                String attFileName = extractString(attachment, "fileName", "attachment");
                LOG.info("Tracking ID {}: found attachment '{}' category='{}'", trackingId, attFileName, attCategory);
                if (isSignedPdf(attachment)) {
                    continue;
                }
                String url = extractString(attachment, "url", "");
                if (url.isBlank()) {
                    continue;
                }
                String fileName = extractString(attachment, "fileName", "attachment");
                String safeFileName = fileName.replaceAll("[/\\\\]", "_");
                Path dest = outputDir.resolve(trackingId + "_" + safeFileName);
                if (Files.exists(dest)) {
                    LOG.info("Skipping already-downloaded file: {}", dest);
                    continue;
                }
                byte[] bytes = dataSource.downloadDocument(url);
                if (bytes.length == 0) {
                    LOG.warn("Empty response downloading attachment {} for tracking ID {}",
                            fileName, trackingId);
                    continue;
                }
                try {
                    Files.write(dest, bytes);
                    LOG.info("Downloaded {} ({} bytes)", dest, bytes.length);
                    count++;
                } catch (Exception e) {
                    LOG.warn("Failed to write attachment {}: {}", dest, e.getMessage());
                }
            }
        }
        return count;
    }

    /**
     * Returns true if the attachment is considered a "signed PDF":
     * the category contains "signed" (case-insensitive), OR the fileName ends
     * with ".pdf" and the category is blank.
     */
    private boolean isSignedPdf(Map<String, Object> attachment) {
        String category = extractString(attachment, "category", "");
        String fileName = extractString(attachment, "fileName", "");
        if (category.toLowerCase().contains("signed")) {
            return true;
        }
        return fileName.toLowerCase().endsWith(".pdf") && category.isBlank();
    }

    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        String str = value.toString();
        return str.isBlank() ? defaultValue : str;
    }
}
