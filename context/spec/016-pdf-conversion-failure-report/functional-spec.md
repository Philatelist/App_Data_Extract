# Functional Specification: PDF Conversion Failure Report

- **Roadmap Item:** Consolidated PDF conversion failure report with failure reasons
- **Status:** Draft
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

When the "Convert attachments to PDF" setting is enabled and some attachments cannot be converted, the current behaviour writes a separate small text file next to each unconverted attachment. These per-file notes contain no information about *why* the conversion failed — they only repeat the file names. This makes it hard for an administrator to understand whether the failures are due to a missing tool, unsupported file formats, or something else, and requires opening many files to get the full picture.

This change replaces that per-file approach with a single, consolidated failure report that appears in the output folder at the end of the run. The report lists every file that could not be converted, each with a plain-language explanation of the cause.

---

## 2. Functional Requirements (The "What")

### 2.1 Consolidated Failure Report

When an export run completes and at least one attachment could not be converted to PDF, the tool writes a single file named `pdf_conversion_failures.txt` to the run's output folder.

- The report lists every failed conversion, one entry per file.
- Each entry includes:
  - The original file name as it came from the system (e.g. `Doc1 copy 2.docx`).
  - The name of the original file as it was saved in the output folder (e.g. `2824884-Doc1_copy_2-1.docx`).
  - A plain-language explanation of why conversion failed (e.g. `LibreOffice is not installed on this host`, `Conversion timed out after 60 seconds`, `Unsupported file format`).
- If no conversions failed during the run, the file is not created.
- The per-attachment companion `.txt` files written by the previous behaviour are removed — only the single consolidated report remains.

**Acceptance Criteria:**
- [ ] Given the "Convert attachments to PDF" setting is ON and at least one attachment fails conversion, then a single file named `pdf_conversion_failures.txt` is present in the run's output folder after the run completes.
- [ ] Given `pdf_conversion_failures.txt` is created, then it lists every failed file with its original name, its saved name, and a human-readable reason for the failure.
- [ ] Given the "Convert attachments to PDF" setting is ON and all attachments convert successfully, then `pdf_conversion_failures.txt` is not created.
- [ ] Given the "Convert attachments to PDF" setting is ON and multiple attachments fail, then all failures appear in the single report — no per-file companion `.txt` files exist alongside them.

### 2.2 ZIP Packaging Always Runs

After the CSV export and attachment download steps complete — even if some or all attachments could not be converted to PDF — the tool must still compress all produced files into a ZIP archive. A partial attachment failure is not a reason to skip packaging.

**Acceptance Criteria:**
- [ ] Given some attachments could not be converted to PDF and were saved in their original format, when the run reaches the packaging step, then a ZIP archive is still created containing all CSV files and all attachment files (converted or original).
- [ ] Given the attachment download step encountered errors but the CSV export completed successfully, then the packaging step still runs and produces a ZIP archive of the CSV output.

---

### 2.3 Remove "Signed PDFs" Step from Run Progress Display

The run progress display no longer shows a step labelled "Export — Signed PDFs" (or similar). This step was a legacy concept from a previous implementation and no longer applies — attachments are now downloaded as a unified archive. Operators should not see it in the dashboard.

**Acceptance Criteria:**
- [ ] Given an operator views the live run progress or a completed run in history, then no step referencing "Signed PDFs" or the previous PDF-export behaviour appears in the step list.

---

## 3. Scope and Boundaries

### In-Scope
- Writing a single `pdf_conversion_failures.txt` per run when at least one conversion fails.
- Including the failure reason (not just the file names) in each entry.
- Removing the per-file companion `.txt` files produced by the previous implementation.
- Ensuring ZIP packaging always runs after CSV export and attachment steps, regardless of conversion failures.
- Removing the legacy "Signed PDFs" step from the run progress display.

### Out-of-Scope
- Changing how unconverted original files are named or saved.
- Surfacing the failure report contents in the web dashboard UI (the file is in the output folder only).
- Changing the PDF conversion logic itself.
- Changing the `enableZipPackaging` admin toggle — that setting remains available.
