# Technical Considerations: In-Repo Parameter Selection and Display Name Overrides

- **Functional Specification:** `context/spec/004-parameter-overrides-and-display-names/functional-spec.md`
- **Status:** Completed
- **Author(s):** Claude

---

## 1. High-Level Technical Approach

All changes are concentrated in `ColumnResolver.java` — the central class that already handles column ordering and display name overrides. The current implementation needs to be updated to:

1. **Change the column order file format** from full `MCPDef:/` instance paths to `Module/Component/Parameter` paths (one per line, no delimiter).
2. **Change the column order filename** from `config/columns/{boUsageType}.csv` to `config/columns/{boInternalName}.csv`.
3. **Change the display name override file** from per-BO `config/overrides/{boType}.csv` (comma-delimited, fieldPath-keyed) to a global `config/overrides/AddParameters.csv` (semicolon-delimited, `Component;Parameter;DisplayName;` with header row).
4. **Retain field paths not in metadata** — currently, `resolveFieldPaths()` and `resolveColumns()` only include paths that exist in both the order file and metadata. They must be changed to include order-file paths even when no matching metadata field exists.
5. **Exclude unlisted components** — when the order file exists, components not represented by any line must be excluded entirely (this is already the implicit behavior of the current filtering, but needs to be explicit).
6. **Pass BO internal name** to `ColumnResolver` instead of usage type.

No changes to CSV writers, BoPipeline, parsers, or domain models.

---

## 2. Proposed Solution & Implementation Plan

### 2.1 ColumnResolver Constructor Change

**Current** (line 26):
```java
public ColumnResolver(List<ComponentMetadata> components, String boType)
```

The `boType` parameter currently receives the BO usage type (e.g., "Contract") from `BoPipeline`. This needs to change so the column order file uses the BO internal name (e.g., "NAFBO").

**Change in BoPipeline**: Pass `metadata.getBoName()` instead of `boType` to the ColumnResolver constructor. The existing `boType` variable in BoPipeline comes from `boTypeConfig.getName()` which is the usage type.

**Files:**
- `src/main/java/com/clmextract/export/BoPipeline.java` — change the argument passed to `new ColumnResolver(...)`.

### 2.2 Column Order File Loading (`loadOrderFile()`)

**Current format** (line 34): Reads `config/columns/{boType}.csv` with full `MCPDef:/` instance paths.

**New format**: Reads `config/columns/{boInternalName}.csv` with paths in `Module/Component/Parameter` format (e.g., `NAFData/ReqNAFInfo/trackingNumber`). One path per line, no header, no delimiter. Empty lines and whitespace trimmed.

**Implementation**: The loading code itself barely changes — it already reads lines and trims. The key difference is:
- The filename uses `boName` (internal name), which is now passed via the constructor.
- Path format no longer has `MCPDef:/` prefix — paths are stored as-is.

**Files:**
- `src/main/java/com/clmextract/csv/ColumnResolver.java` — `loadOrderFile()` method.

### 2.3 Display Name Override File Loading (`loadOverridesFile()`)

**Current**: Reads `config/overrides/{boType}.csv` with comma-delimited `fieldPath,newDisplayName` format, keyed by full `MCPDef:/` instance path.

**New**: Reads `config/overrides/AddParameters.csv` (global, not per-BO) with semicolon-delimited `Component;Parameter;DisplayName;` format. First line is a header row and must be skipped.

**Implementation**:
- Change the file path from `config/overrides/{boType}.csv` to `config/overrides/AddParameters.csv`.
- Change the delimiter from comma to semicolon.
- Skip the first line (header row).
- Parse three columns: component internal name, parameter internal name, display name.
- Store as `Map<String, Map<String, String>>` (component → (parameter → displayName)) instead of `Map<String, String>` (instancePath → displayName).

**Files:**
- `src/main/java/com/clmextract/csv/ColumnResolver.java` — `loadOverridesFile()` method, `displayNameOverrides` field type change.

### 2.4 Field Path Resolution (`resolveFieldPaths()`)

**Current** (lines 73-91):
1. Extracts all field instance paths from metadata, stripping `MCPDef:/` prefix.
2. If order file exists, returns only paths that appear in BOTH the order file and metadata.

**New behavior**:
1. Same extraction from metadata.
2. If order file exists, returns ALL paths from the order file in order file order — even if a path is not found in metadata. This satisfies the requirement that "field paths not in BOMetaData are retained."

**Implementation**: Remove the `if (paths.contains(orderPath))` filter. Return all order file paths directly.

**Files:**
- `src/main/java/com/clmextract/csv/ColumnResolver.java` — `resolveFieldPaths()` method.

### 2.5 Column Resolution (`resolveColumns()`)

**Current** (lines 94-114): Takes a `ComponentMetadata`, matches its fields against the order file by instance path, builds `ResolvedColumn` objects.

