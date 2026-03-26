# Functional Specification: Run Manifest File

- **Roadmap Item:** Generate a manifest file listing all output files and their SHA-256 checksums
- **Status:** Completed
- **Author:** CLM Data Extract Team

---

## 1. Overview and Rationale (The "Why")

After each export run the tool produces multiple CSV files inside the `MetaData` output folder (export CSVs, downloads lists, summary, parent mapping, reports). Currently there is no inventory of what was produced, and downstream consumers — migration teams, auditors, or automated pipelines — have no reliable way to verify that files arrived intact or know exactly what the run produced.

This feature adds a manifest CSV file written at the end of every run. It lists every file that was generated during that run, alongside its SHA-256 checksum, byte size, and generation timestamp. This gives consumers a single source of truth for integrity verification and run inventory, without any manual effort from the admin.

**Success looks like:** After any run, an admin or downstream system can open `Manifest_{DDMMYYYY}_{HHMMSS}.csv`, compare the SHA-256 value of any output file against the manifest entry, and confirm it has not been altered or corrupted.

---

## 2. Functional Requirements (The "What")

### 2.1 Manifest File Generation

- **The tool must generate one manifest CSV file at the end of every run.** Generation is not configurable — it always occurs.
- The manifest is written into the same `MetaData` output folder as all other output files (e.g., `output/MetaData/`).
- The manifest filename follows the existing timestamp template convention: `Manifest_{DDMMYYYY}_{HHMMSS}.csv`, using the same timestamp as the rest of the run's output.

  **Acceptance Criteria:**
  - [x] After every run, a file matching `Manifest_*.csv` exists in the `output/MetaData/` folder.
  - [x] The filename timestamp matches the run timestamp used by other output files of that run.

### 2.2 Manifest CSV Content

The manifest CSV must contain the following columns, in this order:

| Column | Description |
|---|---|
| `Filename` | The name of the generated file (not a full path — filename only, e.g., `CREBO_MetaData_25032026_143022.csv`) |
| `SHA256` | The hex-encoded SHA-256 hash of the file's contents at generation time |
| `SizeBytes` | The file size in bytes |
| `GeneratedAt` | The date/time the file was written, in `yyyy-MM-dd HH:mm:ss` format |

The file must include a header row using these exact column names.

**Acceptance Criteria:**
- [x] The manifest CSV has exactly four columns: `Filename`, `SHA256`, `SizeBytes`, `GeneratedAt`.
- [x] Each data row corresponds to one file that was written during the run.
- [x] The `SHA256` value for each file can be independently verified using a standard `sha256sum` tool.
- [x] The `SizeBytes` value matches the actual file size on disk at the time of writing.

### 2.3 Scope of Files Listed

- The manifest must list **all files** written to the `MetaData` folder during the run: export CSVs, downloads-list CSVs, summary CSV, parent mapping CSV, and report CSVs.
- **The manifest file itself is NOT listed** as one of its own entries.
- The manifest is generated **after** all other files have been written, so every file produced during the run is captured.

**Acceptance Criteria:**
- [x] Every file in `output/MetaData/` from that run appears as a row in the manifest, except the manifest file itself.
- [x] No files from previous runs or unrelated directories appear in the manifest.

### 2.4 Empty Run Behaviour

- If no files were generated during the run (e.g., no BO types were found), the manifest is still created.
- In this case the file contains only the header row and zero data rows.

**Acceptance Criteria:**
- [x] When no output files are produced, the manifest file exists and contains only the header row.
- [x] No error or warning is raised solely because the manifest has no data rows.

### 2.5 Logging

- The tool must log a message upon successful manifest generation, including the filename and the number of files listed.
- If manifest generation fails (e.g., I/O error computing a checksum), the failure is logged as a warning and the run does not exit with an error.

**Acceptance Criteria:**
- [x] The run log contains an entry such as: `Written manifest 'Manifest_25032026_143022.csv' listing 7 file(s)`.
- [x] An I/O failure during manifest writing produces a warning log entry and does not cause the overall run to fail.

---

## 3. Scope and Boundaries

### In-Scope

- Generating a manifest CSV file at the end of every run in the `MetaData` output folder.
- Listing all files written during the current run (export CSVs, downloads lists, summary, parent mapping, reports).
- Computing SHA-256 checksums for each listed file.
- Recording file size in bytes and generation timestamp per file.
- Filename using the `Manifest_{DDMMYYYY}_{HHMMSS}.csv` template.
- Logging the manifest write result.

### Out-of-Scope

- Making manifest generation optional via a config flag (always-on by design).
- Including the manifest file as an entry in itself.
- Non-CSV manifest formats (JSON, plain text).
- Verifying checksums of files from previous runs.
- Listing files from directories other than `MetaData`.
- All other roadmap items: Multi-BO Sequential Processing, Downloads List Generation, Progress Logging, Backup Retention, Offline Test Mode, etc.
