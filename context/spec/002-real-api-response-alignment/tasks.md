# Tasks: Align Parsing with Real API Response Shapes

## Remaining Work: Thread Request Tracking IDs + Ordering Guarantees

- [x] **Slice 1: Thread request tracking IDs through BundlesMapper with positional assignment**
  - [x] Sub-task: Add `List<Long> requestTrackingIds` parameter to `BundlesMapper.map()`. When non-null and size matches, assign tracking IDs positionally (`record.setTrackingId(requestTrackingIds.get(i))`). When null or size mismatches, fall back to extracting from response `trackingNumber` field (current behavior). **[Agent: general-purpose]**
  - [x] Sub-task: Update `BundlesMapperTest` — update all existing `map()` calls to pass `null` (preserving current behavior); add new test for positional assignment with explicit tracking IDs; add new test for null fallback; add new test for size mismatch fallback. **[Agent: general-purpose]**

- [x] **Slice 2: Thread tracking IDs through BundleParser and BatchProcessor**
  - [x] Sub-task: Add `List<Long> requestTrackingIds` parameter to `BundleParser.parse()`, pass through to `bundlesMapper.map()`. **[Agent: general-purpose]**
  - [x] Sub-task: In `BatchProcessor.fetchBatch()`, pass `batchIds` to `bundleParser.parse()` as the request tracking IDs. **[Agent: general-purpose]**
  - [x] Sub-task: In `OfflineDataSource`, update `loadBundles()` to accept `List<Long> requestTrackingIds` and pass through to `bundleParser.parse()`. Update `fetchBatch()` to pass `trackingIds` through. Update `getTrackingNumbers()` to pass `null` (fallback behavior). **[Agent: general-purpose]**
  - [x] Sub-task: Update `BundleParserTest` — pass real tracking IDs (`[16016628L, 17011747L]`) to `parse()` calls; verify records have those tracking IDs assigned positionally. **[Agent: general-purpose]**

- [x] **Slice 3: Build verification and cleanup**
  - [x] Sub-task: Run `mvn clean package` and verify all tests pass. **[Agent: Bash]**
  - [x] Sub-task: Run offline mode (`java -jar target/clm-data-extract-1.0.0.jar --config config-offline.yml`) and verify CSV output is produced with correct tracking IDs, exit code 0. **[Agent: Bash]**
