# Functional Specification: Correct BO Filename Prefix and Sanitize Component Names

- **Roadmap Item:** Phase 5 — Multi-BO Processing & Operational Features (corrective change to filename generation)
- **Status:** Completed
- **Author:** Claude

---

## 1. Overview and Rationale (The "Why")

The tool's filename template system supports placeholders (`{BO}`, `{Component}`, `{DDMMYYYY}`, `{HHMMSS}`) that are resolved when writing CSV export files. Two issues exist in the current resolution logic:

1. **Wrong `{BO}` value.** The `{BO}` placeholder resolves to the BO **usage type** (e.g., "Contract") instead of the BO **internal name** (e.g., "NAFBO"). This produces filenames like `Contract_Summary_04022026_193856.csv` instead of the expected `NAFBO_Summary_04022026_193856.csv`. When multiple BO types share a usage type (e.g., two different BOs both typed "Contract"), this can cause filename collisions and confusion for downstream consumers.

2. **Unsanitized `{Component}` value.** The `{Component}` placeholder uses the raw component display name without any sanitization. Component display names from the API can contain spaces and special characters (e.g., "Financial Reporting and Control Review"), producing filenames with spaces: `NAFBO_Financial Reporting and Control Review_04022026_193856.csv`. Filenames with spaces cause issues with shell scripts, downstream tooling, and cross-platform file handling.

**Success is measured by:** all generated CSV filenames using the BO internal name for `{BO}`, containing no spaces or special characters, and matching the configured `filenameTemplate` pattern.

---

## 2. Functional Requirements (The "What")

### 2.1 Correct `{BO}` Resolution

The `{BO}` placeholder in both `filenameTemplate` and `downloadsFilenameTemplate` must resolve to the BO **internal name**, not the BO usage type.

- The BO internal name is the value of the `name` property from the `BundleProperties` node in the metadata response (e.g., "NAFBO").
- The BO usage type (e.g., "Contract") must NOT be used for `{BO}`.
- This applies to all CSV output types: per-component exports, merged exports, single-only exports, and downloads list CSVs.

- **Acceptance Criteria:**
  - [x] `{BO}` resolves to the BO internal name (e.g., "NAFBO") in export CSV filenames.
  - [x] `{BO}` resolves to the BO internal name in downloads list CSV filenames.
  - [x] When the BO internal name is "NAFBO" and the component is "Summary", the export filename starts with `NAFBO_Summary_`.
  - [x] The BO usage type ("Contract") does not appear in any generated filename.

### 2.2 Sanitize Filename Tokens

Both the `{BO}` and `{Component}` tokens must be sanitized before substitution into the filename template to ensure filenames are safe across operating systems and tooling.

- **Sanitization rules (applied to both `{BO}` and `{Component}` values):**
  1. Replace spaces with underscores (`_`).
  2. Replace any character that is not `[A-Za-z0-9._-]` with an underscore (`_`).
  3. Collapse multiple consecutive underscores into a single underscore.
  4. Trim leading and trailing underscores and dots.

- **Acceptance Criteria:**
  - [x] A component display name of `"Financial Reporting and Control Review"` produces the token `Financial_Reporting_and_Control_Review`.
  - [x] A component display name of `"Summary"` produces the token `Summary` (unchanged, already clean).
  - [x] A component display name containing special characters (e.g., `"Cost & Budget (Review)"`) produces a token with only `[A-Za-z0-9._-]` characters.
  - [x] Multiple consecutive underscores after replacement are collapsed to one.
  - [x] Leading/trailing underscores and dots are removed.
  - [x] The BO internal name is also sanitized using the same rules.
  - [x] No generated filename contains a space character.

### 2.3 Filename Template Enforcement

The configured `filenameTemplate` must be used for ALL CSV outputs without exception. No CSV writer may bypass the template or use a hardcoded alternative naming scheme.

- **Acceptance Criteria:**
  - [x] Per-component CSV files use the configured `filenameTemplate`.
  - [x] Merged-single CSV files use the configured `filenameTemplate` (with `{Component}` as "Merged" for the merged file).
  - [x] Single-only CSV files use the configured `filenameTemplate` (with `{Component}` as "SingleOnly").
  - [x] Downloads CSV files use the configured `downloadsFilenameTemplate`.
  - [x] Per-BO `filenameTemplate` overrides in `boTypes[]` config still take effect when specified.

### 2.4 Expected Filename Examples

Given `filenameTemplate: "{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv"` and the NAFBO metadata:

| Component Display Name | Expected Filename |
|---|---|
| Summary | `NAFBO_Summary_04022026_193856.csv` |
| Financial Reporting and Control Review | `NAFBO_Financial_Reporting_and_Control_Review_04022026_193856.csv` |
| Attachments | `NAFBO_Attachments_04022026_193856.csv` |
| (Merged mode) | `NAFBO_Merged_04022026_193856.csv` |
| (SingleOnly mode) | `NAFBO_SingleOnly_04022026_193856.csv` |

---

## 3. Scope and Boundaries

### In-Scope

- Changing the `{BO}` placeholder to resolve to the BO internal name instead of usage type.
- Adding sanitization logic for both `{BO}` and `{Component}` tokens.
- Applying changes to all CSV writers (per-component, merged-single, single-only, downloads).
- Updating affected tests to expect the corrected filenames.

### Out-of-Scope

- Changes to the `filenameTemplate` configuration format or available placeholders.
- Changes to the `{DDMMYYYY}` or `{HHMMSS}` placeholder resolution.
- Changes to CSV content, column ordering, or data parsing.
- Changes to the metadata or bundles parsing pipeline.
- Changes to backup management or directory structure.
- All other roadmap items (authentication, endpoint loading, multi-BO processing beyond filename changes).
