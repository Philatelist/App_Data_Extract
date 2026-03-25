# Task List: Run Manifest File

- **Spec:** `context/spec/006-run-manifest-file/`
- **Status:** Ready

---

## Slice 1 — `ManifestCsvWriter` writes a correct manifest

_Core class exists and produces a valid CSV for a populated directory._

- [ ] Create `ManifestCsvWriter` in `src/main/java/com/clmextract/csv/ManifestCsvWriter.java` with constructor `(Path outputDir, Path manifestPath, char delimiter)` and a `write()` method. **[Agent: general-purpose]**
- [ ] In `write()`: scan `outputDir` with `Files.list()`, filter to regular files only, exclude the manifest's own `Path`. **[Agent: general-purpose]**
- [ ] For each file: compute SHA-256 via `MessageDigest.getInstance("SHA-256")` + `Files.readAllBytes()`, hex-encode the digest byte-by-byte. Read `Files.size()` and `Files.getLastModifiedTime()` formatted as `yyyy-MM-dd HH:mm:ss` (local time). **[Agent: general-purpose]**
- [ ] Write CSV using OpenCSV with header row `Filename,SHA256,SizeBytes,GeneratedAt` and one data row per file, using the configured delimiter. **[Agent: general-purpose]**
- [ ] Unit test: create a temp directory with 2–3 known text files, invoke `write()`, parse the output CSV and assert: correct row count, filenames match, SHA-256 values match independent `MessageDigest` computation, `SizeBytes` matches `Files.size()`, `GeneratedAt` is parseable. **[Agent: general-purpose]**
- [ ] Self-exclusion test: assert the manifest `Path` itself does not appear as a data row. **[Agent: general-purpose]**

---

## Slice 2 — Empty directory and retry resilience

_The writer handles edge cases gracefully without crashing the run._

- [ ] Empty directory test: invoke `write()` on an empty temp directory, assert the CSV contains only the header row and no exception is thrown. **[Agent: general-purpose]**
- [ ] Add retry loop in `write()`: up to 3 attempts on `IOException`. Before each retry, delete the partially written manifest file if it exists. Between attempts, sleep 500 ms. **[Agent: general-purpose]**
- [ ] After all 3 attempts fail: log `WARN "Manifest generation failed after 3 attempts — skipping"` and return without re-throwing. **[Agent: general-purpose]**
- [ ] Retry test: mock or subclass to simulate failure on attempts 1 and 2, succeed on attempt 3 — assert the manifest is correctly produced and two `WARN` entries were logged. **[Agent: general-purpose]**
- [ ] All-retries-fail test: simulate persistent `IOException` across all 3 attempts — assert no exception propagates and a final warning is logged. **[Agent: general-purpose]**

---

## Slice 3 — Integration into `ExportOrchestrator`

_Manifest is generated automatically at the end of every real run._

- [ ] In `ExportOrchestrator.run()`, after the reports section and before the `finally` block: resolve the manifest filename via the existing `FilenameResolver` instance using `"Manifest_{DDMMYYYY}_{HHMMSS}.csv"`, construct the manifest `Path` in `outputDir`, instantiate `ManifestCsvWriter`, and call `write()`. **[Agent: general-purpose]**
- [ ] Wrap the manifest call in `try/catch(Exception)`, logging a `WARN` on failure — consistent with the existing report-write error handling pattern. **[Agent: general-purpose]**
- [ ] Extend `ExportOrchestratorTest`: after `run()` completes, assert that exactly one file matching `Manifest_*.csv` exists in the output directory. **[Agent: general-purpose]**
- [ ] Build the project (`mvn package`) and verify it compiles and the full test suite passes. **[Agent: general-purpose]**
