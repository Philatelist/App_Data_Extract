# Technical Specification: Yes/No Value Translation for Boolean Fields

- **Functional Specification:** `context/spec/009-yes-no-translation/functional-spec.md`
- **Status:** Approved
- **Author(s):** Alex

---

## 1. High-Level Technical Approach

Field type information (`dataType`) is already present in `FieldMetadata` after metadata parsing, but `BundlesMapper` does not currently use it during value normalisation. The implementation adds two things:

1. **Config layer** (`AppConfig` + `ConfigLoader`): new `yesNoTranslation` section with `enabled`, `trueValue` (default `"YES"`), `falseValue` (default `"NO"`).

2. **Type-aware translation in `BundlesMapper`**: at construction time, build a lookup map of `componentInternalName → fieldInternalName → dataType` from the `BoMetadata` passed to `map()`. Then in `mapRecord()`, look up each field's type and apply translation as the final step in the value chain when type is `"yesNoRadioButtons"` and translation is enabled.

No changes are needed to any CSV writer or `ColumnResolver`.

**Files touched:** `AppConfig`, `ConfigLoader`, `BundlesMapper`. Tests added to `ConfigLoaderTest` and `BundlesMapperTest`.

---

## 2. Proposed Solution & Implementation Plan

### 2.1 Configuration (`AppConfig` + `ConfigLoader`)

**`AppConfig.java`** — add three new fields:

| Field | Type | Default |
|---|---|---|
| `yesNoTranslationEnabled` | `boolean` | `false` |
| `yesNoTrueValue` | `String` | `"YES"` |
| `yesNoFalseValue` | `String` | `"NO"` |

**`ConfigLoader.java`** — read the `yesNoTranslation` sub-map:

```
enabled    → config.setYesNoTranslationEnabled(...)   default: false
trueValue  → config.setYesNoTrueValue(...)            default: "YES"
falseValue → config.setYesNoFalseValue(...)           default: "NO"
```

No validation required — all three fields have safe defaults. An absent section leaves all three at their defaults (translation disabled).

### 2.2 Field Type Lookup in `BundlesMapper`

**At `map()` time**, when `BoMetadata` is available, build:

```
Map<String, Map<String, String>> fieldTypeMap
  outer key: component internalName
  inner key: field internalName
  value:     field dataType
```

Populated by iterating `metadata.getComponents()` → `component.getFields()` → `field.getDataType()`.

This map is built once per `map()` call and used throughout `mapRecord()`. If metadata is null or a component has no fields, skip gracefully.

### 2.3 Translation Logic

Add a private method `applyYesNoTranslation(String value, String fieldType)`:

- If `yesNoTranslationEnabled` is false → return value unchanged.
- If `fieldType` is not `"yesNoRadioButtons"` → return value unchanged.
- If value is null or blank → return value unchanged.
- If `value.equalsIgnoreCase("true")` → return `yesNoTrueValue`.
- If `value.equalsIgnoreCase("false")` → return `yesNoFalseValue`.
- Otherwise → return value unchanged.

### 2.4 Value Processing Order (final, complete chain)

```
unescapeHtml(value)
  → normalizeValue(fieldName, ...)
  → applyDelimiterReplacement(...)
  → applyYesNoTranslation(result, fieldType)
```

`fieldType` is resolved via `fieldTypeMap.getOrDefault(componentInternalName, Map.of()).get(fieldInternalName)`. If not found, `fieldType` is `null` and `applyYesNoTranslation` returns the value unchanged.

### 2.5 Passing config values to `BundlesMapper`

`BundlesMapper` already receives `char delimiter` and `String delimiterSubstituteChar` via constructor (from spec 008). Extend the constructor with three additional parameters:

- `boolean yesNoTranslationEnabled`
- `String yesNoTrueValue`
- `String yesNoFalseValue`

The no-arg constructor delegates to `this(',', null, false, "YES", "NO")`.

The `BundleParser(AppConfig config)` constructor passes the new values from `config`.

---

## 3. Impact and Risk Analysis

- **Additive change:** translation only activates when `enabled: true` in config. Existing runs are unaffected.
- **Field type lookup is defensive:** if a field's type is not found in `fieldTypeMap` (e.g. field comes from order file but isn't in metadata), `fieldType` is null and translation is skipped safely.
- **`BundlesMapper` constructor grows:** `BundleParser` and the no-arg constructor must be kept in sync. The no-arg constructor is used by `OfflineDataSource` — it will correctly default to translation disabled.

---

## 4. Testing Strategy

**`ConfigLoaderTest`:**
- Absent `yesNoTranslation` → enabled false, trueValue `"YES"`, falseValue `"NO"`.
- `enabled: true` with no `trueValue`/`falseValue` → defaults `"YES"` / `"NO"`.
- `enabled: true`, `trueValue: "Yes"`, `falseValue: "No"` → custom values stored.
- `enabled: false` → disabled regardless of trueValue/falseValue.

**`BundlesMapperTest`:**
- Translation disabled → `"true"` on a `yesNoRadioButtons` field passes through unchanged.
- Translation enabled → `"true"` → `"YES"`, `"false"` → `"NO"`.
- Case-insensitive: `"True"` → `"YES"`, `"FALSE"` → `"NO"`.
- Non-boolean value on `yesNoRadioButtons` field (`"maybe"`) → unchanged.
- Empty string on `yesNoRadioButtons` field → unchanged.
- `"true"` on a non-`yesNoRadioButtons` field → unchanged.
- Translation applied after delimiter replacement: if a field value was `"true"` and delimiter replacement was also active, the final value is `"YES"` (translation is last).
