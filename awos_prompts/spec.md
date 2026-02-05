# spec.md
**AWOS + Java CLM Data Extract — Canonical Functional Specification (Cold Start)**

> This document is the **single canonical functional specification** for building and running the project from zero.
> It combines the full end-to-end workflow (operational requirements) with the parsing/CSV invariants (core rules).
> Unless explicitly marked as a note, statements are **hard requirements**.

---

## 1. Purpose and Scope

The system is a **read-only Java CLI tool** that extracts structured Business Object (BO) data from SCLM/CLM via the official REST API and exports deterministic CSV files.

### In Scope
- Config-driven export (single `config.yml`)
- Endpoints file loading (`endpoints.yml`) in original format (read-only)
- Session management (login, auto re-login, logout)
- BO discovery (optional) and sequential BO processing
- Metadata retrieval + parsing (real API shape)
- Tracking IDs retrieval (optional filter) + batching
- Bundles retrieval + parsing (real API shape)
- Streaming CSV export (no full-BO accumulation)
- Attachments download manifest (downloads CSV)
- Backup and retention management
- Run logging (per REST call timing & status)
- Offline mode with real-format fixtures

### Out of Scope
- Any mutation of CLM data
- GUI / web UI
- Scheduling (cron etc.) — user handles externally
- Downloading binary attachments (v1 only produces manifests)
- Incremental/delta exports (each run is full extract)
- Parallel or concurrent BO processing

---

## 2. Success Criteria

Success is measured by:  
A single `config.yml` is sufficient to produce a complete unattended export run with:
- correctly formatted export CSVs
- downloads manifest CSV(s)
- run logs
- backups and retention cleanup

---

## 3. Execution Model (Batch Workflow)

- The tool runs as a **single batch job**.
- BOs are processed **strictly sequentially**.
- For each BO, the pipeline executes in this order:

1) Load & validate config  
2) Load endpoints registry (from `endpoints.yml`)  
3) Ensure session (login)  
4) Fetch & parse BO metadata  
5) Fetch tracking numbers (tracking IDs), apply optional filter  
6) Split tracking IDs into batches  
7) For each batch: call bundles + parse + immediately append CSV rows (streaming)  
8) Generate downloads CSV for the BO  
9) Cleanup BO state (no cross-BO accumulation)  
10) Repeat for next BO  
11) Logout (attempted even after failures)

Fail-fast on unrecoverable errors; logout failures must not fail the run.

---

## 4. Configuration

### 4.1 Config file
- The tool reads a single `config.yml` provided via `--config`.
- If `--config` is missing, print usage and exit non-zero.

Config must support:
- Server connection: base URL
- Credentials: username/password
- Endpoints file path (default: `inputs/endpoints.yml`)
- BO types list (optional). If absent/empty: discover via `getBoTypes`.
- Per-BO optional settings:
  - `trackingFilter` string supporting explicit IDs and inclusive numeric ranges:
    - Example: `"1000-2000, 3050, 4000-4500"`
    - IDs not present in API response are silently ignored
  - Per-BO `filenameTemplate` override (optional)
- CSV mode: `per-component` (default), `merged-single`, `single-only`
- CSV delimiter (default `,`) — NOTE: delimiter is configurable; many environments use `;`
- Filename template with placeholders: `{BO}`, `{Component}`, `{DDMMYYYY}`, `{HHMMSS}`
- Output root folder + main export folder name
- Batch size
- Backup retention days

### 4.2 Endpoints file
- Load `endpoints.yml` from config path (default `inputs/endpoints.yml`).
- The file must be consumed in its **original format** (tool must not modify it).
- Implement an adapter that resolves required operations:
  - `login`
  - `logout`
  - `getBoMetaData`
  - `getTrackingNumbers`
  - `bundles`
  - `getBoTypes` (required only if BO discovery is used)
- If required ops cannot be resolved, exit with a clear error naming the missing operation.

