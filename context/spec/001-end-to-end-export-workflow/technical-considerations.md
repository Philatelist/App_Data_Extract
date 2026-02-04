# Technical Specification: End-to-End Export Workflow

- **Functional Specification:** `context/spec/001-end-to-end-export-workflow/functional-spec.md`
- **Status:** Draft
- **Author(s):** Poe

---

## 1. High-Level Technical Approach

The CLM Data Extract tool is a single-module Java 17 Maven project packaged as a shaded JAR. It runs as a sequential batch process: parse config, load endpoints, authenticate, iterate over BO types, and for each BO execute a metadata-tracking-bundles-CSV pipeline with incremental file writing. There are no external dependencies beyond the SCLM REST API.

The key architectural constraint is **incremental writing with minimal memory**: CSV output is appended batch-by-batch, never holding a full BO dataset in memory. The tool aborts the run on any BO failure.

**Package structure:** `com.clmextract` with subpackages per module.

---

## 2. Proposed Solution & Implementation Plan (The "How")

### 2.1 Project Structure

```
pom.xml
src/main/java/com/clmextract/
  App.java                           # main(), CLI arg parsing, run lifecycle
  config/
    AppConfig.java                   # Top-level config POJO
    BoTypeConfig.java                # Per-BO settings (trackingFilter, filenameTemplate)
    ConfigLoader.java                # SnakeYAML parsing + validation
  endpoint/
    EndpointDefinition.java          # POJO: name, method, path, auth, headers, body, response
    EndpointRegistry.java            # Adapter: loads endpoints.yml, maps to required operations
  session/
    SessionManager.java              # Login/logout lifecycle, session ID storage, auto-re-login
  http/
    RequestExecutor.java             # Generic HTTP execution from EndpointDefinition
    RetryPolicy.java                 # Exponential backoff retry logic
    ApiException.java                # Custom exception for API failures
    SessionExpiredException.java     # Subclass for session expiry detection
  metadata/
    BoMetadata.java                  # POJO: boName, boUsageType, components list
    ComponentMetadata.java           # POJO: internalName, displayName, cardinality, fields list
    FieldMetadata.java               # POJO: internalName, displayName, dataType, instancePath
    MetadataParser.java              # Jackson deserialization of /BOMetaData response
  export/
    ExportOrchestrator.java          # Top-level BO loop, state cleanup, error abort
    BoPipeline.java                  # Per-BO pipeline: metadata -> tracking -> batches -> CSV
    TrackingFilter.java              # Parses "1000-2000, 3050" filter syntax
    BatchProcessor.java              # Splits IDs, calls bundles, dispatches to writers
    BundleParser.java                # Jackson deserialization of /bundles response
  csv/
    CsvWriterFactory.java            # Creates writers based on csvMode
    PerComponentCsvWriter.java       # One file per component (default mode)
    MergedSingleCsvWriter.java       # Merged single-cardinality + separate multi
    SingleOnlyCsvWriter.java         # Single-cardinality only, skip multi
    ColumnResolver.java              # Resolves column order, applies overrides
    FilenameResolver.java            # Resolves {BO}, {Component}, {DDMMYYYY}, {HHMMSS} placeholders
    DownloadsCsvWriter.java          # Writes per-BO downloads CSV (attachment paths only)
  backup/
    BackupManager.java               # Copy current exports to backups/, retention cleanup
  logging/
    LogManager.java                  # Configures Log4j2 file + console appenders per run
    RestCallLogger.java              # Logs per-REST-call timing (start, end, elapsed, status)
  offline/
    OfflineDataSource.java           # Reads sample JSON files instead of calling API
src/main/resources/
  log4j2.xml                        # Default Log4j2 configuration
src/test/java/com/clmextract/       # Unit tests mirror main structure
inputs/
  endpoints.yml                     # User-provided SCLM REST API definitions
  samples/
    boMetaData.sample.json           # Sample metadata for offline mode
    bundles.sample.json              # Sample bundles for offline mode
config/
  columns/                          # Convention: {BoType}.csv for column ordering
  overrides/                        # Convention: {BoType}.csv for display name overrides
```

### 2.2 Configuration (ConfigLoader)

**File:** `config/ConfigLoader.java`

Uses SnakeYAML to load `config.yml` into `AppConfig`. Validation runs immediately after parsing.

**`AppConfig` fields:**

