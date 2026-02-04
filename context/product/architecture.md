# System Architecture Overview: CLM Data Extract

---

## 1. Application & Technology Stack

- **Runtime:** Java 17 (LTS)
- **Build Tool:** Apache Maven with maven-shade-plugin for single runnable JAR packaging
- **CLI Argument Parsing:** Manual `args[]` parsing (single `--config` flag)
- **YAML Parsing:** SnakeYAML -- loads `config.yml` and `endpoints.yml` into Java maps/objects
- **JSON Parsing:** Jackson Databind -- deserializes API responses (metadata, bundles) into typed models
- **HTTP Client:** `java.net.http.HttpClient` (built-in since Java 11) -- synchronous requests for sequential API calls
- **CSV Writing:** OpenCSV -- handles quoting, escaping, and header row generation for export files
- **Logging:** Log4j2 -- dual-output logging to a per-run log file and console with configurable levels

---

## 2. Module Structure

The application is organized into the following internal modules (Java packages). Business Objects are processed strictly sequentially, with full cleanup of in-memory state between each BO type.

- **Configuration Loader** -- Parses and validates `config.yml` using SnakeYAML. Produces a strongly-typed config object covering server URL, credentials, BO type list, field filters, output directory, batch size, and backup retention count.
- **Endpoints-File Adapter** -- Parses the user-provided `endpoints.yml` in its original format. Maps endpoint definitions to the five required operations: login, logout, getBoMetaData, getTrackingNumbers, and bundles. Validates completeness.
- **Session Manager** -- Handles the authentication lifecycle: calls login to obtain a session ID, attaches it to subsequent requests, and ensures logout runs at shutdown (via try/finally or shutdown hook), even on error.
- **API Client / Request Executor** -- A generic HTTP execution layer that constructs requests from endpoint definitions (method, path, headers, body) using `java.net.http.HttpClient`. Handles response status checking and error reporting.
- **Export Orchestrator** -- The top-level run controller. Iterates over configured BO types sequentially, invoking the metadata-tracking-bundles-CSV pipeline for each. Clears all in-memory state between BO types.
- **Metadata Model** -- Parses `/BOMetaData` responses into a structured representation of components and fields (internal name, display name, data type, cardinality, instance path). Resolves field paths for the bundles request.
- **Batching & Filtering Logic** -- Splits tracking ID lists into configurable batch sizes. Applies include/exclude field filters from config to the metadata-derived field path list.
- **CSV Writer** -- Flattens hierarchical bundle data into tabular rows using OpenCSV. Handles single-cardinality components (one row contribution) and multi-cardinality components (one row per instance). Writes one file per BO type.
- **Downloads List Generator** -- Identifies attachment components during bundle processing and writes a separate CSV per BO type listing tracking number, server file path, and file name.
- **Backups Manager** -- Creates timestamped output directories per run. After a successful run, counts existing directories and deletes the oldest beyond the configured retention limit.
- **Logs Manager** -- Configures Log4j2 programmatically for each run: a file appender writing to the run directory and a console appender for stdout. Logs session events, per-BO progress, record counts, and errors.

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