---

## 5. Session Management

- Before any authenticated call, ensure an active session exists.
- Login is performed automatically if no session exists.
- If the session expires (401/403 or equivalent API error):
  - re-login automatically
  - retry the failed request once
- At the end of the run (success or failure), attempt logout.
- Logout failures:
  - log a warning
  - must not cause non-zero exit

---

## 6. Output Directory Structure

Under the configured output root, create:

```
<output-root>/
  <main-export-folder>/     (configurable name, e.g., "MetaData/")
  backups/
  logs/
  downloads/
```

- Export CSVs are written to `<main-export-folder>/`.
- Downloads CSVs are written to `downloads/`.
- Run logs are written to `logs/`.

---

## 7. Backup Retention

- Before starting a new run, copy the current contents of `<main-export-folder>/` into `backups/<timestamp>/`.
- After a successful run, delete backup subdirectories older than retention days:
  - if retention = 0: delete all previous backups
- Backups within retention window are preserved.

---

## 8. Per-BO Processing Pipeline

### 8.1 Metadata Retrieval
- Call `getBoMetaData` for the BO.
- Parse response to extract:
  - component internal name
  - component display name
  - field internal name
  - field display name
  - data type (if available)
  - cardinality (single vs multiple)
  - instance path

#### Metadata API shape requirement
- Metadata response is a **flat node list**, not nested JSON.
- Hierarchy must be reconstructed using:
  - `id`
  - `parentId`
  - `listType`

#### DTO/domain separation
- DTOs mirror API JSON.
- Domain models are annotation-free and represent components/fields/cardinality.

Keep metadata in memory only for the current BO.

### 8.2 Tracking Numbers Retrieval
- Call `getTrackingNumbers` for the BO.
- Keep tracking IDs in memory only for current BO.
- If `trackingFilter` configured:
  - include explicit IDs
  - include inclusive ranges
  - ignore non-matching IDs silently

### 8.3 Bulk Data Fetch and Incremental Writing
- Split tracking IDs into batches of configured batch size.
- For each batch, call `bundles` with:
  - `trackingIds`: the batch IDs (as numbers)
  - `fieldPaths`: derived list of field paths (see section 10)

**Streaming requirement (hard):**
- After each batch response is parsed, append rows to CSV immediately.
- The tool must never hold the full BO dataset in memory; only current batch data may be held.

---

## 9. Bundles Parsing

### 9.1 Response shape
- Bundles response is an **array of arrays**.
- Each top-level array entry corresponds to one tracking ID in the request.

### 9.2 Tracking ID semantics (hard)
- **Primary source of truth** for tracking ID is the request `trackingIds[]`.
- Records are matched **positionally**:
  - record[i] corresponds to requestTrackingIds[i]
- Fallback to response `trackingNumber` is allowed only if request IDs are absent or response size mismatches.

### 9.3 InstancePath normalization (hard)
- Support paths with prefixes `MCPDef:/` or `MCP:/`.
- Strip instance suffix after `|` (e.g., `.../Param|123` → `.../Param`).
- Normalize to:

```
Module/Component/Parameter
```

### 9.4 Cardinality handling
- Single-cardinality components produce one logical row per tracking ID.
- Multi-cardinality components produce multiple rows per tracking ID (one row per child entry), each row includes the tracking ID.

---

## 10. Field Paths, Column Selection, and Ordering

### 10.1 Base rule
`fieldPaths` used for bundles calls are derived from metadata, then optionally constrained by overrides.

### 10.2 BO Parameter Order File (in-repo override)
- Location:
```
inputs/overrides/bo-parameters/{BO}.csv
```
- Format:
  - plain text
  - one normalized path per line:
    - `Module/Component/Parameter`
  - no header
  - ignore empty/whitespace lines

### 10.3 Behavior when order file exists (hard)
- Export **only** the paths listed in the file.
- Column order = file order.
- Components with no listed parameters are excluded.
- **Paths not found in metadata are still kept as columns**:
  - values come from bundles if present, else empty

