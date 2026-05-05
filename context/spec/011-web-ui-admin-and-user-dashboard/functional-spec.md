# Functional Specification: Web UI — Admin Panel & User Dashboard

- **Roadmap Item:** Web UI — Admin Panel & User Dashboard
- **Status:** Approved
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

The CLM Data Extract tool currently runs as a headless CLI application driven by a `config.yml` file. To use it, an administrator must have file-system access to the server, technical knowledge of YAML syntax, and familiarity with all configuration keys. Triggering an export requires running a command in a terminal. There is no way for a non-technical operator to launch an export, monitor its progress, or see results without reading raw log files.

This feature introduces a web-based UI that gives two distinct types of users controlled access to the system through a browser:

- **Administrators** can view and update all tool configuration (currently in `config.yml`) through a form-based interface, removing the need for direct file-system access.
- **Operators (Users)** can select Business Object types for export, configure date-range parameters, trigger manual exports, and monitor the progress and results of each run — all without touching the command line.

**Success is measured by:**
- An administrator can change any configuration property and save it without editing `config.yml` directly.
- An operator can trigger a complete export-to-SFTP run and see its outcome from a browser with no technical assistance.

---

## 2. Functional Requirements (The "What")

### 2.1 Login Page

**As any user, I want to see a login form when I open the Export Tool URL, so that I can authenticate before accessing any functionality.**

- The login page presents a username field, a password field, and a "Login" button.
- There is a single login page that serves both admin and operator roles; the destination after login depends on the credentials entered.
- **Admin credentials:** username `admin`, password `admin` (hardcoded for this version).
- **Operator credentials:** validated by making a REST call to the CLM system using the entered username and password. If CLM returns a valid session, the user is authenticated as an operator.
- If admin credentials match, the user is redirected to the Admin Panel.
- If CLM authentication succeeds, the user is redirected to the User Dashboard.
- If neither condition is met, the login page displays an authentication error message. [NEEDS CLARIFICATION: What should the exact text of the authentication error message be?]
- The login page does not reveal whether the failure was due to an incorrect username or an incorrect password.

**Acceptance Criteria:**
- [ ] Given I enter `admin` / `admin`, when I click Login, then I am taken to the Admin Panel page.
- [ ] Given I enter valid CLM credentials, when I click Login, then I am taken to the User Dashboard page.
- [ ] Given I enter incorrect credentials (not admin/admin and CLM rejects them), when I click Login, then I remain on the login page and an error message is displayed.
- [ ] No authenticated pages are accessible without first logging in (navigating directly to a protected URL redirects to the login page).

---

### 2.2 Admin Panel

**As an administrator, I want to view and edit all tool configuration properties through a web form, so that I can change the tool's behavior without editing files on the server.**

The Admin Panel displays all configuration properties currently defined in `config.yml`, grouped into labeled sections. Every property is editable. At the bottom of the page there is a single **Save** button.

When the admin clicks **Save**, the changes are validated and written to `config.yml` on disk. Changes take effect on the next export run. No restart of the service is required.

#### Config Sections and Fields

**Server Connection**
| Field | Type | Config key |
|---|---|---|
| Server URL | Text | `server.url` |
| Username | Text | `server.username` |
| Password | Password (masked) | `server.password` |

**API & Endpoints**
| Field | Type | Config key |
|---|---|---|
| Endpoints File Path | Text | `endpointsFile` |
| Batch Size | Number (integer ≥ 1) | `batchSize` |
| Max Retry Attempts | Number (integer ≥ 0) | `retry.maxAttempts` |
| Retry Base Delay (ms) | Number (integer ≥ 0) | `retry.baseDelayMs` |
| Offline Mode | Toggle (on/off) | `offlineMode` |

**BO Type Selection**
| Field | Type | Config key |
|---|---|---|
| BO Types | Editable list of names (add/remove) | `boTypes[].name` |
| BO Usage Type Filter | Dropdown (Directory / NonContract / Contract / [blank]) | `boUsageTypeFilter` |
| Tracking Filter | Text | `trackingFilter` |

**Output & Files**
| Field | Type | Config key |
|---|---|---|
| Output Root Directory | Text | `outputRoot` |
| Export Folder Name | Text | `exportFolderName` |
| Backup Retention (days) | Number (integer ≥ 0) | `backupRetentionDays` |
| CSV Mode | Dropdown (per-component / merged-single / single-only) | `csvMode` |
| Delimiter | Single-character text | `delimiter` |
| Filename Template | Text | `filenameTemplate` |
| Downloads Filename Template | Text | `downloadsFilenameTemplate` |
| Generate Summary CSV | Toggle | `generateSummaryCsv` |
| Summary Filename Template | Text (shown when Generate Summary CSV is on) | `summaryFilenameTemplate` |
| Generate Parent CSV | Toggle | `generateParentCsv` |
| Parent Filename Template | Text (shown when Generate Parent CSV is on) | `parentFilenameTemplate` |

