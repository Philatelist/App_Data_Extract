# Roadmap (Declarative)

> This roadmap is **execution‑tracking oriented**.
> Canonical behavior and hard requirements are defined in `spec.md`.

## Phase 1 — Repository & CLI
- [ ] Create CLI entrypoint
- [ ] Load and validate configuration
- [ ] Establish directory layout (outputs, logs, backups, downloads)
- [ ] Logging and exit codes

## Phase 2 — Session & HTTP Layer
- [ ] Authentication and session persistence
- [ ] Retry / backoff and timeouts
- [ ] Config‑driven endpoint adapter

## Phase 3 — Parsing Layer (Real API Shapes)
- [ ] Metadata: flat nodes → domain model
- [ ] Bundles: array‑of‑arrays → domain model
- [ ] Central InstancePath normalization utility
- [ ] Clear DTO ↔ domain separation
- [ ] Offline mode with real‑format fixtures

## Phase 4 — Single‑BO Export Pipeline
- [ ] Fetch tracking identifiers
- [ ] Resolve export schema (fields + headers)
- [ ] Batched bundles fetch with streaming CSV writes
- [ ] Deterministic ordering of rows and columns
- [ ] Attachments download manifest generation

## Phase 5 — Multi‑BO & Operations
- [ ] Sequential BO iteration
- [ ] Backup and retention handling
- [ ] Stable filename templates
- [ ] Packaging (fat JAR, reproducible build)

## Phase 6 — Hardening
- [ ] Integration runbook (online / offline)
- [ ] Regression coverage for overrides and filenames
- [ ] Edge‑case handling (missing fields, empty bundles)
