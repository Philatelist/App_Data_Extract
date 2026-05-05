# Tasks: Web UI — Admin Panel & User Dashboard

**Spec:** `context/spec/011-web-ui-admin-and-user-dashboard/`
**Rule:** The application must remain in a runnable state after every slice.

---

## Slice 1: Static Pages + Minimal Server ✦ First Runnable Increment

> `java -jar clm-extract.jar --config config.yml --serve` starts on port 8082 and serves three polished, navigable HTML pages. No API calls. No backend logic. Just great UI.

- [x] **Add Javalin and SLF4J dependencies to `pom.xml`** — add `io.javalin:javalin:6.4.0`, `org.slf4j:slf4j-api:2.0.x`, `org.apache.logging.log4j:log4j-slf4j2-impl:2.23.1`; add `ServicesResourceTransformer` to maven-shade-plugin. **[Agent: java-backend]**

- [x] **Add `--serve` mode to `App.java`** — detect `--serve` flag and `--port` flag (default 8082); branch to `WebServer.start(configPath, port)`. Existing CLI path is unchanged. **[Agent: java-backend]**

- [x] **Create `WebServer.java`** in `com.clmextract.web` — initializes `Javalin.create()` with static files from classpath `/static`; adds catch-all GET `/` redirect to `/index.html`; starts on given port. No routes yet. **[Agent: java-backend]**

- [x] **Create `index.html` — Login Page** with 2026 design:
  - Split layout: left panel = product value proposition with animated headline and key stats trust block ("X contracts processed", "Y organizations trust it"); right panel = login form with username/password fields and a prominent "Sign In" CTA button
  - Scroll-reveal animation on the stats row using Intersection Observer API
  - Responsive: stacks vertically on mobile
  - Navigation links to admin and dashboard pages (no auth guard yet — for visual review)
  **[Agent: vanilla-frontend]**

- [x] **Create `admin.html` — Admin Panel** with 2026 design:
  - Sticky top navigation bar with logo, "Admin Panel" label, and "Sign Out" link
  - Sidebar navigation listing all config sections (Server, API & Endpoints, BO Types, Output & Files, Column Filtering, Delimiter Replacement, Yes/No Translation, Date Format, SFTP Connection)
  - Each section renders as a card that scrolls into view with a subtle fade-in (Intersection Observer)
  - All config form fields rendered as static HTML with placeholder values — no API calls yet
  - Prominent "Save Configuration" CTA button pinned to bottom of page
  - Responsive: sidebar collapses to hamburger menu on mobile
  **[Agent: vanilla-frontend]**

- [x] **Create `dashboard.html` — Operator Dashboard** with 2026 design:
  - Top navigation bar with logo, "Export Dashboard" label, and "Sign Out" link
  - Hero section: bold headline ("Run your CLM export in one click") + subtext + large "Start Export" CTA button
  - **Export Configuration section** (scrolls in): BO selection checkboxes with placeholder last-run date badges, Frequency picker, Manual Override Date picker, SFTP Target Path field — all static HTML
  - **Status Panel section** (scrolls in): 5 step rows (Export CSV, Export PDF, Export Attachments, Packaging, SFTP Upload) each with a grey "Pending" pill — static for now
  - **Trust block section** (scrolls in): case study card ("Migrated 12,000 contracts in one weekend"), a testimonial quote, and a stats row ("30M+ records exported", "Zero data loss")
  - Responsive: single column on mobile
  **[Agent: vanilla-frontend]**

- [x] **Create `css/style.css`** — shared design system:
  - CSS custom properties for color palette, spacing, and typography
  - Base resets and responsive grid utilities
  - Component styles: `.nav`, `.sidebar`, `.card`, `.form-field`, `.btn-primary`, `.btn-secondary`, `.status-pill` (grey/pending, blue/in-progress, green/success, red/failed)
  - Scroll-reveal keyframe animation (`@keyframes fadeSlideUp`) + `.reveal` class driven by Intersection Observer
  - Media queries for mobile breakpoints
  **[Agent: vanilla-frontend]**

- [x] **Create stub JS files** — `js/auth.js`, `js/admin.js`, `js/dashboard.js` — valid ES6 modules with placeholder exported functions and `console.log` markers confirming which page loaded. No fetch calls yet. **[Agent: vanilla-frontend]**

