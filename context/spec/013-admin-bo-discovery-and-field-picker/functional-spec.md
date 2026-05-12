# Functional Specification: Admin Panel — CLM BO Discovery and Field Picker

- **Roadmap Item:** Post-Spec-012 — replace manual BO type text entry and per-BO column CSV files with a live CLM-driven picker in the Admin Panel
- **Status:** Approved
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

Today, configuring which Business Object types to export and which fields to include requires two manual steps outside the web UI: editing the `boTypes` list in `config.yml` by hand, and editing one `config/columns/<BoType>.csv` file per BO type using internal field path strings.

Neither step is practical for a non-technical admin. The user must already know the exact internal names of BO types and field paths, and must maintain those files outside the application.

This feature makes the Admin Panel self-sufficient:
- The admin fetches all available BO types directly from CLM with one click, sees their display names, and checks the ones to export.
- For each selected BO, the admin opens a field picker showing every component and field available in CLM — with display names, not internal codes — and checks exactly which fields to include.
- Saving from the Admin Panel writes the BO list to `config.yml` and the selected field paths to the corresponding `config/columns/<BoType>.csv` files automatically.

---

## 2. Functional Requirements (The "What")

### 2.1 — Admin login uses CLM credentials, not a hardcoded password

**As an** IT administrator, **I want** to log in with my real CLM credentials and be granted admin access based on my email, **so that** the admin account is not a shared, hardcoded password.

**Background:** Today admin access requires entering `admin` / `admin`. This will be replaced by a config-driven email allow-list combined with real CLM authentication.

**Acceptance Criteria:**
- [ ] `config.yml` has a new top-level field `adminEmails` — a list of email addresses (e.g. `ausov@corcentric.com`) that are permitted admin access.
- [ ] On the login page, when the user enters an email that matches one of the `adminEmails` values, a toggle labelled **"Sign in as Admin"** appears beneath the password field. The toggle is hidden for non-admin emails.
- [ ] When the **Sign in as Admin** toggle is ON and the user submits the form, the server verifies the credentials against CLM (same flow as operator login). If CLM accepts them, the session is granted the `ADMIN` role and the CLM session ID is stored in the session.
- [ ] If CLM rejects the credentials, the login page shows the same error message as a failed operator login — no indication of whether the email is on the admin list.
- [ ] The existing hardcoded `admin` / `admin` shortcut is removed.
- [ ] Operators (non-admin emails, or admin email with the toggle OFF) continue to log in exactly as before, receiving the `OPERATOR` role.

---

### 2.2 — Admin Panel: "Load BO Types from CLM" button

**As an** administrator, **I want** to fetch the full list of BO types available in CLM with one click, **so that** I do not have to know or type their internal names.

**Acceptance Criteria:**
- [ ] The BO Types section of the Admin Panel has a **"Load from CLM"** button.
- [ ] Clicking the button triggers a server-side call to CLM using the admin's active CLM session. The button shows a loading spinner while the call is in progress.
- [ ] On success, the section displays a table of all BO types returned by CLM. Each row shows:
  - Internal name (used by the export pipeline)
  - Display name (human-readable label from CLM metadata)
  - Usage type (e.g. `Contract`, `NonContract`, `Directory`)
  - A checkbox indicating whether this BO is currently included in the export configuration
- [ ] BO types already present in `config.yml` (`boTypes` list) are pre-checked. All others are unchecked by default.
- [ ] If the CLM call fails (network error, session expired), an inline error message is shown beneath the button and the existing BO list is preserved.
- [ ] The table supports text search/filtering so the admin can find a BO type by name without scrolling through the full list.

---

### 2.3 — Admin Panel: Display name override per BO

**As an** administrator, **I want** to set a custom display name (localized label) for each selected BO type, **so that** operators see friendly names in the dashboard BO list.

**Acceptance Criteria:**
- [ ] Each checked BO row in the table has an editable **Display Name** field, pre-filled with the CLM display name.
- [ ] The admin can edit the display name freely. The edited value is stored as `localizedName` in the `boTypes` entry in `config.yml`.
- [ ] If the display name is left blank, the internal name is used as the fallback.

---

### 2.4 — Admin Panel: Field picker per BO type

**As an** administrator, **I want** to see all available fields for a BO type and select exactly which ones to export, **so that** the CSV output contains only the columns my team needs.