**New behavior**: This method needs significant rework because:
1. The order file uses `Module/Component/Parameter` format, not `MCPDef:/` instance paths.
2. Columns from the order file that don't have a matching `FieldMetadata` in the component must still be included (with the parameter internal name as fallback display name).
3. The display name override lookup changes from instance-path-keyed to (component, parameter)-keyed.

**Implementation**:
- For each order file path belonging to this component (determined by matching the component segment), find the corresponding `FieldMetadata` by matching the parameter segment against `field.getInternalName()`.
- If found: use the field's metadata for display name (with override check).
- If not found: create a `ResolvedColumn` using the parameter name from the path as both internal name and fallback display name, checking the override file first.
- The override lookup changes: check `displayNameOverrides.get(componentInternalName).get(parameterInternalName)` instead of `displayNameOverrides.get(instancePath)`.

**Files:**
- `src/main/java/com/clmextract/csv/ColumnResolver.java` — `resolveColumns()` and `buildColumn()` methods.

### 2.6 Component Filtering

When the order file exists, `resolveColumns()` returns an empty list for components that have no paths in the order file. The CSV writers already skip components with empty column lists (`PerComponentCsvWriter` line 50: `if (columns.isEmpty()) continue;`). No additional change needed — the existing behavior already excludes unlisted components.

### 2.7 Files Changed Summary

| File | Change |
|---|---|
| `src/main/java/com/clmextract/csv/ColumnResolver.java` | Rework `loadOrderFile()`, `loadOverridesFile()`, `resolveFieldPaths()`, `resolveColumns()`, `buildColumn()`. Change `displayNameOverrides` type. |
| `src/main/java/com/clmextract/export/BoPipeline.java` | Pass `metadata.getBoName()` to `ColumnResolver` constructor instead of `boType`. |
| `src/test/java/com/clmextract/csv/ColumnResolverTest.java` | Update tests for new file formats, add tests for paths not in metadata, add tests for AddParameters.csv override format. |
| `config/columns/NAFBO.csv` | Create example column order file for offline testing. |
| `config/overrides/AddParameters.csv` | Create example display name override file for offline testing. |

---

## 3. Impact and Risk Analysis

### System Dependencies

- **CSV Writers** (`PerComponentCsvWriter`, `MergedSingleCsvWriter`, `SingleOnlyCsvWriter`): Unaffected. They consume `ResolvedColumn` objects from `ColumnResolver.resolveColumns()`. The interface (`getHeader()`, `getFieldInternalName()`) does not change.
- **BoPipeline**: Minor change — passes `boName` instead of `boType` to ColumnResolver. The `fieldPaths` list returned by `resolveFieldPaths()` is passed to `fetchBatch()` as before.
- **Bundles API request**: The field paths sent to the API must still be in `Module/Component/Parameter` format without prefix. Since the order file now uses exactly this format, paths from the order file can be sent directly. Paths from metadata still have `MCPDef:/` stripped as before.
- **BundlesMapper**: Unaffected. Field values are looked up by `fieldInternalName` in `BundleComponent.getFields()`, which remains unchanged.
- **Downloads CSV writer**: Unaffected.

### Potential Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Order file paths don't match API expectations (wrong format) | Low | Order file format (`Module/Component/Parameter`) matches what `resolveFieldPaths()` already produces after stripping `MCPDef:/`. Verified by tests. |
| Fields in order file but not in metadata produce empty columns | Expected | By design — the spec requires these to be retained. Values populated from bundles response if the API returns them. |
| Global AddParameters.csv conflicts across BOs (same component/parameter name, different display needs) | Low | Component+parameter combination is typically unique. If conflict arises, a per-BO override mechanism could be added later. |
| Existing tests break from ColumnResolver signature change | Certain | Tests updated in the same slice. |

---

## 4. Testing Strategy

### Unit Tests (`ColumnResolverTest`)

- **Column order file parsing**: Create a temp file with `Module/Component/Parameter` paths, verify `resolveFieldPaths()` returns them in order.
- **Fields not in metadata retained**: Add paths to order file that don't exist in test metadata, verify they appear in `resolveFieldPaths()` output and in `resolveColumns()` results.
- **Component exclusion**: Verify that components not listed in the order file produce empty `resolveColumns()` results.
- **AddParameters.csv parsing**: Create a temp file with `Component;Parameter;DisplayName;` format, verify display names are applied in `resolveColumns()` headers.
- **Display name precedence**: Test that AddParameters.csv override takes priority over metadata display name.
- **No order file (default)**: Verify all metadata fields are returned in metadata order when no order file exists.
- **No override file (default)**: Verify metadata display names are used when no override file exists.

### Integration Verification

- Run `mvn clean package` — all tests pass.
- Create `config/columns/NAFBO.csv` with a subset of fields, run offline mode, verify CSV output contains only those columns.
- Create `config/overrides/AddParameters.csv` with display name overrides, verify CSV headers reflect the overrides.
