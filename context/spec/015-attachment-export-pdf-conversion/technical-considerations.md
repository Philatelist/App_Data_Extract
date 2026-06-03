# Technical Specification: Attachment Export and PDF Conversion

- **Functional Specification:** `context/spec/015-attachment-export-pdf-conversion/functional-spec.md`
- **Status:** Draft
- **Author(s):** Alex

---

## 1. High-Level Technical Approach

The feature extends the existing export pipeline in three areas: (1) attachment download and optional PDF conversion, (2) suppression of empty output files, and (3) final archive packaging (already implemented in `ZipPackager` — no changes required).

**Attachment download** replaces the current signed-PDF-split logic in `AttachmentDownloader` with a unified approach: for each tracking ID, the tool calls `getAttachmentInfo` to retrieve file names and versions, then downloads all attachments as a single ZIP archive via `downloadAllAttachmentsZip`. After extraction the ZIP is deleted. A new `PdfConverter` class optionally converts each extracted file using **JODConverter + LibreOffice** — one new Maven dependency (`jodconverter-local`).

**Empty-file suppression** is handled in `RunExecutor` as a post-CSV, pre-packaging sweep that deletes header-only CSV files when `includeEmptyExportFiles` is off.

**Config and UI** gain two new admin-only boolean flags wired through `AppConfig → ConfigController → admin.html / admin.js`.

Systems affected: `AppConfig`, `ConfigController`, `AttachmentDownloader`, `RunExecutor`, `DataSource` interface + `ApiDataSource`, `pom.xml`, Admin Panel frontend, and `README`.

---

## 2. Proposed Solution & Implementation Plan

### 2.1 New Maven Dependency

Add `org.jodconverter:jodconverter-local` (latest stable) to `pom.xml`. This library manages LibreOffice as a long-lived server process and exposes a clean Java API for document conversion. It requires LibreOffice to be installed on the host; the `README` must document installation steps for macOS, Debian/Ubuntu, and RHEL/CentOS.

---

### 2.2 New Config Fields

| Java field | Type | Default | YAML key | JSON key (API) |
|---|---|---|---|---|
| `convertAttachmentsToPdf` | `boolean` | `false` | `convertAttachmentsToPdf` | `convertAttachmentsToPdf` |
| `includeEmptyExportFiles` | `boolean` | `true` | `includeEmptyExportFiles` | `includeEmptyExportFiles` |

`convertAttachmentsToPdf` defaults to `false` (opt-in) — deployments without LibreOffice do not produce errors unless the flag is explicitly turned on. `includeEmptyExportFiles` defaults to `true` to preserve existing behaviour.

Both follow the naming convention of `enableZipPackaging` / `enableSftpUpload`. Existing `config.yml` files load safely with defaults when these keys are absent.

**`ConfigController` changes:**
- `GET /api/config`: add both fields to the response map.
- `PUT /api/config`: read as booleans (`.asBoolean(false)` / `.asBoolean(true)`) and write to the YAML root map.

---

### 2.3 Attachment Info API Response Structure

`getAttachmentInfo(trackingId)` returns a JSON array. Each element has:

```
{
  "id": "<componentId>",
  "parentId": "<contractTrackingId>",
  "listType": "ReqAttachment",
  "Property": [
    { "Name": "fileName",    "Value": "Doc1.docx" },
    { "Name": "fileVersion", "Value": "1" },
    { "Name": "URL",         "Value": "https://..." },
    { "Name": "fileSize",    "Value": "12101" },
    { "Name": "category",    "Value": "Contract" },
    ...
  ]
}
```

The relevant fields are extracted by scanning the `Property` array for entries with `Name == "fileName"` and `Name == "fileVersion"`. Each record is parsed into a lightweight model:

| Field | Source | Notes |
|---|---|---|
| `fileName` | `Property[Name="fileName"].Value` | Full filename with extension, e.g. `"Doc1.docx"` |
| `fileVersion` | `Property[Name="fileVersion"].Value` | Version string, e.g. `"1"` |

The `URL` property is present but **not used** — the bulk ZIP download endpoint is used instead to retrieve all files in one call per record, reducing per-attachment API round trips.

---

### 2.4 New Classes

#### `PdfConverter` — `com.clmextract.export.PdfConverter`

Responsibility: converts a single input file to PDF using JODConverter + LibreOffice.