**Column & Component Filtering**
| Field | Type | Config key |
|---|---|---|
| Skip Columns | Editable list of column names | `skipColumns` |
| Skip Components | Editable list of component names | `skipComponents` |
| Additional Columns | Editable list of (Header name, Position) pairs | `additionalColumns` |

**Delimiter Replacement**
| Field | Type | Config key |
|---|---|---|
| Delimiter Replacement Enabled | Toggle | `delimiterReplacement.enabled` |
| Substitute Character | Single-character text (shown when enabled) | `delimiterReplacement.substituteChar` |

**Yes/No Translation**
| Field | Type | Config key |
|---|---|---|
| Yes/No Translation Enabled | Toggle | `yesNoTranslation.enabled` |
| True Value Text | Text (shown when enabled) | `yesNoTranslation.trueValue` |
| False Value Text | Text (shown when enabled) | `yesNoTranslation.falseValue` |

**Date Format**
| Field | Type | Config key |
|---|---|---|
| Input Date Formats | Editable list of format strings | `dateFormat.inputFormats` |
| Output Date Format | Text | `dateFormat.outputFormat` |
| Input DateTime Formats | Editable list of format strings | `dateFormat.inputDateTimeFormats` |
| Output DateTime Format | Text | `dateFormat.outputDateTimeFormat` |

**SFTP Connection** *(new section — not currently in config.yml)*
| Field | Type | Config key |
|---|---|---|
| SFTP Host | Text | `sftp.host` |
| SFTP Port | Number (integer) | `sftp.port` |
| SFTP Username | Text | `sftp.username` |
| SFTP Password | Password (masked) | `sftp.password` |

**Acceptance Criteria:**
- [ ] When I open the Admin Panel, all fields are pre-populated with the current values from `config.yml`.
- [ ] When I change one or more fields and click Save, the updated values are written to `config.yml` on disk.
- [ ] When I click Save with a required field left blank (Server URL, Endpoints File), the form displays a validation error and does not save.
- [ ] When I click Save with an invalid value (e.g., non-integer for Batch Size, multi-character Delimiter), the form displays a field-level error and does not save.
- [ ] When Save completes successfully, a success confirmation is displayed on the page.
- [ ] Editable lists (Skip Columns, Skip Components, BO Types, Additional Columns, etc.) support adding a new entry and removing an existing entry.
- [ ] Conditional fields (Summary Filename Template, Parent Filename Template, Substitute Character, True/False Value text) are hidden when their parent toggle is off and visible when it is on.

---

### 2.3 User Dashboard

**As an operator, I want a dashboard where I can select BO types, configure the export date range, trigger an export, and see the results — so that I can run and monitor exports without using the command line.**

The dashboard is divided into three areas:
1. **Export Configuration** — parameters for the next run
2. **Export Trigger** — the action button
3. **Status Panel** — live progress and final results

#### Export Configuration Fields

