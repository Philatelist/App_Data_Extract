# Roadmap (Declarative, checkbox-style)

> This roadmap is execution/tracking oriented. Canonical behavior remains `spec.md`.

## Phase 1 — Repo & CLI Skeleton
- [ ] Create CLI entrypoint and config loading
- [ ] Directory layout: logs / backups / downloads / outputs
- [ ] Logging + exit codes

## Phase 2 — Session & HTTP
- [ ] Login/session persistence
- [ ] Retry/backoff and timeouts
- [ ] Endpoint adapter (config-driven)

## Phase 3 — Real API Parsing Layer
- [ ] Metadata: parse flat node array -> domain model
- [ ] Bundles: parse array-of-arrays -> domain model
- [ ] Central InstancePath parsing utility
- [ ] DTO/mapper separation (domain models annotation-free)
- [ ] Offline mode uses real-format fixtures

## Phase 4 — Single-BO Export Pipeline
- [ ] Fetch tracking IDs
- [ ] Resolve fieldPaths + columns
- [ ] Batch bundles fetch + streaming write
- [ ] Deterministic ordering: rows by trackingIds, cols by fieldPaths
- [ ] Downloads list generation (serverFileName from attachments components)

## Phase 5 — Multi-BO + Ops
- [ ] Iterate boTypes sequentially
- [ ] Retention policy for backups/logs
- [ ] Consistent filename templates (sanitized tokens)
- [ ] Packaging: shaded/fat jar, reproducible builds

## Phase 6 — Hardening
- [ ] Integration runbook (online/offline)
- [ ] Regression tests around overrides + filenames
- [ ] Edge-cases: missing metadata fields, empty bundles, partial batches
