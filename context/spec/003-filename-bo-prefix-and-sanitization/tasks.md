# Tasks: Correct BO Filename Prefix and Sanitize Component Names

- [x] **Slice 1: Add sanitization to FilenameResolver and fix `{BO}` resolution**
  - [x] Sub-task: Add `sanitize()` method to `FilenameResolver`. Apply it to both `boName` and `componentName` in `resolve()`. Rules: spaces → `_`, non-`[A-Za-z0-9._-]` → `_`, collapse `_+`, trim leading/trailing `_` and `.`. **[Agent: general-purpose]**
  - [x] Sub-task: Add sanitization tests to `FilenameResolverTest`: "Summary" unchanged, "Financial Reporting and Control Review" → `Financial_Reporting_and_Control_Review`, special chars, null, empty, leading/trailing underscores. Add full resolve test with sanitized output. **[Agent: general-purpose]**

- [x] **Slice 2: Change all CSV writers from `getBoUsageType()` to `getBoName()` and update tests**
  - [x] Sub-task: In `PerComponentCsvWriter`, `MergedSingleCsvWriter`, `SingleOnlyCsvWriter`, and `DownloadsCsvWriter`, replace `metadata.getBoUsageType()` with `metadata.getBoName()` in all `filenameResolver.resolve()` calls (5 call sites total). **[Agent: general-purpose]**
  - [x] Sub-task: Update expected filenames in `PerComponentCsvWriterTest`, `MergedSingleCsvWriterTest`, `SingleOnlyCsvWriterTest`, and `DownloadsCsvWriterTest` to use the BO internal name instead of usage type. Ensure test metadata has `boName` set. **[Agent: general-purpose]**

- [x] **Slice 3: Build verification**
  - [x] Sub-task: Run `mvn clean package` and verify all tests pass. **[Agent: Bash]**
