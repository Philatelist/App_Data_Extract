# Functional Specification: Delimiter Character Handling in CSV Values

- **Roadmap Item:** Configurable delimiter replacement in extracted cell values
- **Status:** Approved
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

When extracted field values contain the configured delimiter character (e.g. `;`), those values must either be properly quoted so the CSV structure remains intact, or the character must be replaced with a safe substitute before writing. Currently, values containing the delimiter are not correctly quoted by the tool, causing extra columns to appear in the output CSV and breaking downstream parsing.

This feature gives the administrator a choice: rely on correct CSV quoting (the safe default), or actively replace delimiter occurrences in values with a chosen substitute string to produce quote-free, visually clean output.

---

## 2. Functional Requirements (The "What")

### 2.1 Configuration

A new optional section `delimiterReplacement` is added to `config.yml`:

```yaml
delimiterReplacement:
  enabled: true          # false = quoting mode (default when section is absent)
  substituteChar: "||"   # the string to replace the delimiter with in values
```

- If `delimiterReplacement` is absent entirely, behaviour is **quoting mode** (no replacement; delimiter occurrences in values are properly quoted by the CSV writer).
- If `enabled: false`, same as absent — quoting mode.
- If `enabled: true`, `substituteChar` is required and must be a non-empty string.
- **Acceptance Criteria:**
  - [ ] Config with `delimiterReplacement` section absent loads without error and uses quoting mode.
  - [ ] Config with `enabled: false` loads without error and uses quoting mode.
  - [ ] Config with `enabled: true` and a valid `substituteChar` loads without error and uses replacement mode.
  - [ ] Config with `enabled: true` and `substituteChar` absent fails at startup with a clear error message.
  - [ ] Config with `enabled: true` and `substituteChar: ""` (empty string) fails at startup with a clear error message.

### 2.2 Validation

- `substituteChar` must be a non-empty string (one or more characters are allowed).
- **None of the individual characters** in `substituteChar` may equal the configured `delimiter` character.
- Both validations are checked at startup, before any API calls.
- **Acceptance Criteria:**
  - [ ] Config with `substituteChar: "||"` and `delimiter: ";"` loads without error.
  - [ ] Config with `substituteChar: "|"` and `delimiter: ";"` loads without error.
  - [ ] Config with `substituteChar: "|;"` and `delimiter: ";"` fails at startup with a clear error message: e.g. `delimiterReplacement.substituteChar must not contain the delimiter character (';')`.
  - [ ] Config with `substituteChar: ";"` and `delimiter: ";"` fails at startup with the same error.

### 2.3 Replacement Mode Behaviour (`enabled: true`)

- Before a cell value is written to any CSV file, every occurrence of the delimiter character in that value is replaced with the full `substituteChar` string.
- This applies to **all** cell values across all BO types, components, and CSV modes (`per-component`, `merged-single`, `single-only`).
- The replacement is applied after all other value transformations (HTML entity decoding, newline flattening).
- **Acceptance Criteria:**
  - [ ] A value `"foo;bar"` with `delimiter: ";"` and `substituteChar: "||"` is written as `foo||bar`.
  - [ ] A value with no delimiter character is written unchanged.
  - [ ] Multiple occurrences of the delimiter in one value are all replaced: `"a;b;c"` → `"a||b||c"`.
  - [ ] Replacement applies consistently across all CSV output files in the run.

### 2.4 Quoting Mode Behaviour (`enabled: false` or absent)

- The CSV writer must correctly quote any cell value that contains the delimiter character, so the file remains valid CSV.
- **This corrects the existing bug** where values containing the delimiter were not properly quoted, producing broken column structure.
- **Acceptance Criteria:**
  - [ ] A value `"foo;bar"` with `delimiter: ";"` and quoting mode active is written as `"foo;bar"` (quoted), and the file parses as a single cell in any standard CSV reader.
  - [ ] Values not containing the delimiter are written without unnecessary quotes.

---

## 3. Scope and Boundaries

### In-Scope

- New `delimiterReplacement` config section (`enabled`, `substituteChar`).
- Startup validation: `substituteChar` required and non-empty when enabled; no character in it may equal the delimiter.
- Replacement applied to all cell values in all CSV output files when enabled.
- Fix for the existing quoting bug so the quoting-mode fallback produces valid CSV.

### Out-of-Scope

- Replacing characters other than the delimiter (e.g. newlines are already handled separately).
- Per-column or per-component replacement rules.
- Transformation of the substitute string itself in values (no recursive escaping).
