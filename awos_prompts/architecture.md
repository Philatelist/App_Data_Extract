# Architecture (AWOS-style, preserving our architecture)

## Canonical docs
- This document is **reference**. Canonical behavior is defined in `spec.md` and `tasks.md`.

## Goal
A Java CLI exports CLM BO data to CSV, using real SCLM API shapes and deterministic ordering, with optional in-repo overrides for parameter selection and header display names.

## High-level flow
1. Load config (endpoints, boTypes, output modes, templates, delimiter).
2. Authenticate / maintain session.
3. For each BO type (sequential):
   1) Fetch BO metadata
   2) Resolve **fieldPaths** (order/selection) and **columns** (headers)
   3) Fetch bundles in batches for `trackingIds[]` + `fieldPaths[]`
   4) Write CSV incrementally (streaming) per selected mode
   5) Write downloads list (attachments to download)

## Key invariants (ours; do not change)
- **Sequential BO processing** (no parallel).
- **Row order** follows request `trackingIds[]` order.
- **Column order** follows request `fieldPaths[]` order.
- `fieldPaths` sent to `/bundles` are in `Module/Component/Parameter` (no `MCPDef:/`).
- CSV filenames use `filenameTemplate` and `{BO}` resolves to **BO internal name**.
- Tracking header is **`Summary.Tracking #`** (first column).

## Modules (logical)
- `config` — config loading/validation.
- `session` — login/logout/re-login.
- `api` — HTTP client + endpoint executor.
- `metadata` — DTOs/mappers/parsers for real metadata shape -> domain `BoMetadata`.
- `bundles` — DTOs/mappers/parsers for real bundles shape -> domain `BundleRecord`.
- `csv`
  - `ColumnResolver` — resolves `fieldPaths` + `ResolvedColumn` headers (uses overrides).
  - `FilenameResolver` — resolves and sanitizes `{BO}`, `{Component}`.
  - Writers: per-component / merged-single / single-only + downloads list.
- `pipeline`
  - `BoPipeline` — orchestrates per-BO run and batch loop.

## Overrides (in-repo)
### Parameter selection & ordering (per-BO)
- File: `config/columns/{BO}.csv`
- Format: one line per fieldPath: `Module/Component/Parameter`
- When present: only listed parameters exported, in file order; unlisted components excluded; paths not in metadata are retained as empty columns if values absent.

### Header display names (global)
- File: `inputs/overrides/parameter-displaynames.csv`
- Format (semicolon, header row):
  `Component;Parameter;DisplayName;`
- Precedence for parameter display name:
  1) `parameter-displaynames.csv`
  2) metadata displayName
  3) bundles DisplayName
  4) internal parameter name
- Final header format: `ComponentDisplayName.ResolvedParameterDisplayName`

## Downloads list (attachments)
- Output: `{BO}_AttachmentsToDownload_{DDMMYYYY}_{HHMMSS}.csv` via downloads filename template
- One column (no header): `serverFileName` values
- Sources: components `ReqAttachment` and `ReqContractAttachment`
