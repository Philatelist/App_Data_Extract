# CLM Data Extract

Command-line tool that connects to an SCLM REST API, exports Business Object metadata into structured CSV files, and produces a downloads list of attachment file paths.

## Prerequisites

- Java 17 or later
- Apache Maven 3.8+

## Build

```bash
mvn clean package
```

This produces a self-contained shaded JAR at `target/clm-data-extract-1.0.0.jar`.

## Run

```bash
java -jar target/clm-data-extract-1.0.0.jar --config config.yml
```

Running without `--config` prints usage and exits with code 1.

## Configuration

All settings are defined in a YAML configuration file. See `config.yml` for a complete example.

### Properties

| Property | Required | Default | Description |
|---|---|---|---|
| `server.url` | Yes | — | Base URL of the SCLM REST API |
| `server.username` | Yes | — | Login username |
| `server.password` | Yes | — | Login password |
| `endpointsFile` | No | `inputs/endpoints.yml` | Path to the endpoints definition file |
| `boTypes` | No | `[]` (auto-discover) | List of BO types to export |
| `boTypes[].name` | Yes | — | BO usage type name |
| `boTypes[].trackingFilter` | No | — | Filter tracking IDs (e.g. `"1000-2000, 3050"`) |
| `boTypes[].filenameTemplate` | No | — | Per-BO override for filename template |
| `csvMode` | No | `per-component` | CSV generation mode (see below) |
| `delimiter` | No | `,` | CSV field delimiter character |
| `filenameTemplate` | No | `{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv` | Filename template for export CSVs |
| `downloadsFilenameTemplate` | No | `{BO}_AttachmentsToDownload_{DDMMYYYY}_{HHMMSS}.csv` | Filename template for downloads CSVs |
| `outputRoot` | Yes | — | Base directory for all output |
| `exportFolderName` | No | `MetaData` | Subdirectory name for export CSVs |
| `batchSize` | No | `100` | Number of tracking IDs per API call |
| `backupRetentionDays` | No | `30` | Days to keep backup directories |
| `offlineMode` | No | `false` | Run without a server using sample data |
| `retry.maxAttempts` | No | `3` | Max retry attempts for failed API calls |
| `retry.baseDelayMs` | No | `1000` | Base delay in ms for exponential backoff |

### CSV Modes

- **`per-component`** (default) — One CSV file per BO component. Each file contains a `Tracking #` column followed by that component's fields.
- **`merged-single`** — All single-cardinality components merge into one CSV file. Each multi-cardinality component gets its own separate file.
- **`single-only`** — All single-cardinality components merge into one file. Multi-cardinality components are skipped entirely.

### Filename Template Placeholders

| Placeholder | Resolves to |
|---|---|
| `{BO}` | BO usage type name |
| `{Component}` | Component display name (or `Merged`/`SingleOnly` for merged modes) |
| `{DDMMYYYY}` | Current date |
| `{HHMMSS}` | Current time |

### Tracking Filter

The `trackingFilter` property accepts a comma-separated list of single IDs and ranges:

```yaml
trackingFilter: "1000-2000, 3050, 4000-4500"
```

Only tracking IDs matching the filter are exported. If omitted, all tracking IDs are exported.

## Column Ordering and Overrides

### Column Order File

Place a file at `config/columns/{BoType}.csv` to control which columns appear and in what order. The file should contain one `ComponentDisplayName.FieldDisplayName` entry per line. Only listed columns are included in the output; unlisted columns are excluded.

### Display Name Overrides

Place a file at `config/overrides/{BoType}.csv` to rename column headers. Each line should be:

```
Original Header,New Header
```

## Offline Mode

Set `offlineMode: true` in the config to run without connecting to a server. The tool reads sample data from `inputs/samples/`:

- `inputs/samples/BOMetaDataResponse.example.json` — Real BO metadata response
- `inputs/samples/BundlesResponse.example.json` — Real bundles response

This is useful for testing the export pipeline without a live API.

```bash
java -jar target/clm-data-extract-1.0.0.jar --config config-offline.yml
```

## API Response Shapes

### Metadata Response

The metadata endpoint returns a flat JSON array of node objects. Each node has a `listType` that determines its role in the hierarchy:

- `BundleProperties` — BO-level info (name, usageType)
- `ModuleProperties` — Module container
- `ComponentProperties` — Component definition (name, displayName, cardinality)
- `ParameterProperties` — Field definition (name, displayName, dataType)

Nodes are linked via `id`/`parentId`. Each node carries a `Property` array with `Name`, `Value`, `InstancePath` entries.

### Bundles Response

The bundles endpoint returns a JSON array of arrays. Each inner array represents one record as a flat list of field objects with `Name`, `DisplayName`, `Value`, `InternalValue`, `DataType`, and `InstancePath`.

The `InstancePath` format for bundles uses pipe-delimited instance IDs: `MCP:/Module|instanceId/Component|instanceId/fieldName`. Multi-cardinality components produce multiple fields with different instance IDs, which are grouped into separate rows.

## Output Directory Structure

```
<outputRoot>/
  <exportFolderName>/    # Export CSV files (e.g. MetaData/)
  downloads/             # Downloads list CSVs (attachment file paths)
  backups/               # Timestamped backup copies of previous exports
    20260101_120000/
  logs/                  # Run log files
    run_01012026_120000.log
```

### Backups

Before each run, the current contents of the export folder are copied into `backups/<timestamp>/`. Backup directories older than `backupRetentionDays` are automatically deleted after each run.

### Downloads CSV

For each BO type, a downloads CSV is produced in the `downloads/` directory listing the `serverFileName` values from attachment components. This is a single-column file with no header. If a BO has no attachments component, an empty file is created.

## Exit Codes

| Code | Meaning |
|---|---|
| 0 | Successful run |
| 1 | Missing `--config` argument or configuration error |
| 2 | Runtime failure during export |
