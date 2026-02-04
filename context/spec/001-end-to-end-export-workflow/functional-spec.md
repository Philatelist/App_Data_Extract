# Functional Specification: End-to-End Export Workflow

- **Roadmap Item:** All phases (1-5) -- complete export workflow
- **Status:** Draft
- **Author:** Poe

---

## 1. Overview and Rationale (The "Why")

CLM administrators currently have no repeatable, configurable way to extract structured business object data from the SCLM system. Each extraction requires ad-hoc scripting -- manually handling session tokens, iterating over records, parsing JSON, and flattening data into spreadsheets. This is error-prone, undocumented, and unsustainable for recurring needs like quarterly audits and multi-phase migration projects.

This specification defines the complete end-to-end export workflow: a single CLI command that reads a configuration file, authenticates against the SCLM REST API, processes each configured (or discovered) business object type sequentially, and produces structured CSV output files. The workflow also generates attachment download manifests, writes detailed logs, and manages backup retention automatically.

**Success is measured by:** a single `config.yml` being sufficient to produce a complete, unattended export run with properly formatted CSV files, download lists, logs, and automatic backup cleanup.

---

## 2. Functional Requirements (The "What")

### 2.1 Configuration

- The tool reads a single `config.yml` file specified via `--config` on the command line.
- The configuration must support the following properties:
  - **Server connection:** base URL of the SCLM REST API.
  - **Credentials:** username and password for authentication.
  - **Endpoints file path:** path to the `endpoints.yml` file (default: `inputs/endpoints.yml`).
  - **BO types:** an optional list of BO type names to export. When empty or omitted, the tool discovers all available BO types via the `getBoTypes` endpoint.
  - **Per-BO settings** (optional, per BO entry):
    - `trackingFilter`: inline filter string supporting explicit IDs and numeric ranges (e.g., `"1000-2000, 3050, 4000-4500"`).
    - `filenameTemplate`: overrides the global filename template for this BO.
  - **CSV mode:** one of `per-component` (default), `merged-single`, or `single-only`.
  - **CSV delimiter:** the character used to separate CSV fields (default: `,`).
  - **Filename template:** a global template for export filenames using placeholders (e.g., `{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv`).
  - **Output root folder:** the base directory for all output.
  - **Main export folder name:** the subdirectory name for export CSVs (e.g., `MetaData`).
  - **Batch size:** the number of tracking IDs per bundles API call.
  - **Backup retention days:** the number of days to keep backup directories.
- The tool must validate all required properties at startup and exit with a clear error message if any are missing or invalid.
  - **Acceptance Criteria:**
    - [ ] When `--config` is omitted, the tool prints a usage message and exits with a non-zero code.
    - [ ] When a required property is missing, the tool prints the property name and expected format and exits with a non-zero code.
    - [ ] When `boTypes` is omitted or empty, the tool calls the `getBoTypes` endpoint to discover all available BO types.
    - [ ] When `boTypes` lists specific names, only those BO types are processed.
    - [ ] When `csvMode` is not specified, it defaults to `per-component`.
    - [ ] When `delimiter` is not specified, it defaults to `,`.

### 2.2 Endpoints File Loading

- The tool reads the endpoints definition file at the path specified in config (default: `inputs/endpoints.yml`).
- The file must be consumed in its **original format** -- it must not be modified by the tool.
- The tool must implement an adapter that parses the YAML structure and maps endpoint definitions to the five required operations: `login`, `logout`, `getBoMetaData`, `getTrackingNumbers`, and `bundles`.
- If any required operation cannot be resolved from the file, the tool must exit with a clear error message naming the missing operation.
  - **Acceptance Criteria:**
    - [ ] The tool parses `endpoints.yml` without modifying the file.
    - [ ] All five required operations are resolved from the endpoint definitions.
    - [ ] When a required endpoint is missing from the file, the tool exits with an error message naming the missing endpoint.

### 2.3 Session Management

- Before any operation that requires a session, the tool must ensure it is logged in.
- Login must be performed automatically if no active session exists.
- If the session has expired (detected by an API error), the tool must re-login automatically and retry the failed request.
- At the end of the run, including failure scenarios, the tool must attempt to log out.
- Logout failures must not prevent run completion or cause the tool to exit with a non-zero code.
  - **Acceptance Criteria:**
    - [ ] The tool logs in automatically before the first authenticated API call.
    - [ ] If an API call fails due to an expired session, the tool re-authenticates and retries the call.
    - [ ] At the end of a successful run, the tool logs out.
    - [ ] At the end of a failed run, the tool still attempts to log out.
    - [ ] If logout itself fails, the tool logs a warning but exits normally (does not throw).

### 2.4 Output Directory Structure

- Under the configurable output root folder, the tool creates the following structure per run:

```
<output-root>/
  <main-export-folder>/     (configurable name, e.g., "MetaData/")
  backups/
  logs/
  downloads/
```

