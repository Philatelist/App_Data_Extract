# Functional Specification: Component Skip List

- **Roadmap Item:** Component Skip List — suppress specific components from CSV output
- **Status:** Approved
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

When the tool exports a BO type it discovers every component defined in the BO metadata. Many of those components are internal system tables (e.g. `TableNamesMapping`, `BundleProperties`, `TrackingNumbers`) that contain no business-relevant data and add noise to the output. Currently there is no way to suppress them short of post-processing the CSV files manually.

This feature gives the administrator a single, globally-applied list of component names to ignore. Any component whose name matches an entry in that list is silently excluded from the export — no CSV file is created for it, no columns from it appear in merged outputs, and the run log records each suppression so the admin can audit what was skipped.

---

## 2. Functional Requirements (The "What")

### 2.1 Configuration

- A new optional key `skipComponents` is added to `config.yml`. Its value is a YAML list of strings.
- If the key is absent or the list is empty, no components are skipped and the tool behaves exactly as it does today.
- Example:
  ```yaml
  skipComponents:
    - TableNamesMapping
    - AddParameter
    - BundleProperties
    - ComponentProperties
    - ModuleProperties
    - ParameterProperties
    - TrackingNumbers
    - UsersTable
    - Account User Recipients
    - External User Recipients
    - Signature Document Details
    - Approval Tasks
  ```
- **Acceptance Criteria:**
  - [ ] A `config.yml` containing `skipComponents` with a list of names loads without error.
  - [ ] A `config.yml` with no `skipComponents` key loads without error and skips no components.
  - [ ] A `config.yml` with `skipComponents: []` (empty list) loads without error and skips no components.

### 2.2 Matching Rules

- Matching is **case-insensitive** and **whitespace-trimmed** on both sides (config entry and discovered component name).
- The match is **exact** after normalisation — partial matches or wildcard patterns are not supported.
- **Acceptance Criteria:**
  - [ ] A component named `TrackingNumbers` is skipped when the config entry is `trackingnumbers`.
  - [ ] A component named `Approval Tasks` is skipped when the config entry is `  Approval Tasks  ` (leading/trailing spaces).
  - [ ] A component named `TrackingNumbersExtra` is **not** skipped when the config entry is `TrackingNumbers`.

### 2.3 Export Behaviour

- The skip list applies to **all BO types** in the run without exception.
- A skipped component produces **no output** of any kind:
  - No CSV file (in `per-component` or `single-only` mode).
  - No columns or rows contributed to a merged CSV (in `merged-single` mode).
  - No entry in the downloads-list CSV for that component.
- The component is excluded regardless of whether it is single-cardinality or multi-cardinality.
- **Acceptance Criteria:**
  - [ ] When `BundleProperties` is in `skipComponents`, no CSV file for `BundleProperties` is created for any BO type in the run.
  - [ ] When running in `merged-single` mode, no column from a skipped component appears in the merged CSV.
  - [ ] Skipped components produce no entry in the run manifest (they were never written).

### 2.4 Logging

- Each time a component is skipped, a log entry is written at **INFO** level to the run log.
- The log entry identifies the BO type, the component name, and that it was suppressed by the skip list.
- Example: `Skipping component "BundleProperties" for BO type "Contract" (in skipComponents list)`
- **Acceptance Criteria:**
  - [ ] For each skipped component, exactly one INFO-level log line is written per BO type that contains it.
  - [ ] If a component in `skipComponents` does not exist in a given BO type's metadata, no log entry is produced for that BO type (no false positives).

---

## 3. Scope and Boundaries

### In-Scope

- New `skipComponents` YAML list key in `config.yml`.
- Case-insensitive, whitespace-trimmed exact matching of component names.
- Global application of the skip list across all BO types.
- Suppression of CSV output in all `csvMode` variants (`per-component`, `merged-single`, `single-only`).
- INFO-level log entry for each suppressed component.

### Out-of-Scope

- Per-BO-type skip lists (all other roadmap items addressed separately).
- Wildcard or pattern-based component matching.
- Skipping individual fields/columns within a component (covered by the existing `skipColumns` feature).
- Any change to the downloads-list CSV format or manifest format beyond the natural absence of suppressed components.
