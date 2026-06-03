# Product Roadmap: CLM Data Extract

_This roadmap outlines our strategic direction based on customer needs and business goals. It focuses on the "what" and "why," not the technical "how."_

---

### Phase 1 -- Project Foundation

_Set up the project skeleton and define the configuration contract._

- [ ] **Java CLI Project Skeleton**
  - [ ] **Maven/Gradle project setup:** Initialize the Java project with build tooling, directory structure, and dependency management.
  - [ ] **CLI entry point:** Create the main class that accepts a `--config config.yml` argument and wires together the run lifecycle.
- [ ] **Configuration Format Design**
  - [ ] **YAML config schema:** Define and document the `config.yml` format covering server URL, credentials, BO type list, field filters, output directory, batch size, and backup retention count.
  - [ ] **Config parsing and validation:** Load the YAML file, validate required fields, and surface clear error messages for missing or invalid values.
- [ ] **Project Documentation**
  - [ ] **README with usage instructions:** Document how to build, configure, and run the tool.

---

### Phase 2 -- Endpoint Loading

_Load and validate the user-provided endpoints file and map it to the operations the tool needs._

- [ ] **Endpoints File Loader**
  - [ ] **Parse endpoints YAML:** Read the `endpoints.yml` file that describes the SCLM REST API surface (paths, methods, auth types, request/response shapes).
  - [ ] **Map to required operations:** Resolve the endpoints needed for login, logout, BOMetaData, trackingNumbers, and bundles from the loaded definitions.
  - [ ] **Validation and error reporting:** Verify all required endpoints are present and well-formed; fail fast with a clear message if any are missing.

---

### Phase 3 -- Authentication & HTTP Execution

_Implement session-based authentication and a robust HTTP request layer._

- [ ] **Authentication Lifecycle**
  - [ ] **Login:** Call the login endpoint with credentials from config, capture and store the session ID.
  - [ ] **Session management:** Attach the session ID header to all subsequent API calls automatically.
  - [ ] **Logout:** Call the logout endpoint at the end of a run, even if errors occurred (finally/shutdown-hook pattern).
- [ ] **HTTP Request Execution**
  - [ ] **Generic request runner:** Build a reusable HTTP client layer that constructs requests from endpoint definitions (method, path, headers, body).
  - [ ] **Error handling and retries:** Handle HTTP errors, timeouts, and authentication failures with clear log messages. Optionally support a simple retry for transient failures.

---

### Phase 4 -- Single-BO Export Pipeline

_Implement the full export pipeline for one BO type end-to-end._

- [ ] **Metadata Retrieval**
  - [ ] **Fetch BO metadata:** Call `/BOMetaData` for a configured BO type and parse the component/field structure (internal names, display names, cardinality, instance paths).
  - [ ] **Resolve field paths:** Build the list of field paths to request from the bundles endpoint, respecting any include/exclude filters from config.
- [ ] **Tracking ID Retrieval**
  - [ ] **Fetch tracking numbers:** Call `/trackingNumbers` for the BO type and collect the full list of IDs to export.
- [ ] **Batched Bulk Data Retrieval**
  - [ ] **Batch and fetch bundles:** Split tracking IDs into batches (size from config) and call `/bundles` for each batch with the resolved field paths.
  - [ ] **Parse bundle responses:** Extract records with their component fields, handling both single-cardinality (flat fields) and multi-cardinality (row-per-instance) components.
- [ ] **CSV Export**
  - [ ] **Flatten to tabular rows:** Convert the hierarchical component/field data into flat CSV rows, expanding multi-cardinality components into separate rows tied to their tracking number.
  - [ ] **Write CSV file:** Write the export CSV for the BO type with a header row using display names and one data row per record/instance.

---

### Phase 5 -- Multi-BO Processing & Operational Features

- [x] **BO Type Discovery and Usage Type Filtering**
  - [x] **Auto-discovery mode:** When `boTypes` is empty/omitted, call `/BOTypes` to discover all available BO types and export them automatically.
  - [x] **usageType filter:** New `boUsageTypeFilter` config option (`Directory`, `NonContract`, `Contract`) filters discovered BOs by their metadata `usageType` before export.
  - [x] **Fail-fast validation:** Invalid `boUsageTypeFilter` values are caught at startup before any API calls.