| Field | YAML key | Type | Required | Default |
|---|---|---|---|---|
| `baseUrl` | `server.url` | `String` | Yes | -- |
| `username` | `server.username` | `String` | Yes | -- |
| `password` | `server.password` | `String` | Yes | -- |
| `endpointsFile` | `endpointsFile` | `String` | No | `inputs/endpoints.yml` |
| `boTypes` | `boTypes` | `List<BoTypeConfig>` | No | empty (discover all) |
| `csvMode` | `csvMode` | `String` (enum) | No | `per-component` |
| `delimiter` | `delimiter` | `char` | No | `,` |
| `filenameTemplate` | `filenameTemplate` | `String` | No | `{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv` |
| `downloadsFilenameTemplate` | `downloadsFilenameTemplate` | `String` | No | `{BO}_AttachmentsToDownload_{DDMMYYYY}_{HHMMSS}.csv` |
| `outputRoot` | `outputRoot` | `String` | Yes | -- |
| `exportFolderName` | `exportFolderName` | `String` | No | `MetaData` |
| `batchSize` | `batchSize` | `int` | No | `100` |
| `backupRetentionDays` | `backupRetentionDays` | `int` | No | `30` |
| `offlineMode` | `offlineMode` | `boolean` | No | `false` |
| `retryMaxAttempts` | `retry.maxAttempts` | `int` | No | `3` |
| `retryBaseDelayMs` | `retry.baseDelayMs` | `long` | No | `1000` |

**`BoTypeConfig` fields:**

| Field | YAML key | Type | Required | Default |
|---|---|---|---|---|
| `name` | `name` | `String` | Yes | -- |
| `trackingFilter` | `trackingFilter` | `String` | No | null (no filter) |
| `filenameTemplate` | `filenameTemplate` | `String` | No | null (use global) |

**Sample `config.yml`:**

```yaml
server:
  url: http://localhost:8080/clm/rest/methods
  username: admin
  password: admin

endpointsFile: inputs/endpoints.yml

boTypes:
  - name: Contract
    trackingFilter: "1000-2000, 3050"
  - name: Amendment

csvMode: per-component
delimiter: ","
filenameTemplate: "{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv"
downloadsFilenameTemplate: "{BO}_AttachmentsToDownload_{DDMMYYYY}_{HHMMSS}.csv"

outputRoot: output
exportFolderName: MetaData
batchSize: 100
backupRetentionDays: 30

retry:
  maxAttempts: 3
  baseDelayMs: 1000
```

**Validation rules:**
- `server.url`, `server.username`, `server.password`, `outputRoot` must be non-null and non-empty.
- `csvMode` must be one of: `per-component`, `merged-single`, `single-only`.
- `batchSize` must be > 0.
- `backupRetentionDays` must be >= 0.
- If `boTypes` entries exist, each must have a non-empty `name`.
- On any validation failure, throw `ConfigValidationException` with the field name and expected format. The main method catches this, prints the message, and exits with code 1.

### 2.3 Endpoints File Adapter (EndpointRegistry)

**File:** `endpoint/EndpointRegistry.java`

Parses `endpoints.yml` using SnakeYAML into a `List<EndpointDefinition>`. The file is read-only and consumed in its original schema.

**Mapping to required operations:**

The adapter resolves required operations by matching the `name` field in the endpoints file:

| Internal Operation | Endpoints File `name` | Required |
|---|---|---|
| `LOGIN` | `login` | Yes |
| `LOGOUT` | `logout` | Yes |
| `GET_BO_TYPES` | `getBoTypes` | No (only needed when boTypes config is empty) |
| `GET_BO_METADATA` | `getBoMetaData` | Yes |
| `GET_TRACKING_NUMBERS` | `getTrackingNumbers` | Yes |
| `BUNDLES` | `bundles` | Yes |

**EndpointDefinition POJO** mirrors the YAML structure:
- `name`: `String`
- `method`: `String` (GET, POST, PUT, DELETE)
- `path`: `String`
- `auth`: `AuthConfig` (type, headerName)
- `requestHeaders`: `List<HeaderDef>` (name, value/valueFrom)
- `requestBody`: `BodyConfig` (type, schema)
- `response`: `ResponseConfig` (type, saveAs)

**Validation:** After loading, check that all required operations are resolvable. If any is missing, throw `EndpointResolutionException` naming the missing operation.

### 2.4 Session Manager

**File:** `session/SessionManager.java`

**State:**
- `sessionId`: `String` (null when not logged in)