**Lifecycle:** `PdfConverter` starts a local LibreOffice office manager instance during `open()` and stops it during `close()`. `AttachmentDownloader` calls `open()` before the per-record loop and `close()` after all records are processed, so LibreOffice starts once per EXPORT_ATTACHMENTS step rather than once per file. Implements `AutoCloseable` for safe use in try-with-resources.

**Method:** `boolean convert(Path inputFile, Path outputPdfPath)`

- If the input extension is `.pdf`: copies the file to `outputPdfPath` and returns `true` — no LibreOffice call.
- Otherwise: delegates to the JODConverter `LocalConverter` to produce a PDF in the target parent directory. Moves the output to `outputPdfPath`.
- Returns `false` on conversion failure, timeout, or if LibreOffice is not installed. Logs the failure reason.
- If LibreOffice is not found on first use, logs once at WARN level and returns `false` on all subsequent calls.

---

### 2.5 `AttachmentDownloader` Rework

**File:** `com.clmextract.export.AttachmentDownloader`

The existing `downloadPdfs(List<Long>)` and `downloadAttachments(List<Long>)` methods and the `isSignedPdf()` helper are removed.

**New constructor:** `AttachmentDownloader(DataSource dataSource, Path outputDir, boolean convertToPdf)`

**New unified method:** `int downloadAttachments(List<Long> trackingIds)` — returns the count of output files written.

**Per-record sequence:**

1. Call `dataSource.getAttachmentInfo(trackingId)` → parse each element in the JSON array to extract `{fileName, fileVersion}` by scanning the `Property` array.
2. If the list is empty, skip the record silently.
3. Call `dataSource.downloadAttachmentsZip(trackingId)` → stream directly to a temp file `<trackingId>_attachments_tmp.zip` in `outputDir` (never buffered fully in memory — see §2.6).
4. If the temp file is zero bytes, log a warning and delete it.
5. Iterate ZIP entries via `ZipInputStream`:
   - For each entry, look up its matching metadata record where `metadata.fileName == zipEntry.getName()`.
   - Extract the base name (strip extension) and version from the matched record.
   - Apply `sanitizeForFilename()` to each component.
   - Assemble the output path: `{trackingId}-{baseFileName}-{fileVersion}.pdf` (when converting) or `{trackingId}-{baseFileName}-{fileVersion}.{originalExtension}` (when not).
   - Fallback if no metadata match: use `{trackingId}-{sanitized zipEntry name}`.
   - Write extracted bytes to a temporary file; then either convert (if flag is on) or rename to the final path.
   - **On conversion failure:** save the original file at the final path with its original extension; write a companion `{sameBaseName}.txt` with a plain-language explanation.
6. Delete the temporary ZIP file.

**`sanitizeForFilename(String input)` static helper:**
- Replaces `/ \ : * ? " < > | \0` with `_`
- Collapses whitespace runs to a single space, trims ends
- Truncates to 100 characters; substitutes `"unknown"` if blank
- ZIP entries containing `..` are skipped (path traversal guard)

---

### 2.6 `DataSource` Interface — New Method

**`downloadAttachmentsZip(String trackingNumber)`** — returns `InputStream`.

A `default` implementation returns an empty stream (backward compatible with offline mode and test stubs).

`ApiDataSource` overrides this: constructs the URL as `{baseUrl}/contract/{trackingNumber}/attachments/{sessionId}` and uses `HttpResponse.BodyHandlers.ofInputStream()` — the response body is streamed directly to a temp file rather than buffered in a `byte[]`. This handles contracts with large attachment archives without exhausting heap.

A new `EndpointRegistry` constant `DOWNLOAD_ATTACHMENTS_ZIP` is registered for `documentsAttachmentsDownloadAllAttachmentsZip` to keep the registry as the authoritative list of known CLM operations. The endpoint's `response.type` in `endpoints.yml` should be updated from `json` to `binary`.

---

### 2.7 `RunExecutor` Pipeline Changes

**Step consolidation:** `STEP_EXPORT_PDF` is removed from the `STEPS` list. The new pipeline is:

```
EXPORT_CSV → EXPORT_ATTACHMENTS → PACKAGING → SFTP_UPLOAD
```

**EXPORT_ATTACHMENTS step** becomes:
```
new AttachmentDownloader(dataSource, outputDir, config.isConvertAttachmentsToPdf())
    .downloadAttachments(exportedIds)
```

If `convertAttachmentsToPdf` is true, `AttachmentDownloader` opens a `PdfConverter` (starting LibreOffice) in a try-with-resources block around the per-record loop.

