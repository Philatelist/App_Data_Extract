# Functional Specification: Yes/No Value Translation for Boolean Fields

- **Roadmap Item:** Yes/No value translation for `yesNoRadioButtons` fields
- **Status:** Approved
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

Fields with type `yesNoRadioButtons` store their values as `true` or `false` in the CLM system. When extracted to CSV, these raw boolean strings are not meaningful to end users or downstream tools. This feature lets the administrator enable an automatic translation of `true` → a configurable "yes" string and `false` → a configurable "no" string for any field whose metadata type is `yesNoRadioButtons`, producing more readable and business-friendly output.

---

## 2. Functional Requirements (The "What")

### 2.1 Configuration

A new optional section `yesNoTranslation` is added to `config.yml`:

```yaml
yesNoTranslation:
  enabled: true
  trueValue: "YES"
  falseValue: "NO"
```

- If `yesNoTranslation` is absent or `enabled: false`, no translation occurs — `true` and `false` are written as-is.
- `trueValue` and `falseValue` default to `"YES"` and `"NO"` respectively when `enabled: true` but the sub-keys are omitted.
- **Acceptance Criteria:**
  - [ ] Config with no `yesNoTranslation` section loads without error and applies no translation.
  - [ ] Config with `enabled: false` loads without error and applies no translation.
  - [ ] Config with `enabled: true` and no `trueValue`/`falseValue` uses `"YES"` and `"NO"` as defaults.
  - [ ] Config with `enabled: true`, `trueValue: "Yes"`, `falseValue: "No"` loads and uses those custom strings.

### 2.2 Field Targeting

- Translation applies **only** to fields whose metadata type identifier (`InternalValue`) equals `"yesNoRadioButtons"`.
- All other field types are unaffected, even if their values happen to be `"true"` or `"false"`.
- **Acceptance Criteria:**
  - [ ] A `yesNoRadioButtons` field with value `"true"` is written as the configured `trueValue`.
  - [ ] A `yesNoRadioButtons` field with value `"false"` is written as the configured `falseValue`.
  - [ ] A field of a different type (e.g. a text field) whose value happens to be `"true"` is written unchanged.

### 2.3 Matching Rules

- The match against `"true"` and `"false"` is **case-insensitive** (so `"True"`, `"TRUE"`, `"False"` all match).
- Any value that is not `"true"` or `"false"` — including empty strings and null — is **passed through unchanged**.
- **Acceptance Criteria:**
  - [ ] Field value `"True"` on a `yesNoRadioButtons` field is written as `trueValue`.
  - [ ] Field value `"FALSE"` on a `yesNoRadioButtons` field is written as `falseValue`.
  - [ ] Empty string on a `yesNoRadioButtons` field is written as empty string (no translation).
  - [ ] Unexpected string (e.g. `"maybe"`) on a `yesNoRadioButtons` field is written as `"maybe"` (no translation).

### 2.4 Scope of Application

- Translation applies to all CSV output modes (`per-component`, `merged-single`, `single-only`) and all BO types.
- Translation is applied **after** all other value transformations (HTML entity decoding, delimiter replacement).
- **Acceptance Criteria:**
  - [ ] Translation is consistent across all `csvMode` values in the same run.

---

## 3. Scope and Boundaries

### In-Scope

- New `yesNoTranslation` config section with `enabled`, `trueValue` (default `YES`), `falseValue` (default `NO`).
- Case-insensitive matching of `true`/`false` values on `yesNoRadioButtons` fields only.
- Pass-through for all other values and field types.
- Applied as the final step in the value normalisation chain.

### Out-of-Scope

- Translating boolean values on fields of other types.
- Custom translation for other field types (e.g. converting numeric codes to labels).
- Per-BO-type or per-component translation rules.