_Loop over all configured BO types, add backup management, logging, downloads list generation, and an offline test mode._

- [ ] **Multi-BO Sequential Processing**
  - [ ] **BO type loop:** Iterate over all BO types listed in config, running the full Phase 4 pipeline for each one sequentially.
  - [ ] **Per-type output files:** Ensure each BO type produces its own distinctly named CSV export file within the run directory.
- [ ] **Downloads List Generation**
  - [ ] **Attachment path extraction:** During bundle processing, identify multi-cardinality attachment components and extract file server paths.
  - [ ] **Write downloads-list CSV:** Produce a separate CSV per BO type listing tracking number, file path, and file name for later retrieval.
- [ ] **Progress Logging**
  - [ ] **Timestamped run log:** Write a log file per run capturing session start, each BO type processed, record counts, errors, and session end.
  - [ ] **Console output:** Mirror key progress milestones to stdout so the admin can monitor a running export.
- [ ] **Backup Retention & Output Organization**
  - [ ] **Timestamped run directories:** Create a new subdirectory per run (e.g., `output/2025-01-15_143022/`) containing all CSV files and the log.
  - [ ] **Retention cleanup:** After a successful run, count existing run directories and delete the oldest ones that exceed the configured retention limit.
- [ ] **Offline Test Mode**
  - [ ] **Sample JSON test mode:** Allow the tool to run against small sample JSON files (like the provided `boMetaData.sample.json` and `bundles.sample.json`) instead of a live API, for development and validation without a server.
- [x] **Run Manifest File**
  - [x] **ManifestCsvWriter:** New class that scans the MetaData output folder, computes SHA-256 checksums, and writes a `Manifest_{DDMMYYYY}_{HHMMSS}.csv` listing every file produced during the run (Filename, SHA256, SizeBytes, GeneratedAt).
  - [x] **Retry resilience:** Up to 3 attempts on I/O failure, with partial file cleanup between retries. Failure after 3 attempts is logged as a warning and does not fail the run.
  - [x] **Orchestrator integration:** Manifest generation is called automatically at the end of every run, after all other output is written, using the same FilenameResolver timestamp as the rest of the run's files.

---

### Phase 6 -- Web UI: Admin Panel & Operator Dashboard

_Replace the CLI-only workflow with a self-contained web application. Administrators configure the export through a browser; operators trigger runs and monitor progress without touching config files._

- [x] **Web Server & Operator Dashboard** *(spec 011)*
  - [x] **Javalin web server:** Embedded HTTP server (port 8080) serving static HTML/JS/CSS and a JSON REST API under `/api/*`. Started alongside the CLI via a `--serve` flag.
  - [x] **Operator login:** Session-based authentication. Operators log in with CLM credentials and receive an `OPERATOR` role; all dashboard routes are protected.
  - [x] **Run control:** `POST /api/run/start` triggers an export in a background thread; `GET /api/run/status` streams live step progress (EXPORT_CSV → EXPORT_PDF → EXPORT_ATTACHMENTS → PACKAGING → SFTP_UPLOAD); `POST /api/run/stop` cancels the run.
  - [x] **Export history:** Completed runs are persisted to `ui-state.json` and surfaced via `GET /api/run/history`.
  - [x] **Admin Panel:** Separate ADMIN-role-only page (`/admin.html`) for managing all `config.yml` settings through a form UI — server connection, BO types, output paths, CSV options, SFTP, date format, and more.
  - [x] **Scheduled exports:** Operators can configure a daily/weekly/monthly schedule; the server triggers automatic runs via `ExportScheduler`.
