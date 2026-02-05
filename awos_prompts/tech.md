# tech.md
AWOS + Java CLM Data Extract — Canonical Technical Architecture (Cold Start)

> This document is the **single canonical technical reference** for the project.
> It defines structure, responsibilities, and non-negotiable implementation rules.
> It is sufficient to implement the system from zero without any other tech notes.

---

## 1. Technology Stack

### 1.1 Language & Runtime
- Java 17
- Maven (single-module build)
- Shaded / fat JAR for execution

### 1.2 Libraries
- HTTP: `java.net.http.HttpClient`
- JSON: Jackson Databind
- YAML: SnakeYAML
- CSV: OpenCSV
- Logging: Log4j2
- Testing: JUnit 5

No other runtime dependencies are required.

---

## 2. Project Structure

```
src/
 └─ main/
    └─ java/
       └─ com/clmextract/
          ├─ app/              # CLI entry, lifecycle
          ├─ config/           # config.yml, endpoints.yml loaders
          ├─ session/          # login/logout/session retry
          ├─ http/             # HTTP executor, retry, logging
          ├─ metadata/         # metadata DTOs + domain model
          ├─ bundles/          # bundles parsing & normalization
          ├─ tracking/         # tracking IDs retrieval & filtering
          ├─ csv/              # CSV writers
          ├─ overrides/        # column + display name overrides
          ├─ filenames/        # filename resolution & sanitization
          ├─ downloads/        # attachments CSV generation
          ├─ backup/           # backup & retention
          └─ offline/          # offline fixtures support
```

Each package has **single responsibility**.
Cross-package access must be minimal and explicit.

---

## 3. Application Lifecycle

1. Parse CLI args  
2. Load and validate `config.yml`  
3. Load and resolve `endpoints.yml`  
4. Initialize logging and output folders  
5. Login (session start)  
6. For each BO:
   - metadata
   - tracking IDs
   - bundles (batched)
   - CSV export (streaming)
   - downloads CSV
   - cleanup BO state
7. Logout
8. Exit

Failure at any step aborts the run, except logout.

---

## 4. Configuration Handling

### 4.1 Config Loading
- YAML → DTO → validated domain config
- Config is immutable after load
- Missing required fields = immediate failure

### 4.2 Endpoints Resolution
- Endpoints are resolved by **semantic operation name**
- Tool must not assume endpoint paths
- Missing required operation is fatal

---

## 5. Session & HTTP Layer

### 5.1 HTTP Execution
- All REST calls go through a single executor
- Executor is responsible for:
  - headers
  - payload
  - response status mapping
  - retry eligibility

### 5.2 Retry Policy
- Retry only for:
  - IO errors
  - HTTP 5xx
- No retry for:
  - validation errors
  - HTTP 4xx (except session expiry)

### 5.3 Session Expiry
- On 401/403:
  - invalidate session
  - login again
  - retry original request once

---

## 6. Metadata Architecture

### 6.1 Metadata DTOs
- Mirror API JSON exactly
- No business logic

### 6.2 Domain Model
- Built from DTOs
- Represents:
  - BO
  - components
  - fields
  - cardinality
- Annotation-free

### 6.3 Hierarchy Reconstruction
- Metadata API returns flat nodes
- Parent/child reconstructed via:
  - `id`
  - `parentId`
  - `listType`

---

## 7. Tracking IDs Architecture

- Tracking IDs are requested explicitly
- Request order is preserved
- Tracking ID is attached to record context early
- No implicit reliance on bundle payload values

---

## 8. Bundles Parsing Architecture

### 8.1 Response Shape
- Bundles response = array of arrays
- One inner array per tracking ID

### 8.2 InstancePath Normalization
- Strip `MCPDef:/` or `MCP:/`
- Strip instance suffix after `|`
- Normalize to:
```
Module/Component/Parameter
```

### 8.3 Cardinality Handling
- Single-cardinality → one row
- Multi-cardinality → N rows

---

## 9. Column Resolution

### 9.1 Column Order Files
Location:
```
inputs/overrides/bo-parameters/{BO}.csv
```

Rules:
- One path per line
- No header
- Paths may not exist in metadata

### 9.2 Column Resolver Responsibilities
- Load override file
- Validate format
- Preserve order exactly
- Resolve metadata matches
- Allow missing metadata paths

---

## 10. Display Name Resolution

### 10.1 Global Override File
```
inputs/overrides/parameter-displaynames.csv
```

### 10.2 Resolution Order
1. Override file
2. Metadata displayName
3. Bundle displayName
4. Internal name

---

## 11. CSV Writers Architecture

### 11.1 Common Rules (Hard)
- **Streaming write**: append batch-by-batch; do not accumulate full BO datasets.
- **Headers written once** when a writer opens its file.
- **No quotes anywhere** (headers or data):
  - OpenCSV must be configured with `withQuoteChar(ICSVWriter.NO_QUOTE_CHARACTER)`.
- **Tracking column** is always the first column in every export CSV:
  - header literal: `Summary.Tracking #`
- **Deterministic row order**:
  - row order equals request `trackingIds[]` order (see spec).
- **Deterministic column order**:
  - follows ColumnResolver output exactly.

### 11.2 Writers
- `PerComponentCsvWriter`
  - one CSV per component
  - multi-cardinality expands to multiple rows per tracking ID
  - single-cardinality produces one row per tracking ID
- `MergedSingleCsvWriter`
  - merges all single-cardinality components into one file
  - multi-cardinality components exported separately
- `SingleOnlyCsvWriter`
  - exports only single-cardinality (merged into one file)
  - multi-cardinality skipped entirely

All writers should share a small common base (open/close/header/writeRow utilities) to enforce invariants.

---

## 12. Attachments Handling

### 12.1 Downloads manifest (Hard)
Downloads CSV is a **manifest** used for later bulk download. v1 does **not** download binaries.

### 12.2 Source components and field (Hard)
For the downloads manifest only, use **exact component whitelist**:
- `ReqAttachment`
- `ReqContractAttachment`

Field:
- `serverFileName`

No fallback to other fields is allowed (avoid accidental `version -> "1"` outputs).

### 12.3 Downloads CSV format (Hard)
- One file per BO in `downloads/`
- Exactly **one column**
- **No header**
- **No quotes** (`NO_QUOTE_CHARACTER`)
- Write one `serverFileName` per line; skip empty values

> Note: elsewhere in the system you may still treat "attachment-like" components generically,
> but the downloads manifest is intentionally strict and whitelist-based.

---

## 13. Filenames & Output

- Filenames via templates
- `{BO}` = metadata internal name
- `{Component}` = display name (sanitized)
- One timestamp per BO

---

## 14. Output & Backup Structure

Output folder names are configuration-driven. The canonical structure under the chosen output root is:

```
<output-root>/
  <main-export-folder>/   # configurable name (e.g., "export" or "MetaData")
  backups/
  logs/
  downloads/
```

Rules:
- Before each run, backup the current `<main-export-folder>/` into `backups/<timestamp>/`.
- Apply retention policy after the run (delete backups older than configured days).
- Downloads manifests are always written under `downloads/`.

---

## 15. Offline Mode Architecture

- Offline provider implements same interfaces
- JSON fixtures match real API shape
- No conditional logic in core pipeline

---

## 16. Logging Architecture

- Structured logging per REST call
- BO context propagated
- Latency always logged

---

## 17. Stability Contract

Any change affecting:
- column order
- CSV headers
- filenames
- attachments logic

MUST update:
- spec.md
- tasks.md
- tech.md

---

**END OF TECH**
