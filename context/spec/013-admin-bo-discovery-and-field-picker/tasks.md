# Tasks: Admin Panel — CLM BO Discovery and Field Picker

- **Functional Specification:** `context/spec/013-admin-bo-discovery-and-field-picker/functional-spec.md`
- **Technical Specification:** `context/spec/013-admin-bo-discovery-and-field-picker/technical-considerations.md`
- **Status:** Ready

---

## Slice 1: Attachment scope fix — PDF/attachment use CSV-exported IDs

- [x] `BoPipeline.execute(BoTypeConfig, Path, DateFilter)` — change return type from `void` to `List<Long>`; return `trackingIds` at the end of the method. **[Agent: java-backend]**
- [x] `RunExecutor.executeRun()` — declare `List<Long> exportedIds = new ArrayList<>()` before the CSV loop; accumulate via `exportedIds.addAll(pipeline.execute(...))` for each BO. **[Agent: java-backend]**
- [x] `RunExecutor.executeRun()` — EXPORT_PDF step: replace `resolveTrackingIds(...)` call with `exportedIds`; EXPORT_ATTACHMENTS step: same. **[Agent: java-backend]**
- [x] Remove `resolveTrackingIds()` helper method (no longer called). **[Agent: java-backend]**
- [x] Unit test: `BoPipeline.execute(boTypeConfig, outputDir, dateFilter)` returns the tracking IDs resolved at step 2. **[Agent: java-backend]**
- [x] Unit test: `RunExecutor` passes accumulated `exportedIds` to mock `AttachmentDownloader`, not re-fetched from CLM. **[Agent: java-backend]**
- [x] Verify: build JAR, run a date-filtered export from the dashboard; confirm in logs that EXPORT_PDF and EXPORT_ATTACHMENTS report the same tracking ID count as EXPORT_CSV. **[Agent: general-purpose]**

---

## Slice 2: Admin login with CLM credentials

- [x] `AppConfig`: add `List<String> adminEmails` field (getter/setter, default empty list). **[Agent: java-backend]**
- [x] `ConfigLoader`: parse `adminEmails` list from YAML; assign empty list as default if missing. **[Agent: java-backend]**
- [x] `ConfigController.getConfig()` and `putConfig()`: include `adminEmails` in GET response and accept it in PUT body. **[Agent: java-backend]**
- [x] `AuthController`: add `GET /api/auth/check-admin?email=` — returns `{"isAdmin": true/false}`; no auth required. **[Agent: java-backend]**
- [x] `WebServer`: add `/api/auth/check-admin` to the unauthenticated allow-list in the `before` filter. **[Agent: java-backend]**
- [x] `AuthController.login()`: accept new `asAdmin` boolean in request body; if `asAdmin == true` and email is in `adminEmails`, CLM-authenticate, grant `ADMIN` role, store `clmSessionId` in server session; remove hardcoded `admin/admin` block. **[Agent: java-backend]**
- [x] Admin Panel config form (`admin.html`/`admin.js`): add `adminEmails` text area (one email per line); populate from `getConfig()` response; include in `collectConfig()`. **[Agent: general-purpose]**
- [x] `index.html`/`auth.js`: debounced 300 ms `input` listener on email field → `GET /api/auth/check-admin`; show/hide "Sign in as Admin" toggle; include `asAdmin` in login `POST /api/auth/login` body. **[Agent: general-purpose]**
- [x] Unit tests: `checkAdmin()` — email in list, email not in list, null/empty `adminEmails`; `login()` — `asAdmin=true` with valid admin email, `asAdmin=false` with admin email falls through to operator flow. **[Agent: java-backend]**
- [x] Verify: add an email to `adminEmails` in Admin Panel and save; log out; enter that email on login page → "Sign in as Admin" toggle appears; log in with CLM credentials + toggle ON → ADMIN session granted; old `admin/admin` no longer works. **[Agent: general-purpose]**

---

## Slice 3: BO type discovery table