**Acceptance Criteria:**
- [ ] Each BO row in the table has an **"Edit Fields"** button. Clicking it opens an expanded inline panel (or a slide-over drawer) for that BO.
- [ ] The system fetches BO metadata from CLM using the `BOMetaData` endpoint (using the admin's CLM session). A loading state is shown during the fetch.
- [ ] The field picker displays fields grouped by component. Each component is a collapsible section showing:
  - Component display name and cardinality (`single` or `multiple`)
  - A list of fields, each showing: display name, internal field path (e.g. `GPAMEA_Data/ReqGPAMEAInfo/agreementType`), and data type
  - A checkbox per field
  - A **Select All / Deselect All** toggle per component
- [ ] If a `config/columns/<BoType>.csv` file already exists for this BO, fields listed in that file are pre-checked. All other fields are unchecked.
- [ ] If no column file exists yet, **all fields are pre-checked** (export everything by default).
- [ ] The admin can search/filter fields by display name or internal name within the picker.
- [ ] Clicking **"Apply"** (or equivalent) closes the picker and marks the BO row as having custom field selection (visual indicator, e.g. a badge "N fields selected").

---

### 2.5 — Saving the configuration

**As an** administrator, **I want** clicking "Save Configuration" to persist both the selected BO list and the field selections, **so that** the next export uses my choices without further manual file editing.

**Acceptance Criteria:**
- [ ] When the admin clicks **Save Configuration**, the server:
  1. Writes the checked BO types (internal name + display name) to the `boTypes` list in `config.yml`.
  2. For each BO that has a field selection (from the field picker), writes the selected field paths — one per line — to `config/columns/<BoType>.csv`, overwriting any existing file.
  3. For any BO whose field picker was never opened (no custom selection made), leaves the existing `config/columns/<BoType>.csv` untouched.
- [ ] Unchecking a BO and saving removes it from the `boTypes` list in `config.yml`. Its column CSV file is **not** deleted (preserved for if it is re-added later).
- [ ] A success toast is shown on save. If saving fails, a clear error message is displayed and no partial writes occur.
- [ ] The operator dashboard BO list reflects the updated selection immediately after save (no server restart needed).

---

### 2.6 — Handling large BO and field lists

**As an** administrator, **I want** the BO and field picker to remain usable even when CLM has dozens of BO types and hundreds of fields per BO, **so that** I can configure the export without the page becoming slow or hard to navigate.

**Acceptance Criteria:**
- [ ] The BO type table is paginated or virtualised if CLM returns more than 50 BO types. [NEEDS CLARIFICATION: confirm an acceptable threshold — 50 is assumed.]
- [ ] Within the field picker, components are collapsed by default when a BO has more than 5 components, requiring the admin to expand each one individually.
- [ ] The "Load from CLM" call for BO types, and the per-BO metadata call for the field picker, each have a visible timeout indicator. If either call takes longer than 30 seconds, an error is shown with a retry option.

---

### 2.7 — PDF and attachment downloads are scoped to the exported record set

**As an** operator, **I want** the PDF and attachment download steps to process only the contracts that were exported in the CSV step, **so that** a filtered run (e.g. date-filtered or BO-filtered) does not download attachments for records outside the filter.

**Background:** Today `RunExecutor` calls `resolveTrackingIds()` independently for the EXPORT_PDF and EXPORT_ATTACHMENTS steps. That method always calls the unfiltered `getTrackingNumbers()` CLM endpoint regardless of the date filter set on the dashboard. The result is that a run filtered to "Create Date from 01-01-2026" exports only matching contracts to CSV but downloads PDFs and attachments for every contract in CLM.

**Acceptance Criteria:**
- [ ] The tracking IDs collected during the EXPORT_CSV step (after applying the date filter or any other active filter) are retained in memory for the duration of the run and reused as the input for the EXPORT_PDF and EXPORT_ATTACHMENTS steps.
- [ ] The PDF download step processes only tracking numbers that were part of the CSV export in the same run — no extra CLM call to re-fetch tracking numbers is made for this step.
- [ ] The attachment download step processes only the same set of tracking numbers — consistent with the CSV step.
- [ ] If the CSV step produced zero exported records (all BOs returned empty), the PDF and attachment steps complete immediately with zero downloads and report Success (not an error).
- [ ] The existing behaviour for unfiltered runs (no date filter, all BOs) is unchanged — all tracking numbers fetched during CSV export are used for PDF and attachment steps.

---

## 3. Scope and Boundaries

### In-Scope

- Scoping PDF and attachment downloads to the tracking IDs collected by the EXPORT_CSV step, so that filtered runs do not download from outside the filter.
- New `adminEmails` field in `config.yml` and Admin Panel config form.
- Replacing the hardcoded `admin/admin` login with CLM-verified admin login.
- "Load from CLM" button in the Admin Panel BO Types section, backed by a new `GET /api/admin/bo-types` endpoint that calls CLM.
- BO type table: display name, usage type, checkbox selection, display name override field.
- Field picker per BO type, backed by a new `GET /api/admin/bo-metadata/{boType}` endpoint that calls CLM.
- Field picker: grouped by component, pre-populated from existing `config/columns/` files, searchable.
- Save Configuration writing `boTypes` in `config.yml` and `config/columns/<BoType>.csv` files.
- Operator dashboard BO list reflecting saved changes without restart.

### Out-of-Scope

- Editing individual field display name overrides (the field display name shown in the CSV header comes from CLM metadata; custom overrides are a separate feature via `config/overrides/parameter-displaynames.csv`).
- Deleting `config/columns/` files when a BO is unchecked.
- Bulk "select all BOs" / "deselect all BOs" (checkboxes are per-row; all-select can be a follow-up).
- Filtering the CLM BO list by usage type in the discovery table (the `boUsageTypeFilter` config field already handles this at export time).
- Scheduling or triggering exports from the Admin Panel (operator dashboard handles that).
- All other Spec-012 pipeline wiring features (separate specification).
