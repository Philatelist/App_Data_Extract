# Tasks: 007 — Component Skip List

- [ ] **Slice 1: `skipComponents` parsed from config and accessible in `AppConfig`**
  - [ ] Add `skipComponents` field (`List<String>`, default `new ArrayList<>()`) to `AppConfig` with null-safe getter and setter, following the `skipColumns` pattern. **[Agent: general-purpose]**
  - [ ] Add `config.setSkipComponents(getStringList(root, "skipComponents"))` to `ConfigLoader.load()`. **[Agent: general-purpose]**
  - [ ] Add tests to `ConfigLoaderTest`: (a) YAML list parses correctly, (b) absent key returns empty list, (c) empty list `[]` returns empty list. **[Agent: general-purpose]**
  - [ ] Run `mvn test -pl . -Dtest=ConfigLoaderTest` and verify all new tests pass. **[Agent: general-purpose]**

- [ ] **Slice 2: Skipped components removed from metadata before pipeline processes them**
  - [ ] In `BoPipeline.execute()`, immediately after `metadata = dataSource.getMetadata(boType)`: if `config.getSkipComponents()` is non-empty, build a normalised skip set (trim + lowercase each entry), iterate `metadata.getComponents()`, log INFO for each match, and call `metadata.setComponents(retainedList)`. **[Agent: general-purpose]**
  - [ ] Add tests to `BoPipelineTest`: (a) component with matching `displayName` is absent after filtering, (b) case-insensitive match works, (c) whitespace-trimmed match works, (d) partial name does not match, (e) empty `skipComponents` removes nothing, (f) each suppressed component logs exactly one INFO line. **[Agent: general-purpose]**
  - [ ] Run `mvn test -pl . -Dtest=BoPipelineTest` and verify all new tests pass. **[Agent: general-purpose]**

- [ ] **Slice 3: End-to-end build and smoke test**
  - [ ] Run `mvn package -DskipTests` and confirm the JAR builds cleanly. **[Agent: general-purpose]**
  - [ ] Add `skipComponents` entries to `config.yml` (e.g., `TableNamesMapping`, `BundleProperties`) and run the JAR. Verify: (a) no CSV file is created for the suppressed component names, (b) the run log contains one INFO line per suppressed component per BO type, (c) all other components are still exported normally. **[Agent: general-purpose]**
