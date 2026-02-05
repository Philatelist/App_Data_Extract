# Functional Specification: In-Repo Parameter Selection and Display Name Overrides

- **Roadmap Item:** Phase 4 — Single-BO Export Pipeline (enhancement to field path resolution and CSV header construction)
- **Status:** Completed
- **Author:** Claude

---

## 1. Overview and Rationale (The "Why")

The BOMetaData response defines all available components and parameters for a BO type, but administrators typically need to export only a subset of parameters in a specific order. The API's metadata display names are also not always suitable for downstream consumers — they may be too long, ambiguous, or inconsistent across environments.

Currently, the tool exports all parameters from metadata in metadata order. Administrators have no way to:
- Select only the parameters they need.
- Control the column order in the CSV output.
- Override column header display names with business-friendly labels.

This specification introduces two in-repo override files that give administrators full control over parameter selection, ordering, and display names without modifying code or metadata.

**Success is measured by:** the tool correctly filtering and ordering CSV columns based on the column order file, applying display name overrides from the overrides file, and excluding components/parameters not listed in the column order file.

---

## 2. Functional Requirements (The "What")

### 2.1 Column Order File

A per-BO file at `config/columns/{BO}.csv` (where `{BO}` is the BO internal name, e.g., `NAFBO`) controls which parameters are exported and in what order.

- **Format:** One field path per line. No header row. No delimiters. Each line is a path in the format `ModuleName/ComponentName/ParameterName`.
- **Example file** (`config/columns/NAFBO.csv`):
  ```
  NAFData/ReqNAFInfo/trackingNumber
  NAFData/ReqNAFInfo/contractNumber
  NAFData/ReqNAFInfo/contractStatus
  NAFData/ReqNAFAccounting/contractualObligationsReportable
  ```
- **Behavior when the file exists:**
  - Only parameters listed in the file are exported.
  - Parameters appear in the CSV in the same order as in the file.
  - Components not represented by any line are excluded from the export entirely.
  - Parameters listed in the file but not found in the BOMetaData are **not ignored** — they remain as columns in the CSV output. Their values are populated from the bundles response data if available, or left empty otherwise.
- **Behavior when the file does not exist:**
  - All parameters from BOMetaData are exported in metadata order (default behavior, unchanged).
- **Naming:** The component and parameter names in each path are **internal names** from the BOMetaData (not display names).

- **Acceptance Criteria:**
  - [x]When `config/columns/NAFBO.csv` exists with 4 field paths, only those 4 columns appear in the CSV output.
  - [x]Columns appear in the order specified in the file.
  - [x]Components not listed in the file do not appear in the CSV output.
  - [x]When the file does not exist, all metadata parameters are exported in metadata order.
  - [x]Field paths listed in the file but not present in BOMetaData are retained as columns (not skipped). Their values come from the bundles response if available, or are empty.
  - [x]Empty lines and leading/trailing whitespace in the file are ignored.
  - [x]The file uses the BO internal name (e.g., `NAFBO`), not the usage type (e.g., `Contract`).

### 2.2 Display Name Override File

A global file at `config/overrides/AddParameters.csv` overrides the column header display name for specific parameters. It applies to all BO types.

- **Format:** Semicolon-delimited, three columns, with a header row. Format: `Component;Parameter;DisplayName;`
- **Example file** (`config/overrides/AddParameters.csv`):
  ```
  Component;Parameter;DisplayName;
  ReqNAFInfo;trackingNumber;Tracking #;
  ReqNAFInfo;contractNumber;Contract No.;
  ReqNAFAccounting;contractualObligationsReportable;Contractual Obligations;
  ```
- **Behavior:**
  - The override only applies to parameters that are already being exported (i.e., present in the column order file or in metadata when no column order file exists).
  - Component and Parameter values are **internal names** from BOMetaData.
  - When a match is found, the `DisplayName` value replaces the metadata display name in the CSV header.
  - When no match is found, the existing display name precedence applies: metadata `displayName` → bundles `DisplayName` → internal parameter name.
- **Behavior when the file does not exist:**
  - No overrides are applied. Headers use the existing display name fallback chain.