- [x] **Build and smoke-test** — run `mvn package`, start with `java -jar target/clm-data-extract-1.0.0.jar --config config.yml --serve`, confirm `http://localhost:8082` loads login page, `http://localhost:8082/admin.html` loads admin panel, `http://localhost:8082/dashboard.html` loads dashboard. No 404s, no JS console errors. **[Agent: java-backend]**

---

## Slice 2: Login Authentication

> Submitting the login form routes to the correct page and rejects bad credentials.

- [ ] Implement `POST /api/auth/login` in `AuthController` — admin/admin check + CLM `SessionManager` call for operators; set session with role. **[Agent: java-backend]**
- [ ] Implement `POST /api/auth/logout` in `AuthController` — clear session. **[Agent: java-backend]**
- [ ] Add `before` filter to `WebServer` — protect all `/api/*` except `/api/auth/login` with 401; protect `admin.html` and `dashboard.html` with session role check, redirect to login if missing. **[Agent: java-backend]**
- [ ] Wire `index.html` login form — `auth.js` POSTs credentials to `/api/auth/login` and redirects on success; shows inline error on failure. **[Agent: vanilla-frontend]**
- [ ] Wire "Sign Out" links on both pages — call `POST /api/auth/logout` then redirect to login. **[Agent: vanilla-frontend]**
- [ ] Verify: admin/admin → admin panel; valid CLM user → dashboard; bad credentials → error shown on login page. **[Agent: java-backend]**

---

## Slice 3: Config Read — Admin Panel Shows Real Values

> Admin panel form fields are populated from the live `config.yml` on page load.

- [ ] Implement `GET /api/config` in `ConfigController` — load `AppConfig` via `ConfigLoader`, serialize to JSON. **[Agent: java-backend]**
- [ ] `admin.js` fetches `GET /api/config` on load and populates all form fields dynamically. **[Agent: vanilla-frontend]**
- [ ] Verify: change a value in `config.yml`, restart, confirm admin panel reflects the new value. **[Agent: java-backend]**

---

## Slice 4: Config Save — Admin Panel Writes `config.yml`

> Clicking "Save Configuration" validates and writes changes to `config.yml`.

- [ ] Add `SftpConfig` inner class to `AppConfig` for `sftp.host`, `sftp.port`, `sftp.username`, `sftp.password`. **[Agent: java-backend]**
- [ ] Implement `PUT /api/config` in `ConfigController` — validate all fields, serialize to YAML, write `config.yml`. **[Agent: java-backend]**
- [ ] `admin.js` collects form values and POSTs to `PUT /api/config`; shows success toast or field-level validation errors. **[Agent: vanilla-frontend]**
- [ ] Unit test: `ConfigControllerValidationTest` — required field missing, invalid delimiter (multi-char), valid round-trip. **[Agent: java-backend]**
- [ ] Verify: change Server URL in admin form, save, read `config.yml` on disk — new value persists. **[Agent: java-backend]**

---

## Slice 5: BO List + Last-Run Dates on Dashboard

> Dashboard checkboxes show real BO names with actual last-run dates from `ui-state.json`.

- [ ] Implement `UiState` Jackson model and `StateStore` — read/write `ui-state.json` atomically (temp-file + `ATOMIC_MOVE`). **[Agent: java-backend]**
- [ ] Implement `GET /api/bos` in `RunController` — returns `[{name, lastRunDate}]` from state. **[Agent: java-backend]**
- [ ] `dashboard.js` fetches `/api/bos` on load and renders BO checkboxes with date badges. **[Agent: vanilla-frontend]**
- [ ] Unit test: `StateStoreTest` — JSON round-trip, malformed JSON recovery → fresh empty state. **[Agent: java-backend]**
- [ ] Verify: dashboard shows BO list from config; first load with no state file shows "—" for all dates. **[Agent: java-backend]**

---

## Slice 6: Export Trigger + Live Status Panel (CSV only)

> Clicking "Start Export" runs a CSV export and the status panel updates in real time via polling.

