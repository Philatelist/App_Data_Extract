# Tasks: End-to-End Export Workflow

---

## Slice 1: Maven project skeleton with shaded JAR and CLI entry point

Build the project from zero so that `java -jar clm-extract.jar --config config.yml` runs, parses the arg, and exits cleanly.

- [x] **1.1** Create `pom.xml` with Java 17 compiler, SnakeYAML, Jackson Databind, OpenCSV, Log4j2, JUnit 5 dependencies, and maven-shade-plugin configured with `com.clmextract.App` as main class. **[Agent: general-purpose]**
- [x] **1.2** Create `App.java` with `main()` that parses `--config` arg and prints usage + exits with code 1 if missing. **[Agent: general-purpose]**
- [x] **1.3** Verify: `mvn package` produces a runnable shaded JAR. Running without `--config` prints usage and exits 1. **[Agent: general-purpose]**

---

## Slice 2: Configuration loading and validation

The tool reads `config.yml`, validates required fields, and prints a clear error or logs success.

- [x] **2.1** Create `AppConfig.java` and `BoTypeConfig.java` POJOs with all fields from the tech spec (server, credentials, boTypes, csvMode, delimiter, filenameTemplate, downloadsFilenameTemplate, outputRoot, exportFolderName, batchSize, backupRetentionDays, offlineMode, retry settings). **[Agent: general-purpose]**
- [x] **2.2** Create `ConfigLoader.java`: load YAML via SnakeYAML into `AppConfig`, apply defaults, validate required fields. Throw `ConfigValidationException` with field name on failure. **[Agent: general-purpose]**
- [x] **2.3** Wire `ConfigLoader` into `App.main()`: load config, print validated config summary to stdout, exit 0 on success. **[Agent: general-purpose]**
- [x] **2.4** Create a sample `config.yml` in the project root for testing. **[Agent: general-purpose]**
- [x] **2.5** Write `ConfigLoaderTest`: valid config loads, missing required fields throw with field name, defaults applied, invalid csvMode rejected. **[Agent: general-purpose]**

---

## Slice 3: Endpoints file adapter

The tool loads `endpoints.yml`, maps to required operations, and validates completeness.

- [x] **3.1** Create `EndpointDefinition.java` POJO and nested `AuthConfig`, `HeaderDef`, `BodyConfig`, `ResponseConfig` classes mirroring the YAML structure. **[Agent: general-purpose]**
- [x] **3.2** Create `EndpointRegistry.java`: load `endpoints.yml` via SnakeYAML into `List<EndpointDefinition>`, resolve required operations (LOGIN, LOGOUT, GET_BO_METADATA, GET_TRACKING_NUMBERS, BUNDLES, optional GET_BO_TYPES) by name matching. Throw `EndpointResolutionException` for missing required operations. **[Agent: general-purpose]**
- [x] **3.3** Wire into `App.main()`: after config load, load endpoint registry, log resolved operations summary. **[Agent: general-purpose]**
- [x] **3.4** Write `EndpointRegistryTest`: parses real `endpoints.yml` correctly, all required operations resolved, missing endpoint throws with name. **[Agent: general-purpose]**

---

## Slice 4: HTTP request executor with retry and REST-call logging

The tool can execute generic HTTP requests from endpoint definitions with exponential backoff retry and per-call logging.

- [x] **4.1** Create `ApiException.java` and `SessionExpiredException.java` custom exceptions. **[Agent: general-purpose]**
- [x] **4.2** Create `RetryPolicy.java`: exponential backoff logic (configurable max attempts and base delay, retryable on IOException/5xx, non-retryable on 4xx). **[Agent: general-purpose]**
- [x] **4.3** Create `RequestExecutor.java`: build `HttpRequest` from `EndpointDefinition` + dynamic headers + optional body using `java.net.http.HttpClient`. Handle auth types (`basicHeaders` attaches user/password, `sessionHeader` attaches session_id). Check response status (2xx success, 401/403 session expiry, other non-2xx API error). Wrap with `RetryPolicy`. **[Agent: general-purpose]**
- [x] **4.4** Create `RestCallLogger.java`: wrap request execution to log start time, end time, elapsed ms, BO name, endpoint name, success/failure, error details. **[Agent: general-purpose]**
- [x] **4.5** Create `LogManager.java`: configure Log4j2 programmatically with file appender (to `logs/` directory) and console appender. **[Agent: general-purpose]**

---

## Slice 5: Session manager (login/logout lifecycle)

The tool authenticates against the real API, stores the session, and logs out in finally.

