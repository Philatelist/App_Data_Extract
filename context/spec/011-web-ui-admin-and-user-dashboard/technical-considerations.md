# Technical Specification: Web UI — Admin Panel & User Dashboard

- **Functional Specification:** `context/spec/011-web-ui-admin-and-user-dashboard/functional-spec.md`
- **Status:** Approved
- **Author(s):** Alex

---

## 1. High-Level Technical Approach

The tool gains a **server mode** alongside the existing CLI mode. When launched with `--serve`, `App.java` starts a Javalin embedded HTTP server instead of running a single-shot export. The server:

1. Serves static HTML/JS/CSS pages from `src/main/resources/static/` inside the fat JAR
2. Exposes a REST API at `/api/` for authentication, config management, run control, and state queries
3. Runs exports asynchronously in a single background thread, reporting step-by-step status via polling
4. Persists UI state (last-run dates per BO, schedule, current run status) to a `ui-state.json` file alongside `config.yml`

All existing CLI behavior is unaffected — `--serve` is a new additive branch in `App.java`.

**New packages introduced:** `web`, `web.api`, `web.state`, `web.run`, `web.scheduler`, `sftp`, `packaging`

**Significant new capabilities not in the current codebase:** binary file download from CLM (PDFs + attachments), ZIP packaging with 200MB splitting, SFTP upload.

---

## 2. Proposed Solution & Implementation Plan

### 2.1 Architecture Changes

**New launch command:**
```
java -jar clm-extract.jar --config config.yml --serve [--port 8082]
```
Port defaults to **8082** if omitted.

`App.java` detects `--serve` in args and branches to `WebServer.start(configPath, port)`. The existing export path is unchanged.

**New Java packages and their responsibilities:**

| Package | Key Classes | Responsibility |
|---|---|---|
| `com.clmextract.web` | `WebServer` | Javalin init, route registration, static file serving, auth `before` filter |
| `com.clmextract.web.api` | `AuthController`, `ConfigController`, `RunController`, `ScheduleController` | REST endpoint handlers |
| `com.clmextract.web.state` | `UiState`, `StateStore` | Jackson model for `ui-state.json`; atomic reads/writes |
| `com.clmextract.web.run` | `RunExecutor`, `RunStatus` | Async export wrapper; per-step status tracking |
| `com.clmextract.web.scheduler` | `ExportScheduler` | `ScheduledExecutorService`-based auto-run scheduling |
| `com.clmextract.sftp` | `SftpUploader` | JSch SFTP connection + file upload |
| `com.clmextract.packaging` | `ZipPackager` | ZIP creation with streaming 200MB part splitting |

---

### 2.2 Data Model / State

**`AppConfig` extension** — new `SftpConfig` inner class added as field `sftp`:

| Field | Type | Default | Config key |
|---|---|---|---|
| host | String | — | `sftp.host` |
| port | int | 22 | `sftp.port` |
| username | String | — | `sftp.username` |
| password | String | — | `sftp.password` |

**`ui-state.json`** — lives in the same directory as `config.yml`, written/read by `StateStore`:

| Key path | Type | Purpose |
|---|---|---|
| `boLastRun.<boName>` | ISO-8601 datetime or null | Last successful run timestamp per BO |
| `schedule.frequency` | `DAILY` \| `WEEKLY` \| `MONTHLY` | Selected frequency |
| `schedule.enabled` | boolean | Whether auto-schedule is active |
| `schedule.timeOfDay` | `HH:mm` string | Time of day for auto-runs |
| `schedule.nextRunAt` | ISO-8601 datetime | Computed next scheduled run time |
| `sftpTargetPath` | String | Last-used SFTP target folder path |
| `currentRun.runId` | String | Timestamp-based ID (`YYYYMMDD_HHmmss`) |
| `currentRun.startedAt` | ISO-8601 datetime | |
| `currentRun.completedAt` | ISO-8601 datetime or null | Null while running |
| `currentRun.selectedBos` | String[] | BO names selected for this run |
| `currentRun.steps.<step>` | `PENDING` \| `IN_PROGRESS` \| `SUCCESS` \| `FAILED` | Per-step status |

**Run steps (in order):** `EXPORT_CSV`, `EXPORT_PDF`, `EXPORT_ATTACHMENTS`, `PACKAGING`, `SFTP_UPLOAD`

---

### 2.3 API Contracts

All endpoints under `/api/`. Auth state is stored in Javalin's HTTP session (in-memory, backed by Jetty session store). Session carries: `role` (`ADMIN` | `OPERATOR`) and `clmSessionId` (operator only).

A `before` filter on all `/api/*` except `/api/auth/login` checks for an active session and returns 401 if absent. Operator-only routes return 403 if role is ADMIN (admin has no access to run controls), and admin-only routes return 403 if role is OPERATOR.

**Authentication**