**Empty-file suppression:** A new private method `deleteEmptyCsvFiles(Path outputDir)` is called after EXPORT_CSV succeeds and before EXPORT_ATTACHMENTS begins. It lists all `*.csv` files in `outputDir`, counts lines via `Files.lines(path).count()`, and deletes any with count `≤ 1`. Only called when `!config.isIncludeEmptyExportFiles()`.

---

### 2.8 Admin Panel (Frontend)

**Files:** `admin.html`, `js/admin.js` — no CSS changes needed; all existing toggle styles apply.

Both toggles are added at the **bottom of the Output & Files section** (`<section id="output">`), after the "Generate Parent Linkage File" block and its template field. They use the full `form-group-toggle` pattern with `field-hint` description text.

| Setting | HTML ID | Default `checked` | Hint text |
|---|---|---|---|
| Convert attachments to PDF | `convert-attachments-pdf` | no | "When enabled, downloaded attachments are converted to PDF. Requires LibreOffice to be installed on the host — see README for setup instructions." |
| Include empty export files | `include-empty-files` | yes | "When enabled, CSV files that contain only a header row and no data records are included in the output. When disabled, those files are omitted." |

**`populateForm()` additions:**
```
setCheck('convert-attachments-pdf', config.convertAttachmentsToPdf !== false);
setCheck('include-empty-files',     config.includeEmptyExportFiles !== false);
```

**`collectConfig()` additions:**
```
convertAttachmentsToPdf: bool('convert-attachments-pdf'),
includeEmptyExportFiles: bool('include-empty-files'),
```

---

## 3. Impact and Risk Analysis

### System Dependencies

- **Existing tests:** `AttachmentDownloaderTest` and `RunExecutorAttachmentScopeTest` reference the old `downloadPdfs` / `downloadAttachments` signatures — both must be rewritten.
- **Run history:** Removing `EXPORT_PDF` from `STEPS` changes the steps shown in the dashboard. Existing `ui-state.json` history entries that contain an `EXPORT_PDF` step remain readable (Jackson ignores unknown keys) but the step no longer appears in new runs.
- **Host requirement:** LibreOffice must be installed on any host where `convertAttachmentsToPdf = true`. See §5 for README content.

### Potential Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| **ZIP entry names don't match `fileName` metadata** — if the CLM server renames files inside the ZIP | Low | Fallback to `{trackingId}-{zipEntryName}` with sanitization; log a warning per unmatched entry |
| **LibreOffice not installed but conversion enabled** | Medium | `PdfConverter` logs a single WARN on first failure; the run warning list in the dashboard surfaces a human-readable message; conversion degrades to original-file-saved + companion `.txt` |
| **Large attachment archives exhausting heap** | Medium | Mitigated by streaming `InputStream` in `ApiDataSource.downloadAttachmentsZip()` — ZIP written to temp file, never fully buffered |

---

## 4. Testing Strategy

- **`PdfConverterTest`** (new): unit tests for the `.pdf` pass-through case, successful JODConverter conversion (mock), conversion failure, LibreOffice-absent case, and try-with-resources lifecycle.
- **`AttachmentDownloaderTest`** (rewrite): cover silent skip on empty metadata, successful no-convert path, successful convert path (mocked `PdfConverter`), conversion failure (verify original + companion `.txt`), ZIP path-traversal guard, and `sanitizeForFilename` edge cases.
- **`RunExecutorAttachmentScopeTest`** (rewrite): update to new `downloadAttachments(List<Long>)` signature; verify exported tracking IDs are passed correctly; verify `EXPORT_PDF` is absent from the pipeline.
- **`AppConfigTest` / `ConfigControllerTest`**: verify both new fields round-trip through YAML and the GET/PUT API.
- **`RunExecutorTest`** (new case): verify `deleteEmptyCsvFiles` removes header-only CSVs when `includeEmptyExportFiles = false`; leaves files intact when `true`.
- **Manual/integration**: run against a real CLM instance with `convertAttachmentsToPdf = true`; verify naming pattern, companion `.txt` on failure, and ZIP splitting above 200 MB.

---

## 5. README Additions

The `README` must include a **"PDF Conversion Setup"** section with LibreOffice installation commands for:

- **macOS:** `brew install --cask libreoffice`
- **Debian / Ubuntu:** `apt-get install libreoffice`
- **RHEL / CentOS / Fedora:** `dnf install libreoffice`

And note that `convertAttachmentsToPdf` defaults to `false` — the feature is inert until explicitly enabled and LibreOffice is installed.