**BO Selection**
- A list of checkboxes, one per available BO type (e.g., NAF, GPE, GPNY, PGCS — populated from the tool's known/configured BO types).
- If no checkbox is selected, the export runs for all available BO types.
- Next to each BO name, the **Last Successful Run Date** for that BO is displayed (read from the system's run history). [NEEDS CLARIFICATION: What format should this date be displayed in? e.g., DD/MM/YYYY or relative "3 days ago"?]
- If no prior successful run exists for a BO, the date shows as "Never". [NEEDS CLARIFICATION: Preferred display text for a BO with no run history?]

**Frequency**
- A picker allowing the user to select the export frequency: Daily, Weekly, Monthly. [NEEDS CLARIFICATION: Are these the complete set of frequency options, or should others be included?]
- The selected frequency serves two purposes:
  1. **Date calculation:** When computing the default export window, the frequency determines how far back from the last successful run date the export window extends.
  2. **Auto-schedule (optional):** The user can enable an automatic scheduled export for the selected frequency. When enabled, exports run automatically at the configured interval without requiring the user to click the trigger button. [NEEDS CLARIFICATION: At what time of day should auto-scheduled exports run? Configurable or fixed midnight?]

**Manual Override Date**
- A date picker field labeled "Override Last Run Date."
- When filled in, this date is used as the "from" date for the export window instead of the system-tracked last successful run date.
- When left blank, the system uses the stored last successful run date for each selected BO.

**SFTP Target Path**
- A text field for the destination folder path on the SFTP server (e.g., `/exports/clm/q2-2025`).
- This path is relative to / appended to the SFTP root configured in the Admin Panel.
- This field is required before export can be triggered. [NEEDS CLARIFICATION: Should this field remember/pre-fill the last-used path?]

#### Export Trigger

- A prominent **"Start Export"** button.
- When clicked, the button is disabled and the Status Panel activates.
- Export logic:
  - If Manual Override Date is filled → use that date as the "from" date for all selected BOs.
  - If Manual Override Date is empty → use the per-BO stored last successful run date as the "from" date.
  - Contracts are included in the export if any field's modification date falls between the "from" date and the moment the export is triggered.
  - The current state of each contract (not a diff) is exported.
- If no BO is selected, the export runs for all available BOs.

#### Status Panel

Displays real-time progress during an export run, with a row per step. Each row has:
- A step name
- A status indicator: In Progress (spinner), Success (green), Failed (red), Pending (grey)

Steps displayed:
1. **Export — CSV** (metadata per contract)
2. **Export — PDF** (signed contract documents)
3. **Export — Attachments** (other attached files)
4. **Packaging** (ZIP creation; if > 200MB, split into ≤ 200MB parts)
5. **SFTP Upload**

After the run completes, each step retains its final green/red status, giving the operator a clear success/failure summary.

Generated file naming (displayed to operator as reference):
- Metadata CSV: `Summary<Contract_ID>.CSV`
- Signed Contract PDF: `<Contract_ID>.pdf`
- Other attachments: `<Contract_ID>_filename.ext`
- ZIP archive: `<YYYYMMDD><HH24MMSS>.zip` (split into numbered parts if > 200MB)

**Acceptance Criteria:**
- [ ] Given I am logged in as a valid CLM user, when I open the User Dashboard, then I see BO checkboxes with the last successful run date shown next to each.
- [ ] Given I select 2 BOs and click Start Export, then only those 2 BOs are exported.
- [ ] Given no BO is selected and I click Start Export, then all available BOs are exported.
- [ ] Given I fill in the Manual Override Date, when the export runs, then that date is used as the "from" date for all selected BOs regardless of stored last-run dates.
- [ ] Given I leave Manual Override Date blank, when the export runs, then each BO uses its own stored last successful run date.
- [ ] The SFTP Target Path field must not be empty before the Start Export button is clickable (or a validation error is shown if clicked while empty).
- [ ] During an export run, the Status Panel updates each step's indicator in real time (Pending → In Progress → Success/Failed).
- [ ] After the run completes, the Status Panel shows a final green or red indicator per step.
- [ ] After a successful run, the Last Successful Run Date next to each processed BO is updated to the current run's timestamp.
- [ ] Given I enable auto-schedule with Weekly frequency, then the export runs automatically once per week without requiring manual trigger. [NEEDS CLARIFICATION: Should the auto-schedule be cancelable from the dashboard, and does it show the next scheduled run time?]

---

## 3. Scope and Boundaries

### In-Scope

- A web-based login page routing to Admin Panel or User Dashboard based on credentials.
- Admin Panel with all `config.yml` properties editable via a form, plus new SFTP connection fields (`sftp.host`, `sftp.port`, `sftp.username`, `sftp.password`).
- User Dashboard with BO selection, last-run dates per BO, frequency picker (with optional auto-schedule), manual override date, SFTP target path, export trigger, and live status panel.
- Delta export logic: include contracts modified since the effective "from" date.
- File packaging: ZIP with 200MB part splitting.
- SFTP upload to the configured server and user-specified target path.
- Post-run status persisted so the dashboard shows updated last-run dates after page refresh.

### Out-of-Scope

- Downloading attachment binary files to the user's browser (SFTP-to-server transfer only).
- User management (adding/editing/deleting user accounts beyond the two hardcoded roles).
- Multi-user simultaneous exports from the same session.
- Role-based permissions beyond the two existing roles (admin / operator).
- All other roadmap items: Java CLI skeleton, Endpoint Loading, Authentication Lifecycle, Single-BO Pipeline, Multi-BO Processing, Backup Management, Offline Test Mode, Downloads List Generation (these remain CLI-only features addressed in separate specifications).
