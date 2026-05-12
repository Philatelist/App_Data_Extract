# Technical Specification: Admin Panel — CLM BO Discovery and Field Picker

- **Functional Specification:** [013-admin-bo-discovery-and-field-picker/functional-spec.md](functional-spec.md)
- **Status:** Approved
- **Author(s):** Alex

---

## 1. High-Level Technical Approach

Seven distinct changes across backend and frontend:

1. **Admin login via CLM** — Add `adminEmails` to `AppConfig`, new `GET /api/auth/check-admin` route, modified `AuthController.login()`. Removes the hardcoded `admin/admin` shortcut.
2. **AdminController** — New controller class handling all BO-discovery and column-file API endpoints.
3. **BO type discovery** — `GET /api/admin/bo-types` calls CLM `getBoTypes()` then fetches display names in parallel via `getMetadata()`, merges with current `config.yml` for checked/localizedName state.
4. **Field picker backend** — `GET /api/admin/bo-metadata/{boType}` (metadata from CLM) and `GET`/`PUT /api/admin/columns/{boType}` (read/write `config/columns/<boType>.csv`).
5. **Save configuration** — BO list written via the existing `PUT /api/config`; per-BO column files written immediately on Apply via `PUT /api/admin/columns/{boType}`.
6. **Frontend** — New BO discovery table + field picker UI in `admin.html`/`admin.js`; login toggle logic in `index.html`/`auth.js`.
7. **Attachment scope fix (§2.7)** — `BoPipeline.execute()` returns `List<Long>` (the collected tracking IDs); `RunExecutor` accumulates them during the CSV step and passes them to PDF/attachment steps directly.

No new infrastructure, libraries, or services are required. All changes are within the existing Java + Javalin + vanilla JS stack.

---

## 2. Proposed Solution & Implementation Plan

### 2.1 Admin login with CLM credentials

**AppConfig changes:**
- Add `List<String> adminEmails` field (default: empty list).
- `ConfigLoader` parses `adminEmails` from YAML.
- `ConfigController.getConfig()` includes `adminEmails` in response.
- `ConfigController.putConfig()` accepts and writes `adminEmails` to YAML.

**New endpoint — `GET /api/auth/check-admin`:**
- No authentication required (added to the allow-list in the `before` filter in `WebServer`).
- Query param: `?email=<value>`.
- Checks if the email is in `config.getAdminEmails()`.
- Returns `{"isAdmin": true}` or `{"isAdmin": false}`. Does not reveal whether the list is empty.
- Registered in `AuthController`.

**Modified `AuthController.login()`:**
- Request body gains a new boolean field `asAdmin`.
- If `asAdmin == true` AND the submitted username/email is in `adminEmails`:
  - CLM-authenticate using submitted credentials.
  - On success: grant session role `ADMIN`, store CLM session ID in `ctx.sessionAttribute("clmSessionId")`.
- If `asAdmin == false` OR email not in `adminEmails`: existing operator login flow (no change).
- Remove the hardcoded `if ("admin".equals(username) && "admin".equals(password))` block.

**Frontend (`index.html`, `auth.js`):**
- "Sign in as Admin" toggle: hidden by default, `display:none`.
- Debounced `input` listener on the email field (300 ms): calls `GET /api/auth/check-admin?email=`. Shows/hides the toggle based on response.
- Login `POST /api/auth/login` body: `{username, password, asAdmin}`.

---

### 2.2 & 2.3 BO type discovery and display name override

**New class — `AdminController`** in `com.clmextract.web.api`.

