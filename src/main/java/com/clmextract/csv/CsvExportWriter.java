package com.clmextract.csv;

import com.clmextract.export.BundleRecord;

import java.io.Closeable;
import java.util.List;

public interface CsvExportWriter extends Closeable {

    void writeHeaders();

    void writeRecords(List<BundleRecord> records);
}
