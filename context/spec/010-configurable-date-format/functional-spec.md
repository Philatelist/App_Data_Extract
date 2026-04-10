# Functional Specification: Configurable Date Format Output

- **Roadmap Item:** Configurable date format for CSV output
- **Status:** Draft
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

The CLM API returns date and datetime field values in fixed formats (e.g., `MM/DD/YYYY` and `MM/DD/YYYY HH:MM:SS`). Today the tool writes these values into CSV files exactly as received. This creates friction for downstream consumers — legal teams, migration teams, reporting tools — who may require a different format, such as `DD/MM/YYYY` used in European locales.

Administrators need to control both the date and datetime output formats through `config.yml` without modifying code or recompiling. The input formats must also be configurable to protect against future API changes.

**Success looks like:** An admin changes a few lines in `config.yml`, reruns the export, and all date and datetime fields in all CSVs are written in the new format.

---

## 2. Functional Requirements (The "What")

### 2.1 Configuring the Date and Datetime Formats

- The admin can specify format settings in `config.yml` under a `dateFormat` section.
- The section contains **two independent pairs** — one for date-only fields, one for datetime fields:
  - `inputFormat` / `outputFormat` — for fields the API identifies as date-only (e.g., `MM/DD/YYYY`)
  - `inputDateTimeFormat` / `outputDateTimeFormat` — for fields the API identifies as datetime (e.g., `MM/DD/YYYY HH:MM:SS`)
- Format strings use standard date-time pattern notation (e.g., `MM/dd/yyyy`, `dd/MM/yyyy HH:mm:ss`).
- If the `dateFormat` section is **absent**, all date and datetime values are written to CSV exactly as received. Current behaviour is preserved for users who have not opted in.
- Each pair is independent: an admin can configure only `inputFormat`/`outputFormat` (date-only) without configuring datetime formats, and vice versa.
- If a pair is partially configured (e.g., `inputFormat` present but `outputFormat` missing), the tool must **fail at startup** with a clear error message before making any API calls.

**Acceptance Criteria:**
- [ ] Given no `dateFormat` section, all date and datetime values are written unchanged.
- [ ] Given only `inputFormat`/`outputFormat` configured, date-only fields are reformatted; datetime fields are written as-is.
- [ ] Given only `inputDateTimeFormat`/`outputDateTimeFormat` configured, datetime fields are reformatted; date-only fields are written as-is.
- [ ] Given all four entries configured, both date and datetime fields are independently reformatted.
- [ ] Given `inputFormat` present but `outputFormat` missing (or vice versa), the tool exits at startup with a message identifying the missing field — before any API call is made. Same applies to the datetime pair.

**Example config:**
```yaml
dateFormat:
  inputFormat:          "MM/dd/yyyy"
  outputFormat:         "dd/MM/yyyy"
  inputDateTimeFormat:  "MM/dd/yyyy HH:mm:ss"
  outputDateTimeFormat: "dd/MM/yyyy HH:mm:ss"
```

### 2.2 Scope of Reformatting

- Format settings apply **globally** to all fields the API identifies as date or datetime type, across all components and all BO types in the same run.
- There is no per-field or per-component override in this version.

**Acceptance Criteria:**
- [ ] All date-type fields across all components and BO types are reformatted using the configured date pair.
- [ ] All datetime-type fields across all components and BO types are reformatted using the configured datetime pair.

### 2.3 Handling Malformed or Empty Values

- If a date or datetime field value does not match its configured input format (including empty or null values), the tool writes the **original value as-is** to the CSV.
- A **warning is logged** for each such value, including the field name, the offending value, and the expected input format.
- The run continues normally — malformed values do not stop the export.

**Acceptance Criteria:**
- [ ] Given an empty date field, the CSV cell is written empty and a warning is logged.
- [ ] Given a value that does not match the configured input format, the original value is written and a warning is logged.
- [ ] The warning includes the field name, the value received, and the expected input format.
- [ ] The export run completes successfully despite malformed values.

---

## 3. Scope and Boundaries

### In-Scope

- A `dateFormat` section in `config.yml` with four optional string fields: `inputFormat`, `outputFormat`, `inputDateTimeFormat`, `outputDateTimeFormat`.
- Fail-fast startup validation when a format pair is partially configured.
- Global reformatting of all API-identified date-type and datetime-type fields in CSV output.
- As-is passthrough with a logged warning for values that fail to parse.

### Out-of-Scope

- Per-field or per-component format overrides (may be addressed in a future spec).
- Timezone conversion or locale-based formatting.
- Any change to how dates are stored or transmitted by the CLM API.
