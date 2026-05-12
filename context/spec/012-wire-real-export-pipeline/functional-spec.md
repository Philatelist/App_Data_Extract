# Functional Specification: Wire Real Export Pipeline into Web UI

- **Roadmap Item:** Post-Spec-011 — connect the operator dashboard "Start Export" trigger to the existing CLM data extraction pipeline
- **Status:** Completed
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

The CLM Data Extract web UI has a fully built operator dashboard where users can trigger exports. When an operator clicks **Start Export**, the dashboard shows a live status panel with five steps (Export CSV, Export PDF, Export Attachments, Packaging, SFTP Upload). However, the **Export CSV step is a placeholder** — it waits one second and reports success without making any API calls or writing any files.

The result: the operator triggers an export, all five steps turn green, but the output directory contains no data.

The goal of this feature is to replace the stub with a real invocation of the existing export pipeline — the same logic the CLI uses — so that a dashboard-triggered run:
- Authenticates using the operator's active CLM session (already obtained at login)
- Fetches tracking numbers and bundle data from CLM for the selected BO types
- Applies the date filter configured on the dashboard using the CLM custom API endpoints
- Produces CSV files and optionally attachment downloads, a manifest, and a ZIP in the same output directory structure the CLI produces
- Respects the "Generate Manifest", "Enable ZIP Packaging", "Enable SFTP Upload", and report configuration set in the Admin Panel

---

## 2. Functional Requirements (The "What")

### 2.1 — Start Export triggers real CLM data fetch

**As an** operator, **I want** clicking "Start Export" to actually retrieve data from the CLM system, **so that** CSV files are produced at the end of the run.

**Acceptance Criteria:**
- [x] When the operator clicks Start Export, the system uses the CLM session ID that was established when the operator logged in — it does not prompt for credentials again.
- [x] For each BO type being exported, the system calls the CLM API to fetch tracking numbers, then retrieves bundle data in batches.
- [x] At the end of the Export CSV step, one CSV file per BO type is present in the output directory.
- [x] The Export CSV step in the status panel turns **green (Success)** only after the files have been written to disk, and **red (Failed)** if any unrecoverable error occurs.

---

### 2.2 — Selected BOs control which types are exported

**As an** operator, **I want** the BO checkboxes on the dashboard to control what gets exported, **so that** I can run a partial export without changing the global config.

**Acceptance Criteria:**
- [x] If the operator checks one or more BO types, only those BO types are processed in the run.
- [x] If no BO types are checked (all unchecked), all BO types defined in `config.yml` are exported — identical behaviour to a CLI run with no filter.
- [x] The selected BOs are visible in the Export History panel against the run record after the run completes.

---

### 2.3 — Date filter is applied via CLM custom API endpoints

**As an** operator, **I want** the date filter fields on the dashboard to limit which contracts are included, **so that** I can run incremental extracts without exporting the full history every time.

The CLM server exposes two custom endpoints for filtered tracking number retrieval:

| Dashboard setting | CLM endpoint used | Key parameter |
|---|---|---|
| **Create Date** + Date From | `GET custom/trackingNumbersAfterDate` | `dateTime` header: date in `dd-M-yyyy HH:mm:ss` format |
| **Modified within the period** (auto-schedule toggle ON) | `GET custom/trackingNumbersInFlight` | `daysBeforeToday` header: integer number of days |
| No date filter set | Standard `trackingNumbers` endpoint | No date filtering |

