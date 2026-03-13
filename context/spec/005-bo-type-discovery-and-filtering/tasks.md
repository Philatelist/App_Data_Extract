# Tasks: BO Type Discovery and Usage Type Filtering

---

## Slice 1: Config parses and validates `boUsageTypeFilter`

After this slice: the tool accepts `boUsageTypeFilter: Contract` and loads cleanly; setting an invalid value fails fast before any API calls. All existing behaviour is unchanged.

- [x] Add `boUsageTypeFilter` field (String, default null) with getter/setter to `AppConfig`. **[Agent: general-purpose]**
- [x] Parse `boUsageTypeFilter` as a top-level string in `ConfigLoader.load()`. **[Agent: general-purpose]**
- [x] Add `VALID_BO_USAGE_TYPE_FILTERS = Set.of("Directory", "NonContract", "Contract")` to `ConfigLoader` and validate the field in `validate()` — throw `ConfigValidationException` with the specified message if non-null and not in the set. **[Agent: general-purpose]**
- [x] Remove the guard in `ConfigLoader.validate()` that throws when `boTypes[i].name` is blank (blank name is now valid — it signals discovery mode). **[Agent: general-purpose]**
- [x] Update `config.yml` — add commented `boUsageTypeFilter` field with allowed values noted. **[Agent: general-purpose]**
- [x] Extend `ConfigLoaderTest` with cases: valid values (`Contract`, `Directory`, `NonContract`), invalid values (`contract`, `BadValue`), absent field (null), and blank `boTypes[i].name` no longer throws. **[Agent: general-purpose]**
- [x] Run `mvn test` — all tests pass. **[Agent: general-purpose]**

---

## Slice 2: Discovery mode — all BOs (no filter)

After this slice: running with `boTypes: []` (or omitted) and no `boUsageTypeFilter` causes the tool to call `/BOTypes`, log all discovered BO types, and run the full export pipeline on all of them. In offline mode the tool runs end-to-end with a discovered BO.

- [x] Fix `OfflineDataSource.getBoTypes()` to return `List.of(metadata.getBoName())` instead of `List.of(metadata.getBoUsageType())` so offline discovery returns a valid BO name. **[Agent: general-purpose]**
- [x] Extend `ExportOrchestrator.resolveBoTypes()`: add a private `isDiscoveryMode()` helper that filters `config.getBoTypes()` to non-blank entries and returns true when the result is empty. Update the method to use this helper, log the Mode 2 discovery message, handle the empty-discovery-result case (log warning, return empty list so the pipeline loop exits cleanly), and log the discovered BO count and names. **[Agent: general-purpose]**
- [x] Add `ExportOrchestratorTest` with a stub `DataSource` covering: explicit mode (`getBoTypes()` never called), discovery-all with 3 BOs (all 3 processed), empty discovery result (no BOs processed, no exception). **[Agent: general-purpose]**
- [x] Run `mvn test` — all tests pass. **[Agent: general-purpose]**
- [x] Run the tool in offline mode with `boTypes: []` in `config-offline.yml` and verify via log output that the BO is discovered and exported. **[Agent: general-purpose]**

---

## Slice 3: Discovery mode — filtered by `boUsageTypeFilter`

After this slice: setting `boUsageTypeFilter: Contract` (with empty `boTypes`) causes the tool to inspect each discovered BO's metadata and export only matching BOs. BOs with no `usageType` are excluded with a warning.

- [x] Extend `ExportOrchestrator.resolveBoTypes()` with Mode 3 logic: when `boUsageTypeFilter` is set and discovery mode is active, iterate over discovered BO names, call `dataSource.getMetadata(boTypeName)`, read `metadata.getBoUsageType()`, include or exclude based on match, log a warning for null `usageType`, log the retained count and list, and log a warning if retained list is empty. **[Agent: general-purpose]**
- [x] Extend `ExportOrchestratorTest` with filter scenarios using the stub `DataSource`: filter matches 2 of 3 BOs (only 2 processed), BO with null `usageType` is excluded, filter retains zero BOs (no export, no exception). **[Agent: general-purpose]**
- [x] Run `mvn test` — all tests pass. **[Agent: general-purpose]**
