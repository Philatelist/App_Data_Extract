# System Architecture Overview: CLM Data Extract

---

## 1. Application & Technology Stack

- **Runtime:** Java 17 (LTS)
- **Build Tool:** Apache Maven with maven-shade-plugin for single runnable JAR packaging
- **CLI Argument Parsing:** Manual `args[]` parsing (`--config` flag; `--serve` flag starts the web server)
- **Web Server:** Javalin 6 -- embedded HTTP server serving the Admin Panel + Operator Dashboard on port 8080; session-based auth via Javalin's server-side session
- **Frontend:** Vanilla HTML/JS/CSS served as classpath static resources -- no build step, no framework
- **YAML Parsing:** SnakeYAML -- loads `config.yml` and `endpoints.yml` into Java maps/objects
- **JSON Parsing:** Jackson Databind -- deserializes API responses (metadata, bundles) into typed models; serializes API responses from web controllers
- **HTTP Client:** `java.net.http.HttpClient` (built-in since Java 11) -- synchronous requests for CLM API calls
- **CSV Writing:** OpenCSV -- handles quoting, escaping, and header row generation for export files
- **Logging:** Log4j2 -- dual-output logging to a per-run log file and console with configurable levels

---

## 2. Module Structure

The application has two runtime modes — CLI batch mode and web serve mode — sharing the same export pipeline core.

### 2a. Export Pipeline (shared by both modes)

Business Objects are processed strictly sequentially, with full cleanup of in-memory state between each BO type.

- **Configuration Loader (`ConfigLoader`)** -- Parses and validates `config.yml` using SnakeYAML. Produces a strongly-typed `AppConfig` covering server URL, credentials, BO type list, field filters, output directory, batch size, backup retention, admin email allow-list, SFTP settings, and feature flags.
- **Endpoints-File Adapter (`EndpointRegistry`)** -- Parses the user-provided `endpoints.yml`. Maps endpoint definitions to the required CLM operations. Validates completeness at startup.
- **Session Manager** -- Handles the CLM authentication lifecycle: login, session-ID attachment, and logout. Can be bypassed via `injectSessionId()` when a pre-authenticated CLM session (from the web UI) is reused.
- **API Client / Request Executor (`RequestExecutor`)** -- A generic HTTP layer that constructs requests from endpoint definitions using `java.net.http.HttpClient`. Supports absolute-path endpoints (custom CLM REST paths that do not share the base URL prefix). `RetryPolicy` retries transient failures (5xx except 500, which is a CLM application error and is not retried).
- **Export Pipeline (`BoPipeline`)** -- The per-BO pipeline: fetch metadata → resolve tracking IDs (with optional date filter routing) → batch fetch → write CSV. The three-argument overload `execute(BoTypeConfig, Path, DateFilter)` returns `List<Long>` (the collected tracking IDs) so that callers can scope PDF/attachment downloads to the exported record set.
- **Run Executor (`RunExecutor`)** -- Orchestrates a full export run in a background thread. Accumulates tracking IDs from the CSV step and passes them directly to the PDF and attachment download steps, ensuring filtered runs do not download outside the filter.
- **Metadata Model** -- Parses `/BOMetaData` responses into `BoMetadata` / `ComponentMetadata` / `FieldMetadata` objects.
- **Column Resolver (`ColumnResolver`)** -- Determines which fields to export per BO type. Reads an optional `config/columns/<BoType>.csv` (one field instance path per line); if absent, all fields from metadata are included.
- **CSV Writers** -- `CsvExportWriter` (per-component, merged-single, or single-only mode), `SummaryCsvWriter`, `ParentCsvWriter`, `DownloadsCsvWriter`, `ManifestCsvWriter`.
- **Backups Manager** -- Creates timestamped output directories per run and enforces retention by deleting the oldest run directories beyond the configured limit.

### 2b. Web Server Layer