- [x] **Real Export Pipeline Wiring** *(spec 012)*
  - [x] **BoPipeline integration:** `RunExecutor` replaced stubs with real `BoPipeline.execute()` calls, passing the operator-selected BO list, output directory, and date filter.
  - [x] **Date filtering:** Dashboard exposes "Create Date from" and "Modified within period" filters; `RunExecutor` routes to `getTrackingNumbersAfterDate()` or `getTrackingNumbersInFlight()` accordingly.
  - [x] **SKIPPED step status:** ZIP packaging and SFTP upload steps can be individually disabled; disabled steps show a grey "Skipped" pill in the dashboard without failing the run.
  - [x] **Attachment scope fix:** PDF and attachment download steps now use the tracking IDs collected during the CSV export step, not a separate unfiltered CLM call — so filtered runs (by date or BO) produce consistent output across all steps.
- [x] **Admin Panel: CLM BO Discovery & Field Picker** *(spec 013)*
  - [x] **CLM-credential admin login:** Admin email allow-list (`adminEmails` in `config.yml`). On the login page, entering a listed email reveals a "Sign in as Admin" toggle; submitting with the toggle ON authenticates against CLM and grants the `ADMIN` role. The hardcoded `admin/admin` shortcut is removed.
  - [x] **BO type discovery:** "Load from CLM" button in the Admin Panel fetches all BO types from CLM, enriches them with display names and usage types via parallel metadata calls, and renders a searchable table. BOs already in config are pre-checked.
  - [x] **Display name override:** Each BO row has an editable display name field; the value is saved as `localizedName` in `config.yml` and shown to operators on the dashboard.
  - [x] **Field picker per BO:** "Edit Fields" opens an inline panel showing all components (collapsible when > 5) and their fields with checkboxes. Pre-populated from the existing `config/columns/<BoType>.csv` if present; otherwise all fields are pre-checked. Apply writes the selection immediately.
  - [x] **Save configuration:** "Save Configuration" writes the checked BO list to `config.yml` via the existing `PUT /api/config` endpoint. Column files are written per-BO on Apply and are never deleted when a BO is unchecked.
- [x] **Credential Security: Encrypted Password Storage** *(spec 014)*
- [x] **Attachment Export and PDF Conversion** *(spec 015)*
  - [x] **Attachment download:** After CSV export, the tool downloads all attachments per record as a ZIP archive, extracts them, and deletes the archive. Records with no attachments are silently skipped.
  - [x] **PDF conversion (admin-controlled):** New `convertAttachmentsToPdf` config flag (default off). When on, JODConverter + LibreOffice converts each extracted file to PDF named `{trackingId}-{docName}-{version}.pdf`. Conversion failure saves the original file alongside a companion `.txt` explanation.
  - [x] **Empty-file suppression (admin-controlled):** New `includeEmptyExportFiles` config flag (default on). When off, header-only CSVs are deleted before packaging.
  - [x] **Admin Panel toggles:** Both settings exposed as toggles in the Output & Files section of the Admin Panel (admin-only).
  - [x] **Archive packaging:** `ZipPackager` already splits at 200 MB; no changes required — attachment files are packaged automatically alongside CSVs.
  - [x] **`CredentialEncryptor`:** AES-256-GCM encrypt/decrypt utility using `javax.crypto` only (no new dependencies). Token format `ENC(base64(IV+ciphertext+GCM-tag))`. Master key derived from `CLM_EXTRACT_KEY` environment variable via SHA-256.
  - [x] **Config load decryption:** `ConfigLoader.load()` transparently decrypts `ENC(...)` passwords before use. Fails fast with `ConfigValidationException` if encrypted values are present but `CLM_EXTRACT_KEY` is not set. Warns when plaintext password is found with key present.
  - [x] **Serve-mode startup check:** `WebServer.start()` checks for encrypted passwords on startup and throws `IllegalStateException` if the key is absent, preventing the server from starting in an unusable state.
  - [x] **Admin Panel password security:** `GET /api/config` never sends password values to the browser; sends `serverPasswordIsSet` / `sftp.passwordIsSet` boolean flags instead. `PUT /api/config` encrypts new passwords when the key is set; preserves existing value when the field is left blank.
  - [x] **Backwards compatibility:** Existing plaintext `config.yml` files work without migration. Plaintext passwords trigger a warning log but do not block startup.
