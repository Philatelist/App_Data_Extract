# Change Request (Patch Mode): Add in-repo overrides for parameter order + column display names

## Low-token patch rules (MANDATORY)
- Patch-only: modify only what is necessary.
- Minimal diffs: no rewrites, no reformatting.
- Output only: list of files to change, minimal diffs, commands to run.
- Do NOT reread large docs; use only this change request.

## Goal
Implement two override mechanisms stored INSIDE the repository:
1) One BO-level CSV file that defines parameter selection and column order (one file per BO).
2) One global CSV file that overrides column display names by (Component, Parameter) across all BOs.

## Required Files (in repo)
1) BO parameter override:
- Path: `inputs/overrides/bo-parameters/{BO}.csv` (e.g., `.../NAFBO.csv`)
- Format (semicolon, header required):
  `Component;Parameter;`
- Order of rows defines export order.
- Only listed parameters are exported for that component.

2) Display name override:
- Path: `inputs/overrides/parameter-displaynames.csv`
- Format (semicolon, header required):
  `Component;Parameter;DisplayName;`
- Applies to all BOs.
- Overrides header display name for that parameter.

## Rules
- Component name is component internal name from BOMetaData ComponentProperties where Property(Name='name').Value.
- Parameter name is parameter internal name from BOMetaData ParameterProperties where Property(Name='name').Value.

### Precedence
Parameter list/order per component:
1) BO override file (if present for that component)
2) BOMetaData order

Parameter display name:
1) display name override file
2) BOMetaData displayName
3) bundles DisplayName
4) internal name

## Implementation notes (minimal)
- Parse both CSVs with semicolon delimiter.
- Load once per run.
- Apply during header construction and fieldPaths building.

## Constraints
- Keep existing flat bundles and trackingId-as-key logic intact.
- Patch only; do not refactor unrelated code.
