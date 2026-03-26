<!--
This document describes HOW to build the feature at an architectural level.
It is NOT a copy-paste implementation guide.

DO:
- Describe data models (table names, key columns, relationships)
- Describe API contracts (endpoints, request/response shapes)
- Reference file paths where code will live
- Note critical configuration requirements

DON'T:
- Include full code implementations
- Write complete schema definitions
- Provide copy-paste config files
-->

# Technical Specification: Run Manifest File

- **Functional Specification:** `context/spec/006-run-manifest-file/functional-spec.md`
- **Status:** Completed
- **Author(s):** CLM Data Extract Team

---

## 1. High-Level Technical Approach

At the very end of `ExportOrchestrator.run()`, after all BO pipelines, reports, summary, and parent CSVs have been written, a new `ManifestCsvWriter` is invoked. It scans the `MetaData` output directory, computes a SHA-256 checksum for each file found, and writes a single manifest CSV. Because `BackupManager` moves all pre-existing files out of `MetaData/` before the run begins, every file present in the folder at manifest-write time was produced by the current run.

No new external dependencies are required. SHA-256 is computed via `java.security.MessageDigest` (JDK standard library). CSV writing follows the existing OpenCSV pattern already used by `SummaryCsvWriter` and `ParentCsvWriter`.

---

## 2. Proposed Solution & Implementation Plan

### 2.1 New Class: `ManifestCsvWriter`

- **Package:** `com.clmextract.csv`
- **File:** `src/main/java/com/clmextract/csv/ManifestCsvWriter.java`
- **Responsibility:** Scans a given directory `Path`, computes SHA-256 + file size + last-modified time for each file (excluding itself), and writes the manifest CSV.

**Constructor parameters:**
| Parameter | Type | Purpose |
|---|---|---|
| `outputDir` | `Path` | The `MetaData` folder to scan |
| `manifestPath` | `Path` | Full path of the manifest file to write (excluded from its own entries) |
| `delimiter` | `char` | CSV delimiter from config (consistent with all other writers) |

**Method:**
- `void write()` — performs the scan, checksum computation, and CSV write. Retries up to 3 attempts on `IOException`. On each failure, logs a warning with the attempt number. If all 3 attempts fail, logs a final warning and returns without re-throwing — the run must not fail due to manifest errors.

**CSV columns (in order):** `Filename`, `SHA256`, `SizeBytes`, `GeneratedAt`

- `Filename`: file name only (not full path), via `path.getFileName().toString()`
- `SHA256`: hex-encoded digest from `MessageDigest.getInstance("SHA-256")` reading the file with `Files.readAllBytes()`
- `SizeBytes`: `Files.size(path)`
- `GeneratedAt`: `Files.getLastModifiedTime(path)` formatted as `yyyy-MM-dd HH:mm:ss` (local time)

Files are listed in the order returned by `Files.list()` (filesystem order).

**Retry behaviour:**
- Maximum attempts: 3 (fixed, not configurable)
- On each failed attempt: log `WARN "Manifest write attempt {n}/3 failed: {reason}"`
- Between attempts: short fixed delay (500 ms) to allow transient I/O conditions to clear
- After 3 failures: log `WARN "Manifest generation failed after 3 attempts — skipping"`
- The partial/failed manifest file, if partially written, is deleted before each retry to avoid a corrupt output

### 2.2 Integration in `ExportOrchestrator`

- **File:** `src/main/java/com/clmextract/export/ExportOrchestrator.java`

The manifest is generated in `run()`, inside the `try` block, after the reports section and before the `finally` block. This guarantees all other output files have been closed and flushed.

**Execution order in `run()`:**
1. BO pipeline loop
2. Reports generation
3. **→ Manifest generation (new)**
4. `finally`: logout, backup retention, summary CSV close

**Filename resolution:** reuses the existing `FilenameResolver` instance already created at the top of `run()`, ensuring the timestamp matches all other output files of the run:
```
filenameResolver.resolve("Manifest_{DDMMYYYY}_{HHMMSS}.csv", null, null)
```

### 2.3 SHA-256 Computation

- Uses `java.security.MessageDigest.getInstance("SHA-256")` — no new dependency.
- Reads each file fully into memory via `Files.readAllBytes()`. Output files are CSV text (typically small to medium size) — acceptable for v1.
- Digest bytes are hex-encoded using `String.format("%02x", b)` per byte.

### 2.4 No Config Changes

No new fields are added to `config.yml` or `AppConfig`. Manifest generation is unconditional and retry count is fixed at 3.

---

## 3. Impact and Risk Analysis

**System Dependencies:**
- `ExportOrchestrator` — one new method call added at the end of the `try` block.
- `com.clmextract.csv` package — one new class added.
- No changes to `BoPipeline`, existing CSV writers, `BackupManager`, or `DataSource`.

**Potential Risks & Mitigations:**

| Risk | Mitigation |
|---|---|
| I/O failure during manifest write | Retry up to 3 attempts with 500 ms delay. Delete partial file before each retry. After 3 failures, log `WARN` and continue — run is not failed. |
| Large files cause OOM when read for checksum | Output files are CSVs; practically bounded. Can be replaced with streaming `DigestInputStream` in future without changing the interface. |
| `Files.list()` includes directories or hidden files | Filter with `Files.isRegularFile(path)` during the scan. |
| Manifest file is accidentally listed as its own entry | Exclude by comparing each scanned `Path` against the manifest's own `Path` before processing. |

---

## 4. Testing Strategy

- **Unit test for `ManifestCsvWriter`:** Create a temp directory with 2–3 known files, invoke `write()`, read back the CSV and assert: correct row count, correct filenames, SHA-256 values match independent computation via `MessageDigest`, `SizeBytes` matches `Files.size()`, `GeneratedAt` is parseable and non-null.
- **Empty directory test:** Invoke `write()` on an empty directory, assert the output CSV contains only the header row and no data rows.
- **Self-exclusion test:** Confirm the manifest file path itself does not appear as a row in the output.
- **Retry test:** Simulate I/O failure on write attempts 1 and 2, succeed on attempt 3 — assert manifest is correctly produced and two warnings were logged.
- **All-retries-fail test:** Simulate persistent I/O failure, assert no exception propagates out of `write()`, a final warning is logged, and the run continues.
- **Integration point:** Extend `ExportOrchestratorTest` to verify that after `run()` completes, a file matching `Manifest_*.csv` exists in the output directory.