| Method | Path | Auth | Request body | Response |
|---|---|---|---|---|
| POST | `/api/auth/login` | None | `{username, password}` | `{role}` + sets session cookie |
| POST | `/api/auth/logout` | Any | — | 200 |

Login logic: if `username=admin` and `password=admin` → set role ADMIN. Otherwise call CLM login endpoint via existing `SessionManager` — if successful, set role OPERATOR and store `clmSessionId` in session.

**Config — Admin only**

| Method | Path | Request body | Response |
|---|---|---|---|
| GET | `/api/config` | — | Full `AppConfig` as JSON |
| PUT | `/api/config` | Updated `AppConfig` JSON | 200 or `{errors: [{field, message}]}` |

PUT validates all fields (required, types, value ranges) then writes `config.yml` using SnakeYAML dump. Does not replace the in-memory config of a currently running export.

**Run & BOs — Operator only**

| Method | Path | Request body | Response |
|---|---|---|---|
| GET | `/api/bos` | — | `[{name, lastRunDate}]` from state |
| POST | `/api/run/start` | `{boNames[], overrideDate, sftpTargetPath}` | `{runId}` or 409 if already running |
| GET | `/api/run/status` | — | `currentRun` object from state |

**Schedule — Operator only**

| Method | Path | Request body | Response |
|---|---|---|---|
| GET | `/api/schedule` | — | `schedule` object from state |
| PUT | `/api/schedule` | `{frequency, enabled, timeOfDay}` | 200; reconfigures `ExportScheduler` |

---

### 2.4 Component Breakdown

**`WebServer`**
- Initializes `Javalin.create()` with static file serving from classpath `/static`
- Registers all routes and the before-filter
- Holds singleton references: `StateStore`, `RunExecutor`, `ExportScheduler`, config file path
- On startup: initializes `StateStore` (creates `ui-state.json` if missing), starts `ExportScheduler`