**Methods:**
- `ensureLoggedIn()`: If `sessionId` is null, calls login. Otherwise, no-op.
- `login()`: Constructs request from the `LOGIN` endpoint definition. Sends `user` and `password` as headers (per the `basicHeaders` auth type). Parses the plain-text response body as the session ID. Stores it. Logs the event.
- `logout()`: Calls the `LOGOUT` endpoint with the `session_id` header. Wraps in try/catch -- logout failures are logged as warnings, never thrown.
- `getSessionId()`: Returns the current session ID for use by the request executor.
- `invalidateSession()`: Sets `sessionId` to null (used before re-login on session expiry).

**Auto-re-login logic** lives in `RequestExecutor` (see below), not in SessionManager. SessionManager provides the primitives.

**Shutdown guarantee:** The `ExportOrchestrator` wraps the entire run in a try/finally that calls `sessionManager.logout()`.

### 2.5 HTTP Request Executor

**File:** `http/RequestExecutor.java`

Uses `java.net.http.HttpClient` (created once in the constructor, reused for all requests).

**`execute(EndpointDefinition endpoint, Map<String, String> dynamicHeaders, String body)` method:**

1. Builds the full URL: `config.baseUrl + endpoint.path`.
2. Creates `HttpRequest.Builder` with the correct HTTP method.
3. Attaches static headers from the endpoint definition.
4. Attaches dynamic headers (e.g., `boType` header, `session_id`).
5. If auth type is `sessionHeader`, attaches `session_id: {sessionId}` from SessionManager.
6. If auth type is `basicHeaders`, attaches `user` and `password` from config.
7. If body is non-null, sets it as the request body with appropriate Content-Type.
8. Sends the request synchronously.
9. Checks HTTP status code:
   - 2xx: return response body as `String`.
   - 401/403 or response body containing session-expiry indicator: throw `SessionExpiredException`.
   - Other non-2xx: throw `ApiException` with status code and body.

**Retry logic (RetryPolicy):**

Wraps `execute()` calls with exponential backoff:
- Max attempts: `config.retryMaxAttempts` (default 3).
- Base delay: `config.retryBaseDelayMs` (default 1000ms).
- Delay formula: `baseDelay * 2^(attempt-1)` (1s, 2s, 4s...).
- Retryable conditions: `IOException`, HTTP 5xx status codes.
- Non-retryable: 4xx errors (except session expiry, which triggers re-login instead of retry).

**Session expiry handling:**
When `SessionExpiredException` is caught, the executor calls `sessionManager.invalidateSession()`, then `sessionManager.login()`, then retries the original request once. If the retry also fails with session expiry, the exception propagates.

### 2.6 Metadata Parser

**File:** `metadata/MetadataParser.java`

Uses Jackson `ObjectMapper` to deserialize the `/BOMetaData` JSON response into `BoMetadata`.

**Deserialization mapping** (based on sample `boMetaData.sample.json`):

```
JSON                          -> Java
-------------------------------------------------------
root object                   -> BoMetadata
  .boName                     -> String boName
  .boUsageType                -> String boUsageType
  .components[]               -> List<ComponentMetadata>
    .internalName             -> String internalName
    .displayName              -> String displayName
    .cardinality              -> String cardinality ("single"/"multiple")
    .instancePath             -> String instancePath
    .fields[]                 -> List<FieldMetadata>
      .internalName           -> String internalName
      .displayName            -> String displayName
      .dataType               -> String dataType
      .instancePath           -> String instancePath
```

**Field path resolution:** For each field, the `instancePath` (e.g., `MCPDef:/ExampleData/ReqSummary/trackingNumber`) is the value sent in the `fieldPaths` array to the bundles endpoint.

### 2.7 Export Orchestrator & BO Pipeline

**File:** `export/ExportOrchestrator.java`

```
run():
  configLoader.load()
  endpointRegistry.load()
  backupManager.backupCurrentExports()
  outputDirs.createAll()
  sessionManager.ensureLoggedIn()
  try:
    boTypes = resolveBOTypes()   // from config list or GET /BOTypes
    for each boType in boTypes:
      boPipeline.execute(boType)   // throws on failure -> aborts run
  finally:
    sessionManager.logout()
    backupManager.enforceRetention()
```

**File:** `export/BoPipeline.java`

