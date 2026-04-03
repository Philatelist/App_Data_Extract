# Technical Specification: Delimiter Character Handling in CSV Values

- **Functional Specification:** `context/spec/008-delimiter-replacement/functional-spec.md`
- **Status:** Approved
- **Author(s):** Alex

---

## 1. High-Level Technical Approach

Two orthogonal problems are solved together:

**1. Quoting bug fix:** Five writers (`PerComponentCsvWriter`, `DownloadsCsvWriter`, `ParentCsvWriter`, `SummaryCsvWriter`, `ManifestCsvWriter`) are constructed with `ICSVWriter.NO_QUOTE_CHARACTER`, which prevents OpenCSV from ever quoting a field — even when it contains the delimiter. The fix is to remove `NO_QUOTE_CHARACTER` from all data-writing writers so OpenCSV falls back to its standard double-quote behaviour.

**2. Replacement mode:** A new `delimiterReplacement` config section enables opt-in replacement of the delimiter character in cell values with a user-defined substitute string. The replacement is applied in `BundlesMapper` as the last step of value normalisation — the single point where all field values are processed before being stored in `BundleComponent`. No CSV writer code changes are needed for this path.

**Files touched:** `AppConfig`, `ConfigLoader`, `BundlesMapper`, `BundleParser` (or wherever `BundlesMapper` is instantiated), `PerComponentCsvWriter`, `DownloadsCsvWriter`, `ParentCsvWriter`. Tests added to `ConfigLoaderTest` and a new `BundlesMapperTest`.

---

## 2. Proposed Solution & Implementation Plan

### 2.1 Configuration (`AppConfig` + `ConfigLoader`)

**`AppConfig.java`** — add two new fields:

| Field | Type | Default |
|---|---|---|
| `delimiterReplacementEnabled` | `boolean` | `false` |
| `delimiterSubstituteChar` | `String` | `null` |

**`ConfigLoader.java`** — parse the `delimiterReplacement` sub-map from the YAML root:

```
enabled        → config.setDelimiterReplacementEnabled(...)
substituteChar → config.setDelimiterSubstituteChar(...)
```

Add to `validate()`:
- If `delimiterReplacementEnabled` is `true` and `delimiterSubstituteChar` is null or empty → `ConfigValidationException("delimiterReplacement.substituteChar is required when replacement is enabled")`
- If `delimiterReplacementEnabled` is `true` and any character in `delimiterSubstituteChar` equals `delimiter` → `ConfigValidationException("delimiterReplacement.substituteChar must not contain the delimiter character ('X')")`

### 2.2 Quoting Bug Fix (CSV Writers)

Remove `.withQuoteChar(ICSVWriter.NO_QUOTE_CHARACTER)` from the builder in:

| File | Current | After fix |
|---|---|---|
| `PerComponentCsvWriter.java` | `NO_QUOTE_CHARACTER` | default (double-quote) |
| `DownloadsCsvWriter.java` | `NO_QUOTE_CHARACTER` | default (double-quote) |
| `ParentCsvWriter.java` | `NO_QUOTE_CHARACTER` | default (double-quote) |

`MergedSingleCsvWriter` and `SingleOnlyCsvWriter` already use default quoting — no change needed.

`SummaryCsvWriter` and `ManifestCsvWriter` write only system-generated values (BO names, checksums, filenames) — leave as-is.

### 2.3 Replacement Logic (`BundlesMapper`)

`BundlesMapper` processes all extracted field values at lines 150 and 172 via:
```java
normalizeValue(fieldName, unescapeHtml(value))
```

Add a `delimiterSubstituteChar` field (`String`, nullable) to `BundlesMapper`, set via constructor. Add a final step in the value chain:

```
unescapeHtml(value)  →  normalizeValue(fieldName, ...)  →  applyDelimiterReplacement(...)
```

`applyDelimiterReplacement(String value)`:
- If `delimiterSubstituteChar` is null → return value unchanged.
- Otherwise → `value.replace(String.valueOf(delimiter), delimiterSubstituteChar)`.

The delimiter character also needs to be available in `BundlesMapper` (passed alongside substituteChar via constructor).

**Instantiation site:** Wherever `BundlesMapper` is constructed (likely `BundleParser` or `ApiDataSource`), pass `config.getDelimiter()` and `config.getDelimiterSubstituteChar()` (null when replacement is disabled).

### 2.4 Value Processing Order (final)

For all extracted field values in replacement mode:
1. `unescapeHtml()` — decode HTML entities, flatten newlines
2. `normalizeValue()` — backslash normalisation for `serverFileName`
3. `applyDelimiterReplacement()` — replace delimiter with substituteChar

---

## 3. Impact and Risk Analysis

- **Quoting bug fix is a visible behaviour change:** Writers that previously emitted unquoted broken CSV will now emit properly quoted fields. Downstream consumers reading the files as plain text will see `"foo;bar"` instead of `foo;bar` — this is correct RFC 4180 CSV, but worth noting.
- **Replacement mode is additive:** Only active when explicitly configured; zero impact on existing runs.
- **`BundlesMapper` constructor change:** Any call site that constructs `BundlesMapper` directly must be updated to pass the two new arguments. Expected to be a single site (`BundleParser` or equivalent).
- **`SummaryCsvWriter` / `ManifestCsvWriter` left with `NO_QUOTE_CHARACTER`:** These write only controlled system values, not user data, so the risk is negligible. Can be revisited later.

---

## 4. Testing Strategy

**`ConfigLoaderTest`:**
- `delimiterReplacement` section absent → `delimiterReplacementEnabled` false, `delimiterSubstituteChar` null.
- `enabled: true` + valid `substituteChar: "||"` → loads correctly.
- `enabled: true` + `substituteChar` absent → `ConfigValidationException`.
- `enabled: true` + `substituteChar: ""` → `ConfigValidationException`.
- `enabled: true` + `substituteChar: "|;"` with `delimiter: ";"` → `ConfigValidationException`.

**`BundlesMapperTest` (new):**
- Replacement disabled (substituteChar null) → value with delimiter passes through unchanged.
- Replacement enabled → all occurrences of delimiter replaced: `"a;b;c"` with `substituteChar="||"` → `"a||b||c"`.
- Value without delimiter → unchanged.
- Replacement applied after HTML decoding: `"&amp;;bar"` → `"&;bar"` → `"&||bar"`.

**Manual smoke test:**
- Build JAR. Run with `delimiterReplacement.enabled: true`, `substituteChar: "||"`. Open output CSV. Confirm no unquoted delimiter in any data column.
- Run without `delimiterReplacement`. Open CSV. Confirm values containing delimiter are properly double-quoted.
