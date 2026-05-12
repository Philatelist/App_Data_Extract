# Technical Specification: Wire Real Export Pipeline into Web UI

- **Functional Specification:** `context/spec/012-wire-real-export-pipeline/functional-spec.md`
- **Status:** Completed
- **Author(s):** Alex

---

## 1. High-Level Technical Approach

The EXPORT_CSV step in `RunExecutor.executeRun()` is replaced with a direct call to `BoPipeline` — bypassing `ExportOrchestrator` entirely (which owns its own login/logout/backup lifecycle). The existing `ApiDataSource` with injected CLM session is reused without modification. Date-filtered tracking number retrieval is achieved by adding two new CLM custom endpoints to `endpoints.yml` and two new default methods to the `DataSource` interface. Two boolean flags (`enableZipPackaging`, `enableSftpUpload`) are added to `AppConfig` and the Admin Panel; a new `SKIPPED` run status skips the relevant step gracefully. All output for a web run is written to a per-run timestamped subdirectory under `outputRoot/exportFolderName/`.

**Systems affected:** `AppConfig`, `endpoints.yml`, `EndpointRegistry`, `DataSource` interface, `ApiDataSource`, `TrackingNumberFetcher`, `BoPipeline`, `RunExecutor`, `RunStatus`, `RunController`, `ConfigController`, `admin.html` + `admin.js`, `dashboard.js`, `style.css`.

---

## 2. Proposed Solution & Implementation Plan

### 2.1 — Config: Two new boolean fields

**File:** `src/main/java/com/clmextract/config/AppConfig.java`

| Field | Type | Default | Purpose |
|---|---|---|---|
| `enableZipPackaging` | `boolean` | `true` | When `false`, Packaging step is skipped |
| `enableSftpUpload` | `boolean` | `true` | When `false`, SFTP Upload step is skipped |

- `ConfigLoader` assigns both defaults if missing from YAML.
- `ConfigController.getConfig()` includes both fields in the JSON response.
- `ConfigController.putConfig()` reads and persists both fields. No validation required (boolean).

---

### 2.2 — New CLM custom tracking endpoints

**File:** `inputs/endpoints.yml`

Two new endpoint entries appended under the existing list. Both live under the same `basePath` as all other endpoints (`/services/rest/methods`):

| Entry name | Method | Path | Request headers |
|---|---|---|---|
| `trackingNumbersAfterDate` | GET | `/custom/trackingNumbersAfterDate` | `session_id`, `boType`, `dateTime` |
| `trackingNumbersInFlight` | GET | `/custom/trackingNumbersInFlight` | `session_id`, `boType`, `daysBeforeToday` |

Both return a JSON array of tracking number strings (same shape as the standard `trackingNumbers` endpoint).

**File:** `src/main/java/com/clmextract/endpoint/EndpointRegistry.java`

Two new public constants:
- `GET_TRACKING_NUMBERS_AFTER_DATE` → mapped operation name `"trackingNumbersAfterDate"`
- `GET_TRACKING_NUMBERS_IN_FLIGHT` → mapped operation name `"trackingNumbersInFlight"`

Both added to the required-endpoints validation set.

---

### 2.3 — DataSource: two new default methods

**File:** `src/main/java/com/clmextract/export/DataSource.java`

```
List<Long> getTrackingNumbersAfterDate(String boType, String dateTime)
    default → delegates to getTrackingNumbers(boType)

List<Long> getTrackingNumbersInFlight(String boType, int daysBeforeToday)
    default → delegates to getTrackingNumbers(boType)
```

Defaults ensure `OfflineDataSource` and any test stubs remain unaffected.

**File:** `src/main/java/com/clmextract/export/ApiDataSource.java`

Override both methods. Each calls `TrackingNumberFetcher.fetch()` with the appropriate `EndpointRegistry` constant and additional headers (`dateTime` or `daysBeforeToday`). The `dateTime` value must be formatted as `dd-M-yyyy HH:mm:ss` — conversion from the ISO date supplied by the frontend is done here.

---

### 2.4 — DateFilter value object

**File (new):** `src/main/java/com/clmextract/web/run/DateFilter.java`

| Field | Type | Source |
|---|---|---|
| `dateField` | `String` | `"createDate"` or `""` |
| `dateFrom` | `String` | ISO date from dashboard picker, e.g. `"2026-01-15"` |
| `modifiedWithinPeriod` | `boolean` | Dashboard toggle |
| `daysBeforeToday` | `int` | Computed by server from `ScheduleState.frequency` |

**Active-filter resolution logic** (evaluated per BO at runtime in `RunExecutor`):

| Condition | Tracking fetch method |
|---|---|
| `dateField == "createDate"` AND `dateFrom` is non-blank | `getTrackingNumbersAfterDate(boType, formattedDate)` |
| `modifiedWithinPeriod == true` | `getTrackingNumbersInFlight(boType, daysBeforeToday)` |
| All other cases | `getTrackingNumbers(boType)` — no date filter |