### 10.4 Behavior when order file is absent (note)
- Default: export all metadata fields in API order.
- (If implementation chooses a deterministic sort, it must be documented; otherwise keep API order.)

---

## 11. Display Name Overrides and Header Construction

### 11.1 Global display name override file
- Location:
```
inputs/overrides/parameter-displaynames.csv
```
- Format (semicolon-delimited, header required):
```
Component;Parameter;DisplayName;
```
- Keys use **internal names**:
  - Component = internal component name
  - Parameter = internal parameter name

Malformed rows may be skipped.

### 11.2 Display name precedence (hard)
1) `parameter-displaynames.csv`
2) metadata field displayName
3) bundles DisplayName (if present)
4) internal parameter name

### 11.3 Header format (hard)
For non-tracking columns:
```
<ComponentDisplayName>.<ResolvedParameterDisplayName>
```

---

## 12. CSV Generation

### 12.1 CSV modes
- Default (`per-component`): one CSV per component, each contains only that component’s fields.
- `merged-single`: all single-cardinality components merged into one CSV; multi-cardinality components each separate.
- `single-only`: export only single-cardinality components (merged into one); skip multi-cardinality.

### 12.2 Tracking column (hard)
Every export CSV must include a tracking column as the first column.
Header must be:

```
Summary.Tracking #
```

Row order must match request tracking ID order.

### 12.3 Delimiter and quoting (hard)
- Delimiter is configurable.
- **No quoting is allowed** in any CSV writer (headers or data):
  - Use `ICSVWriter.NO_QUOTE_CHARACTER`.

> NOTE: disabling quoting may produce non-RFC CSV when values contain delimiter/newlines.
> This is a hard requirement for this project.

### 12.4 Filenames (hard)
- Use the configured filename template:
  - `{BO}`, `{Component}`, `{DDMMYYYY}`, `{HHMMSS}`
- `{BO}` uses internal BO name (not usageType).
- `{Component}` uses component display name but must be sanitized:
  - spaces → `_`
  - non `[A-Za-z0-9._-]` → `_`
  - collapse multiple `_`
  - trim leading/trailing `_` and `.`

Timestamp tokens are evaluated once per BO run.

---

## 13. Downloads CSV (Attachments Manifest)

### 13.1 Purpose
Generate a downloads manifest listing server-side file paths for attachments.

### 13.2 Rules (hard)
- Generate one downloads CSV per BO in `downloads/`.
- CSV contains **one column only**, **no header**, **no quotes**.
- Data source components:
  - `ReqAttachment`
  - `ReqContractAttachment`
- Data source field:
  - `serverFileName`
- No fallback to other fields is allowed.

If no relevant attachments component exists for a BO, still generate an empty file for that BO.

---

## 14. Logging

- Write a per-run log file in `logs/`.
- For each REST call, capture:
  - start time
  - end time
  - elapsed time
  - BO (if applicable)
  - endpoint/action name
  - success/failure
  - error details (if applicable)
- Also write key milestones to stdout:
  - login
  - BO start/end
  - record counts / batch counts
  - logout

---

## 15. Offline Mode

- Offline mode uses **real API-shaped JSON fixtures** for:
  - metadata
  - bundles
- The pipeline is identical; only data source changes.
- Outputs (structure) must match online mode for the same fixture inputs.

---

## 16. State Cleanup (Hard)

After completing a BO:
- clear metadata
- clear tracking IDs
- clear batch buffers
- clear any record accumulators

No data from BO A may remain when BO B begins.

---

## 17. Determinism Guarantees

The system guarantees:
- deterministic filenames for a run (template-driven with single timestamp capture per BO)
- deterministic column order (override-driven or API order)
- deterministic row order (request tracking ID order)
- repeatable output for identical inputs

---

**END OF SPEC**
