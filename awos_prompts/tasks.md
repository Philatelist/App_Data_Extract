# tasks.md
**AWOS + Java CLM Data Extract — Canonical Build Tasks**

> This file is the **single canonical build recipe** to build the project from zero.
> Execute slices **in order**. No legacy paths, no historical variants.

---

## Slice 01 — Project Skeleton & CLI
**Goal:** a runnable shaded JAR with a CLI entry point.

- Create Maven project (Java 17).
- Dependencies: SnakeYAML, Jackson Databind, OpenCSV, Log4j2, JUnit 5.
- Build a shaded/fat JAR (`maven-shade-plugin`) with `com.clmextract.App` as Main-Class.
- CLI:
  - `--config <path>` required (print usage + exit code 1 if missing).
  - Optional flags (future-proof): `--bo`, `--trackingIds`, `--offline`.
- Exit code: `0` success, non-zero on failure.

**Verification**
- `mvn -q clean package`
- `java -jar target/*.jar` prints usage and exits `1`
- `java -jar target/*.jar --config config.yml` loads config and exits `0`

---

## Slice 02 — Configuration Loading & Validation
**Goal:** load `config.yml`, validate required fields, apply defaults.

- Create config POJOs: `AppConfig`, `BoTypeConfig` with all required settings:
  - server/baseUrl, credentials/auth
  - boTypes list / discovery mode
  - csvMode (`per-component`, `merged-single`, `single-only`)
  - delimiter
  - filenameTemplate, downloadsFilenameTemplate
  - outputRoot/exportFolderName
  - batchSize
  - backupRetentionDays
  - offlineMode
  - retry (attempts, baseDelayMs, timeouts)
- Implement `ConfigLoader`:
  - parse YAML
  - apply defaults
  - validate required fields
  - throw `ConfigValidationException` with field name on failure
- Print a short validated-config summary to logs/stdout.

**Verification**
- Valid config loads
- Missing required fields fails with a clear message
- Defaults applied as expected

---

## Slice 03 — Endpoints Registry
**Goal:** load `endpoints.yml` and resolve required operations.

- Create endpoint definition POJOs reflecting YAML structure.
- Implement `EndpointRegistry` that loads and resolves operations:
  - LOGIN
  - LOGOUT
  - GET_BO_METADATA
  - GET_TRACKING_NUMBERS
  - BUNDLES
  - (optional) GET_BO_TYPES
- Throw `EndpointResolutionException` if required op missing.

**Verification**
- Registry loads and prints resolved endpoints summary
- Missing endpoint yields actionable error

---

## Slice 04 — HTTP Executor + Retry + REST-call logging
**Goal:** generic HTTP execution from endpoint definitions.

- Implement `RetryPolicy` (exponential backoff, configurable).
- Implement `RequestExecutor` using `java.net.http.HttpClient`:
  - build request from endpoint definition
  - inject headers/body
  - map non-2xx to `ApiException`
  - map 401/403 to `SessionExpiredException`
- Implement `RestCallLogger` (start/end/elapsed, endpoint, BO, status, error).

**Verification**
- Retry triggers on IO/5xx
- No retry on 4xx (except session relogin flow)
- Logs show per-call timing

---

## Slice 05 — Session Manager (login/logout lifecycle)
**Goal:** robust session handling with relogin-on-expiry.

- `SessionManager`:
  - `ensureLoggedIn()`, `login()`, `logout()`, `getSessionId()`, `invalidateSession()`
- Integrate with `RequestExecutor`:
  - on `SessionExpiredException`: invalidate + login + retry original request once.

**Verification**
- One login per run
- Relogin works on forced 401/403
- Logout executes in finally

---

## Slice 06 — Output Structure + Backup/Retention
**Goal:** deterministic output directories and safe reruns.

- Directories:
  - `<outputRoot>/<exportFolderName>/`
  - `<outputRoot>/backups/`
  - `<outputRoot>/logs/`
  - `<outputRoot>/downloads/`
- `BackupManager`:
  - backup existing export folder into `backups/<timestamp>/`
  - clear export folder
  - enforce retention by days

**Verification**
- Second run creates backup, export folder refreshed
- Retention deletes old backups as configured

---

## Slice 07 — BO Metadata parsing (real API shapes)
**Goal:** parse metadata into a usable domain model.

- Support real metadata shape: **flat node array**, not nested JSON.
- Rebuild hierarchy using:
  - `id`, `parentId`, `listType`
- DTO → domain separation (domain models are annotation-free).

**Verification**
- Components, cardinality, internal names, display names parsed correctly
- Instance paths preserved

---

## Slice 08 — Tracking IDs retrieval + ordering guarantees
**Goal:** correct record identity and stable ordering.

- Fetch tracking IDs list for BO.
- Thread **request trackingIds[]** through the pipeline.
- Bundles parsing assigns trackingId **positionally**:
  - record[i].trackingId = requestTrackingIds[i]
- Fallback to response `trackingNumber` only if request IDs absent/mismatched.
- Guarantee:
  - **row order == request trackingIds order**

**Verification**
- Known trackingIds produce CSV rows in same order

---

## Slice 09 — Bundles fetch + parsing (real API shapes)
**Goal:** parse bundles array-of-arrays reliably.