`daysBeforeToday` is computed server-side from the persisted `ScheduleState.frequency` in `ui-state.json`:
- `DAILY` → 1, `WEEKLY` → 7, `MONTHLY` → 30.

---

### 2.5 — RunStatus: SKIPPED

**File:** `src/main/java/com/clmextract/web/run/RunStatus.java`

Add `SKIPPED` to the existing enum. Steps marked `SKIPPED` are treated as non-failures for determining overall run success.

---

### 2.6 — RunExecutor: EXPORT_CSV step replacement

**File:** `src/main/java/com/clmextract/web/run/RunExecutor.java`

**`startRun()` signature change:**
```
boolean startRun(List<String> selectedBos, String sftpTargetPath,
                 String clmSessionId, DateFilter dateFilter)
```
`dateFilter` may be `null` (no filter). Existing callers (`RunController`, `ExportScheduler`) pass `null` initially.

**`executeRun()` — EXPORT_CSV step replaces the `Thread.sleep(1000)` stub with:**

1. Load `AppConfig` from `configPath`.
2. Determine `outputDir`:
   ```
   Path.of(config.getOutputRoot(), config.getExportFolderName(), runId)
   ```
3. `Files.createDirectories(outputDir)`.
4. Resolve BO list: if `selectedBos` is non-empty, filter `config.getBoTypes()` to only those names; otherwise use all of `config.getBoTypes()`.
5. Instantiate `BoPipeline(config, dataSource)`.
6. For each BO, call `pipeline.execute(boTypeConfig, outputDir, dateFilter)` (new overload — see §2.7).
7. If `config.isGenerateSummaryCsv()`: run `SummaryCsvWriter` across all BOs (same as `ExportOrchestrator`).
8. Fetch and write reports if `config.getReports()` is non-empty.
9. If `config.isGenerateSummaryCsv()` and manifest enabled: run `ManifestCsvWriter(outputDir, ...)`.
10. Call `updateStep(EXPORT_CSV, SUCCESS)`.

**Partial BO failure handling:** if one BO throws an exception, log the error, record it in the run's warning list (new `List<String> warnings` field on `RunState`), and continue with remaining BOs. EXPORT_CSV is marked `SUCCESS` if at least one BO succeeds. If all BOs fail, mark `FAILED`.

**PACKAGING step:**
```
if (!config.isEnableZipPackaging()) {
    updateStep(runId, STEP_PACKAGING, RunStatus.SKIPPED);
    // fall through to SFTP step
} else {
    // existing ZipPackager logic unchanged
}
```

**SFTP_UPLOAD step:**
```
if (!config.isEnableSftpUpload()) {
    updateStep(runId, STEP_SFTP_UPLOAD, RunStatus.SKIPPED);
} else {
    // determine what to upload:
    //   if enableZipPackaging → glob *.zip.* from outputDir.getParent()
    //   if !enableZipPackaging → upload all regular files in outputDir directly
    // existing SftpUploader.upload() call
}
```

---

### 2.7 — BoPipeline: new execute overload

**File:** `src/main/java/com/clmextract/export/BoPipeline.java`

Add:
```
public void execute(BoTypeConfig boTypeConfig, Path outputDir, DateFilter dateFilter)
```

Behaviour differences from the existing `execute(BoTypeConfig)`:
- Uses the supplied `outputDir` for CSV output instead of computing `Path.of(outputRoot, exportFolderName)` from config.
- At Step 2 (tracking number fetch), checks `dateFilter`:
  - If active "createDate" filter → calls `dataSource.getTrackingNumbersAfterDate(boType, formatted)`
  - If `modifiedWithinPeriod` → calls `dataSource.getTrackingNumbersInFlight(boType, daysBeforeToday)`
  - Otherwise → `dataSource.getTrackingNumbers(boType)` (unchanged)
- All other steps (metadata, batching, CSV writing, downloads) are identical.

The original `execute(BoTypeConfig)` is kept unchanged for CLI use.

---

### 2.8 — POST /api/run/start — extended request body

**File:** `src/main/java/com/clmextract/web/api/RunController.java`

Extended JSON body fields:

| Field | Type | Notes |
|---|---|---|
| `boNames` | `string[]` | existing |
| `sftpTargetPath` | `string` | existing |
| `dateField` | `string` | `"createDate"` or `""` |
| `dateFrom` | `string` | ISO date `"yyyy-MM-dd"` or `""` |
| `modifiedWithinPeriod` | `boolean` | `true` triggers `trackingNumbersInFlight` |

