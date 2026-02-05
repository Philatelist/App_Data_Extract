# architecture.md
**AWOS + Java CLM Data Extract — Architecture (Cold Start, Reference)**

> This document defines the **system architecture** (components, responsibilities, and data flow).
> It intentionally avoids duplicating detailed functional/technical requirements.
>
> **Canonical requirements live in:** `spec.md`, `tech.md`, `tasks.md`.

---

## 1. Purpose

A Java CLI application exports CLM Business Object (BO) data to CSV using CLM REST APIs.
The architecture prioritizes:
- deterministic outputs
- streaming (batch-by-batch) processing
- config-driven behavior
- clear separation of concerns (DTO ↔ domain ↔ IO)

---

## 2. Non-goals (Architectural)

- No concurrency across BOs (sequential BO pipeline)
- No mutation of CLM data (read-only)
- No UI
- No attachment binary downloads (manifest-only)

(Details and constraints are defined in `spec.md`.)

---

## 3. High-level System Context

### Inputs
- `config.yml` (runtime configuration)
- `endpoints.yml` (API endpoints in original format)
- In-repo overrides (selected by configuration / conventions), including:
  - **Column selection/order per BO:** `config/columns/{BO}.csv`  ✅ (authoritative location)
  - Other override files may exist; see `spec.md` / `tech.md`

### External Systems
- CLM REST API (login/logout, metadata, tracking IDs, bundles)

### Outputs
- Export CSVs (one or more files per BO depending on mode)
- Downloads manifest CSV for attachments
- Logs
- Backups (retention-managed)

---

## 4. Core Data Flow

For each run:

1) **Bootstrap**
- Parse CLI args
- Load/validate config
- Load/resolve endpoints registry
- Initialize output directories, backups, logging

2) **Session**
- Establish authenticated session
- Refresh session on expiry (policy defined in `spec.md`)

3) **Per-BO Pipeline (sequential)**
- Fetch and parse BO metadata
- Resolve the export schema (field paths and column headers)
  - primary selection/order is controlled by `config/columns/{BO}.csv`
- Fetch bundles in batches
- Write CSV incrementally (streaming)
- Generate downloads manifest (attachments list)
- Cleanup BO-scoped state

4) **Shutdown**
- Logout (best effort)
- Finalize logs/outputs

---

## 5. Architectural Components (Modules)

### 5.1 App / Orchestration
**Responsibility:** lifecycle and top-level control flow  
- `AppRunner` / `Main`
- `BoProcessor` (sequential BO loop)
- Fail-fast orchestration and exit codes (per `spec.md`)

### 5.2 Configuration & Endpoint Resolution
**Responsibility:** interpret external configuration without hardcoding behavior  
- `ConfigLoader` (config.yml)
- `EndpointRegistry` (endpoints.yml)
- Strong validation, immutable runtime config

### 5.3 Session & HTTP Execution
**Responsibility:** all network I/O and auth lifecycle  
- `SessionManager`
- `RequestExecutor` (single gateway for REST calls)
- `RetryPolicy` (transient failures only)
- `RestCallLogger` (timing + status)

### 5.4 Metadata Parsing
**Responsibility:** transform metadata API responses into a domain model  
- DTOs mirror API payload
- Domain model is annotation-free
- Cardinality and internal names available to downstream stages

### 5.5 Tracking IDs
**Responsibility:** obtain and filter tracking IDs for bundles calls  
- `TrackingIdService`
- Optional filtering rules (defined in `spec.md`)

### 5.6 Bundles Parsing
**Responsibility:** normalize bundles payloads into records + component rows  
- `BundlesService` (fetch)
- `BundlesParser` (parse)
- `InstancePathUtil` (normalize instance paths)

### 5.7 Schema Resolution (Columns & Headers)
**Responsibility:** decide which fields are requested and how CSV headers are named  
- `ColumnResolver`
- `DisplayNameResolver`
- Uses:
  - metadata as baseline
  - `config/columns/{BO}.csv` as authoritative per-BO selection/order
  - optional display-name overrides (details in `spec.md` / `tech.md`)

### 5.8 CSV Writing
**Responsibility:** streaming export to CSV files  
- Writer implementations per output mode (per-component, merged-single, single-only)
- Common base utilities:
  - header building
  - row formatting
  - file open/close discipline

### 5.9 Downloads Manifest (Attachments)
**Responsibility:** produce a CSV list of attachment server paths  
- `DownloadsCsvWriter` (manifest-only)
- Attachment identification rules and exact fields are defined in `spec.md`

### 5.10 Backup & Retention
**Responsibility:** preserve previous outputs and enforce cleanup  
- `BackupManager`
- `RetentionPolicy`

### 5.11 Offline Mode Provider
**Responsibility:** swap data source while keeping pipeline identical  
- `OfflineDataProvider` (fixtures)
- Same interfaces as online services

---

## 6. Key Architectural Interfaces

The architecture is intentionally interface-driven to keep components testable:

- `EndpointRegistry` → resolves operation to request templates
- `RequestExecutor` → executes HTTP requests and returns typed DTOs
- `SessionManager` → provides valid session tokens
- `MetadataService` / `BundlesService` / `TrackingIdService`
- `ColumnResolver` → returns ordered column definitions per component/mode
- `CsvWriter` → writes headers once and appends rows incrementally

---

## 7. Cross-cutting Concerns

### Determinism
All output determinism rules are defined in `spec.md` and must be enforced by:
- schema resolution
- stable ordering
- consistent filename resolution

### Memory & Streaming
- batch-only buffers
- no full-BO accumulation
- immediate write after each bundles batch

### Observability
- per-call timing and status
- per-BO milestones
- failures provide actionable context

---

## 8. Extension Points

- Add new CSV modes by implementing a new writer strategy
- Add new override file types by extending schema resolution layer
- Support additional endpoints by extending the endpoints adapter (without changing the endpoints.yml format)

---

## 9. References (Canonical)
- `spec.md` — functional rules and invariants
- `tech.md` — implementation rules, packages, coding constraints
- `tasks.md` — slice-by-slice build plan