```
execute(boTypeConfig):
  // Step 1: Metadata
  metadata = metadataParser.fetch(boTypeConfig.name)

  // Step 2: Tracking numbers
  trackingIds = fetchTrackingNumbers(boTypeConfig.name)
  if boTypeConfig.trackingFilter != null:
    trackingIds = TrackingFilter.apply(trackingIds, boTypeConfig.trackingFilter)

  // Step 3: Resolve columns
  columnResolver = new ColumnResolver(metadata, boTypeConfig.name)
  fieldPaths = columnResolver.resolveFieldPaths()

  // Step 4: Open CSV writers (incremental mode)
  csvWriters = csvWriterFactory.create(metadata, columnResolver, boTypeConfig)
  downloadsCsvWriter = new DownloadsCsvWriter(metadata, boTypeConfig)
  csvWriters.writeHeaders()
  downloadsCsvWriter.writeHeaders()

  // Step 5: Batched fetch + incremental write
  batches = BatchProcessor.split(trackingIds, config.batchSize)
  for each batch in batches:
    bundleResponse = requestExecutor.execute(BUNDLES, batch, fieldPaths)
    records = bundleParser.parse(bundleResponse)
    csvWriters.writeRecords(records)          // append rows immediately
    downloadsCsvWriter.writeRecords(records)  // extract attachment paths
    records = null                             // release batch memory

  // Step 6: Close writers
  csvWriters.close()
  downloadsCsvWriter.close()

  // Step 7: State cleanup
  metadata = null
  trackingIds = null
```

**Error handling:** Any exception in `BoPipeline.execute()` propagates up to `ExportOrchestrator`, which aborts the run (no subsequent BOs are processed). The finally block still calls logout.

### 2.8 Tracking Number Filter

**File:** `export/TrackingFilter.java`

**Input:** filter string like `"1000-2000, 3050, 4000-4500"`

**Parsing algorithm:**
1. Split by `,` and trim each segment.
2. For each segment:
   - If it contains `-`, split by `-` to get `[start, end]`. Parse both as `long`. Add range `[start, end]` inclusive.
   - Otherwise, parse as a single `long` ID.
3. Build a `Set<Long>` of all allowed IDs (expand ranges).
4. Filter the tracking IDs list: retain only IDs present in the allowed set.
5. IDs in the filter that don't exist in the API response are silently ignored.

### 2.9 CSV Writing

#### Column Resolution (`ColumnResolver`)

**File:** `csv/ColumnResolver.java`

1. Load column order file if `config/columns/{BoType}.csv` exists:
   - Read lines, trim, skip empty. Each line is a field path.
   - Filter metadata fields to only those listed, in the listed order.
   - Silently skip paths not found in metadata.
2. Load display name overrides if `config/overrides/{BoType}.csv` exists:
   - Parse as CSV: `fieldPath,newDisplayName`.
   - Build a `Map<String, String>` from field instancePath to override name.
3. Build the final column list:
   - First column: always `Tracking #` (literal).
   - Remaining columns: `ComponentDisplayName.ParameterDisplayName` (or overridden name).

#### CSV Mode Implementations

All CSV writers share a common pattern: open file with OpenCSV `CSVWriter`, write header once, append data rows per batch, close at the end.

**`PerComponentCsvWriter`:**
- Opens one `CSVWriter` per component.
- For single-cardinality components: one row per record (tracking ID + field values).
- For multi-cardinality components: one row per instance (tracking ID + field values from each `rows[]` entry).

**`MergedSingleCsvWriter`:**
- Opens one `CSVWriter` for all single-cardinality components merged (columns from all single components, prefixed with `ComponentDisplayName.`).
- Opens separate `CSVWriter`s for each multi-cardinality component.

**`SingleOnlyCsvWriter`:**
- Opens one `CSVWriter` for all single-cardinality components merged.
- Skips multi-cardinality components entirely.

#### Filename Resolution (`FilenameResolver`)

**File:** `csv/FilenameResolver.java`

Resolves placeholders in the template string:
- `{BO}`: BO type name (e.g., `Contract`).
- `{Component}`: component display name (e.g., `Summary`). For merged files, use `Merged` or similar.
- `{DDMMYYYY}`: current date formatted as day-month-year.
- `{HHMMSS}`: current time formatted as hour-minute-second.

The timestamp is captured once at the start of the BO pipeline so all files for one BO share the same timestamp.

#### Downloads CSV Writer

**File:** `csv/DownloadsCsvWriter.java`

