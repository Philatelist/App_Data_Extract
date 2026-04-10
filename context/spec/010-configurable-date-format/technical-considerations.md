# Technical Specification: Configurable Date Format Output

- **Functional Specification:** `context/spec/010-configurable-date-format/functional-spec.md`
- **Status:** Draft
- **Author(s):** Alex

---

## 1. High-Level Technical Approach

Add a `dateFormat` config section parsed into a new `DateFormatConfig` object. Propagate each field's `dataType` through `ResolvedColumn` so writers know the field's type. Introduce a `DateFormatter` utility that applies format conversion at write time inside `PerComponentCsvWriter` (and any other writers that produce data cells). No changes to the API client, metadata fetch, or bundle parsing layers.

---

## 2. Proposed Solution & Implementation Plan (The "How")

### 2.1 New Config Class — `DateFormatConfig`

**File:** `src/main/java/com/clmextract/config/DateFormatConfig.java`

Four nullable `String` fields:

| Field | Config key | Purpose |
|---|---|---|
| `inputFormat` | `dateFormat.inputFormat` | Pattern to parse incoming date-only values |
| `outputFormat` | `dateFormat.outputFormat` | Pattern to write date-only values to CSV |
| `inputDateTimeFormat` | `dateFormat.inputDateTimeFormat` | Pattern to parse incoming datetime values |
| `outputDateTimeFormat` | `dateFormat.outputDateTimeFormat` | Pattern to write datetime values to CSV |

All four default to `null`. The object itself is `null` on `AppConfig` when the `dateFormat` section is absent entirely.

### 2.2 `AppConfig` — Add `dateFormat` field

Add `private DateFormatConfig dateFormat` (default `null`) with standard getter/setter. No change to existing fields.

### 2.3 `ConfigLoader` — Parse and validate

**Parsing:** In `load()`, read the `dateFormat` map using the existing `getMap()` helper. If present, construct a `DateFormatConfig` and set it on the config. Uses the existing `getStringOrDefault()` helper for each of the four keys.

**Validation:** In `validate()`, if `config.getDateFormat() != null`:
- If `inputFormat` is set but `outputFormat` is absent (or vice versa) → throw `ConfigValidationException` identifying the missing key.
- Same check for the `inputDateTimeFormat` / `outputDateTimeFormat` pair.
- Each pair is validated independently; having only one pair configured is valid.

### 2.4 `ResolvedColumn` — Add `dataType` field

Add `private final String dataType` alongside the existing fields. Pass it through the constructors and expose via `getDataType()`. `ColumnResolver.buildColumn()` already has access to `FieldMetadata.getDataType()` — pass it into `ResolvedColumn` there.

For columns synthesized without a backing `FieldMetadata` (e.g., the SFTP filename, additional columns, source-component moves), `dataType` is `null`, and the formatter will skip them.

### 2.5 New Utility — `DateFormatter`

**File:** `src/main/java/com/clmextract/csv/DateFormatter.java`

Constructed with a `DateFormatConfig` (may be `null`). Exposes one method:

```
String format(String value, String dataType)
```

**Logic:**
1. If config is `null` → return `value` unchanged.
2. Determine the applicable format pair from `dataType`:
   - `"genericDate"` → use `inputFormat` / `outputFormat` pair (date-only).
   - `"modernDate"` → use `inputDateTimeFormat` / `outputDateTimeFormat` pair (datetime).
   - Anything else → return `value` unchanged.
3. If the relevant pair's input/output formats are both `null` → return `value` unchanged.
4. Parse `value` using `java.time` (`DateTimeFormatter` + `LocalDate` or `LocalDateTime`).
5. On successful parse → return value formatted with the output pattern.
6. On parse failure (including empty/null `value`) → log a `WARN` with field value and expected pattern; return `value` unchanged.

Uses `java.time.DateTimeFormatter` (Java 17 standard library — no new dependency).

### 2.6 `PerComponentCsvWriter` — Apply formatting

- Accept a `DateFormatter` at construction.
- Change `resolveValue()` from `static` to an instance method so it can access the formatter.
- After the raw string value is resolved, call `dateFormatter.format(rawValue, col.getDataType())` before writing to the row array.

`CsvWriterFactory` constructs `PerComponentCsvWriter` — it must receive `DateFormatConfig` from `BoPipeline` and pass a `DateFormatter` instance to the writer.

> **Note:** `MergedSingleCsvWriter` and `SingleOnlyCsvWriter` use independent value-resolution logic and will need the same `DateFormatter` injection if those modes are used for date-typed fields.

---

## 3. Impact and Risk Analysis

**System Dependencies:**
- `ColumnResolver` → `ResolvedColumn` (new `dataType` field touches all column construction paths)
- `PerComponentCsvWriter` → constructor signature changes (affects `CsvWriterFactory`)
- `ConfigLoader` + `AppConfig` → additive, no existing fields touched

**Potential Risks & Mitigations:**

| Risk | Mitigation |
|---|---|
| `genericDate` vs `modernDate` — actual API type strings for date-only vs datetime are confirmed from sample data but have not been validated against a live run with datetime fields | Log the raw `dataType` value at DEBUG level during metadata parsing; add a note in config docs listing the known type strings |
| `resolveValue()` becoming an instance method breaks `static` call sites in tests | Refactor callsites; ensure tests construct a proper `PerComponentCsvWriter` with a no-op `DateFormatter` |
| `java.time.DateTimeFormatter` is strict by default — patterns like `MM/dd/yyyy` with single-digit months may fail if the API pads or doesn't pad | Use `DateTimeFormatterBuilder` with optional sections, or document that the input pattern must exactly match the API's output padding |

---

## 4. Testing Strategy

- **Unit tests for `DateFormatter`:**
  - Correct reformatting for date-only and datetime values given valid input/output patterns.
  - Returns value as-is and logs a warning for values that don't match the input pattern.
  - Returns value as-is when config is `null` or the relevant pair is unconfigured.
  - Empty string input → returned as-is with warning.

- **Unit tests for `ConfigLoader` validation:**
  - Partial date pair (`inputFormat` only) → `ConfigValidationException` with clear message.
  - Partial datetime pair → same.
  - Both pairs absent → no error.
  - Both pairs fully configured → no error.

- **Integration / end-to-end:**
  - Run in offline mode with sample data containing known date values; verify CSV output matches configured output format.
