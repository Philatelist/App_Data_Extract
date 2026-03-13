# Technical Specification: BO Type Discovery and Usage Type Filtering

- **Functional Specification:** `context/spec/005-bo-type-discovery-and-filtering/functional-spec.md`
- **Status:** Approved
- **Author(s):** Claude

---

## 1. High-Level Technical Approach

The core infrastructure for this feature already exists in the codebase:

- `DataSource.getBoTypes()` is defined and implemented in `ApiDataSource` (calls `/BOTypes` via `GET_BO_TYPES`)
- `BoMetadata.getBoUsageType()` is already populated by `MetadataMapper` from the `BundleProperties` node
- `ExportOrchestrator.resolveBoTypes()` already contains a partial discovery stub

The implementation adds three targeted changes:

1. **`AppConfig` / `ConfigLoader`** — add `boUsageTypeFilter` field, parse it, and update validation to allow blank BO names as a discovery signal.
2. **`ExportOrchestrator.resolveBoTypes()`** — extend the existing stub to apply `boUsageTypeFilter` when set, handle empty discovery results gracefully, and add logging.
3. **Tests** — cover the three modes and their edge cases.

No new classes are required. No changes to `DataSource`, `ApiDataSource`, `MetadataParser`, or any CSV/pipeline code.

---

## 2. Proposed Solution & Implementation Plan

### 2.1 Config Model Changes

**File:** `src/main/java/com/clmextract/config/AppConfig.java`

Add one field:

| Field | Type | Default |
|-------|------|---------|
| `boUsageTypeFilter` | `String` | `null` |

Add getter and setter following the existing pattern.

---

**File:** `src/main/java/com/clmextract/config/ConfigLoader.java`

**Parsing change:** Read `boUsageTypeFilter` as a top-level string from the YAML root. Null if absent.

**Validation changes (two):**

1. **Allow blank BO names.** The current loop:
   ```
   for each boTypes[i]: if name is blank → throw
   ```
   Must change to: skip the blank-name check. A `boTypes` list composed entirely of blank names is treated as empty (discovery mode) — it is valid. The `ExportOrchestrator` handles the mode decision at runtime.

2. **Validate `boUsageTypeFilter`.** If the value is non-null and non-empty, it must be one of: `Directory`, `NonContract`, `Contract` (case-sensitive). If it does not match, throw `ConfigValidationException` with the message:
   `"Invalid boUsageTypeFilter value: '[value]'. Allowed values: Directory, NonContract, Contract."`

The allowed values set is a `static final Set<String>` following the same pattern as `VALID_CSV_MODES`.

---

### 2.2 Discovery Logic Changes

**File:** `src/main/java/com/clmextract/export/ExportOrchestrator.java`

Extend the existing `resolveBoTypes(DataSource dataSource)` method. The current logic already handles the "explicit vs. discover all" split. The updated logic:

```
resolveBoTypes(dataSource):

  effectiveBoTypes = config.getBoTypes()
                     filtered to entries where name is non-blank

  if effectiveBoTypes is non-empty:
    → Explicit mode
    log "Explicit boTypes configured: processing N BO type(s): [list]"
    return effectiveBoTypes

  → Discovery mode
  boTypeNames = dataSource.getBoTypes()   // calls /BOTypes
  log "Discovered N BO type(s) from /BOTypes: [list]"

  if boTypeNames is empty:
    log warning "No BO types found on server. Nothing to export."
    return empty list

  if config.boUsageTypeFilter is null or blank:
    → Mode 2: all discovered
    log "No explicit boTypes configured. Discovering all BO types from server."
    wrap boTypeNames as BoTypeConfig list and return

  → Mode 3: filter by usageType
  log "No explicit boTypes configured. Discovering BO types with usageType filter: [value]."

  retained = []
  for each boTypeName in boTypeNames:
    metadata = dataSource.getMetadata(boTypeName)   // reuses existing ApiDataSource call
    usageType = metadata.getBoUsageType()
    if usageType is null:
      log warning "BO type [name]: no usageType found in metadata — excluded."
      continue
    if usageType equals config.boUsageTypeFilter:
      retained.add(boTypeName)

  log "Retained K of N BO type(s) after usageType filter '[value]': [list]."

  if retained is empty:
    log warning "No BO types to process. Exiting."

  return retained as BoTypeConfig list
```

**Important:** metadata fetched during filtering (Mode 3) is used only to read `usageType`. The pipeline in `BoPipeline` will fetch metadata again for each BO during export. This is by design: it keeps the pipeline self-contained and avoids passing stale pre-fetched metadata state into the export loop. (See §3 for the efficiency note.)

**Mode detection helper:** Extract `isDiscoveryMode()` as a private method to avoid duplicating the blank-name filtering logic.