- **`WebServer`** -- Entry point for serve mode. Builds the Javalin app, registers the `before` auth filter (role check; `/api/auth/login` and `/api/auth/check-admin` are unauthenticated), wires all route handlers, and starts the server.
- **`AuthController`** -- Handles `POST /api/auth/login` and `POST /api/auth/logout`. Login accepts an optional `asAdmin` flag: if `true` and the submitted email is in `config.adminEmails`, CLM authenticates and the session receives the `ADMIN` role with the CLM session ID stored server-side; otherwise `OPERATOR`. `GET /api/auth/check-admin?email=` returns `{"isAdmin": true/false}` without authentication — used by the login page to show/hide the admin toggle.
- **`ConfigController`** -- `GET /api/config` and `PUT /api/config` (ADMIN only). Reads and writes the full `config.yml`, including `adminEmails` and `boTypes` with `localizedName` overrides.
- **`RunController`** -- `POST /api/run/start`, `GET /api/run/status`, `GET /api/run/history`, `POST /api/run/stop`, `GET /api/schedule`, `PUT /api/schedule`, `DELETE /api/schedule`.
- **`AdminController`** -- ADMIN-only endpoints for live CLM discovery (requires the admin's CLM session stored at login):
  - `GET /api/admin/bo-types` -- calls CLM `getBoTypes()`, then fetches display names and usage types in parallel (5-thread pool, 30 s per-call timeout, fallback to internal name on failure), and merges with the current `config.yml` state for `checked` and `localizedName`.
  - `GET /api/admin/bo-metadata/{boType}` -- calls CLM `getMetadata()` and returns the full component/field structure with `instancePath` stripped of the `MCPDef:/` prefix.
  - `GET /api/admin/columns/{boType}` -- reads `config/columns/<boType>.csv`; returns `{"fieldPaths": null}` if the file does not exist (signals "all fields selected").
  - `PUT /api/admin/columns/{boType}` -- creates `config/columns/` if needed and writes the supplied field paths one per line, overwriting any existing file.
- **`StateStore`** -- Reads/writes `ui-state.json` (run history, current run status, schedule) as a simple JSON file; no database.
- **`ExportScheduler`** -- Background thread that fires automatic runs based on the schedule stored in `ui-state.json`.

---

## 3. Data & File I/O

- **Input Files:**
  - `config.yml` -- user-provided run configuration (SnakeYAML)
  - `endpoints.yml` -- SCLM REST API endpoint definitions (SnakeYAML)
  - Sample JSON files (`boMetaData.sample.json`, `bundles.sample.json`) -- used in offline test mode
- **Output Files (per run, in timestamped directory):**
  - `{BoType}_export.csv` -- flat CSV with all requested field data for one BO type
  - `{BoType}_downloads.csv` -- attachment file path listing for one BO type
  - `run.log` -- timestamped log of the entire run
- **No database or persistent state** -- the tool is stateless between runs

---

## 4. External Services & APIs

- **SCLM REST API** -- the sole external dependency. The tool communicates over HTTP/HTTPS using session-header authentication.
  - `GET /login` -- basic-header auth, returns session ID as plain text
  - `POST /logout` -- session-header auth, ends the session
  - `GET /BOMetaData` -- session-header + boType header, returns JSON component/field structure
  - `GET /trackingNumbers` -- session-header + boType header, returns JSON array of ID strings
  - `POST /bundles` -- session-header auth, JSON body with trackingIds and fieldPaths, returns JSON bundle data
- **No other external services** -- no databases, no cloud APIs, no message queues

---

## 5. Packaging & Distribution

- **Artifact:** Single executable JAR (fat/shaded JAR via maven-shade-plugin)
- **Execution:** `java -jar clm-extract.jar --config config.yml`
- **Java Requirement:** Java 17+ must be installed on the host machine
- **No installer, no container** -- users copy the JAR and config files to any machine with Java 17

---

## 6. Processing Rules & Constraints

- Business Objects must be processed strictly sequentially -- no parallel BO processing.
- Full cleanup of in-memory state (metadata model, tracking IDs, batch buffers) must occur between BOs to prevent data leakage.
- API requests are synchronous and sequential -- no concurrent HTTP calls.
- The tool is a single-run batch process -- it executes, produces output, and exits. No daemon mode.