- [x] Create `AdminController` in `com.clmextract.web.api`; constructor accepts `configPath` and `AppConfig`. **[Agent: java-backend]**
- [x] `AdminController.getBoTypes()` — `GET /api/admin/bo-types` (requires `ADMIN` role): reads `clmSessionId` from server session; builds `ApiDataSource` and injects session; calls `getBoTypes()`; fetches display names in parallel (fixed 5-thread pool, 30 s timeout per call, fallback to internalName on failure); loads raw `config.yml` to determine `checked` and `localizedName`; returns JSON array `[{internalName, displayName, usageType, checked, localizedName}]`. **[Agent: java-backend]**
- [x] `WebServer.start()`: construct `AdminController`, register `GET /api/admin/bo-types`. **[Agent: java-backend]**
- [x] `admin.html`: add "BO Types" section with a **"Load from CLM"** button and a table placeholder; add text search input above the table. **[Agent: general-purpose]**
- [x] `admin.js`: on "Load from CLM" click — show spinner, call `GET /api/admin/bo-types`, render table rows (Checkbox | Internal Name | Display Name editable `<input>` | Usage Type | Fields badge `—` | Edit Fields button); pre-check rows whose `checked == true`; show inline error on failure; filter rows by text search. **[Agent: general-purpose]**
- [x] Unit test: `AdminController.getBoTypes()` with a stubbed `DataSource` returning a fixed BO list and metadata; verify parallel fetch, fallback on timeout, correct merge with config. **[Agent: java-backend]**
- [x] Verify: log in as admin, click "Load from CLM" → table renders with BO rows; BOs already in `config.yml` are pre-checked; text search filters rows. **[Agent: general-purpose]**

---

## Slice 4: Save BO list and display name override

- [x] `admin.js`: "Save Configuration" click — collect checked BO rows (internalName + `localizedName` from the display name `<input>`), merge into the existing config payload alongside `adminEmails`; call existing `PUT /api/config`; show success/error toast. **[Agent: general-purpose]**
- [x] Verify: check/uncheck BOs, edit display names, click Save; reload Admin Panel and click "Load from CLM" → previously checked BOs remain checked with the edited display name. **[Agent: general-purpose]**

---

## Slice 5: Field picker backend

- [x] `AdminController.getBoMetadata()` — `GET /api/admin/bo-metadata/{boType}` (requires `ADMIN` role): calls `dataSource.getMetadata(boType)`; returns `{boName, boDisplayName, components:[{internalName, displayName, cardinality, fields:[{internalName, displayName, instancePath, dataType}]}]}` where `instancePath = field.getInstancePath().replace("MCPDef:/", "")`. **[Agent: java-backend]**
- [x] `AdminController.getColumns()` — `GET /api/admin/columns/{boType}` (requires `ADMIN` role): reads `config/columns/<boType>.csv`; returns `{"fieldPaths":[...]}` if file exists, `{"fieldPaths":null}` if missing. **[Agent: java-backend]**
- [x] `AdminController.putColumns()` — `PUT /api/admin/columns/{boType}` (requires `ADMIN` role): creates `config/columns/` directory if needed; writes one field path per line to `config/columns/<boType>.csv`, overwriting existing. **[Agent: java-backend]**
- [x] `WebServer.start()`: register `GET /api/admin/bo-metadata/{boType}`, `GET /api/admin/columns/{boType}`, `PUT /api/admin/columns/{boType}`. **[Agent: java-backend]**
- [x] Unit tests: `getColumns()` — file exists returns paths, file missing returns null; `putColumns()` — creates directory if absent, overwrites existing file. **[Agent: java-backend]**
- [x] Verify with curl: `GET /api/admin/bo-metadata/{boType}` returns correct component/field structure; `PUT /api/admin/columns/{boType}` writes the CSV; subsequent `GET /api/admin/columns/{boType}` returns the saved paths. **[Agent: general-purpose]**

---

## Slice 6: Field picker frontend

- [x] `admin.js`: "Edit Fields" button click — call `GET /api/admin/bo-metadata/{boType}` and `GET /api/admin/columns/{boType}` in parallel; show loading state. **[Agent: general-purpose]**
- [x] Render field picker panel inline under the BO row: components as collapsible sections (collapsed by default when >5 components); each field shows display name, instancePath, dataType, and a checkbox; Select All / Deselect All toggle per component; search input filters fields by display name or instancePath. **[Agent: general-purpose]**
- [x] Pre-populate: if `fieldPaths != null` → check only matching paths; if `fieldPaths == null` → check all fields. **[Agent: general-purpose]**
- [x] "Apply" button: call `PUT /api/admin/columns/{boType}` with checked instancePaths; on success update the Fields badge to "N fields selected"; close the picker. **[Agent: general-purpose]**
- [x] Verify: open field picker for a BO with no existing column file → all fields pre-checked; deselect some, click Apply → badge shows correct count; reopen → only selected fields are checked; open a BO that already has a `config/columns/<boType>.csv` → only those paths are pre-checked. **[Agent: general-purpose]**

---

## Subagent Recommendations

| Task/Slice | Issue | Recommendation |
|---|---|---|
| Slices 2–6: admin.html, admin.js, auth.js, index.html | No frontend specialist agent available | Add a `vanilla-frontend` agent for plain HTML/JS/CSS tasks |
| Slices 1–6: Verification sub-tasks | UI verification relies on curl + log inspection; no browser MCP | Install browser MCP for automated end-to-end UI verification |
