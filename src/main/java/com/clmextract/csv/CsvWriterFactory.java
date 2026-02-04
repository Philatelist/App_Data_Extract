package com.clmextract.csv;

import com.clmextract.metadata.BoMetadata;

import java.nio.file.Path;

public class CsvWriterFactory {

    public static CsvExportWriter create(String csvMode, BoMetadata metadata,
                                         ColumnResolver columnResolver,
                                         FilenameResolver filenameResolver,
                                         String filenameTemplate,
                                         Path outputDir, char delimiter) {
        switch (csvMode) {
            case "per-component":
                return new PerComponentCsvWriter(metadata, columnResolver, filenameResolver,
                        filenameTemplate, outputDir, delimiter);
            case "merged-single":
                return new MergedSingleCsvWriter(metadata, columnResolver, filenameResolver,
                        filenameTemplate, outputDir, delimiter);
            case "single-only":
                return new SingleOnlyCsvWriter(metadata, columnResolver, filenameResolver,
                        filenameTemplate, outputDir, delimiter);
            default:
                throw new IllegalArgumentException("Unsupported CSV mode: " + csvMode);
        }
    }
}