**`RunExecutor`**
- Wraps the full export pipeline as an async `Runnable` submitted to a `SingleThreadExecutor` (ensures only one run at a time)
- Pipeline steps in sequence:
  1. Load fresh `AppConfig` from `config.yml` (picks up any changes saved since last run)
  2. Authenticate CLM session (reuse operator's `clmSessionId` from run request, or re-login if expired)
  3. For each selected BO: run metadata → tracking numbers → bundles → CSV (reuses existing `ExportOrchestrator` per BO) — updates `EXPORT_CSV` step
  4. For each selected BO: download signed contract PDFs from CLM attachment endpoints — updates `EXPORT_PDF` step
  5. For each selected BO: download other attachments — updates `EXPORT_ATTACHMENTS` step
  6. `ZipPackager.pack(runOutputDir)` — updates `PACKAGING` step
  7. `SftpUploader.upload(zipParts, sftpTargetPath)` — updates `SFTP_UPLOAD` step
- On each step completion (SUCCESS or FAILED): calls `StateStore.updateStep(runId, step, status)`
- On full completion: updates `boLastRun` for each BO that succeeded in CSV export

**`StateStore`**
- Reads/writes `ui-state.json` via `ObjectMapper` (Jackson, already in project)
- Thread-safe via `ReadWriteLock` (web API thread + background run thread both access)
- Atomic write: serialize to `ui-state.json.tmp`, then `Files.move(ATOMIC_MOVE)` to `ui-state.json`
- On startup: if `ui-state.json` is missing or malformed JSON, initializes a fresh empty state

**`ExportScheduler`**
- Uses `ScheduledExecutorService` with a single thread
- On startup: reads `schedule` from state; if `enabled=true` and `nextRunAt` is in the past, fires immediately
- After each run: computes next `nextRunAt` based on `frequency` and `timeOfDay`, persists to state, reschedules
- On `PUT /api/schedule`: cancels current scheduled task, reschedules with new parameters (or cancels entirely if `enabled=false`)

**`ZipPackager`**
- Streams all files from the run output directory into a `ZipOutputStream` writing to a new ZIP file
- Tracks bytes written; when approaching 200MB boundary, closes current part and opens next part file
- Part naming: `<YYYYMMDD><HH24MMSS>.zip.001`, `.zip.002`, etc. (single part still named `.zip.001` for consistency)
- Returns list of part file paths for `SftpUploader`

**`SftpUploader`**
- Opens JSch `ChannelSftp` to `sftp.host:sftp.port` with `sftp.username` / `sftp.password`
- Ensures target directory exists on SFTP server (creates it if absent)
- Uploads each ZIP part file sequentially using `put()` in overwrite mode
- Closes channel in finally block; any exception marks `SFTP_UPLOAD=FAILED` with error message in state

**`ConfigController` (write path)**
- Deserializes incoming JSON to `AppConfig` via Jackson
- Validates field-by-field (required: `server.url`, `endpointsFile`; integer ≥ 1: `batchSize`; single char: `delimiter`; etc.)
- On success: serializes `AppConfig` back to YAML using SnakeYAML and writes `config.yml`

---

### 2.5 Frontend Structure

All files served from `src/main/resources/static/` as classpath resources inside the fat JAR:

| File | Purpose |
|---|---|
| `index.html` | Login form; POSTs `/api/auth/login`, redirects to `admin.html` or `dashboard.html` based on `role` |
| `admin.html` | Admin panel; renders config form sections dynamically from JSON |
| `dashboard.html` | Operator dashboard; BO checkboxes, date/schedule controls, start button, status panel |
| `js/auth.js` | Shared: login/logout fetch, 401 redirect-to-login interceptor |
| `js/admin.js` | Config fetch, dynamic form render, field validation, save |
| `js/dashboard.js` | BO list fetch, start export, 2-second polling of `/api/run/status`, DOM status indicator updates |
| `css/style.css` | Shared styles (status indicator colors: green, red, grey, spinner) |

The status panel polls `GET /api/run/status` every 2 seconds while `currentRun.completedAt` is null. On completion, polling stops.

---

### 2.6 New Maven Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| `io.javalin:javalin` | 6.x | Embedded HTTP server + routing |
| `com.github.mwiede:jsch` | 0.2.x | SFTP client (maintained JSch fork) |
| `org.apache.logging.log4j:log4j-slf4j2-impl` | 2.23.1 (matches existing) | Bridges Javalin's SLF4J calls into existing Log4j2 |

**Maven Shade plugin update**: add `ServicesResourceTransformer` alongside the existing `ManifestResourceTransformer` to correctly merge `META-INF/services/` entries required by Javalin/Jetty.

---

## 3. Impact and Risk Analysis

**System Dependencies:**
- `App.java` — modified to detect `--serve` flag; all existing CLI logic unchanged
- `AppConfig` + `ConfigLoader` — `SftpConfig` inner class added; new `ConfigWriter` write method added; existing `load()` unchanged
- `ExportOrchestrator` — reused as-is inside `RunExecutor` per BO; no modifications
- `SessionManager` — reused to validate CLM credentials at operator login

**Potential Risks & Mitigations:**

| Risk | Mitigation |
|---|---|
| Two concurrent run requests | `RunExecutor` uses a `SingleThreadExecutor`; `POST /api/run/start` returns 409 if `currentRun.completedAt` is null |
| Admin saves config while export is running | `ConfigController` writes to disk; the in-memory `AppConfig` already passed to `RunExecutor` is not replaced. Next run picks up new config. |
| CLM session expires during a long export | Existing `SessionReLoginHandler` handles re-login transparently; operator's `clmSessionId` is refreshed automatically |
| Binary file download (PDF/attachments) is new | Requires calling CLM attachment download endpoints not currently used. The CLM endpoint pattern must be confirmed against `endpoints.yml`. |
| Delta filtering (by modification date) not in current orchestrator | `TrackingNumberFetcher` currently fetches all IDs. Date-range filtering will need to be added either as an API query parameter (if CLM supports it) or as a post-fetch filter step. This is a non-trivial addition. |
| `ui-state.json` corruption on crash | Atomic write via temp-file + `ATOMIC_MOVE`. On startup: malformed JSON → fresh empty state (logs warning) |
| Large ZIP exhausts disk space | ZIP streaming avoids loading all data into memory, but disk must have capacity for the full export + ZIP. Document minimum disk requirement. |
| JSch strict host checking fails first SFTP connection | Default `StrictHostKeyChecking=no`. Add optional `sftp.strictHostChecking` config key (default false). Document security trade-off. |
| Javalin META-INF/services conflict in fat JAR | Add `ServicesResourceTransformer` to maven-shade-plugin |

---

## 4. Testing Strategy

**Unit tests (JUnit 5, existing framework):**
- `StateStoreTest` — JSON round-trip, atomic write, malformed-JSON recovery → fresh state
- `ZipPackagerTest` — split at exactly 200MB boundary, correct part file naming, single file produces one part
- `RunStatusTest` — invalid step transitions rejected, completed run state is immutable
- `ConfigControllerValidationTest` — required field missing → correct error, invalid delimiter (multi-char) → field-level error, valid payload → round-trip to YAML and back
- `AuthControllerTest` — `admin/admin` → role ADMIN, CLM 401 response → login error returned, no session created

**Integration tests (Javalin test tooling: `io.javalin:javalin-testtools`):**
- Login → GET config → PUT config (mutated) → GET config → verify values persisted
- Login as operator → POST run/start (offline mode) → poll run/status until `completedAt` not null → verify `ui-state.json` updated with new `boLastRun`

**Manual browser testing:**
- Admin panel: all config sections render, conditional fields show/hide, save updates `config.yml`
- Operator dashboard: BO list with last-run dates, manual export trigger, live status panel updates, SFTP upload result shown
