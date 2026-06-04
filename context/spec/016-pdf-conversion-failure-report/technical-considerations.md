# Technical Specification: PDF Conversion Failure Report

- **Functional Specification:** `context/spec/016-pdf-conversion-failure-report/functional-spec.md`
- **Status:** Completed
- **Author(s):** Alex

---

## 1. High-Level Technical Approach

Two targeted changes to existing classes:

1. **`PdfConverter.convert()`** — change return type from `boolean` to a `ConversionResult` record that carries both a success flag and a plain-language failure reason. This gives callers the reason without any shared mutable state.

2. **`AttachmentDownloader`** — collect `ConversionFailure` records during the per-record loop instead of writing per-file companion `.txt` files. After all records are processed, if the failure list is non-empty, write `pdf_conversion_failures.txt` to `outputDir`.

No other classes change.

---

## 2. Implementation Plan

### 2.1 `PdfConverter` — `ConversionResult` return type

Replace `boolean convert(Path inputFile, Path outputPdfPath)` with:

```
ConversionResult convert(Path inputFile, Path outputPdfPath)
```

**`ConversionResult` record** (inner or top-level in the same package):

| Field | Type | Description |
|---|---|---|
| `success` | `boolean` | `true` if the file was converted (or was already a PDF pass-through) |
| `reason` | `String` | Empty string on success; human-readable failure cause on failure |

Failure reason strings (examples; exact wording is implementation detail):
- `"LibreOffice is not installed on this host. Install LibreOffice to enable PDF conversion."`
- `"Conversion process timed out after 60 seconds."`
- `"Conversion process exited with error code N."`
- `"Unsupported or unreadable file format."`

The `.pdf` pass-through path returns `ConversionResult(true, "")`.

All existing call sites in `AttachmentDownloader` that currently check `boolean converted = pdfConverter.convert(...)` are updated to `ConversionResult result = pdfConverter.convert(...); result.success()`.

---

### 2.2 `AttachmentDownloader` — collect failures, write single report

**New inner record:**
```
record ConversionFailure(String originalFileName, String savedAsFileName, String reason) {}
```

**Structural change in `processTrackingId`:**
- Remove the block that writes the companion `.txt` file (currently lines ~176–186 in `AttachmentDownloader.java`).
- Instead, when `result.success()` is false, add a `ConversionFailure` to a list.
- The failure list is scoped to the `downloadAttachments(List<Long>)` method (declared before the per-record loop, passed into / returned from `processTrackingId`, or accumulated via a method-level field on a helper object).

**New private method: `writeFailureReport(Path outputDir, List<ConversionFailure> failures)`**
- Called once, at the end of `downloadAttachments`, if `!failures.isEmpty()`.
- Writes `outputDir/pdf_conversion_failures.txt`.
- File format (plain text, UTF-8):

```
PDF Conversion Failures
=======================
Run output: <outputDir absolute path>

<originalFileName>
  Saved as : <savedAsFileName>
  Reason   : <reason>

<originalFileName>
  Saved as : <savedAsFileName>
  Reason   : <reason>

Total: N file(s) could not be converted to PDF.
```

---

### 2.3 `RunExecutor` — PACKAGING always runs + remove EXPORT_PDF step

**Remove STEP_EXPORT_PDF from the `STEPS` list entirely.**

Currently `STEP_EXPORT_PDF` is kept in `STEPS` and initialized to `SKIPPED`, which causes the dashboard to render "Export — Signed PDFs: Skipped" in every run. It must be removed from the list so it never appears in run state or history.

- Remove `STEP_EXPORT_PDF` from `RunExecutor.STEPS`.
- Remove the `updateStep(runId, STEP_EXPORT_PDF, RunStatus.SKIPPED)` call that follows the EXPORT_CSV block.
- Update `RunExecutorTest`: any assertion that checks for `STEP_EXPORT_PDF` in the step map must be removed.

**PACKAGING must run even when EXPORT_ATTACHMENTS has failures.**

Currently, if the `EXPORT_ATTACHMENTS` step throws an unhandled exception, `failRemaining` is called which marks PACKAGING (and SFTP) as FAILED — preventing the ZIP from being created even though all CSV files were successfully exported.

Change: in the `catch` block for EXPORT_ATTACHMENTS, do not call `failRemaining`. Instead: mark only `STEP_EXPORT_ATTACHMENTS` as `FAILED`, log the error, and allow execution to fall through to the PACKAGING step. The PACKAGING step already has its own try/catch. SFTP still runs (or skips) as configured.

This means that even in the worst case (EXPORT_ATTACHMENTS crashes entirely), the run produces a ZIP of whatever files were written up to that point.

---

## 3. Impact and Risk Analysis

- **`PdfConverterTest`** — all tests that currently call `convert()` and check `boolean` must be updated to call `.success()` on the returned `ConversionResult`. The test logic does not otherwise change.
- **`AttachmentDownloaderTest`** — tests that assert a companion `.txt` exists must be updated to assert it does NOT exist; new tests must assert `pdf_conversion_failures.txt` is present (with correct content) when failures occur, and absent when all conversions succeed.
- **`RunExecutorTest` / `RunExecutorAttachmentScopeTest`** — remove any assertion checking for `STEP_EXPORT_PDF` in the step map; add a test verifying that PACKAGING runs (is not FAILED) when EXPORT_ATTACHMENTS throws.
- No other classes are affected.

---

## 4. Testing Strategy

- **`PdfConverterTest`**: update all `convert()` call sites to use `.success()`; add test confirming `.reason()` is non-empty on failure and empty on success.
- **`AttachmentDownloaderTest`**: replace companion-`.txt` assertions with `pdf_conversion_failures.txt` assertions; cover single failure, multiple failures (all in one file), and zero failures (file absent).
- **`Spec016*`** acceptance test class: cover all four acceptance criteria end-to-end using stub `DataSource`.
