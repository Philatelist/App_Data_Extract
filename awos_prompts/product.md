# Product Definition (AWOS-style, reconciled with our export model)

## Problem
Administrators need a repeatable way to extract CLM BO data into CSV for auditing, reporting, and downstream processing.

## Users
- CLM admins / support engineers
- Audit & compliance analysts
- Data ops / migration teams

## What it does
A Java CLI that:
1) Logs into CLM
2) Retrieves tracking IDs per BO
3) Retrieves BO metadata
4) Fetches bundles in batches
5) Exports CSV with deterministic ordering
6) Optionally emits an attachments-to-download list

## Output model (reconciled)
- The tool processes **one BO type at a time** (sequential).
- Default export is **per-component CSVs for that BO** (component-first).
- Other modes (merged / single-only) may also exist, but **we do not claim “one CSV per BO” as the default**.
- Downloads list is a separate one-column CSV.

## Key promises (non-negotiable)
- Deterministic: row order == request trackingIds; column order == request fieldPaths.
- Config-driven endpoints and templates.
- In-repo overrides for:
  - parameter selection/order (`config/columns/{BO}.csv`)
  - display names (`inputs/overrides/parameter-displaynames.csv`)

## Non-goals
- No UI
- No writing back to CLM
- No attachment file downloads (only list generation)

## Success metrics
- Runs against live server with real response shapes
- Produces stable CSV outputs across runs (given same inputs)
- Enables admins to control columns/headers without code changes