- Uses the dedicated `downloadsFilenameTemplate` (e.g., `{BO}_AttachmentsToDownload_{DDMMYYYY}_{HHMMSS}.csv`).
- Output directory: `<outputRoot>/downloads/`.
- Writes one column only: attachment file paths.
- Identifies the attachments component from metadata by looking for a multi-cardinality component whose `internalName` contains `Attachment` (case-insensitive).
- Extracts the `serverFileName` field value from each row of the attachments component.
- If no attachments component exists, the file is created empty (0 data rows).

### 2.10 Bundle Response Parser

**File:** `export/BundleParser.java`

Uses Jackson to deserialize the `/bundles` response. Structure (based on sample):

```
JSON                                  -> Java
-----------------------------------------------------------
root.boName                           -> String (informational)
root.records[]                        -> List<BundleRecord>
  .trackingId                         -> long
  .components[]                       -> List<BundleComponent>
    .componentInternalName            -> String
    .fields                           -> Map<String, String>  (single-cardinality)
    .rows[]                           -> List<Map<String, String>> (multi-cardinality)
```

**Single vs. multi-cardinality detection:** A component has either a `fields` object (single) or a `rows` array (multi). Jackson's `@JsonProperty` with `@JsonInclude(NON_NULL)` handles both cases. The CSV writer uses the metadata's cardinality to know which to expect.

### 2.11 Backup Manager

**File:** `backup/BackupManager.java`

**`backupCurrentExports()`:**
1. Check if `<outputRoot>/<exportFolderName>/` exists and has files.
2. If yes, create `<outputRoot>/backups/<timestamp>/` directory (format: `yyyyMMdd_HHmmss`).
3. Copy all files from the export folder into the backup directory.
4. Delete the original files from the export folder (clean slate for the new run).

**`enforceRetention()`:**
1. List all subdirectories in `<outputRoot>/backups/`.
2. Parse each directory name as a timestamp.
3. Delete any directory whose timestamp is older than `now - backupRetentionDays`.
4. If `backupRetentionDays` is 0, delete all backup directories.

Uses `java.nio.file.Files` for all file operations. Errors during backup/retention are logged as warnings but do not abort the run.

### 2.12 Logging

**File:** `logging/LogManager.java`

Configures Log4j2 programmatically at startup:

1. **File appender:** Writes to `<outputRoot>/logs/run_<DDMMYYYY>_<HHMMSS>.log`.
2. **Console appender:** Writes to stdout.
3. **Log level:** INFO for file and console. DEBUG available via config.

**File:** `logging/RestCallLogger.java`

Wraps each REST API call to capture:
- Start time (`Instant.now()` before call)
- End time (`Instant.now()` after call)
- Elapsed time (milliseconds)
- BO name (from pipeline context, or "N/A" for login/logout)
- Endpoint name (from `EndpointDefinition.name`)
- Status: `SUCCESS` or `FAILURE`
- Error details (exception message if failure)

Log format: `[{startTime}] {endpoint} | BO: {boName} | {elapsed}ms | {status} | {errorDetails}`

### 2.13 Offline Test Mode

**File:** `offline/OfflineDataSource.java`

When `config.offlineMode` is `true`:
- The `RequestExecutor` is replaced (or wrapped) by `OfflineDataSource`.
- Instead of HTTP calls, it reads from files in `inputs/samples/`:
  - `boMetaData.sample.json` for metadata requests.
  - `bundles.sample.json` for bundle requests.
  - Login/logout are no-ops (return a dummy session ID / succeed silently).
  - `getBoTypes` returns the `boName` from the sample metadata file.
  - `getTrackingNumbers` returns the tracking IDs from the sample bundles file.
- All downstream logic (CSV generation, column resolution, downloads CSV) works identically.

### 2.14 Main Entry Point

**File:** `App.java`

```
public static void main(String[] args):
  if args does not contain "--config" followed by a path:
    print usage: "Usage: java -jar clm-extract.jar --config <config.yml>"
    System.exit(1)

  configPath = args[indexOf("--config") + 1]
  config = ConfigLoader.load(configPath)
  LogManager.configure(config)
  orchestrator = new ExportOrchestrator(config)
  try:
    orchestrator.run()
    System.exit(0)
  catch Exception:
    log.error("Run failed", e)
    System.exit(2)
```

### 2.15 Maven Configuration (pom.xml)

**Group/Artifact:** `com.clmextract:clm-data-extract:1.0.0`

**Dependencies:**