**`GET /api/admin/bo-types`** (requires `ADMIN` role):
1. Reads `clmSessionId` from server session.
2. Builds `ApiDataSource` (using `config.getEndpointsFile()`) and injects the session ID.
3. Calls `dataSource.getBoTypes()` → `List<String>` of internal names.
4. Fetches display names in parallel: create a fixed thread pool of 5 threads, submit one `dataSource.getMetadata(name)` task per BO, collect results within a 30-second timeout. If a metadata call times out or fails, that BO gets `displayName = internalName` as a fallback.
5. Loads current `config.yml` via `ConfigLoader.loadRaw()` to determine `checked` (is the BO in `boTypes`?) and `localizedName` (the admin's display name override).
6. Response shape — JSON array:
   ```
   [
     { internalName, displayName, usageType, checked, localizedName }
   ]
   ```
   - `usageType` comes from `BoMetadata.getBoUsageType()` (if field exists on the model; add if missing).
   - `checked` is `true` if the BO's internal name appears in `config.boTypes`.
   - `localizedName` is the value from `config.boTypes[i].localizedName` if present, else `""`.

---

### 2.4 Field picker

**`GET /api/admin/bo-metadata/{boType}`** (requires `ADMIN` role):
- Uses admin CLM session to call `dataSource.getMetadata(boType)` (reuses `MetadataParser`).
- Response shape:
  ```
  {
    boName, boDisplayName,
    components: [
      {
        internalName, displayName, cardinality,
        fields: [
          { internalName, displayName, instancePath, dataType }
        ]
      }
    ]
  }
  ```
  where `instancePath` is `field.getInstancePath().replace("MCPDef:/", "")` — the value written to the columns CSV.

**`GET /api/admin/columns/{boType}`** (requires `ADMIN` role):
- Reads `config/columns/<boType>.csv` (path constructed as `Path.of("config", "columns", boType + ".csv")`).
- If the file exists: returns `{"fieldPaths": ["path1", "path2", ...]}`.
- If the file does not exist: returns `{"fieldPaths": null}` — signals "all fields selected" to the frontend.

**`PUT /api/admin/columns/{boType}`** (requires `ADMIN` role):
- Request body: `{"fieldPaths": ["path1", "path2", ...]}`.
- Creates the `config/columns/` directory if it doesn't exist.
- Writes one path per line to `config/columns/<boType>.csv`, overwriting any existing file.
- Returns `200 OK` on success.

**Frontend (`admin.html`, `admin.js`):**
- New "BO Types" section in the Admin Panel with a **Load from CLM** button.
- On load: renders a table with columns — Checkbox | Internal Name | Display Name (editable `<input>`) | Usage Type | Fields badge | Edit Fields button.
- BO types already in `config.yml` are pre-checked.
- **Edit Fields** button: calls `GET /api/admin/bo-metadata/{boType}` and `GET /api/admin/columns/{boType}` in parallel; renders the field picker panel.
- Field picker: components shown as collapsible sections; each field has a checkbox; Select All / Deselect All per component; search input filters fields by display name or internal name.
- Pre-populate: if `fieldPaths != null`, check only those paths; if `fieldPaths == null`, check all fields.
- **Apply** button: calls `PUT /api/admin/columns/{boType}` with the checked paths; updates the badge showing "N fields selected"; closes the picker.
- Text search in the BO table filters rows by name.

---

### 2.5 Saving the configuration

**BO list** — saved via the existing `PUT /api/config` endpoint. The `boTypes` array in the request body carries the checked BOs (internal name + `localizedName`). `adminEmails` is also written via `PUT /api/config` (both `getConfig` and `putConfig` updated).

**Column files** — written per-BO immediately on Apply (see §2.4 `PUT /api/admin/columns/{boType}`). No extra step at Save Configuration time for column files.

---

### 2.6 Route registration in `WebServer`

New routes:
- `GET /api/auth/check-admin` — added to the path allow-list in the `before` filter so it passes unauthenticated.
- `GET /api/admin/bo-types`
- `GET /api/admin/bo-metadata/{boType}`
- `GET /api/admin/columns/{boType}`
- `PUT /api/admin/columns/{boType}`

`AdminController` is constructed in `WebServer.start()` and receives `configPath` and the global `AppConfig` (for CLM connection settings and `endpointsFile`).

---

### 2.7 Attachment scope fix

**`BoPipeline.execute(BoTypeConfig, Path, DateFilter)` → return type changed from `void` to `List<Long>`:**
- At step 2 (after tracking IDs are resolved and filtered), the method proceeds as today.
- At the end of the method (after all CSV writing and parent/summary steps), return `trackingIds`.
- The `void` overload `execute(BoTypeConfig)` used by the CLI is a separate method and is unchanged.

**`RunExecutor.executeRun()` — EXPORT_CSV step:**
- Accumulate: `List<Long> exportedIds = new ArrayList<>()`.
- For each BO: `exportedIds.addAll(pipeline.execute(boTypeConfig, outputDir, dateFilter))`.

**`RunExecutor.executeRun()` — EXPORT_PDF and EXPORT_ATTACHMENTS steps:**
- Remove the `resolveTrackingIds(selectedBos, dataSource, stepConfig)` calls.
- Use `exportedIds` directly: `downloader.downloadPdfs(exportedIds)` and `downloader.downloadAttachments(exportedIds)`.
- `resolveTrackingIds()` helper method can be removed entirely (no longer called).

---

## 3. Impact and Risk Analysis

**System dependencies:**
- `AuthController` depends on `AppConfig` being non-null for the admin check. If config fails to load on startup, the `check-admin` endpoint must return `{"isAdmin": false}` gracefully rather than 500.
- `AdminController` uses `ApiDataSource` with the admin's CLM session. If the session expires between login and a discovery call, CLM returns 401 → `SessionExpiredException` → return HTTP 401 to frontend → JS redirects to login.
- `BoPipeline.execute()` return type change is limited to the two-argument and three-argument overloads. All callers are in `RunExecutor` and `ExportOrchestrator`; both must be updated.

**Potential risks & mitigations:**

| Risk | Mitigation |
|---|---|
| Parallel metadata calls overloading CLM | Fixed thread pool of 5; 30 s per-call timeout; fallback to internal name on failure |
| Admin CLM session stored server-side could be stolen | Javalin's server-side session (HttpOnly cookie) already used for `clmSessionId` in spec 012 — same security model |
| `adminEmails` empty → no one can log in as admin | On `PUT /api/config`, warn if `adminEmails` is saved as empty; keep the check-admin endpoint returning `false` rather than erroring |
| Column CSV path is relative to working directory | Consistent with `ColumnResolver.loadOrderFile()` which uses the same relative path `config/columns/<boType>.csv` |
| `BoPipeline` tracking ID accumulation across BOs — duplicate IDs if same record in multiple BOs | Not possible: each BO has its own distinct tracking number space |

---

## 4. Testing Strategy

- **`AuthController`** — unit tests for `checkAdmin()` (email in list, email not in list, null config) and `login()` with `asAdmin=true/false`.
- **`AdminController.getBoTypes()`** — unit test with a stubbed `DataSource` returning a fixed BO list and metadata; verify parallel fetch, fallback on timeout, merge with config.
- **`AdminController.getColumns()` / `putColumns()`** — unit tests for read (file exists, file missing) and write (creates directory, overwrites existing).
- **`BoPipeline.execute()` return value** — unit test that the returned list matches the tracking IDs resolved in step 2.
- **`RunExecutor`** — unit test verifying that after the CSV step, `exportedIds` is passed to mock `AttachmentDownloader` (not re-fetched from CLM).
- **Integration smoke test** — start the web server, log in as admin (using the CLM credentials in `config.yml`), call `GET /api/admin/bo-types`, verify a non-empty response.
