# Technical Considerations: Align Parsing with Real API Response Shapes

- **Functional Specification:** `context/spec/002-real-api-response-alignment/functional-spec.md`
- **Status:** Draft
- **Author(s):** Claude

---

## 1. High-Level Technical Approach

The bulk of the DTO/mapper/parser refactoring (spec sections 2.1, 2.3, 2.4) is already complete — Slices 1-6 of the original implementation plan landed the following:

- `InstancePathUtil` centralized path parser
- `MetadataNodeDto`, `PropertyDto`, `MetadataMapper` for metadata hierarchy reconstruction
- `BundleFieldDto`, `BundlesMapper` for flat bundles parsing
- Jackson annotations stripped from all six domain model classes
- `MetadataParser` and `BundleParser` rewritten to use DTOs + mappers
- `BoMetadata` threaded through `DataSource.fetchBatch()` so BundlesMapper can determine cardinality
- Offline mode updated to use real-format example fixtures

Two requirements from the updated functional spec remain unimplemented:

1. **Tracking ID from request** (spec 2.2): `BundlesMapper.map()` currently extracts tracking IDs from the response `trackingNumber` field. It must instead accept the request `trackingIds[]` array and assign tracking IDs to records positionally, falling back to the response field only when request IDs are unavailable.

2. **Row/column ordering guarantees** (spec 2.8): CSV rows must follow request `trackingIds[]` order; CSV columns must follow request `fieldPaths[]` order. Column ordering is already handled by `ColumnResolver` (which derives `fieldPaths` from metadata order or an order file). Row ordering requires the tracking IDs to flow through the mapper so records can be keyed in request order.

Both gaps are addressed by a single change: threading `List<Long> requestTrackingIds` from the call site through to `BundlesMapper.map()`.

---

## 2. Proposed Solution & Implementation Plan

### 2.1 Signature Changes (call chain)

The request tracking IDs are already available at each call site. They need to be forwarded through to the mapper:

**`BundlesMapper.map()`** — add `List<Long> requestTrackingIds` parameter:

```java
// Current
public BundleResponse map(List<List<BundleFieldDto>> rawRecords, BoMetadata metadata)

// New
public BundleResponse map(List<List<BundleFieldDto>> rawRecords, BoMetadata metadata,
                          List<Long> requestTrackingIds)
```

Behavior:
- When `requestTrackingIds` is non-null and its size matches `rawRecords.size()`, assign `record.setTrackingId(requestTrackingIds.get(i))` positionally.
- When `requestTrackingIds` is null or size mismatches, fall back to extracting `trackingNumber` from the response field (current behavior).
- Records are built in iteration order of `rawRecords`, which inherently matches request order since the API returns inner arrays in the same order as the request `trackingIds[]`.

**`BundleParser.parse()`** — add `List<Long> requestTrackingIds` parameter:

```java
// Current
public BundleResponse parse(String json, BoMetadata metadata)

// New
public BundleResponse parse(String json, BoMetadata metadata, List<Long> requestTrackingIds)
```

Simply passes through to `bundlesMapper.map(rawRecords, metadata, requestTrackingIds)`.

**`BatchProcessor.fetchBatch()`** — already has `batchIds`, just forward:

```java
// Current (line 53)
return bundleParser.parse(response, metadata);

// New
return bundleParser.parse(response, metadata, batchIds);
```

**`DataSource.fetchBatch()`** — no change needed. The interface already receives `List<Long> trackingIds` as the first parameter, and implementations (`ApiDataSource`, `OfflineDataSource`) already have access to it.

**`ApiDataSource.fetchBatch()`** — already passes `trackingIds` to `batchProcessor.fetchBatch()` which has them as `batchIds`.

**`OfflineDataSource.fetchBatch()`** — must pass `trackingIds` through to `bundleParser.parse()`:

```java
// Current
cachedBundles = bundleParser.parse(json, metadata);

// New — loadBundles gains a requestTrackingIds parameter
// fetchBatch passes trackingIds through
```

Note: `OfflineDataSource.loadBundles()` is also called from `getTrackingNumbers()` where no request tracking IDs are available. In that case, `null` is passed, triggering the fallback behavior.

### 2.2 Column Ordering (already satisfied)

