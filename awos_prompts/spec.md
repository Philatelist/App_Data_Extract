# End-to-End Export Workflow Specification

## Overview

Implement an end-to-end export workflow driven by:

- a **user configuration file**
  (property names and action names are implementation-defined but must support all requirements below)

- a **user-provided endpoints definition file** located at
  `inputs/endpoints.json`
  - the file must be consumed in its **original format**
  - the file must **not be modified**
  - an adapter / parser must be implemented

---

## Session Rules

### Session Validation and Login

- Before any operation that requires a session, validate whether the current session is active.
- If the session is valid, reuse it without re-authentication.
- If the session is missing or inactive, perform automatic login.

### Logout

- At the end of the run, including failure scenarios, attempt to log out.
- Logout failures must not prevent run completion.

---

## Output Structure (Per Run)

Under a configurable **output root folder**, create the following structure:

```
<output-root>/
├─ <main-export-folder>/   (configurable name, e.g. "MetaData/")
├─ backups/
├─ logs/
├─ downloads/
```

### Backups Retention

- Backup retention must be configurable in days.
- Expired backups must be deleted automatically.

---

## Export File Rules

### Filename Format

- Export filename format must be configurable using a template, for example:

```
{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv
```

### CSV Format

- CSV delimiter must be configurable.

---

## Business Object (BO) Handling

### BO Source

- BOs may be:
  - explicitly listed in the configuration, **or**
  - discovered using an endpoint from the endpoints definition file

- If the BO list (or `boType`) in the configuration is **empty or not specified**, all BOs returned by the discovery endpoint must be processed.
- If the BO list is specified in the configuration, **only** the listed BOs must be processed.
- BO name/type is not known upfront.

### Execution Order

- BOs must be processed **strictly sequentially**.

---

## Per-BO Processing Flow

For each BO, execute the following steps in order:

### 1. Metadata Retrieval

- Fetch BO metadata.
- Keep metadata **in memory only for the current BO**.

### 2. Tracking Numbers Retrieval

- Fetch tracking numbers.
- Keep tracking numbers **in memory only for the current BO**.

#### Optional Filtering

- Tracking numbers may be filtered using a configuration rule that supports:
  - explicit IDs
  - numeric ranges

### 3. Bulk Data Fetch

- Fetch bulk data in batches.
- Batch size must be configurable.
- Use a bundles-like endpoint that accepts:
  - `trackingIds`
  - `fieldPaths`

### Incremental Writing and Memory Usage

- Data must be written to output files **incrementally**, immediately after each batch request for a defined number of tracking numbers.
- The implementation must not keep the full dataset of a single BO in memory.
- Only the minimum data required for the current batch may be held in memory at any time.

### 4. CSV Generation

Default behavior:
- Generate CSV files **per component**.

Optional behaviors (configurable):
- Merge all single-cardinality components into a single CSV.
- Export only single-cardinality components.

### 5. Downloads CSV

- Generate a downloads CSV per BO.
- The CSV must contain **only one column**:
  - attachment file path
- Data must come from the **Attachments** component for that BO.

- The downloads CSV must use a **dedicated filename template** defined by the configuration property `downloadsFilenameTemplate`.
- Default example: `{BO}_AttachmentsToDownload_{DDMMYYYY}_{HHMMSS}.csv`.

- If the BO has no Attachments component, generate an **empty** downloads CSV file.

### 6. State Cleanup

- After finishing a BO, clear all temporary state before processing the next BO.

---

## Column Rules

### Column Naming

- CSV header columns must follow the format:

```
ComponentDisplayName.ParameterDisplayName
```

### Tracking Number Column

- Every generated CSV file must include the tracking number column.
- The tracking number column must be the **first column** in the CSV.
- The column name must be **`Tracking #`**.

### Parameters Order File (Optional)

- A per-BO parameters-order file may be provided.
- File path must be configurable.
- When present:
  - export only parameters listed in the file
  - preserve the exact order defined
  - omit all parameters not listed

### Parameter Name Overrides (Optional)

- An override table may be provided to rename `ParameterDisplayName` in outputs.

---

## Logging

### Run Log

- A run log must be written to the `logs/` folder.

### REST Call Logging

For each REST call, log:

- start time
- end time
- elapsed time
- BO
- endpoint / action name
- success or failure status
- error details (if applicable)

---

## Failure Handling

- Abort the entire run if any BO fails.
- At the end of the run, including failure scenarios, attempt to log out.
- Partial exports produced before the failure must remain intact and logged.