- [x] **5.1** Create `SessionManager.java`: `ensureLoggedIn()`, `login()`, `logout()`, `getSessionId()`, `invalidateSession()`. Login calls the LOGIN endpoint via `RequestExecutor`, parses plain-text session ID. Logout calls LOGOUT endpoint, catches and logs failures as warnings. **[Agent: general-purpose]**
- [x] **5.2** Add session-expiry re-login logic to `RequestExecutor`: on `SessionExpiredException`, call `invalidateSession()` + `login()` + retry original request once. **[Agent: general-purpose]**
- [x] **5.3** Create `ExportOrchestrator.java` skeleton: runs `ensureLoggedIn()`, placeholder BO loop (log "no BOs to process"), and `logout()` in finally block. Wire into `App.main()`. **[Agent: general-purpose]**
- [x] **5.4** Verify: run with real server config, observe login/logout in logs. Run with invalid credentials, observe clear error. **[Agent: general-purpose]**

---

## Slice 6: Output directory structure and backup management

The tool creates the output directory tree and manages backups with day-based retention.

- [x] **6.1** Create `BackupManager.java`: `backupCurrentExports()` copies existing export folder contents into `backups/<timestamp>/`, then clears the export folder. `enforceRetention()` deletes backup subdirectories older than configured days. Uses `java.nio.file.Files`. Errors logged as warnings, not thrown. **[Agent: general-purpose]**
- [x] **6.2** Wire into `ExportOrchestrator.run()`: call `backupCurrentExports()` before BO processing, `enforceRetention()` in finally block after logout. Create output subdirectories (`exportFolderName/`, `backups/`, `logs/`, `downloads/`) if they don't exist. **[Agent: general-purpose]**
- [x] **6.3** Write `BackupManagerTest`: backup creates timestamped directory, retention deletes old backups, retention 0 deletes all, empty export folder is a no-op. **[Agent: general-purpose]**

---

## Slice 7: Metadata parsing and field-path resolution

The tool fetches and parses BO metadata from the API into a structured model and resolves field paths.

- [x] **7.1** Create metadata POJOs: `BoMetadata.java`, `ComponentMetadata.java`, `FieldMetadata.java` with Jackson annotations. **[Agent: general-purpose]**
- [x] **7.2** Create `MetadataParser.java`: call `GET_BO_METADATA` endpoint via `RequestExecutor` with boType header, deserialize JSON response into `BoMetadata` using Jackson `ObjectMapper`. **[Agent: general-purpose]**
- [x] **7.3** Write `MetadataParserTest`: deserialize `boMetaData.sample.json` into correct `BoMetadata` structure, verify components, fields, cardinality, instance paths. **[Agent: general-purpose]**

---

## Slice 8: Tracking numbers retrieval and filtering

The tool fetches tracking numbers and applies optional inline filters (explicit IDs and numeric ranges).

- [x] **8.1** Add tracking number fetching to `BoPipeline`: call `GET_TRACKING_NUMBERS` endpoint with boType header, parse JSON array of strings response into `List<Long>`. **[Agent: general-purpose]**
- [x] **8.2** Create `TrackingFilter.java`: parse filter string (e.g., `"1000-2000, 3050, 4000-4500"`), split by comma, handle ranges (split by `-`) and single IDs, build allowed set, filter tracking IDs list. Silently ignore non-matching filter values. **[Agent: general-purpose]**
- [x] **8.3** Write `TrackingFilterTest`: single IDs, ranges, combined, empty filter returns all, non-matching IDs silently skipped. **[Agent: general-purpose]**

---

## Slice 9: Batched bulk data fetch with bundle parsing

The tool fetches bundle data in configurable batches and parses the response.

- [x] **9.1** Create bundle response POJOs: `BundleResponse.java`, `BundleRecord.java`, `BundleComponent.java` with Jackson annotations. Handle single-cardinality (`fields` map) and multi-cardinality (`rows` list) in `BundleComponent`. **[Agent: general-purpose]**
- [x] **9.2** Create `BundleParser.java`: deserialize `/bundles` JSON response into `BundleResponse` using Jackson. **[Agent: general-purpose]**
- [x] **9.3** Create `BatchProcessor.java`: split tracking IDs into batches of configured size. For each batch, build the JSON request body (`trackingIds` + `fieldPaths`), call BUNDLES endpoint, return parsed `BundleResponse`. **[Agent: general-purpose]**
- [x] **9.4** Write `BundleParserTest`: deserialize `bundles.sample.json`, verify records, single-cardinality fields, multi-cardinality rows. **[Agent: general-purpose]**

---

## Slice 10: Streaming CSV export (per-component mode, default)

The tool produces one CSV per component with incremental batch-by-batch writing, `Tracking #` as first column, and configurable delimiter and filename template.