- Bundles API returns **array of arrays**.
- Components/fields identified via InstancePath.
- `InstancePathUtil`:
  - strip `MCPDef:/` or `MCP:/`
  - strip instance suffix after `|`
  - normalize to `Module/Component/Parameter`

**Verification**
- Single-cardinality fields map
- Multi-cardinality rows list-of-maps
- Record ordering preserved

---

## Slice 10 — Column order (in-repo parameter selection)
**Goal:** control which parameters are exported and their order.

### 10.1 Column order file
- Location (in-repo):
  - `inputs/overrides/bo-parameters/{BO}.csv`
- Format:
  - one field path per line, no header, no delimiter
  - `Module/Component/Parameter`
- Behavior:
  - When file exists: export only listed parameters, in listed order
  - Exclude components that have no listed parameters
  - **Retain order-file paths not found in metadata** as columns (values from bundles if present, else empty)
  - Ignore empty lines/whitespace

### 10.2 Matching rules
- Match by **internal names** (not display names), case-sensitive.

**Verification**
- With order file: only those columns appear, in exact order
- Unlisted components absent
- Unknown-in-metadata paths still appear as columns

---

## Slice 11 — Display name overrides (global)
**Goal:** business-friendly headers without code changes.

### 11.1 Global override file
- Location:
  - `inputs/overrides/parameter-displaynames.csv`
- Format (semicolon-delimited, header row required):
  - `Component;Parameter;DisplayName;`
- Behavior:
  - Override applies only to exported params
  - Keys are internal names (component + parameter)
  - Malformed rows skipped silently

### 11.2 Header precedence
1. `parameter-displaynames.csv` override
2. metadata `displayName`
3. bundles `DisplayName`
4. internal parameter name

### 11.3 Header format
- `ComponentDisplayName.ResolvedParameterDisplayName`

**Verification**
- Overrides apply and win over metadata
- Non-overridden fields use metadata/bundles/internal fallback

---

## Slice 12 — Filename resolution
**Goal:** correct, safe, deterministic filenames.

- Use `filenameTemplate` and `downloadsFilenameTemplate`.
- `{BO}` uses **boName internal**, not usageType.
- `{Component}` uses component display name but must be sanitized.
- Sanitize rules:
  - spaces → `_`
  - non `[A-Za-z0-9._-]` → `_`
  - collapse multiple `_`
  - trim leading/trailing `_` and `.`
- Timestamp captured once per BO run (`{DDMMYYYY}`, `{HHMMSS}`).

**Verification**
- Filenames stable, safe, match template

---

## Slice 13 — CSV writers (core export)
**Goal:** streaming export, per-component default.

### 13.1 CSV policy (no quotes)
- All CSV writers must use:
  - `withQuoteChar(ICSVWriter.NO_QUOTE_CHARACTER)`
- Applies to headers and data.

### 13.2 Tracking header
- First column header must be:
  - `Summary.Tracking #`

### 13.3 Writers
- `PerComponentCsvWriter`:
  - one CSV per component
  - write header once
  - stream rows batch-by-batch
- `MergedSingleCsvWriter`:
  - merge all single-cardinality components into one file
  - multi-cardinality components separate files
- `SingleOnlyCsvWriter`:
  - merge single-cardinality only
  - skip multi-cardinality entirely

**Verification**
- Headers have **no quotes**
- First column header is `Summary.Tracking #`
- Row order preserved

---

## Slice 14 — Downloads CSV (attachments)
**Goal:** produce downloads list with server file paths.

- Output: `downloads/NAFBO_AttachmentsToDownload_<date>_<time>.csv` (template-driven)
- CSV: **one column, no header**
- Source components (only these):
  - `ReqAttachment`
  - `ReqContractAttachment`
- Source field (only this):
  - `serverFileName`
- No fallback to other fields (avoid `version -> "1"` issue).
- Writer uses **NO_QUOTE_CHARACTER**.

**Verification**
- File contains actual server file paths (not `1`)
- One value per line

---

## Slice 15 — Offline mode (real fixtures)
**Goal:** reproducible runs without server.

- Offline mode uses real-format JSON fixtures:
  - metadata sample
  - bundles sample
- Pipeline identical to online (no special branches beyond datasource swap).

**Verification**
- Offline run generates same CSV structure

---

## Slice 16 — End-to-end verification (“golden run”)
**Goal:** confidence that the whole system works.

- Run with:
  - 1 BO (e.g., NAFBO)
  - known trackingIds subset
  - attachments present
- Validate:
  - filenames correct
  - headers correct (no quotes, `Summary.Tracking #`)
  - column selection + overrides
  - downloads list contains server file paths

**Verification**
- `mvn clean package` passes
- offline and online runs produce consistent structure

---

## Slice 17 — Logging, errors, and RCA-ready diagnostics
**Goal:** make failures explainable.

- Clear exception types
- Structured logs per endpoint call
- Include BO name, endpoint, latency, status, retry count

**Verification**
- A simulated failure yields actionable logs

---

## Slice 18 — Final readiness checklist
- Clean build
- Cold start success
- Deterministic outputs
- Docs updated (README / INDEX)

**Verification**
- New machine / clean clone can build and run

---

**END**
