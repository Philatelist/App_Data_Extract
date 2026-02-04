# Product Definition: CLM Data Extract

- **Version:** 1.0
- **Status:** Proposed

---

## 1. The Big Picture (The "Why")

### 1.1. Project Vision & Purpose

Enable CLM administrators to reliably extract structured contract data from an SCLM REST API system into flat CSV files, supporting data migration, compliance audits, and business reporting without manual screen-scraping or ad-hoc API scripting.

### 1.2. Target Audience

CLM platform administrators responsible for system operations, data migration projects, audit preparation, and producing data extracts for downstream business teams.

### 1.3. User Personas

- **Persona 1: "Dana the CLM Admin"**
  - **Role:** IT administrator managing the organization's CLM platform.
  - **Goal:** Run a single command to export all contract metadata for a quarterly compliance audit, producing clean CSV files she can hand to the legal team.
  - **Frustration:** Currently has to write throwaway scripts each time, manually handle session tokens, and wrangle JSON responses into spreadsheets. There is no repeatable, configurable process.

- **Persona 2: "Marco the Migration Lead"**
  - **Role:** Project lead overseeing a CLM-to-CLM migration.
  - **Goal:** Extract every business object type (Contracts, Amendments, etc.) with all field data intact, plus a manifest of attachments to download separately, so the migration team can map and load data into the target system.
  - **Frustration:** The CLM system has no built-in bulk export that covers all BO types with full field coverage. He needs a tool that can be configured once and run repeatedly as the migration proceeds.

### 1.4. Success Metrics

- A single `config.yml` is sufficient to define connection, authentication, target BO types, and output preferences for a complete export run.
- The tool produces one CSV file per configured BO type containing all requested field data.
- The tool produces a separate downloads-list CSV per BO type listing attachment file paths for later retrieval.
- Export runs complete unattended after launch, with progress logged to a timestamped log file.
- Backup retention is configurable and automatically enforced (old export runs are cleaned up).

---

## 2. The Product Experience (The "What")

### 2.1. Core Features

- **YAML-driven configuration** -- A single config file defines the server URL, credentials, which BO types to export, which fields to include or exclude, output directory, backup retention count, and logging preferences.
- **Automatic authentication** -- The tool signs into the SCLM REST API at the start of a run and manages the session throughout. It logs out cleanly when done.
- **Metadata discovery** -- For each configured BO type, the tool fetches BO metadata to learn the available components and fields, then fetches all tracking numbers.
- **Bulk data retrieval** -- The tool calls the bundles endpoint with batches of tracking IDs and the resolved field paths to retrieve record data.
- **CSV export** -- Flattens the hierarchical component/field structure into tabular CSV files, one file per BO type.
- **Downloads list generation** -- Produces a separate CSV per BO type listing attachment file server paths and associated tracking numbers, so files can be retrieved in a follow-up step.
- **Progress logging** -- Writes timestamped log entries to a run-specific log file (and optionally to stdout) covering sign-in, each BO type processed, record counts, and any errors.
- **Backup management** -- Each run writes output into a timestamped subdirectory. The tool auto-deletes the oldest backups when the configured retention limit is exceeded.

### 2.2. User Journey

1. The admin creates or edits a `config.yml` file specifying the CLM server URL, credentials, the list of BO types to export (e.g., `Contract`, `Amendment`), optional field filters, and output preferences (directory, backup retention count).
2. The admin runs the tool from the command line: `clm-extract --config config.yml`.
3. The tool authenticates against the SCLM REST API and logs the session start.
4. For each configured BO type, the tool:
   a. Fetches metadata (components and fields).
   b. Fetches all tracking numbers.
   c. Retrieves bundle data in batches.
   d. Writes a CSV export file with all field data.
   e. Writes a downloads-list CSV with attachment paths.
   f. Logs progress and record counts.
5. The tool logs out, enforces backup retention, and exits with a summary.
6. The admin finds the output in a timestamped subdirectory containing the CSV files, downloads lists, and a log file.

---

## 3. Project Boundaries

### 3.1. What's In-Scope for this Version

- YAML configuration file parsing and validation.
- SCLM REST API authentication (login/logout with session management).
- Fetching BO types metadata (`/BOMetaData`) for configured types.
- Fetching tracking numbers (`/trackingNumbers`) per BO type.
- Fetching bundle data (`/bundles`) in configurable batch sizes.
- Flattening hierarchical bundle responses into flat CSV rows.
- Handling single-cardinality components (one row contribution) and multi-cardinality components (one row per instance, e.g., attachments).
- Writing one CSV export file per BO type.
- Writing one downloads-list CSV per BO type (tracking number, file path, file name).
- Timestamped run directories for output organization.
- Run log file with progress entries.
- Configurable backup retention (auto-delete oldest run directories).
- CLI entry point accepting a `--config` flag.
- Error handling with clear log messages for API failures, auth issues, and missing configuration.

### 3.2. What's Out-of-Scope (Non-Goals)

- Downloading actual attachment/document binary files (v1 produces the list only).
- GUI or web interface.
- Incremental/delta exports (every run is a full extract).
- Writing to databases or non-CSV formats (JSON, Excel, etc.).
- Parallel/concurrent API requests.
- Scheduling or cron integration (users schedule externally).
- Support for non-SCLM REST API systems.
- User management, role export, or other non-BO-type data.
- Automatic field mapping or transformation beyond flattening.
