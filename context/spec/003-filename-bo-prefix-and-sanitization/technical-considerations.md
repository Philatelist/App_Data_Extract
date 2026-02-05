# Technical Considerations: Correct BO Filename Prefix and Sanitize Component Names

- **Functional Specification:** `context/spec/003-filename-bo-prefix-and-sanitization/functional-spec.md`
- **Status:** Completed
- **Author(s):** Claude

---

## 1. High-Level Technical Approach

Two isolated changes, both in the filename resolution layer:

1. **`{BO}` resolution fix**: All four CSV writers currently pass `metadata.getBoUsageType()` (e.g., "Contract") as the `boName` argument to `FilenameResolver.resolve()`. Each call site must be changed to pass `metadata.getBoName()` (e.g., "NAFBO") instead.

2. **Token sanitization**: Add a `sanitize()` method to `FilenameResolver` that normalizes both `{BO}` and `{Component}` tokens before substitution. The method applies: spaces → `_`, non-`[A-Za-z0-9._-]` → `_`, collapse consecutive `_`, trim leading/trailing `_` and `.`.

No changes to CSV content, parsers, domain models, or pipeline logic.

---

## 2. Proposed Solution & Implementation Plan

### 2.1 Add `sanitize()` Method to `FilenameResolver`

Add a static or instance method to `FilenameResolver`:

```java
static String sanitize(String token) {
    if (token == null || token.isEmpty()) return "";
    String s = token.replace(' ', '_');
    s = s.replaceAll("[^A-Za-z0-9._-]", "_");
    s = s.replaceAll("_+", "_");
    s = s.replaceAll("^[_.]+|[_.]+$", "");
    return s;
}
```

Call `sanitize()` on both `boName` and `componentName` inside `resolve()` before substitution:

```java
public String resolve(String template, String boName, String componentName) {
    return template
            .replace("{BO}", sanitize(boName))
            .replace("{Component}", sanitize(componentName))
            .replace("{DDMMYYYY}", dateStr)
            .replace("{HHMMSS}", timeStr);
}
```

**File:** `src/main/java/com/clmextract/csv/FilenameResolver.java`

### 2.2 Change `{BO}` Value in All CSV Writers

Four files pass `metadata.getBoUsageType()` to `filenameResolver.resolve()`. Each must change to `metadata.getBoName()`:

**`PerComponentCsvWriter.java`** (line 55):
```java
// Before
filenameResolver.resolve(filenameTemplate, metadata.getBoUsageType(), comp.getDisplayName());
// After
filenameResolver.resolve(filenameTemplate, metadata.getBoName(), comp.getDisplayName());
```

**`MergedSingleCsvWriter.java`** (lines 63, 92):
```java
// Before (merged file)
filenameResolver.resolve(filenameTemplate, metadata.getBoUsageType(), "Merged");
// After
filenameResolver.resolve(filenameTemplate, metadata.getBoName(), "Merged");

// Before (multi-cardinality files)
filenameResolver.resolve(filenameTemplate, metadata.getBoUsageType(), comp.getDisplayName());
// After
filenameResolver.resolve(filenameTemplate, metadata.getBoName(), comp.getDisplayName());
```

**`SingleOnlyCsvWriter.java`** (line 64):
```java
// Before
filenameResolver.resolve(filenameTemplate, metadata.getBoUsageType(), "SingleOnly");
// After
filenameResolver.resolve(filenameTemplate, metadata.getBoName(), "SingleOnly");
```

**`DownloadsCsvWriter.java`** (line 67):
```java
// Before
filenameResolver.resolve(downloadsFilenameTemplate, metadata.getBoUsageType(), null);
// After
filenameResolver.resolve(downloadsFilenameTemplate, metadata.getBoName(), null);
```

### 2.3 Files Changed Summary

| File | Change |
|---|---|
| `src/main/java/com/clmextract/csv/FilenameResolver.java` | Add `sanitize()` method, apply to both tokens in `resolve()` |
| `src/main/java/com/clmextract/csv/PerComponentCsvWriter.java` | `getBoUsageType()` → `getBoName()` (1 call site) |
| `src/main/java/com/clmextract/csv/MergedSingleCsvWriter.java` | `getBoUsageType()` → `getBoName()` (2 call sites) |
| `src/main/java/com/clmextract/csv/SingleOnlyCsvWriter.java` | `getBoUsageType()` → `getBoName()` (1 call site) |
| `src/main/java/com/clmextract/csv/DownloadsCsvWriter.java` | `getBoUsageType()` → `getBoName()` (1 call site) |
| `src/test/java/com/clmextract/csv/FilenameResolverTest.java` | Add sanitization tests |
| `src/test/java/com/clmextract/csv/PerComponentCsvWriterTest.java` | Update expected filenames (`"Contract_"` → `"TestBO_"` or similar) |
| `src/test/java/com/clmextract/csv/MergedSingleCsvWriterTest.java` | Update expected filenames |
| `src/test/java/com/clmextract/csv/SingleOnlyCsvWriterTest.java` | Update expected filenames |
| `src/test/java/com/clmextract/csv/DownloadsCsvWriterTest.java` | Update expected filenames |

---

## 3. Impact and Risk Analysis

### System Dependencies

- **CSV Writers**: Only the `boName` argument changes. The `resolve()` method signature stays the same. No writer logic changes.
- **FilenameResolver**: The `resolve()` method signature is unchanged. Callers are unaffected by the internal sanitization.
- **BoPipeline**: No changes. It passes metadata and templates to writers, which handle the resolution internally.
- **ColumnResolver**: No changes.
- **Parsers and domain models**: No changes.

### Potential Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Downstream systems depend on current filenames (e.g., "Contract_" prefix) | Medium | Document the change. Old filenames were incorrect per the spec. |
| `boName` is null for some BO types | Low | `sanitize()` handles null by returning empty string. Same behavior as current code. |
| Sanitization changes "Merged" or "SingleOnly" tokens | None | These tokens contain only `[A-Za-z]` characters and pass through unchanged. |
| Existing tests break from filename changes | Certain | All test expected values updated in the same slice. |

---

## 4. Testing Strategy

### Unit Tests (`FilenameResolverTest`)

- **Sanitization cases**:
  - `"Summary"` → `"Summary"` (no change)
  - `"Financial Reporting and Control Review"` → `"Financial_Reporting_and_Control_Review"`
  - `"Cost & Budget (Review)"` → `"Cost_Budget_Review_"` → trimmed → `"Cost_Budget_Review"`
  - `null` → `""`
  - `""` → `""`
  - `"___test___"` → `"test"`
- **Full resolve with sanitization**: Template `{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv` with boName `"NAFBO"` and component `"Financial Reporting and Control Review"` → `"NAFBO_Financial_Reporting_and_Control_Review_01012026_120000.csv"`

### CSV Writer Tests

- Update all expected filenames from `"Contract_..."` to `"TestBO_..."` (or whatever `boName` the test metadata uses).
- Verify no filename contains spaces.

### Integration Verification

- Run `mvn clean package` — all tests pass.
- Run offline mode — verify output filenames start with `NAFBO_` and contain no spaces.