`RunController.startRun()` reads these fields, computes `daysBeforeToday` from `stateStore.read().getSchedule().getFrequency()` when `modifiedWithinPeriod` is true, constructs a `DateFilter`, and passes it to `runExecutor.startRun()`.

---

### 2.9 — RunState: warnings list

**File:** `src/main/java/com/clmextract/web/state/UiState.java` — inner class `RunState`

Add `List<String> warnings = new ArrayList<>()` field with getter/setter. Populated by `RunExecutor` when a BO fails but the overall step is still `SUCCESS`. Serialized to `ui-state.json` via Jackson. Rendered in the Export History panel as a collapsible warning list beneath a run entry.

---

### 2.10 — Admin Panel: ZIP and SFTP toggles

**File:** `src/main/resources/static/admin.html`

- In the **SFTP Connection** card: add two toggle switches above the SFTP host fields:
  - "Enable ZIP Packaging" (`id="enable-zip-packaging"`, type checkbox/toggle)
  - "Enable SFTP Upload" (`id="enable-sftp-upload"`, type checkbox/toggle)
- When "Enable SFTP Upload" is OFF, the SFTP credential fields grey out (disabled, not hidden).

**File:** `src/main/resources/static/js/admin.js`

- `loadConfig()`: read `enableZipPackaging` and `enableSftpUpload` from API response, set toggle states.
- `collectConfig()`: include both boolean values in the PUT body.
- Wire toggle change events to enable/disable the SFTP credential input fields.

---

### 2.11 — Dashboard: date filter in start payload + SKIPPED pill

**File:** `src/main/resources/static/js/dashboard.js`

`startExport()` payload extended:
```javascript
{
  boNames: getSelectedBos(),
  sftpTargetPath: sftpPath,
  dateField: document.getElementById('date-field')?.value || '',
  dateFrom: document.getElementById('date-from')?.value || '',
  modifiedWithinPeriod: document.getElementById('modified-period')?.checked ?? false
}
```

PILL map addition:
```javascript
SKIPPED: { text: 'Skipped', cls: 'skipped' }
```

**File:** `src/main/resources/static/css/style.css`

```css
.status-pill.skipped { background: #e8e8e8; color: #777; }
```

---

## 3. Impact and Risk Analysis

| Risk | Impact | Mitigation |
|---|---|---|
| `BoPipeline.execute()` new overload breaks existing tests | Medium | Keep original overload unchanged; add tests for new overload separately |
| Date format mismatch (`yyyy-MM-dd` from browser vs `dd-M-yyyy HH:mm:ss` for CLM API) | High | Format conversion is centralised in `ApiDataSource.getTrackingNumbersAfterDate()`, with an explicit unit test |
| Custom tracking endpoints not in `endpoints.yml` causes startup failure | High | New constants are added to the required-endpoints validation set; missing entries will fail fast at startup with a clear error |
| SFTP upload of raw files (when ZIP disabled) uploads the wrong directory | Medium | `RunExecutor` distinguishes the two cases explicitly: ZIP parts from parent dir vs raw files from `outputDir` |
| `ExportScheduler` calls `startRun()` with `null` DateFilter — no date filtering | Low / expected | `null` DateFilter falls back to unfiltered tracking numbers, which is the correct scheduled-run behaviour |
| Old run directories accumulate under `outputRoot/exportFolderName/` | Low | After EXPORT_CSV completes, `RunExecutor` applies `BackupManager.enforceRetention()` scoped to `runId`-pattern subdirectories |

---

## 4. Testing Strategy

### Unit tests

| Test class | What it covers |
|---|---|
| `DateFilterTest` | Active-filter resolution: correct method selected for each dateField/modifiedWithinPeriod combination |
| `ApiDataSourceDateFilterTest` | `getTrackingNumbersAfterDate()` and `getTrackingNumbersInFlight()` build correct headers; verify date format conversion |
| `BoPipelineOverloadTest` | New `execute(boType, outputDir, dateFilter)` writes CSVs to the supplied `outputDir` (use `@TempDir`) |
| `RunExecutorTest` (extend existing) | PACKAGING skipped when `enableZipPackaging=false`; SFTP skipped when `enableSftpUpload=false`; warnings list populated on partial BO failure |
| `ConfigControllerValidationTest` (extend existing) | `enableZipPackaging` and `enableSftpUpload` round-trip through PUT/GET |

### Integration / manual verification

- Operator logs in, selects BOs with Create Date filter, starts export → CSV files appear in `output/MetaData/<runId>/` with records after the filter date.
- Admin disables ZIP → Packaging step shows "Skipped", no `.zip.*` files created.
- Admin disables SFTP → SFTP Upload step shows "Skipped", no SFTP connection attempted.
- Admin disables both → run completes after Attachments step, all active steps green.
- Partial BO failure → EXPORT_CSV still shows Success, warnings list in Export History shows which BO failed.
