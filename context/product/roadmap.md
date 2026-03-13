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
