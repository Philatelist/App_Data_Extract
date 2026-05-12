package com.clmextract.packaging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipPackager {

    private static final Logger LOG = LogManager.getLogger(ZipPackager.class);

    private static final long DEFAULT_PART_SIZE = 200L * 1024 * 1024;

    private final long partSizeBytes;

    public ZipPackager() {
        this(DEFAULT_PART_SIZE);
    }

    public ZipPackager(long partSizeBytes) {
        this.partSizeBytes = partSizeBytes;
    }

    /**
     * Packs all regular files in runOutputDir into split ZIP parts.
     * Returns list of part paths (empty if runOutputDir contains no regular files).
     */
    public List<Path> pack(Path runOutputDir, String baseName) throws IOException {
        List<Path> files = collectRegularFiles(runOutputDir);

        LOG.info("Packaging {} file(s) from {}", files.size(), runOutputDir);

        if (files.isEmpty()) {
            return List.of();
        }

        Path outputDir = runOutputDir.getParent() != null ? runOutputDir.getParent() : runOutputDir;

        List<Path> parts = new ArrayList<>();
        int partNum = 1;
        long bytesWrittenInCurrentPart = 0L;

        Path currentPartPath = partPath(outputDir, baseName, partNum);
        ZipOutputStream zos = openPart(currentPartPath);
        parts.add(currentPartPath);

        for (Path file : files) {
            long fileSize = Files.size(file);

            if (bytesWrittenInCurrentPart > 0 && bytesWrittenInCurrentPart + fileSize > partSizeBytes) {
                closePart(zos, currentPartPath, bytesWrittenInCurrentPart);
                partNum++;
                bytesWrittenInCurrentPart = 0L;
                currentPartPath = partPath(outputDir, baseName, partNum);
                zos = openPart(currentPartPath);
                parts.add(currentPartPath);
            }

            String entryName = file.getFileName().toString();
            zos.putNextEntry(new ZipEntry(entryName));
            try (InputStream in = Files.newInputStream(file)) {
                in.transferTo(zos);
            }
            zos.closeEntry();

            bytesWrittenInCurrentPart += fileSize;
        }

        closePart(zos, currentPartPath, bytesWrittenInCurrentPart);

        LOG.info("Packaging complete: {} part(s)", parts.size());
        return parts;
    }

    private List<Path> collectRegularFiles(Path dir) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    files.add(entry);
                }
            }
        }
        return files;
    }

    private ZipOutputStream openPart(Path partPath) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(partPath));
        LOG.info("Opened ZIP part: {}", partPath);
        return zos;
    }

    private void closePart(ZipOutputStream zos, Path partPath, long bytesWritten) throws IOException {
        zos.close();
        LOG.info("Closed ZIP part: {} ({} bytes written)", partPath, bytesWritten);
    }

    private Path partPath(Path dir, String baseName, int partNum) {
        return dir.resolve(String.format("%s.zip.%03d", baseName, partNum));
    }
}