- **Acceptance Criteria:**
  - [x]When `config/overrides/AddParameters.csv` exists and lists `ReqNAFInfo;trackingNumber;Tracking #;`, the CSV header for that parameter is `Summary.Tracking #`.
  - [x]The override applies across all BO types (global).
  - [x]Parameters not listed in the override file use the default display name from metadata.
  - [x]The override file only affects parameters that are being exported.
  - [x]When the file does not exist, no overrides are applied.
  - [x]Empty lines and malformed rows in the file are silently skipped.
  - [x]The header row (`Component;Parameter;DisplayName;`) is skipped during parsing.

### 2.3 Display Name Precedence

The final column header for a parameter follows this precedence (highest to lowest):

1. Display name from `config/overrides/AddParameters.csv` (if the parameter is listed).
2. Display name from BOMetaData (`displayName` property of the `ParameterProperties` node).
3. `DisplayName` field from the bundles API response.
4. Internal parameter name (fallback).

The resolved display name is always prefixed by the component display name: `ComponentDisplayName.ResolvedParameterDisplayName`.

- **Acceptance Criteria:**
  - [x]When a parameter has an entry in AddParameters.csv, that display name is used regardless of metadata.
  - [x]When no AddParameters.csv entry exists, the metadata display name is used.
  - [x]When metadata has no display name, the bundles response `DisplayName` is used.
  - [x]When none of the above are available, the internal parameter name is used.

### 2.4 Field Path Format

Field paths in the column order file use the format `ModuleName/ComponentName/ParameterName`, which corresponds to the path segments from the metadata InstancePath (without the `MCPDef:/` prefix and without trailing slashes).

- The module name is typically the first segment after `MCPDef:/` (e.g., `NAFData`).
- The component name is the internal name from `ComponentProperties` (e.g., `ReqNAFInfo`).
- The parameter name is the internal name from `ParameterProperties` (e.g., `trackingNumber`).

- **Acceptance Criteria:**
  - [x]The tool correctly matches `NAFData/ReqNAFInfo/trackingNumber` in the column order file to the parameter with InstancePath `MCPDef:/NAFData/ReqNAFInfo/trackingNumber` in metadata.
  - [x]Path matching is case-sensitive.

### 2.5 CSV Header Format

CSV column headers use the component display name as a prefix, followed by the resolved parameter display name.

- **Format:** `ComponentDisplayName.ResolvedParameterDisplayName`
- **With column order file and display name override:** `ComponentDisplayName.OverriddenDisplayName`
- **With column order file and no display name override:** `ComponentDisplayName.MetadataDisplayName`
- **Without column order file (default):** `ComponentDisplayName.ParameterDisplayName` (existing behavior)

The `Summary.Tracking #` column remains the first column in every export CSV.

- **Acceptance Criteria:**
  - [x]With column order file and display name override, the header is `ComponentDisplayName.OverriddenDisplayName`.
  - [x]With column order file and no display name override, the header is `ComponentDisplayName.MetadataDisplayName`.
  - [x]The `Summary.Tracking #` column is always the first column in every export CSV.

---

## 3. Scope and Boundaries

### In-Scope

- Reading and parsing `config/columns/{BO}.csv` (one field path per line, BO internal name in filename).
- Reading and parsing `config/overrides/AddParameters.csv` (semicolon-delimited, `Component;Parameter;DisplayName;`).
- Filtering exported parameters to only those listed in the column order file.
- Ordering exported columns according to the column order file.
- Excluding components with no listed parameters.
- Retaining field paths not found in BOMetaData as columns (populated from bundles data or empty).
- Overriding column header display names from the overrides file.
- Display name precedence: override file → metadata → bundles → internal name.
- Applying overrides across all CSV modes (per-component, merged-single, single-only).

### Out-of-Scope

- Changes to the `filenameTemplate` or filename generation.
- Changes to CSV delimiter or quoting behavior.
- Changes to metadata or bundles parsing.
- Changes to tracking ID handling or row ordering.
- Changes to downloads list CSV generation.
- Changes to backup management or session management.
- All other roadmap items.
