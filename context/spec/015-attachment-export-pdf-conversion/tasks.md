# Tasks: Attachment Export and PDF Conversion

Spec: `context/spec/015-attachment-export-pdf-conversion/`

---

- [ ] **Slice 1: Admin Panel — attachment and output settings toggles**

  > Admins can see, toggle, and save both new settings from the Admin Panel.
  > The application remains fully functional after this slice.

  - [ ] Add `convertAttachmentsToPdf` (boolean, default `false`) and `includeEmptyExportFiles` (boolean, default `true`) fields to `AppConfig` with getters/setters. No YAML keys exist yet — both fields must default gracefully when absent from `config.yml`. **[Agent: java-backend]**
  - [ ] Update `ConfigController.getConfig()` to include both fields in the response map; update `putConfig()` to read and persist them via `.asBoolean(false)` / `.asBoolean(true)`. Update `AppConfigTest` and `ConfigControllerTest` to cover the round-trip through YAML and the GET/PUT API. **[Agent: java-backend]**
  - [ ] Add "Convert attachments to PDF" (id: `convert-attachments-pdf`, default unchecked) and "Include empty export files" (id: `include-empty-files`, default checked) toggles to `admin.html` at the bottom of the Output & Files section, using the full `form-group-toggle` pattern with `field-hint` paragraphs. Wire `setCheck` in `populateForm()` and `bool()` in `collectConfig()` in `admin.js`, using `!== false` guards for default-ON values. **[Agent: vanilla-frontend]**
  - [ ] Verify: `mvn package` succeeds. Start the app with `--serve`. Log in as admin. Open Admin Panel → Output & Files section. Confirm both toggles appear with correct labels and hint text. Toggle "Include empty export files" off, save, reload page — confirm the value persisted. Delete any screenshots produced during this check. **[Agent: java-backend]**

---

- [ ] **Slice 2: PDF conversion infrastructure (dependency, PdfConverter, DataSource ZIP download)**

  > Lays the conversion and download foundations. No pipeline behaviour changes yet.

  - [ ] Add `org.jodconverter:jodconverter-local` (latest stable) to `pom.xml`. Run `mvn dependency:resolve` to confirm the artifact resolves cleanly. **[Agent: java-backend]**
  - [ ] Implement `PdfConverter` in `com.clmextract.export`: `AutoCloseable`; `open()` starts the JODConverter `LocalOfficeManager`; `close()` stops it; `boolean convert(Path inputFile, Path outputPdfPath)` — if input extension is `.pdf`, copy to `outputPdfPath` and return `true` (no LibreOffice call); otherwise delegate to `LocalConverter`; return `false` on failure, timeout, or absent LibreOffice (log WARN once via a static flag). **[Agent: java-backend]**
  - [ ] Add `default InputStream downloadAttachmentsZip(String trackingNumber)` to the `DataSource` interface (returns an empty stream). Implement in `ApiDataSource`: build URL as `{baseUrl}/contract/{trackingNumber}/attachments/{sessionId}`, use `HttpResponse.BodyHandlers.ofInputStream()` for streaming (never buffered as byte[]). Register `DOWNLOAD_ATTACHMENTS_ZIP` constant for `documentsAttachmentsDownloadAllAttachmentsZip` in `EndpointRegistry`; update the entry in `endpoints.yml` so `response.type` is `binary`. **[Agent: java-backend]**
  - [ ] Write `PdfConverterTest`: `.pdf` pass-through (no LibreOffice call), successful conversion (mock `LocalConverter`), conversion failure returns `false`, LibreOffice-absent path logs exactly one WARN, `AutoCloseable` lifecycle (manager started in `open()`, stopped in `close()`). **[Agent: java-backend]**
  - [ ] Verify: `mvn test -Dtest=PdfConverterTest` — all tests pass. `mvn package` — fat JAR produced without errors. **[Agent: java-backend]**

---

