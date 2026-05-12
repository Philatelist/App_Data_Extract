# Tasks: Wire Real Export Pipeline into Web UI

- **Functional Specification:** `context/spec/012-wire-real-export-pipeline/functional-spec.md`
- **Technical Specification:** `context/spec/012-wire-real-export-pipeline/technical-considerations.md`
- **Status:** Ready

---

## Slice 1: Admin Panel toggles — Enable ZIP Packaging + Enable SFTP Upload

- [x] `AppConfig`: add `enableZipPackaging` (default `true`) and `enableSftpUpload` (default `true`) with getters/setters. **[Agent: java-backend]**
- [x] `ConfigLoader`: assign both defaults if missing from YAML. **[Agent: java-backend]**
- [x] `ConfigController`: include both fields in GET and PUT responses. **[Agent: java-backend]**
- [x] `admin.html`: add two toggle switches in the SFTP Connection card — "Enable ZIP Packaging" (`id="enable-zip-packaging"`) and "Enable SFTP Upload" (`id="enable-sftp-upload"`); SFTP credential fields grey out (disabled, not hidden) when the SFTP toggle is OFF. **[Agent: general-purpose]**
- [x] `admin.js`: `loadConfig()` reads both fields and sets toggle states; `collectConfig()` includes both boolean values in the PUT body; wire toggle change events to enable/disable SFTP credential inputs. **[Agent: general-purpose]**
- [x] Verify: start server, open Admin Panel — both toggles are visible with correct default states; toggle ZIP OFF, save, reload → ZIP remains OFF; toggle SFTP OFF → SFTP credential fields become greyed out/disabled. **[Agent: general-purpose]**

---

## Slice 2: SKIPPED step status — disabled steps show grey "Skipped" pill

- [x] `RunStatus`: add `SKIPPED` to the enum alongside `PENDING, IN_PROGRESS, SUCCESS, FAILED`. **[Agent: java-backend]**
- [x] `RunExecutor.executeRun()`: PACKAGING step — if `!config.isEnableZipPackaging()`, call `updateStep(runId, STEP_PACKAGING, RunStatus.SKIPPED)` and fall through to SFTP step; SFTP step — if `!config.isEnableSftpUpload()`, call `updateStep(runId, STEP_SFTP_UPLOAD, RunStatus.SKIPPED)` and return. **[Agent: java-backend]**
- [x] `dashboard.js` PILL map: add `SKIPPED: { text: 'Skipped', cls: 'skipped' }`. **[Agent: general-purpose]**
- [x] `style.css`: add `.status-pill.skipped { background: #e8e8e8; color: #777; }`. **[Agent: general-purpose]**
- [x] Verify: disable ZIP Packaging in Admin Panel; start an export from the dashboard (EXPORT_CSV is still the stub); the Packaging step row shows a grey "Skipped" pill; the run proceeds to the SFTP step. **[Agent: general-purpose]**

---

## Slice 3: Register custom tracking endpoints in EndpointRegistry + DataSource defaults

- [x] `EndpointRegistry`: add `GET_TRACKING_NUMBERS_AFTER_DATE` (operation name `"trackingNumbersAfterDate"`) and `GET_TRACKING_NUMBERS_IN_FLIGHT` (operation name `"trackingNumbersInFlight"`) public constants; add both to `OPERATION_NAME_MAP`; add both to the required-endpoints validation set. **[Agent: java-backend]**
- [x] `DataSource` interface: add two new default methods — `getTrackingNumbersAfterDate(String boType, String dateTime)` (default delegates to `getTrackingNumbers(boType)`) and `getTrackingNumbersInFlight(String boType, int daysBeforeToday)` (default delegates to `getTrackingNumbers(boType)`). **[Agent: java-backend]**
- [x] Verify: build and start server — no missing-endpoint startup errors; both endpoint constants resolve without throwing in `EndpointRegistry`. **[Agent: general-purpose]**

---

## Slice 4: Real EXPORT_CSV — wire BoPipeline into RunExecutor (no date filter yet)

- [x] `DateFilter.java` (new): value object with fields `String dateField`, `String dateFrom`, `boolean modifiedWithinPeriod`, `int daysBeforeToday`. **[Agent: java-backend]**
- [x] `BoPipeline`: add new `execute(BoTypeConfig boTypeConfig, Path outputDir, DateFilter dateFilter)` overload — writes CSV to the supplied `outputDir` instead of computing from config; if `dateFilter` is null, falls through to `getTrackingNumbers`; all other steps (metadata, batching, CSV writing, downloads) identical to the original; original `execute(BoTypeConfig)` remains unchanged. **[Agent: java-backend]**
- [x] `UiState.RunState`: add `List<String> warnings = new ArrayList<>()` field with getter/setter; serialized to `ui-state.json` via Jackson. **[Agent: java-backend]**
- [x] `RunExecutor.startRun()`: add 4th parameter `DateFilter dateFilter` (nullable); pass it through to `executeRun()`. **[Agent: java-backend]**
- [x] `RunExecutor.executeRun()`: replace `Thread.sleep(1000)` stub with the real pipeline — load `AppConfig`; create `outputDir` as `Path.of(config.getOutputRoot(), config.getExportFolderName(), runId)`; call `Files.createDirectories(outputDir)`; resolve BO list (filter `config.getBoTypes()` by `selectedBos` if non-empty, else use all); instantiate `BoPipeline(config, dataSource)`; loop each BO calling `pipeline.execute(boTypeConfig, outputDir, dateFilter)` — on per-BO exception, log, add to `runState.warnings`, continue; run `SummaryCsvWriter` if `generateSummaryCsv` enabled; fetch and write reports if configured; run `ManifestCsvWriter` if manifest enabled; call `BackupManager.enforceRetention()` scoped to `runId`-pattern subdirectories; mark `EXPORT_CSV` SUCCESS if at least one BO succeeded, FAILED if all failed. **[Agent: java-backend]**
- [x] `ExportScheduler`: update `startRun()` call to pass `null` as the `DateFilter` argument. **[Agent: java-backend]**
- [x] `RunController.startRun()`: update `runExecutor.startRun()` call to pass `null` DateFilter for now (real wiring in Slice 5). **[Agent: java-backend]**
- [x] Verify: start server, log in as operator, start export from dashboard → CSV files appear under `output/MetaData/<runId>/`; Export History panel shows the run with green Success for EXPORT_CSV. **[Agent: general-purpose]**