- [ ] Implement `RunStatus` enum (`PENDING`, `IN_PROGRESS`, `SUCCESS`, `FAILED`) and `RunExecutor` — async `SingleThreadExecutor` wrapping `ExportOrchestrator`; calls `StateStore.updateStep()` after each step. **[Agent: java-backend]**
- [ ] Implement `POST /api/run/start` in `RunController` — validate not already running (409 if active), launch `RunExecutor`. **[Agent: java-backend]**
- [ ] Implement `GET /api/run/status` in `RunController` — return `currentRun` from state. **[Agent: java-backend]**
- [ ] `dashboard.js` — on Start Export click: disable button, begin 2s polling of `/api/run/status`, update each step row's status pill color and label. Stop polling when `completedAt` is non-null. **[Agent: vanilla-frontend]**
- [ ] Unit test: `RunExecutorTest` — step transitions, 409 on concurrent start attempt. **[Agent: java-backend]**
- [ ] Verify: trigger export from dashboard, watch CSV step turn green, check output directory for CSV files; verify concurrent start returns 409. **[Agent: java-backend]**

---

## Slice 7: Binary File Download — PDFs & Attachments

> Export pipeline fetches signed contract PDFs and other attachments from CLM.

- [ ] Identify CLM attachment download endpoints in `endpoints.yml`; implement download logic in `RunExecutor` for `EXPORT_PDF` and `EXPORT_ATTACHMENTS` steps, writing files as `<Contract_ID>.pdf` and `<Contract_ID>_filename.ext`. **[Agent: java-backend]**
- [ ] Verify: after export, run output directory contains PDF and attachment files alongside CSVs. **[Agent: java-backend]**

---

## Slice 8: ZIP Packaging with 200MB Splitting

> All output files are packaged into a ZIP after export (split at 200MB).

- [ ] Implement `ZipPackager` in `com.clmextract.packaging` — streaming `ZipOutputStream`; tracks bytes written; splits at 200MB into `.zip.001`, `.zip.002`, etc. Returns list of part paths. **[Agent: java-backend]**
- [ ] Wire `ZipPackager.pack(runOutputDir)` into `RunExecutor` `PACKAGING` step. **[Agent: java-backend]**
- [ ] Unit test: `ZipPackagerTest` — single file produces one part, split at exactly 200MB boundary, correct part naming. **[Agent: java-backend]**
- [ ] Verify: post-export, ZIP parts are present in output directory. **[Agent: java-backend]**

---

## Slice 9: SFTP Upload

> ZIP parts are uploaded to the configured SFTP server at the user-specified target path.

- [ ] Add `com.github.mwiede:jsch:0.2.x` to `pom.xml`. **[Agent: java-backend]**
- [ ] Implement `SftpUploader` in `com.clmextract.sftp` — connect to `sftp.host:sftp.port`, ensure target directory exists, upload each ZIP part sequentially, close channel in finally block. **[Agent: java-backend]**
- [ ] Wire `SftpUploader` into `RunExecutor` `SFTP_UPLOAD` step. **[Agent: java-backend]**
- [ ] Verify: configure SFTP credentials in admin panel, run export, confirm ZIP parts appear on SFTP server at the specified target path. **[Agent: java-backend]**

---

## Slice 10: Auto-Schedule — Frequency Picker & Recurring Exports

> Operator enables a recurring auto-export schedule from the dashboard.

- [ ] Implement `ExportScheduler` in `com.clmextract.web.scheduler` — `ScheduledExecutorService` (single thread); compute `nextRunAt` from `frequency` + `timeOfDay`; fire immediately on startup if `nextRunAt` is in the past. **[Agent: java-backend]**
- [ ] Implement `GET /api/schedule` and `PUT /api/schedule` in `ScheduleController`. **[Agent: java-backend]**
- [ ] `dashboard.js` — frequency picker and auto-schedule enable toggle POST to `PUT /api/schedule`; display `nextRunAt` beneath the toggle. **[Agent: vanilla-frontend]**
- [ ] Unit test: `ExportSchedulerTest` — reschedule on settings update, immediate fire when `nextRunAt` is overdue. **[Agent: java-backend]**
- [ ] Verify: enable weekly schedule, confirm `nextRunAt` is 7 days out in the UI; restart server, confirm schedule survives restart via `ui-state.json`. **[Agent: java-backend]**

---

## Notes

**Browser visual verification:** No browser MCP is configured. Slice 1 build verification is done via `curl` for HTTP 200 status checks + manual browser review by the user. For automated UI verification in later slices, consider installing a browser automation MCP.