- [ ] **Slice 3: AttachmentDownloader rework**

  > Downloads and extracts attachment ZIPs per record, saves files with the correct naming pattern, with optional PDF conversion and graceful failure handling.

  - [ ] Replace `AttachmentDownloader` with new constructor `(DataSource, Path outputDir, boolean convertToPdf)`. Remove `downloadPdfs`, the old `downloadAttachments(List<Long>)`, and `isSignedPdf`. Implement unified `int downloadAttachments(List<Long> trackingIds)`: per tracking ID — call `dataSource.getAttachmentInfo(trackingId)` and parse the `Property` array to extract `{fileName, fileVersion}` entries; call `dataSource.downloadAttachmentsZip(trackingId)` and stream to `<trackingId>_attachments_tmp.zip` in `outputDir`; iterate `ZipInputStream` entries, match by `fileName`, apply `sanitizeForFilename()` to each name component, assemble output filename `{trackingId}-{baseName}-{version}.ext`; when `convertToPdf` is true open a `PdfConverter` and call `convert()` — on failure save original at final path + write companion `{baseName}.txt` with plain-language reason; delete temp ZIP. Implement static `sanitizeForFilename(String)`: replace `/ \ : * ? " < > | \0` with `_`, collapse whitespace, trim, truncate to 100 chars, substitute `"unknown"` if blank; skip ZIP entries containing `..`. **[Agent: java-backend]**
  - [ ] Rewrite `AttachmentDownloaderTest`: empty metadata → silent skip; no-convert path → files saved with original extension and correct naming; convert path (mocked `PdfConverter.convert()` returns true) → PDF at expected path; conversion failure → original file present + companion `.txt` present; ZIP path-traversal guard → entry with `..` is skipped; `sanitizeForFilename` → illegal chars replaced, blank substituted, long strings truncated. **[Agent: java-backend]**
  - [ ] Verify: `mvn test -Dtest=AttachmentDownloaderTest` — all tests pass. **[Agent: java-backend]**

---

- [ ] **Slice 4: RunExecutor pipeline consolidation and empty-file suppression**

  > Wires the new downloader into the run pipeline, removes the EXPORT_PDF step, and adds header-only CSV suppression.

  - [ ] Remove `STEP_EXPORT_PDF` from `RunExecutor.STEPS`. Delete the EXPORT_PDF step execution block. Update the EXPORT_ATTACHMENTS step to construct `new AttachmentDownloader(dataSource, outputDir, config.isConvertAttachmentsToPdf())` and call `.downloadAttachments(exportedIds)`. **[Agent: java-backend]**
  - [ ] Add private method `deleteEmptyCsvFiles(Path outputDir)` to `RunExecutor`: list `*.csv` files non-recursively, count lines via `Files.lines(path).count()`, delete files with count `≤ 1`, log each deletion at INFO. Call it after EXPORT_CSV success is recorded, guarded by `!config.isIncludeEmptyExportFiles()`. **[Agent: java-backend]**
  - [ ] Rewrite `RunExecutorAttachmentScopeTest`: verify `STEP_EXPORT_PDF` is absent from `RunExecutor.STEPS`; verify exported tracking IDs from the CSV step are passed unchanged to `downloadAttachments()`; verify `deleteEmptyCsvFiles` deletes header-only CSVs when `includeEmptyExportFiles = false`; verify files are preserved when `includeEmptyExportFiles = true`. **[Agent: java-backend]**
  - [ ] Verify: `mvn test -Dtest=RunExecutorAttachmentScopeTest,RunExecutorTest` — all tests pass. `mvn package` — JAR produced and application starts without errors with `--serve`. **[Agent: java-backend]**

---

- [ ] **Slice 5: README — PDF Conversion Setup documentation**

  > Operators know how to install LibreOffice and understand the opt-in default.

  - [ ] Add a "PDF Conversion Setup" section to `README.md` with LibreOffice installation commands for macOS (`brew install --cask libreoffice`), Debian/Ubuntu (`apt-get install libreoffice`), and RHEL/CentOS/Fedora (`dnf install libreoffice`). Note that `convertAttachmentsToPdf` defaults to `false` and the feature is a no-op until both LibreOffice is installed and the toggle is enabled in the Admin Panel. **[Agent: java-backend]**
  - [ ] Verify: Read `README.md` and confirm the section is present with all three platform commands and the opt-in note. **[Agent: java-backend]**

---

- [ ] **Slice 6: Feature Testing & Regression**

  > Verifies the whole feature end-to-end against functional-spec.md, run after all implementation slices are complete.

  - [ ] Read `functional-spec.md` acceptance criteria in full. Generate acceptance-level tests that verify the entire feature as a whole — not individual slices. Cover applicable layers (unit for pure logic, integration for service interactions, e2e for user flows) based on the project's testing stack. Write tests with RED validation (must fail before implementation is confirmed done). Annotate each test with `@spec: 015-attachment-export-pdf-conversion` and `@regression` if suitable for long-term regression. **[Agent: general-purpose]**
  - [ ] Run all generated tests. All must pass. Fix any failures before proceeding. **[Agent: general-purpose]**