**Acceptance Criteria:**
- [x] When the operator selects **Create Date** and fills in **Date From**, the export fetches only tracking numbers whose creation date is on or after that date, using `custom/trackingNumbersAfterDate`.
- [x] When the **Modified within the period** toggle is ON (auto-schedule mode), the export uses `custom/trackingNumbersInFlight` with a `daysBeforeToday` value computed from the schedule frequency (Daily = 1, Weekly = 7, Monthly = 30).
- [x] When no date filter is set, all tracking numbers are fetched using the standard endpoint — no date restriction is applied.
- [x] The `POST /api/run/start` request body includes `dateField`, `dateFrom`, and `modifiedWithinPeriod` so that the date filter selected on the dashboard is forwarded to the server. *(Defect found in testing: the original `startExport()` JavaScript function omitted these three fields from the fetch body, causing the server to always receive a blank filter and fall back to unfiltered tracking number retrieval.)*
- [x] The `boType` header sent to CLM custom endpoints is keyed as `boUsageType` in the internal headers map to match the `valueFrom` mapping in `endpoints.yml` — consistent with how the standard `trackingNumbers` endpoint is called. *(Defect found in testing: `getTrackingNumbersAfterDate()` and `getTrackingNumbersInFlight()` used key `"boType"` instead of `"boUsageType"`, causing the BO type header to be omitted from the CLM request.)*
- [x] All custom CLM endpoint entries in `endpoints.yml` whose paths begin with `/services/rest/custom/...` carry `absolutePath: true`, so `RequestExecutor` resolves the URL against the server origin only and does not append the path to `config.baseUrl` (which already contains `/services/rest/methods`). Affected entries: `trackingNumbersAfterDate`, `trackingNumbersInFlight`, `allApprovals`, `trackingNumbersPEP`, `usersPEP`. *(Defect found in testing: without `absolutePath: true` the constructed URL doubled the path segment — e.g. `…/services/rest/methods/services/rest/custom/trackingNumbersAfterDate` — and the CLM server returned HTTP 404. `getBundleParent` already had `absolutePath: true` and served as the reference.)*
- [ ] [NEEDS CLARIFICATION: Should "Last Modified Date" with a specific Date From/To range use a different CLM endpoint, or fall back to fetching all records and filtering client-side? The `trackingNumbersInFlight` endpoint only matches an exact number of days ago, not a range.]
- [x] `trackingNumbersAfterDate` returns only records currently in the **Manage** stage whose creation date is on or after the given date. This is a CLM business rule enforced inside `PEPWSManager.getTrackingNumsAfterDate` — records in other stages are excluded regardless of their creation date. An empty result is therefore correct when no Manage-stage records exist after the specified date, and is not an error. *(Confirmed in testing: moving a record to Manage stage caused it to appear in the response; the code, URL, session, and date format are all correct.)*

---

### 2.4 — Output location matches CLI mode

**As an** administrator, **I want** web-triggered exports to write files to the same directory structure as CLI exports, **so that** I can find output files in the familiar location.

**Acceptance Criteria:**
- [x] CSV files, attachment downloads, the manifest, and ZIP parts are written inside `outputRoot` / `exportFolderName` as configured in `config.yml` — the same root the CLI uses.
- [x] Each run creates a new timestamped subdirectory (e.g. `output/export/20260512_131900/`) so that runs do not overwrite each other.
- [x] The backup retention policy configured in `config.yml` is enforced after each web run, automatically removing older run directories that exceed the limit.

---

### 2.5 — Manifest and reports follow Admin Panel configuration

**As an** administrator, **I want** to control from the Admin Panel whether the manifest CSV and configured reports are generated during web runs, **so that** I can match the output to what downstream systems expect.

**Acceptance Criteria:**
- [x] If **Generate Manifest CSV** is enabled in the Admin Panel config, a `Manifest_{DDMMYYYY}_{HHMMSS}.csv` file is produced in the run output directory after all BO CSVs are written.
- [x] If any **Reports** are configured in `config.yml` (visible in the Admin Panel), those reports are fetched and written to CSV after the main export completes.
- [x] If **Generate Manifest CSV** is disabled, no manifest file is written and no error is reported.

---

### 2.6 — Admin Panel toggle: Enable / Disable ZIP Packaging

**As an** administrator, **I want** to turn off ZIP packaging from the Admin Panel, **so that** I can access the raw CSV files directly without unpacking an archive.

**Acceptance Criteria:**
- [x] The Admin Panel has a new **Enable ZIP Packaging** toggle (on by default). The setting is saved to `config.yml`.
- [x] When the toggle is **ON**, the Packaging step runs as today: all output files in the run directory are archived into one or more `.zip.001`, `.zip.002` … parts.
- [x] When the toggle is **OFF**, the Packaging step is skipped entirely. The step row in the dashboard status panel shows **Skipped** (a neutral grey pill, distinct from Pending) and the run proceeds to the next step.
- [x] Disabling ZIP packaging does not affect the CSV, PDF, or Attachment download steps.

---

### 2.7 — Admin Panel toggle: Enable / Disable SFTP Upload

**As an** administrator, **I want** to turn off SFTP upload from the Admin Panel, **so that** I can run exports that stay local without requiring SFTP credentials to be configured.