| Dependency | Version | Purpose |
|---|---|---|
| `org.yaml:snakeyaml` | 2.2 | YAML parsing for config and endpoints |
| `com.fasterxml.jackson.core:jackson-databind` | 2.17.x | JSON deserialization of API responses |
| `com.opencsv:opencsv` | 5.9 | CSV writing with proper escaping |
| `org.apache.logging.log4j:log4j-core` | 2.23.x | File + console logging |
| `org.apache.logging.log4j:log4j-api` | 2.23.x | Logging API |
| `org.junit.jupiter:junit-jupiter` | 5.10.x | Unit testing (test scope) |

**Plugins:**

| Plugin | Purpose |
|---|---|
| `maven-compiler-plugin` | Java 17 source/target |
| `maven-shade-plugin` | Produce single runnable JAR with `App` as main class |
| `maven-surefire-plugin` | Run unit tests |

**Shade config:** Main class set to `com.clmextract.App`. Transformer merges META-INF/services.

---

## 3. Impact and Risk Analysis

### System Dependencies

- **SCLM REST API availability:** The tool is entirely dependent on the API being reachable and responsive. Network outages or server downtime will cause run failures.
- **API response format stability:** The tool assumes the JSON structure of metadata and bundles responses matches the sample files. If the API changes its response schema, the Jackson deserialization will break.
- **Endpoints file compatibility:** The tool assumes the endpoints file follows the established YAML schema. Any schema changes require adapter updates.

### Potential Risks & Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| **Session expiry mid-run** | Batch fetch fails partway through a BO | Auto-re-login and retry the failed request. Already written CSV data is preserved since writes are incremental. |
| **Large BO with millions of records** | High memory pressure, long run times | Incremental batch-by-batch writing ensures constant memory. Configurable batch size lets users tune for their environment. |
| **API rate limiting or throttling** | Repeated 429 or 503 errors | Exponential backoff retry handles transient failures. If persistent, the user can increase batch size to reduce call count. |
| **Malformed API responses** | Jackson deserialization failure | The tool logs the raw response body on parse errors and aborts the BO. The error message should help diagnose the issue. |
| **Disk space exhaustion** | CSV write fails mid-run | No specific mitigation beyond OS-level monitoring. The tool logs IOException details. |
| **Concurrent runs on same output directory** | File corruption or overwrite conflicts | Out of scope. Users are responsible for not running concurrent instances against the same output root. |
| **Credentials in config.yml** | Plaintext passwords on disk | Document in README that `config.yml` should have restricted file permissions. Environment variable support is a future enhancement. |

---

## 4. Testing Strategy

### Unit Tests

Each module has unit tests that run without a live API:

- **ConfigLoaderTest:** Valid config loads correctly. Missing required fields throw with field name. Defaults are applied. Invalid csvMode is rejected.
- **EndpointRegistryTest:** Sample endpoints.yml is parsed correctly. Missing required endpoint throws with operation name.
- **TrackingFilterTest:** Single IDs, ranges, combined filters, empty filter, non-matching IDs.
- **ColumnResolverTest:** No order file (use all fields in API order). Order file present (filter and reorder). Override file present (rename headers). Both present.
- **FilenameResolverTest:** All placeholders resolve correctly. Per-BO override template.
- **MetadataParserTest:** Sample JSON deserializes to correct `BoMetadata` structure.
- **BundleParserTest:** Sample JSON deserializes. Single-cardinality vs. multi-cardinality components are handled.
- **BackupManagerTest:** Backup creates timestamped directory. Retention deletes old backups. Retention 0 deletes all.
- **PerComponentCsvWriterTest:** Correct files created. Headers match. Multi-cardinality rows expanded. Tracking # is first column.
- **MergedSingleCsvWriterTest:** Single components merged. Multi components separate.
- **DownloadsCsvWriterTest:** Attachment paths extracted. Empty file for BO without attachments.

### Integration Test (Offline Mode)

A full end-to-end test using `offlineMode: true`:
1. Run the tool with a test config pointing to the sample JSON files.
2. Verify the output directory structure is created.
3. Verify CSV files are generated with correct content.
4. Verify downloads CSV is generated.
5. Verify log file is created with expected entries.

This can be automated as a Maven integration test (`maven-failsafe-plugin`) that runs the `App.main()` method with a test config, then asserts on the output files.

### Manual Test (Live API)

For validation against a real SCLM instance:
1. Configure `config.yml` with valid server credentials.
2. Run with a single BO type and small tracking filter.
3. Verify CSV output matches expected data.
4. Verify session login/logout in server logs.
5. Test session expiry by using a short session timeout on the server.
