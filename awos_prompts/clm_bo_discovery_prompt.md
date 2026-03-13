
# Claude Code Prompt — BO Discovery Enhancement

## Context
You are working in the **Java CLM Data Extract** repository.

This project is a **Java batch extractor** for CLM / Selectica / myContracts / Determine.

Current behavior:
- `config.yml` may contain explicit `boTypes`
- when `boTypes` are explicitly listed, the tool processes only those BOs
- login/session is already implemented
- endpoints are config-driven
- metadata parsing already supports the real API shape (flat node array)
- bundles parsing already supports the real API shape (array of arrays)
- CSV export, filename rules, deterministic ordering, overrides, downloads manifest already exist

Important existing rules that must NOT be broken:

- trackingId comes from request trackingIds[]
- row order == request tracking ID order
- column order == resolved fieldPaths order
- field paths used for bundles are normalized
- {BO} in filenames uses internal BO name, not usage type
- first tracking header is Summary.Tracking #
- CSV writers use NO_QUOTE_CHARACTER
- downloads manifest logic must remain unchanged
- existing explicit boTypes behavior must remain unchanged unless the new config mode applies

---

# Goal

Implement a new **BO discovery / BO filtering enhancement**.

Today the tool expects explicit BOs in config.
We now need to support the following additional behavior.

---

# Case A — Process All BOs

If config property:

boTypes:
  - name:

contains no BO name (empty / blank), then the tool must process **all BO types** returned by:

{{baseUrl}}/BOTypes

This request requires the active CLM session.

---

# Case B — Process Discovered BOs Filtered by usageType

If explicit BO names are not provided, allow specifying a **BO usage type filter** in config.

Allowed usage type values:

- Directory
- NonContract
- Contract

Behavior:

1. Call {{baseUrl}}/BOTypes with session and get the full BO list.
2. For each BO from that list, call {{baseUrl}}/BOMetaData
3. Pass header:
   boType = current BO
4. Parse metadata response.
5. Inspect BundleProperties.Property list.
6. Find property where Name = usageType

Example:

{
  "Name": "usageType",
  "Value": "Contract"
}

7. Use this value to decide whether the BO should be included.

---

# Required Config Behavior

## Existing explicit mode

If config has explicit BO names:

boTypes:
  - name: NAFBO
  - name: GPEBO

then process exactly those BOs.

---

## Discovery mode

If config has no explicit BO names:

### Mode 1 — All discovered BOs

Example:

boTypes: []

or

boTypes:
  - name:

Then process **all BOs** returned by /BOTypes.

### Mode 2 — Filter by usageType

Add config property:

boUsageTypeFilter: Contract

Allowed values:

- Directory
- NonContract
- Contract

Behavior:

discover all BOs via /BOTypes  
fetch metadata for each BO  
include only BOs whose usageType matches the filter

---

# Precedence Rules

1. Explicit BO names override everything
2. boUsageTypeFilter applies only when explicit BO names are absent
3. If neither explicit BO nor filter exist → process all BOs

---

# Engineering Requirements

## Minimal change
Do not redesign unrelated parts of the system.

## Preserve existing behavior
Do NOT break:

- explicit BO config
- session lifecycle
- export pipeline
- CSV behavior
- filenames
- overrides
- downloads manifest

## Reuse metadata parser
Do not create new metadata parsing logic.

## Session handling
Use existing authenticated request pipeline.

## Config validation
Validate boUsageTypeFilter.
If invalid → fail fast.

## Logging
Add INFO logs for:

explicit BO mode  
discovered BO mode  
usageType filtered mode  
number of BOs returned from BOTypes  
number of BOs retained after filtering

## Efficiency
Metadata will be fetched once per discovered BO.

## Error handling
If metadata retrieval fails during filtering → fail run clearly.

---

# Expected Implementation Areas

Likely areas in repository:

config model / loader  
BO discovery logic  
endpoint usage for /BOTypes  
metadata inspection helper  
orchestrator bootstrap

---

# Required Deliverables

## Code changes
Implement the feature fully.

## Tests

Add tests for:

explicit BO names  
discovery mode without filter  
discovery mode with filter  
invalid filter value  
metadata without usageType  
empty BOTypes result

## Documentation
Update config examples if needed.

---

# Output Format

Claude must return:

1. Summary of change
2. Files changed
3. Reason for each change
4. Config examples
5. Edge cases handled
6. Tests added or updated

---

# Constraints

Do NOT:

change CSV formats  
change attachment logic  
change downloads manifest logic  
break deterministic ordering
