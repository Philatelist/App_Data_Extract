# Specification (startup-friendly, AWOS-style)

## 1. Scope
Java CLI that exports CLM BO data to CSV using real SCLM API response shapes.

## 2. Core rules
### 2.1 Deterministic ordering
- Rows in CSV are in the same order as request `trackingIds[]`.
- Columns are in the same order as request `fieldPaths[]`.

### 2.2 fieldPaths format
- Requests to `/bundles` use `Module/Component/Parameter` (no `MCPDef:/` prefixes).

### 2.3 Tracking ID source
- Tracking ID for each bundles record comes from the request `trackingIds[]` array (positional).

### 2.4 Filenames
- Filenames use `filenameTemplate` and `{BO}` resolves to BO internal name.
- `{BO}` and `{Component}` tokens are sanitized for filesystem safety.

### 2.5 CSV header
- The first column header is always: `Summary.Tracking #`
- Remaining headers are `ComponentDisplayName.ResolvedParameterDisplayName`

### 2.6 Overrides
#### Column order file (per-BO)
- `config/columns/{BO}.csv`
- One `Module/Component/Parameter` per line
- When present:
  - Only listed params exported
  - Order matches file order
  - Unlisted components excluded
  - Paths not in metadata are retained as columns

#### Display names file (global)
- `inputs/overrides/parameter-displaynames.csv`
- Semicolon-delimited, header row:
  `Component;Parameter;DisplayName;`
- Precedence: overrides → metadata → bundles → internal

### 2.7 Downloads list
- One-column CSV, no header
- Contains `serverFileName` values from components:
  - `ReqAttachment`
  - `ReqContractAttachment`

## 3. Out of scope
- Attachment downloads (only list)
- UI
- Schema transformations beyond flattening to CSV
