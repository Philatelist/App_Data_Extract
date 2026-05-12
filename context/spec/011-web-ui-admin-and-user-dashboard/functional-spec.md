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
| BO Types | Editable table with two columns — **Internal Name** (used by the job runner to identify the BO in CLM API calls) and **Display Name** (localized label shown to operators on the dashboard) | `boTypes[].name` / `boTypes[].localizedName` |
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

The dashboard is divided into four sections:
1. **Export Configuration** — parameters for the next run (hidden while a job is running)
2. **Export Status** — live step-by-step progress
3. **Scheduled Export** — active schedule info with management actions
4. **Export History** — log of past runs

#### Export Configuration Fields

**Enable Auto-Schedule toggle** (top of form)
- When **off**: one-time manual export mode. The CTA button reads "Start Export". Export Status panel is visible.
- When **on**: schedule mode. Schedule settings appear. Export Status panel is hidden. The CTA button reads "Schedule".

**Schedule Settings** (visible when Enable Auto-Schedule is on)
| Field | Notes |
|---|---|
| Frequency | Daily / Weekly / Monthly |
| Day of Week | Visible only when Frequency = Weekly |
| Time of Day | 24-hour time picker |
| Time Zone | Dropdown of common time zones |

**BO Selection**
- A grid of cards, one per BO type populated from the tool config.
- Each card shows the BO's **display name** (localized name configured in the Admin Panel) and the **last successful run date** for that BO. If no prior run exists the date shows as "—".
- All cards are unchecked by default. If no card is checked, the export runs for all BOs.
- After a job completes, last-run dates on the cards update automatically without a page refresh.

**Date Filter**
- **Date Field** dropdown: Create Date / Last Modified Date.
- **Date From**: date picker for the start of the filter window.
- **Date To**: date picker for the end of the filter window. Hidden when Enable Auto-Schedule is on.
- When Enable Auto-Schedule is on, a **Modified within the period** toggle appears. When checked, Date Field and Date From are also hidden; the export will include only contracts modified since the last scheduled run.

**SFTP Target Path** (required)
- Text field for the destination folder path on the SFTP server (e.g., `/exports/clm/q2-2025`).
- Validation fires on Start Export click; the field receives focus if left blank.

#### Export Trigger / Schedule CTA

- **Start Export** (auto-schedule off): validates SFTP path, fires `POST /api/run/start`, hides Export Configuration, shows Export Status with running badge and Stop button.
- **Schedule** (auto-schedule on): saves the schedule via `PUT /api/schedule`, shows or updates the Scheduled Export panel.
- **Stop** button: appears in the Export Status title row while a job is running; fires `POST /api/run/stop`.

#### Running State

While a job is in progress:
- The **Export Configuration** section is hidden entirely.
- The **Export Status** section header shows a pulsing green **"Last job still running"** badge and an active **Stop** button.
- On page reload, the running state is restored from `ui-state.json` and polling resumes automatically.

#### Export Status Panel

Displays real-time progress, with a row per step. Each row has a step name and a status pill: Pending (grey) → In Progress (blue) → Success (green) / Failed (red).

Steps:
1. Export — CSV Metadata
2. Export — Signed PDFs
3. Export — Attachments
4. Packaging (ZIP)
5. SFTP Upload

After the job completes, Export Configuration reappears and BO last-run dates refresh.

#### Scheduled Export Panel

Visible only when an active schedule is saved. Displays:
- Frequency (e.g., "Weekly, every Monday at 02:00 (America/New_York)")
- Business Objects (display names, or "All BOs")
- Date Filter summary

Two action buttons:
- **Edit**: re-fetches the saved schedule and fills all Export Configuration fields; scrolls to the form.
- **Delete**: calls `DELETE /api/schedule`, resets the schedule state, hides the panel.

#### Export History

A list of past runs, newest first. Each entry shows:
- Start date/time
- Duration
- Overall status pill (All Success / Failed / In Progress)
- Per-step badge row

**Acceptance Criteria:**
- [ ] Given I am logged in as a valid CLM user, when I open the User Dashboard, then I see BO cards showing localized display names and last-run dates.
- [ ] Given I select 2 BOs and click Start Export, then only those 2 BOs are exported.
- [ ] Given no BO is selected and I click Start Export, then all available BOs are exported.
- [ ] The SFTP Target Path field is required; clicking Start Export while it is blank shows a validation message.
- [ ] While a job is running, the Export Configuration section is hidden and the Export Status header shows the running badge and Stop button.
- [ ] Clicking Stop cancels the in-progress job.
- [ ] During an export run, each step pill updates in real time (Pending → In Progress → Success/Failed) via 2-second polling.
- [ ] After a successful run, BO last-run dates update on the dashboard without a page refresh.
- [ ] Given Enable Auto-Schedule is on and I click Schedule, the Scheduled Export panel appears with the saved configuration.
- [ ] Clicking Edit on the Scheduled Export panel fills all form fields from the saved schedule.
- [ ] Clicking Delete on the Scheduled Export panel removes the schedule and hides the panel.
- [ ] Export History shows all past runs with step-level status badges.

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