---

## Slice 5: Date filter wiring — filtered export via CLM custom endpoints

- [x] `ApiDataSource`: override `getTrackingNumbersAfterDate(String boType, String dateTime)` — converts `dateTime` from ISO `yyyy-MM-dd` format to `dd-M-yyyy HH:mm:ss`, calls `TrackingNumberFetcher.fetch()` with `GET_TRACKING_NUMBERS_AFTER_DATE` endpoint and headers `boType` + `dateTime`; override `getTrackingNumbersInFlight(String boType, int daysBeforeToday)` — calls `TrackingNumberFetcher.fetch()` with `GET_TRACKING_NUMBERS_IN_FLIGHT` endpoint and headers `boType` + `daysBeforeToday`. **[Agent: java-backend]**
- [x] `BoPipeline.execute(BoTypeConfig, Path, DateFilter)`: apply DateFilter routing at the tracking number fetch step — if `dateField == "createDate"` and `dateFrom` is non-blank → call `getTrackingNumbersAfterDate`; else if `modifiedWithinPeriod` → call `getTrackingNumbersInFlight(boType, dateFilter.getDaysBeforeToday())`; else → `getTrackingNumbers(boType)`. **[Agent: java-backend]**
- [x] `RunController.startRun()`: read `dateField`, `dateFrom`, `modifiedWithinPeriod` from the JSON request body; when `modifiedWithinPeriod` is true, compute `daysBeforeToday` from `stateStore.read().getSchedule().getFrequency()` (DAILY=1, WEEKLY=7, MONTHLY=30); construct `DateFilter`; pass to `runExecutor.startRun()`. **[Agent: java-backend]**
- [x] Verify: set "Create Date" + a Date From in the dashboard; start export; confirm in server logs that `trackingNumbersAfterDate` was called with the correctly formatted `dateTime` header. **[Agent: general-purpose]**

---

## Slice 6: Unit tests

- [x] `DateFilterTest`: verify active-filter resolution — for each combination of `dateField`/`dateFrom`/`modifiedWithinPeriod`, assert that the correct `DataSource` method would be selected. **[Agent: java-backend]**
- [x] `ApiDataSourceDateFilterTest`: assert `getTrackingNumbersAfterDate()` sends the `dateTime` header in `dd-M-yyyy HH:mm:ss` format; assert `getTrackingNumbersInFlight()` sends the `daysBeforeToday` header as an integer string. **[Agent: java-backend]**
- [x] `BoPipelineOverloadTest`: new `execute(boTypeConfig, outputDir, dateFilter)` overload writes CSVs to the supplied `@TempDir` outputDir. **[Agent: java-backend]**
- [x] `RunExecutorTest` (extend existing): PACKAGING step is SKIPPED when `enableZipPackaging=false`; SFTP step is SKIPPED when `enableSftpUpload=false`; `warnings` list is populated when a BO fails but at least one succeeds. **[Agent: java-backend]**
- [x] `ConfigControllerValidationTest` (extend existing): `enableZipPackaging` and `enableSftpUpload` round-trip correctly through PUT then GET. **[Agent: java-backend]**
- [x] Verify: `mvn test` passes with no failures. **[Agent: java-backend]**

---

## Slice 7: Verification gap fixes

- [x] `RunExecutor.executeRun()`: after EXPORT_CSV succeeds, instantiate `BackupManager(config.getOutputRoot(), config.getExportFolderName(), config.getBackupRetentionDays())` and call `enforceRetention()` — this removes older run subdirectories that exceed the retention limit. **[Agent: java-backend]**
- [x] `RunExecutor.executeRun()`: guard ManifestCsvWriter with `if (csvConfig.isGenerateSummaryCsv())` — only generate the manifest file when the "Generate Manifest CSV" flag is enabled in config; when disabled, no manifest file is written and no error is reported. **[Agent: java-backend]**
- [x] `RunExecutor.executeRun()` SFTP_UPLOAD step: when `sftpConfig.isEnableSftpUpload()` is true, check `sftpConfig.isEnableZipPackaging()` — if ZIP is enabled, glob `*.zip.*` from `outputDir.getParent()` as before; if ZIP is disabled, collect all regular files directly from `outputDir` using `Files.list(outputDir).filter(Files::isRegularFile)` and upload those instead. **[Agent: java-backend]**
- [x] Verify: `mvn test` passes with no failures. **[Agent: java-backend]**

---

## Subagent Recommendations

| Task/Slice | Issue | Recommendation |
|---|---|---|
| Slices 1–2: admin.html, admin.js, dashboard.js, style.css | No frontend specialist agent available | Add a `vanilla-frontend` agent for plain HTML/JS/CSS tasks |
| Slices 1–5: Verification sub-tasks | UI verification relies on curl + log inspection; no browser MCP | Install browser MCP for automated end-to-end UI verification |
