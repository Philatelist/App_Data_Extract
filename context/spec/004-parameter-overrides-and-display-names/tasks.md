# Tasks: In-Repo Parameter Selection and Display Name Overrides

- [x] **Slice 1: Rework ColumnResolver for new file formats**
  - [x] Sub-task: Change `loadOrderFile()` to read `config/columns/{boName}.csv` with `Module/Component/Parameter` paths (one per line, no header). Change `loadOverridesFile()` to read `config/overrides/AddParameters.csv` (semicolon-delimited `Component;Parameter;DisplayName;` with header row). Change `displayNameOverrides` from `Map<String, String>` to `Map<String, Map<String, String>>`. **[Agent: general-purpose]**
  - [x] Sub-task: Change `resolveFieldPaths()` to return ALL order file paths directly (remove the metadata-existence filter). Change `resolveColumns()` to match order file paths by component/parameter internal name, create `ResolvedColumn` for paths not in metadata (using parameter name as fallback). Update `buildColumn()` override lookup to use component+parameter keys. **[Agent: general-purpose]**

- [x] **Slice 2: Pass BO internal name to ColumnResolver and update tests**
  - [x] Sub-task: In `BoPipeline`, pass `metadata.getBoName()` instead of `boType` to `new ColumnResolver(...)`. **[Agent: general-purpose]**
  - [x] Sub-task: Rewrite `ColumnResolverTest` for new file formats: test order file parsing with `Module/Component/Parameter` paths, test paths not in metadata are retained, test component exclusion, test AddParameters.csv override parsing, test display name precedence, test defaults when no files exist. **[Agent: general-purpose]**

- [x] **Slice 3: Create example override files and verify**
  - [x] Sub-task: Create `config/columns/NAFBO.csv` with a subset of field paths from the real metadata. Create `config/overrides/AddParameters.csv` with example display name overrides. **[Agent: general-purpose]**
  - [x] Sub-task: Run `mvn clean package` and verify all tests pass. **[Agent: Bash]**