- [x] **10.1** Create `FilenameResolver.java`: resolve `{BO}`, `{Component}`, `{DDMMYYYY}`, `{HHMMSS}` placeholders in template strings. Capture timestamp once per BO pipeline run. **[Agent: general-purpose]**
- [x] **10.2** Create `ColumnResolver.java`: build ordered column list from metadata. First column always `Tracking #`. Remaining columns as `ComponentDisplayName.ParameterDisplayName`. Load optional order file from `config/columns/{BoType}.csv` and optional overrides from `config/overrides/{BoType}.csv`. **[Agent: general-purpose]**
- [x] **10.3** Create `PerComponentCsvWriter.java`: open one OpenCSV `CSVWriter` per component in the export folder. Write header row once. For each batch of records, append data rows immediately (single-cardinality: one row per record; multi-cardinality: one row per instance). Use configured delimiter. Close writers at end. **[Agent: general-purpose]**
- [x] **10.4** Create `CsvWriterFactory.java`: return the appropriate writer based on `csvMode` config (for now, only `per-component`). **[Agent: general-purpose]**
- [x] **10.5** Wire into `BoPipeline.execute()`: metadata -> tracking IDs -> column resolution -> open CSV writers -> batched fetch loop (fetch + parse + write rows) -> close writers -> cleanup state. **[Agent: general-purpose]**
- [x] **10.6** Wire `ExportOrchestrator` to iterate over configured BO types (or discovered via `GET_BO_TYPES` if list is empty), calling `BoPipeline.execute()` for each. Abort run on any BO failure. Clear state between BOs. **[Agent: general-purpose]**
- [x] **10.7** Write `PerComponentCsvWriterTest` and `ColumnResolverTest`: correct files created, headers match, Tracking # first column, multi-cardinality rows expanded, order file filters/reorders, overrides rename headers. **[Agent: general-purpose]**
- [x] **10.8** Write `FilenameResolverTest`: all placeholders resolve, per-BO override template. **[Agent: general-purpose]**

---

## Slice 11: Merged-single and single-only CSV modes

Add the two alternative CSV generation modes.

- [x] **11.1** Create `MergedSingleCsvWriter.java`: merge all single-cardinality components into one CSV file (columns from all single components). Each multi-cardinality component gets its own separate CSV file. **[Agent: general-purpose]**
- [x] **11.2** Create `SingleOnlyCsvWriter.java`: merge all single-cardinality components into one file. Skip multi-cardinality components entirely. **[Agent: general-purpose]**
- [x] **11.3** Update `CsvWriterFactory.java` to return the correct writer for `merged-single` and `single-only` modes. **[Agent: general-purpose]**
- [x] **11.4** Write `MergedSingleCsvWriterTest`: single components merged, multi components separate. `SingleOnlyCsvWriterTest`: only single components exported. **[Agent: general-purpose]**

---

## Slice 12: Downloads list CSV generator

For each BO, produce a downloads CSV in the `downloads/` directory listing attachment file paths.

- [x] **12.1** Create `DownloadsCsvWriter.java`: identify the attachments component from metadata (multi-cardinality, internalName contains "Attachment" case-insensitive). Extract `serverFileName` field from each row. Write one-column CSV using the dedicated `downloadsFilenameTemplate`. If no attachments component, create an empty file. Write incrementally per batch. **[Agent: general-purpose]**
- [x] **12.2** Wire into `BoPipeline.execute()`: open downloads writer alongside export writers, pass each batch's records to it, close at end. **[Agent: general-purpose]**
- [x] **12.3** Write `DownloadsCsvWriterTest`: attachment paths extracted correctly, one column only, empty file for BO without attachments component. **[Agent: general-purpose]**

---

## Slice 13: Offline test mode using sample JSON files

Provide a flag to run without a live server, using sample data files.

- [x] **13.1** Move sample files to `inputs/samples/boMetaData.sample.json` and `inputs/samples/bundles.sample.json`. **[Agent: general-purpose]**
- [x] **13.2** Create `OfflineDataSource.java`: implements the same interface as `RequestExecutor`. Login/logout are no-ops. `getBoTypes` returns boName from sample metadata. `getTrackingNumbers` returns tracking IDs from sample bundles. `getBoMetaData` returns parsed sample metadata. `bundles` returns parsed sample bundles. **[Agent: general-purpose]**
- [x] **13.3** Wire into `App.main()` / `ExportOrchestrator`: when `offlineMode: true` in config, use `OfflineDataSource` instead of `RequestExecutor`. **[Agent: general-purpose]**
- [x] **13.4** Verify: run with `offlineMode: true`, confirm CSV and downloads files are produced from sample data without any server connection. **[Agent: general-purpose]**

---

## Slice 14: README with build and run instructions

Document how to build, configure, and run the tool.

- [x] **14.1** Create `README.md`: project description, prerequisites (Java 17, Maven), build command (`mvn clean package`), config.yml reference with all properties and defaults, run command (`java -jar clm-extract.jar --config config.yml`), offline mode instructions, column ordering and override file conventions, output directory structure description. **[Agent: general-purpose]**