`ColumnResolver.resolveFieldPaths()` already produces field paths in metadata component/field order, optionally overridden by the column order file. The CSV writers (`PerComponentCsvWriter`, `MergedSingleCsvWriter`, `SingleOnlyCsvWriter`) consume `ResolvedColumn` lists from `ColumnResolver.resolveColumns()`, so columns already appear in the correct order. No changes needed.

### 2.3 Row Ordering (already satisfied by design)

The bundles API returns inner arrays in the same order as the request `trackingIds[]`. `BundlesMapper` iterates `rawRecords` sequentially and builds `BundleRecord` objects in that order. `BoPipeline` writes records to CSV incrementally as each batch returns. Within a batch, order is preserved. Across batches, `BoPipeline` iterates batches in order. No additional sorting or reordering is needed — the positional assignment of tracking IDs from the request array is sufficient to guarantee correct ordering.

### 2.4 Files Changed

| File | Change |
|---|---|
| `src/main/java/com/clmextract/api/mapper/BundlesMapper.java` | Add `requestTrackingIds` parameter; positional assignment with fallback |
| `src/main/java/com/clmextract/export/BundleParser.java` | Add `requestTrackingIds` parameter; pass through to mapper |
| `src/main/java/com/clmextract/export/BatchProcessor.java` | Pass `batchIds` to `bundleParser.parse()` |
| `src/main/java/com/clmextract/export/OfflineDataSource.java` | Pass `trackingIds` through `loadBundles()` to parser |
| `src/test/java/com/clmextract/api/mapper/BundlesMapperTest.java` | Update all `map()` calls; add test for positional assignment; add test for null fallback |
| `src/test/java/com/clmextract/export/BundleParserTest.java` | Update `parse()` calls with tracking IDs |

### 2.5 No files deleted or created

All changes are modifications to existing files. No new classes are needed.

---

## 3. Impact and Risk Analysis

### System Dependencies

- **CSV writers** (`PerComponentCsvWriter`, `MergedSingleCsvWriter`, `SingleOnlyCsvWriter`): Unaffected. They consume `BundleRecord.getTrackingId()` which is already a `long`. The source of the value (request vs response) is transparent to them.
- **ColumnResolver**: Unaffected. Already produces correct column ordering.
- **BoPipeline**: Unaffected. Already passes `batch` (a `List<Long>`) to `dataSource.fetchBatch()`.
- **DownloadsCsvWriter**: Unaffected. Uses tracking IDs from `BundleRecord`.

### Potential Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| API returns inner arrays in different order than request `trackingIds[]` | Low — API documentation and observed behavior confirm positional correspondence | Fallback to response `trackingNumber` field remains available; can log a warning if positional ID doesn't match response `trackingNumber` when both are present |
| `requestTrackingIds.size()` != `rawRecords.size()` (API filters some IDs) | Medium — possible if some tracking IDs have no data | When sizes differ, fall back to response field extraction; log a warning |
| Offline mode `getTrackingNumbers()` calls `loadBundles(null)` | Certain — by design | The `null` requestTrackingIds triggers the response-field fallback, which is correct for this code path since we're discovering tracking IDs, not assigning them |
| Existing tests break from signature change | Certain | All callers updated in the same slice; tests updated to pass tracking IDs or `null` |

---

## 4. Testing Strategy

### Unit Tests

- **`BundlesMapperTest`**:
  - Add test: positional assignment — provide `requestTrackingIds = [100L, 200L]` with two raw records, verify `record.getTrackingId()` is `100` and `200` regardless of whether `trackingNumber` field exists in the response.
  - Add test: null fallback — pass `null` for `requestTrackingIds`, verify mapper falls back to extracting from response `trackingNumber` field (existing behavior).
  - Add test: size mismatch — pass `requestTrackingIds` with different size than `rawRecords`, verify fallback behavior.
  - Update existing tests: add `requestTrackingIds` or `null` to all existing `map()` calls.

- **`BundleParserTest`**:
  - Update `parse()` calls to pass tracking IDs from the real example data (`[16016628L, 17011747L]`).
  - Verify records come back with those tracking IDs assigned positionally.

### Integration Verification

- Run `mvn clean package` — all tests pass.
- Run offline mode (`java -jar target/clm-data-extract-1.0.0.jar --config config-offline.yml`) — verify CSV output still contains correct tracking IDs and data, exit code 0.
- Compare CSV output before and after — should be identical since the real example data already has `trackingNumber` fields that match the positional assignment.