- Export CSV files are written to `<main-export-folder>/`.
- Download list CSVs are written to `downloads/`.
- Run logs are written to `logs/`.
- Before starting a new run, the tool copies the current contents of `<main-export-folder>/` into `backups/` with a timestamp.
  - **Acceptance Criteria:**
    - [ ] On first run with an empty output root, the tool creates all four subdirectories.
    - [ ] Export CSVs appear in the configured main export folder.
    - [ ] Downloads CSVs appear in `downloads/`.
    - [ ] The run log appears in `logs/`.
    - [ ] If a previous export exists, its contents are copied to `backups/` before the new run overwrites them.

### 2.5 Backup Retention

- Backup retention is configured in days.
- After a successful run, the tool scans the `backups/` directory and deletes any backup subdirectories older than the configured retention period.
  - **Acceptance Criteria:**
    - [ ] Backups older than the configured number of days are deleted after a successful run.
    - [ ] Backups within the retention period are preserved.
    - [ ] If retention is set to 0, all previous backups are deleted.

### 2.6 Per-BO Processing Pipeline

BOs must be processed strictly sequentially. For each BO, the following steps execute in order:

#### 2.6.1 Metadata Retrieval

- Fetch BO metadata via the `getBoMetaData` endpoint, passing the BO type name.
- Parse the response to extract components and their fields: internal name, display name, data type, cardinality (`single` or `multiple`), and instance path.
- Keep metadata in memory only for the current BO.
  - **Acceptance Criteria:**
    - [ ] The tool calls `getBoMetaData` with the correct BO type header.
    - [ ] The parsed metadata includes all components and fields with their properties.
    - [ ] Metadata from the previous BO is not present in memory when processing the next BO.

#### 2.6.2 Tracking Numbers Retrieval

- Fetch tracking numbers via the `getTrackingNumbers` endpoint, passing the BO type name.
- Keep tracking numbers in memory only for the current BO.
- If a `trackingFilter` is configured for this BO, filter the list:
  - Explicit IDs: include only the specified IDs (e.g., `3050`).
  - Numeric ranges: include IDs within the range, inclusive (e.g., `1000-2000` includes 1000 and 2000).
  - Combined: `"1000-2000, 3050, 4000-4500"` includes all three segments.
  - IDs not present in the API response are silently ignored (no error).
  - **Acceptance Criteria:**
    - [ ] The tool calls `getTrackingNumbers` with the correct BO type header.
    - [ ] When no filter is configured, all returned tracking numbers are used.
    - [ ] When a filter is configured with explicit IDs, only those IDs are retained.
    - [ ] When a filter is configured with ranges, only IDs within the inclusive range are retained.
    - [ ] Filter values that don't match any returned ID are silently skipped.

#### 2.6.3 Bulk Data Fetch and Incremental Writing

- Split the tracking numbers list into batches of the configured batch size.
- For each batch, call the `bundles` endpoint with:
  - `trackingIds`: the batch of tracking IDs (as numbers).
  - `fieldPaths`: the list of field instance paths derived from metadata.
- Parse each response to extract records with their component fields.
- **Incremental writing:** Data must be written to output CSV files immediately after each batch response is received. The tool must not accumulate the full dataset of a BO in memory. Only the minimum data required for the current batch may be held in memory at any time.
  - **Acceptance Criteria:**
    - [ ] Tracking IDs are split into batches of the configured size.
    - [ ] The last batch may be smaller than the configured size.
    - [ ] Each batch call includes the correct tracking IDs and field paths.
    - [ ] CSV rows for each batch are written (appended) to the output file immediately after the batch response is parsed.
    - [ ] At no point does the tool hold the full BO dataset in memory -- only the current batch's records.
    - [ ] The CSV header row is written once before the first batch; subsequent batches append data rows only.

#### 2.6.4 CSV Generation

- **Default mode (`per-component`):** Generate one CSV file per component. Each component's CSV contains only that component's fields.
- **`merged-single` mode:** Merge all single-cardinality components into one CSV file. Multi-cardinality components each get their own separate CSV file.
- **`single-only` mode:** Export only single-cardinality components (merged into one file). Multi-cardinality components are skipped entirely.
- **Tracking # column:** Every export CSV file must include a `Tracking #` column containing the record's tracking number. This column appears as the first column in every file.
- **Column header format:** `ComponentDisplayName.ParameterDisplayName` (except the `Tracking #` column which uses the literal header `Tracking #`).
- **Filename:** Uses the filename template with placeholders resolved: `{BO}`, `{Component}`, `{DDMMYYYY}`, `{HHMMSS}`.
- **Multi-cardinality handling (per-component mode):** Each row in a multi-cardinality component becomes a separate CSV row. The tracking number ties each row back to the parent record.
  - **Acceptance Criteria:**
    - [ ] In `per-component` mode, one CSV file is generated per component.
    - [ ] In `merged-single` mode, all single-cardinality components are merged into one CSV; multi-cardinality components each get their own file.
    - [ ] In `single-only` mode, only single-cardinality components are exported in one merged file.
    - [ ] Every export CSV includes `Tracking #` as the first column.
    - [ ] Remaining column headers follow the `ComponentDisplayName.ParameterDisplayName` format.
    - [ ] The configured delimiter is used between fields.
    - [ ] Filenames match the resolved filename template.
    - [ ] Multi-cardinality rows include the tracking number to associate them with the parent record.

