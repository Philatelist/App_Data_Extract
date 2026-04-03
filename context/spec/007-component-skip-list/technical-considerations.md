# Technical Specification: Component Skip List

- **Functional Specification:** `context/spec/007-component-skip-list/functional-spec.md`
- **Status:** Approved
- **Author(s):** Alex

---

## 1. High-Level Technical Approach

The skip list is stored in `AppConfig` as `List<String>` (parsed from the `skipComponents` YAML key, following the identical pattern of the existing `skipColumns` field). The filtering itself happens in `BoPipeline.execute()` immediately after the metadata is loaded, before `ColumnResolver` is constructed. The raw component list from metadata is replaced with a filtered copy, so all downstream code — `ColumnResolver`, `CsvWriterFactory`, every CSV writer — naturally never sees the suppressed components. No changes are needed to the CSV writing layer.

Files touched: `AppConfig`, `ConfigLoader`, `BoPipeline`. Tests added to `ConfigLoaderTest` and `BoPipelineTest`.

---

## 2. Proposed Solution & Implementation Plan

### 2.1 Configuration (`AppConfig` + `ConfigLoader`)

**`AppConfig.java`** — add one new field following the `skipColumns` pattern exactly:

| Field | Type | Default |
|---|---|---|
| `skipComponents` | `List<String>` | `new ArrayList<>()` |

Getter: `getSkipComponents()`. Setter: null-safe, same as `setSkipColumns`.

**`ConfigLoader.java`** — add one line in `load()`:

```
config.setSkipComponents(getStringList(root, "skipComponents"));
```

No validation required; an absent or empty key returns an empty list, which means no filtering.

### 2.2 Filtering Logic (`BoPipeline`)

**`BoPipeline.execute()`** — insert a filtering step immediately after `metadata` is loaded (Step 1), before `ColumnResolver` is constructed (Step 3):

- If `config.getSkipComponents()` is empty, skip the entire block (zero overhead for the common case).
- Build a **normalised skip set**: for each entry in `config.getSkipComponents()`, apply `.trim().toLowerCase()`. Use a `HashSet` for O(1) lookup.
- Iterate over `metadata.getComponents()`. For each component, check whether `component.getDisplayName().trim().toLowerCase()` **or** `component.getInternalName().trim().toLowerCase()` is in the skip set.
  - **Match**: log `INFO "Skipping component \"{}\" for BO type \"{}\" (in skipComponents list)"` using the component's `displayName` and the current `boType`. Do **not** add to the retained list.
  - **No match**: add to the retained list.
- Call `metadata.setComponents(retainedList)`.

From this point, `ColumnResolver`, `CsvWriterFactory`, and every CSV writer see only the retained components — no further changes needed.

### 2.3 Matching against both names

Matching against both `displayName` and `internalName` covers the realistic case where a CLM administrator may know either name. The spec names in the example (`TableNamesMapping`, `BundleProperties`, etc.) appear to be internal names, but the matching is transparent to the admin.

---

## 3. Impact and Risk Analysis

- **System Dependencies:** Only `BoPipeline` is modified at runtime. `AppConfig` and `ConfigLoader` changes are additive and backward-compatible (absent key → empty list → no filtering).
- **Metadata object mutation:** `metadata.setComponents()` is called on a freshly-fetched object, scoped to a single BO type pipeline run. There is no shared state risk.
- **Manifest correctness:** Because suppressed components never produce output files, they never appear in the manifest — correct by construction, no manifest code needs changes.
- **Risk — wrong component name in config:** If an admin misspells a component name, no error is raised and no log entry is produced (the component simply doesn't match and is exported normally). This is acceptable per the spec (no false-positive log entries).

---

## 4. Testing Strategy

**`ConfigLoaderTest`:**
- `skipComponents` YAML list is parsed into `List<String>` correctly.
- Absent key → empty list.
- Empty YAML list (`[]`) → empty list.

**`BoPipelineTest`:**
- When a component's `displayName` is in `skipComponents`, it is absent from the component list after filtering (verify via the metadata object or absence of output file).
- Case-insensitive match works (e.g., config entry `"trackingnumbers"` suppresses component `displayName="TrackingNumbers"`).
- Whitespace trimming works (config entry `"  BundleProperties  "` suppresses component).
- Partial name does **not** match (`"Tracking"` does not suppress `"TrackingNumbers"`).
- When `skipComponents` is empty, no components are removed.
- Each suppressed component produces exactly one INFO log line per BO type.
