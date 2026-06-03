# Functional Specification: Attachment Export and PDF Conversion

- **Roadmap Item:** Attachment download, PDF conversion, and final archive packaging
- **Status:** Completed
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

Currently, an export run produces CSV files containing the field data for each configured record type, but the documents attached to those records — signed contracts, supporting agreements, related files — are not included. Users who need a complete export package must retrieve those attachments manually through a separate, ad-hoc process.

This feature closes that gap: when an export run completes its CSV work, the tool also downloads, optionally converts, and packages all attached documents for every exported record. The end result is a single, self-contained output archive containing both the structured CSV data and the full document set, ready to hand off or import without any manual follow-up.

Two new administrator-controlled settings support this:
1. A toggle to turn PDF conversion of downloaded attachments on or off.
2. A toggle to control whether export files containing no records are included in the output.

**Success looks like:** An administrator triggers an export run and receives a complete, ready-to-use archive — CSVs plus all associated documents — without any additional manual steps.

---

## 2. Functional Requirements (The "What")

### 2.1 Attachment Download

During an export run, after the CSV data for a record type has been exported, the tool downloads all attached documents for every record of that type. This applies to all configured record types.

- For each record, the tool retrieves the attachment list (document names and version numbers) and downloads a bundled archive containing all attached files.
- Once downloaded, the archive is extracted and its individual files are made available for the next step.
- The downloaded archive is deleted immediately after extraction — only the extracted files are kept.
- If a record has no attachments, the tool silently skips it and moves on to the next record.

**Acceptance Criteria:**
- [x] Given an export run is started with at least one configured record type that has attachments, when the CSV export step for that type completes, then the tool proceeds to download attachments for each record without requiring any user action.
- [x] Given the tool has downloaded and extracted the attachment archive for a record, then the original downloaded archive file is no longer present in the output folder.
- [x] Given a record has no attached documents, then no files are written for that record and no error or warning is shown.

---

### 2.2 PDF Conversion (When Enabled)

When the "Convert attachments to PDF" setting is turned ON, every extracted attachment file is converted to a PDF document before being saved.

**File naming:** Each resulting PDF is named using the following pattern:
`[Tracking Number]-[Document Name]-[Version].pdf`
Example: `CLM-10042-SupportingAgreement-v2.pdf`

**File location:** Converted PDFs are saved in the same output folder as the CSV file for the corresponding record type.

**When conversion fails:** If an attachment cannot be converted (unsupported format, damaged file, etc.):
- The original file is saved as-is, keeping its original file name and extension.
- A companion text file with the same base name is placed next to the original file containing a plain-language explanation of why the conversion could not be completed (e.g., `SupportingAgreement-v2.txt`).
- The run continues normally.

**Acceptance Criteria:**
- [x] Given the "Convert attachments to PDF" setting is ON and an attachment is successfully converted, then a PDF file named `[TrackingNumber]-[DocumentName]-[Version].pdf` appears in the same output folder as the record type's CSV.
- [x] Given the "Convert attachments to PDF" setting is ON and an attachment cannot be converted, then the original file is present in the output folder alongside a text file bearing the same base name; the text file contains a human-readable explanation of the conversion failure.
- [x] Given the "Convert attachments to PDF" setting is ON and some attachments fail conversion, then the run does not stop — successfully converted files are still included in the output.

---

### 2.3 Attachment Export Without PDF Conversion (When Disabled)

When the "Convert attachments to PDF" setting is turned OFF, attachment files are extracted and saved in their original file formats. The same naming pattern is used (`[Tracking Number]-[Document Name]-[Version]`), but each file keeps its original extension (e.g., `.docx`, `.png`, `.xlsx`).

**Acceptance Criteria:**
- [x] Given the "Convert attachments to PDF" setting is OFF, when attachments are downloaded and extracted, then each file is saved with its original extension in the output folder, with no conversion attempted.

---

### 2.4 Admin Panel: Attachment and Output Settings

Two new settings appear in the Admin Panel. Both are visible and editable only by administrator users; operators cannot see or access these settings because the Admin Panel is restricted to administrators.

**"Convert attachments to PDF"**
- When ON: all downloaded attachments are converted to PDF before being saved (see §2.2).
- When OFF: attachments are saved in their original file format (see §2.3).

**"Include empty export files"**
- When ON: all CSV files are included in the output, even those containing only a header row with no data records beneath it. This is the current default behavior.
- When OFF: CSV files with no data records (header row only) are omitted from the output and are not included in the final archive.

**Acceptance Criteria:**
- [x] Given an administrator opens the Admin Panel, then both the "Convert attachments to PDF" toggle and the "Include empty export files" toggle are visible on the page.
- [x] Given an administrator changes either toggle and saves the configuration, then subsequent export runs reflect the updated setting.
- [x] Given the "Include empty export files" setting is OFF and a configured record type yields no records during an export run, then the CSV file for that record type is not present in the output folder or the final archive.
- [x] Given the "Include empty export files" setting is ON and a configured record type yields no records, then the CSV file for that record type is present in the output (containing only the header row).

---

### 2.5 Final Archive Packaging

After all CSV files and attachment files have been produced for all record types, the tool compresses everything into a single ZIP archive.

- If the total size is 200 MB or less, a single archive file is created.
- If the total exceeds 200 MB, the tool automatically splits the output into sequential parts, each smaller than 200 MB, using the standard multi-part archive naming convention (e.g., `export.zip.001`, `export.zip.002`).

**Acceptance Criteria:**
- [x] Given an export run completes, then a ZIP archive is present in the run's output folder containing all produced CSV files and all attachment files (PDFs or originals).
- [x] Given the total output is 200 MB or less, then a single archive file is created.
- [x] Given the total output exceeds 200 MB, then multiple archive parts are created in the run output folder, each smaller than 200 MB, using the `.zip.001`, `.zip.002` naming pattern.

---

## 3. Scope and Boundaries

### In-Scope

- Downloading attached documents for all configured record types as part of every export run.
- Extracting attachment archives and deleting the downloaded archives after extraction.
- Converting extracted files to PDF (when the setting is ON) and saving unconverted originals (when OFF or on failure).
- Naming attachment output files using the `[Tracking Number]-[Document Name]-[Version]` pattern.
- Saving a companion explanation text file alongside any attachment that could not be converted to PDF.
- Two new admin-only settings in the Admin Panel: "Convert attachments to PDF" and "Include empty export files."
- Packaging all CSV and attachment output into a ZIP archive at the end of the run.
- Automatically splitting the archive into parts smaller than 200 MB when the total size exceeds that limit.

### Out-of-Scope

- SFTP upload of the final archive (an existing, separate pipeline step — not modified here).
- Per-record-type attachment settings — the PDF conversion and empty-file toggles apply globally to all configured types.
- Selective or incremental attachment downloads — every run performs a full download.
- Previewing or browsing attachments within the web interface.
- The following roadmap items (addressed in separate specifications): Multi-BO Sequential Processing, Downloads List Generation, Progress Logging, Backup Retention, and Offline Test Mode.