#### 2.6.5 Parameters Order File (Optional)

- If a file exists at `config/columns/{BoType}.csv`, the tool uses it to control column output.
- The file is plain text with one field path per line (e.g., `ReqSummary/trackingNumber`).
- When present:
  - Export only the parameters listed in the file.
  - Preserve the exact order defined in the file.
  - Omit all parameters not listed.
- When absent, all fields from metadata are exported in the order returned by the API.
  - **Acceptance Criteria:**
    - [ ] When `config/columns/{BoType}.csv` exists, only listed fields are exported.
    - [ ] Fields appear in the exact order specified in the file.
    - [ ] Unlisted fields are omitted from the CSV.
    - [ ] When the file does not exist, all fields are exported in API order.
    - [ ] A field path in the file that does not match any metadata field is silently ignored.

#### 2.6.6 Parameter Name Overrides (Optional)

- If a file exists at `config/overrides/{BoType}.csv`, the tool uses it to rename display names in CSV headers.
- The file is CSV with columns: `fieldPath,newDisplayName`.
- When present, any matching `ParameterDisplayName` in the header is replaced with `newDisplayName`.
- When absent, original display names from metadata are used.
  - **Acceptance Criteria:**
    - [ ] When `config/overrides/{BoType}.csv` exists, matching field display names are replaced in headers.
    - [ ] Non-matching fields keep their original display names.
    - [ ] When the file does not exist, all original display names are used.

#### 2.6.7 Downloads CSV

- For each BO, generate a downloads CSV in the `downloads/` directory.
- The CSV contains **one column only**: the attachment file path.
- Data comes from the Attachments component (multi-cardinality component with file path fields) for that BO.
- If the BO has no attachments component, an **empty downloads CSV** is still generated for that BO (a file with no data rows, or an empty file).
  - **Acceptance Criteria:**
    - [ ] A downloads CSV is generated for every BO, regardless of whether it has an attachments component.
    - [ ] For BOs with attachments, the CSV contains one row per attachment file path.
    - [ ] The CSV contains exactly one column with attachment file paths.
    - [ ] For BOs without an attachments component, the downloads CSV is empty (no data rows).

#### 2.6.8 State Cleanup

- After finishing all processing for a BO, clear all temporary in-memory state: metadata, tracking numbers, batch buffers, and collected records.
- This must happen before processing the next BO.
  - **Acceptance Criteria:**
    - [ ] After processing BO "A", no data from BO "A" is present when BO "B" processing starts.
    - [ ] Memory usage does not accumulate across BO processing.

### 2.7 Logging

- A run log file is written to the `logs/` directory for each run.
- For each REST API call, the log must capture:
  - Start time
  - End time
  - Elapsed time
  - BO name (if applicable)
  - Endpoint / action name
  - Success or failure status
  - Error details (if applicable)
- Key milestones are also logged to the console (stdout) so the admin can monitor a running export.
  - **Acceptance Criteria:**
    - [ ] A log file is created in `logs/` for each run.
    - [ ] Every REST API call is logged with all seven fields (start, end, elapsed, BO, endpoint, status, error).
    - [ ] Progress milestones (login, BO start/end, record counts, logout) appear on stdout.
    - [ ] Errors include sufficient detail to diagnose the failure.

---

## 3. Scope and Boundaries

### In-Scope

- YAML configuration parsing and validation with all properties described above.
- Endpoints file adapter for `inputs/endpoints.yml` (original format, read-only).
- Session management with auto-login and auto-re-login on session expiry.
- BO discovery via `getBoTypes` endpoint when no explicit list is configured.
- Sequential BO processing with full state cleanup between BOs.
- Metadata retrieval, tracking number retrieval (with optional filtering), and batched bundle fetching.
- Incremental batch-by-batch CSV writing (no full-BO in-memory accumulation).
- CSV generation in three modes: `per-component`, `merged-single`, `single-only`.
- `Tracking #` column in every export CSV.
- Configurable CSV delimiter and filename template (global with per-BO override).
- Convention-based parameters-order files (`config/columns/{BoType}.csv`).
- Convention-based parameter name override files (`config/overrides/{BoType}.csv`).
- Downloads CSV generation (one column, attachment file paths only) for every BO (empty file if no attachments).
- Output directory structure with configurable export folder name.
- Backup retention in days with automatic cleanup.
- Per-run log files with per-REST-call timing and status.
- Console output of progress milestones.

### Out-of-Scope

- Downloading actual attachment/document binary files (v1 produces the list only).
- GUI or web interface.
- Incremental/delta exports (every run is a full extract).
- Writing to databases or non-CSV formats (JSON, Excel, etc.).
- Parallel/concurrent API requests.
- Scheduling or cron integration (users schedule externally).
- Support for non-SCLM REST API systems.
- User management, role export, or other non-BO-type data.
- Automatic field mapping or transformation beyond flattening and renaming.