**Acceptance Criteria:**
- [x] The Admin Panel has a new **Enable SFTP Upload** toggle (on by default). The setting is saved to `config.yml`.
- [x] When the toggle is **ON**, the SFTP Upload step behaves as today: ZIP parts (or raw output files if ZIP is disabled) are uploaded to the configured SFTP target path.
- [x] When the toggle is **OFF**, the SFTP Upload step is skipped entirely. The step row in the dashboard status panel shows **Skipped** and the run completes after the Packaging step (or after the CSV/Attachment steps if Packaging is also disabled).
- [x] When both ZIP Packaging and SFTP Upload are disabled, the run completes after Export Attachments. The overall run is still marked **Success** if all active steps succeed.
- [x] When SFTP Upload is **ON** but ZIP Packaging is **OFF**, the raw output files from the run directory are uploaded to SFTP directly (no archiving).
- [x] The SFTP connection settings (host, port, username, password) remain in the Admin Panel and are simply not used when the toggle is OFF.

---

### 2.8 — Errors in the export pipeline surface correctly in the status panel

**As an** operator, **I want** the status panel to accurately reflect which step failed and why, **so that** I can diagnose export problems without checking server logs.

**Acceptance Criteria:**
- [x] If the CLM API returns an authentication error during the export, the Export CSV step turns **red (Failed)** and the remaining steps are marked **red (Failed)** immediately.
- [x] If one BO type fails (e.g. a metadata fetch error), the export logs the error and continues with the remaining BO types. The Export CSV step shows **green (Success)** if at least one BO exported successfully, with a warning note in the Export History entry for that run. [NEEDS CLARIFICATION: confirm this partial-success behaviour is acceptable, or if any BO failure should mark the whole step Failed.]
- [x] If the export session expires mid-run, the Export CSV step reports failure with a message indicating session expiry.
- [x] If the server is restarted while a run is in progress, the interrupted run is detected on startup: all IN_PROGRESS and PENDING steps are marked **red (Failed)**, `completedAt` is set, a warning "Run interrupted by server restart" is appended, and the run is moved to the Export History — so the dashboard does not show a phantom active run after restart.
- [x] CLM HTTP 500 errors (application-level errors such as "Tracking Number is invalid" or "User does not have access to the BO type") are not retried by `RetryPolicy` — they are permanent failures that a retry cannot resolve. *(Defect found in testing: `RetryPolicy` retried all 5xx errors, causing each CLM 500 error in the attachment download step to waste 3+ seconds on two unnecessary retry attempts before giving up. Fixed by adding HTTP 500 to the no-retry condition alongside 4xx. Genuinely transient server errors — 502, 503, 504 — still retry.)*
- [x] The attachment info request for each contract uses the actual contract tracking number as the `trackingNumber` HTTP header sent to CLM. *(Defect found in testing: the `documentsAttachmentsGetAllAttachmentInfo` endpoint definition in `endpoints.yml` had `value: '12345'` — a static example value — instead of `valueFrom: trackingNumber`. Every attachment info call sent the hardcoded value `12345` to CLM regardless of the contract being processed. Fixed by changing to `valueFrom: trackingNumber`.)*

---

## 3. Scope and Boundaries

### In-Scope

- Replacing the 1-second stub in the Export CSV step with a real invocation of the existing export orchestration logic.
- Injecting the operator's active CLM session into the export — no second login.
- Filtering exported BO types by the operator's dashboard selection.
- Applying the date filter from the dashboard using the CLM custom API endpoints (`custom/trackingNumbersAfterDate`, `custom/trackingNumbersInFlight`).
- Writing output to `outputRoot/exportFolderName/` in a new timestamped subdirectory.
- Enforcing backup retention after each web run.
- Generating manifest CSV and running reports if enabled in `config.yml` / Admin Panel.
- New **Enable ZIP Packaging** toggle in the Admin Panel (saved to `config.yml`).
- New **Enable SFTP Upload** toggle in the Admin Panel (saved to `config.yml`).
- **Skipped** status pill in the dashboard status panel for steps that are disabled.

### Out-of-Scope

- Changes to the SFTP connection settings UI (fields already exist in the Admin Panel).
- "Last Modified Date" range filtering via a dedicated CLM endpoint (requires clarification — falls back to standard tracking numbers fetch for now).
- Adding new endpoints to the CLM server.
- Parallel / concurrent BO processing.
- The PDF download, Attachment download steps themselves — they are already wired and work independently of this change.
