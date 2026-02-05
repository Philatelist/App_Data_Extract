# Tasks (checkbox-style, updated)

## A. Parsing real API shapes
- [x] Metadata DTO + mapper + parser (flat node array)
- [x] Bundles DTO + mapper + parser (array-of-arrays)
- [x] InstancePathUtil (MCPDef/MCP + strip instance IDs)
- [x] Domain models annotation-free
- [x] Offline mode uses real-format fixtures

## B. Ordering guarantees
- [x] Thread request trackingIds into BundlesMapper (positional trackingId assignment)
- [x] Ensure row order == request order
- [x] Ensure column order == fieldPaths order

## C. Filenames
- [x] `{BO}` uses boName (internal), not usageType
- [x] Sanitize `{BO}` and `{Component}` tokens

## D. Overrides
- [ ] Column order file: `config/columns/{BO}.csv` (Module/Component/Parameter per line)
- [ ] Display name overrides: **global** `inputs/overrides/parameter-displaynames.csv`
  - Format: `Component;Parameter;DisplayName;` (semicolon, header row)
- [ ] Retain order-file paths not in metadata as columns

## E. CSV output policy
- [ ] Set writers to `NO_QUOTE_CHARACTER` (no quotes in headers/data)
- [ ] Tracking header is `Summary.Tracking #`

## F. Downloads list
- [ ] DownloadsCsvWriter writes one column, no header
- [ ] Source fields: `serverFileName`
- [ ] Source components: `ReqAttachment`, `ReqContractAttachment`
