package com.clmextract.csv;

import com.clmextract.config.DateFormatConfig;
import com.clmextract.metadata.BoMetadata;

import java.nio.file.Path;

public class CsvWriterFactory {

    public static CsvExportWriter create(String csvMode, BoMetadata metadata,
                                         ColumnResolver columnResolver,
                                         FilenameResolver filenameResolver,
                                         String filenameTemplate,
                                         Path outputDir, char delimiter,
                                         DateFormatConfig dateFormatConfig) {
        DateFormatter dateFormatter = new DateFormatter(dateFormatConfig);
        switch (csvMode) {
            case "per-component":
                return new PerComponentCsvWriter(metadata, columnResolver, filenameResolver,
                        filenameTemplate, outputDir, delimiter, dateFormatter);
            case "merged-single":
                return new MergedSingleCsvWriter(metadata, columnResolver, filenameResolver,
                        filenameTemplate, outputDir, delimiter, dateFormatter);
            case "single-only":
                return new SingleOnlyCsvWriter(metadata, columnResolver, filenameResolver,
                        filenameTemplate, outputDir, delimiter, dateFormatter);
            default:
                throw new IllegalArgumentException("Unsupported CSV mode: " + csvMode);
        }
    }
}
