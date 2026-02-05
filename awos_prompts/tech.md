# Technical Notes (structured, updated)

## Canonicality
This document describes engineering intent. Canonical behavior is in `spec.md`.

## Determinism
- Rows follow request `trackingIds[]` order (positional mapping for bundles).
- Columns follow request `fieldPaths[]` order.

## Real API shapes
- Metadata: flat node array; hierarchy via `id/parentId/listType`.
- Bundles: array-of-arrays; component/parameter derived from InstancePath.

## InstancePath parsing
- Strip `MCPDef:/` or `MCP:/`
- Strip `|<instanceId>` suffixes
- module = first segment, component = second, parameter = last

## ColumnResolver behavior (updated, agreed)
### Selection & ordering
- If `config/columns/{BO}.csv` exists: return **all** paths from the file in file order.
- Paths not present in BOMetaData are **retained** (columns exist; values may be empty unless returned by bundles).

### Header display names
- Global override file: `inputs/overrides/parameter-displaynames.csv`
- Format: `Component;Parameter;DisplayName;` (semicolon, header row)
- Precedence: overrides → metadata → bundles → internal

## CSV quoting policy (per your requirement)
- Writers are configured with `NO_QUOTE_CHARACTER` to avoid quotes in headers and data.
- Note: values containing delimiter/newlines may produce non-RFC CSV; this is an explicit trade-off.

## Downloads CSV
- One column, no header
- Values: `serverFileName`
- Components: `ReqAttachment`, `ReqContractAttachment`