---

### 2.3 Config YAML

**File:** `config.yml`

Add the new optional field with a comment:

```yaml
# Optional: filter discovered BOs by usage type. Allowed: Directory, NonContract, Contract
# Only applies when boTypes is empty (discovery mode).
# boUsageTypeFilter: Contract
```

---

### 2.4 `OfflineDataSource` Stub

**File:** `src/main/java/com/clmextract/export/OfflineDataSource.java`

Verify that `getBoTypes()` returns a non-empty list for offline/test use. If it currently returns an empty list or throws, add a small hardcoded sample list of 2–3 BO names so that offline runs work with discovery mode.

---

### 2.5 Endpoints File

**File:** `inputs/endpoints.yml`

Verify that a `getBoTypes` endpoint entry exists. If it is already present (expected, since `GET_BO_TYPES` is already mapped in `EndpointRegistry`), no change is needed. If absent, add it:

```yaml
- name: getBoTypes
  method: GET
  path: /BOTypes
  auth:
    type: sessionHeader
    headerName: session_id
  response:
    type: json
```

---

## 3. Impact and Risk Analysis

**System Dependencies:**

- `ConfigLoader` → `AppConfig`: additive only; no existing fields changed.
- `ExportOrchestrator` → `DataSource.getMetadata()`: already called in `BoPipeline`; calling it additionally in `resolveBoTypes()` for filtering is a new usage of an existing, well-tested method.
- `ExportOrchestrator` → `DataSource.getBoTypes()`: already defined and called; this spec only adds the filter branch on top.

**Potential Risks & Mitigations:**

| Risk | Mitigation |
|------|-----------|
| Metadata fetched twice per BO in Mode 3 (once to read `usageType`, once in the export pipeline) | Accepted as-is per the prompt requirement to reuse the existing metadata parser and keep the pipeline self-contained. The extra calls are only for the pre-export filtering pass and occur only in Mode 3. |
| Config validation removes the `boTypes[i].name required` guard | The blank-name check is removed from `validate()`. Runtime discovery mode handles the empty-names case. No regression because: (a) the only callers of `getBoTypes()` in the orchestrator already handle empty results; (b) the empty-result warning is explicit. |
| `boUsageTypeFilter` is validated only at startup — misspelling is caught early | Fail-fast at `ConfigLoader.validate()` before any API calls are made. |
| `/BOTypes` endpoint missing from `endpoints.yml` | `EndpointRegistry.getEndpoint(GET_BO_TYPES)` will throw `EndpointResolutionException` with a clear message if the entry is absent. No additional guard needed. |
| Metadata call failure during usageType filtering (Mode 3) | `dataSource.getMetadata()` throws `RuntimeException` on API failure. This propagates to the orchestrator's `try/finally`, which still ensures logout runs. Fail-fast behaviour matches the functional spec requirement. |

---

## 4. Testing Strategy

All tests use JUnit 5 following the existing test structure.

**`ConfigLoaderTest`** (extend existing file):

| Case | Assertion |
|------|-----------|
| `boUsageTypeFilter: Contract` | Parses without error; `config.getBoUsageTypeFilter()` equals `"Contract"` |
| `boUsageTypeFilter: Directory` | Parses without error |
| `boUsageTypeFilter: NonContract` | Parses without error |
| `boUsageTypeFilter: contract` (lowercase) | `ConfigValidationException` with clear message |
| `boUsageTypeFilter: BadValue` | `ConfigValidationException` with clear message |
| `boUsageTypeFilter` absent | No error; field is null |
| `boTypes: [{name: ""}]` | No `ConfigValidationException` (blank name is allowed) |

**`ExportOrchestratorTest`** (new test class using a stub/fake `DataSource`):

| Case | Setup | Assertion |
|------|-------|-----------|
| Explicit mode | `config.boTypes = [NAFBO, GPEBO]` | Orchestrator processes exactly those two BOs; `getBoTypes()` not called |
| Discovery — all | `config.boTypes = []`, no filter; `getBoTypes()` returns 3 names | All 3 BOs processed |
| Discovery — filtered | `config.boTypes = []`, `boUsageTypeFilter = "Contract"`; `getBoTypes()` returns 3 names; metadata for 2 returns `usageType=Contract`, 1 returns `usageType=Directory` | Only 2 BOs processed |
| Discovery — missing usageType | One BO's metadata has `boUsageType = null` | That BO is excluded; warning logged |
| Discovery — empty BOTypes | `getBoTypes()` returns empty list | No BOs processed; exits cleanly |
| Invalid filter | `boUsageTypeFilter = "BadValue"` | `ConfigValidationException` at load time, before orchestrator runs |
