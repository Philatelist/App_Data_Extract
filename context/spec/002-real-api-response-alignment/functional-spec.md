# Functional Specification: Align Parsing with Real API Response Shapes

- **Roadmap Item:** Phase 4 — Single-BO Export Pipeline (corrective change to metadata retrieval, bundle parsing, and CSV export)
- **Status:** In Review
- **Author:** Poe

---

## 1. Overview and Rationale (The "Why")

The initial implementation was built against simplified sample JSON fixtures that assumed a nested object structure for both the metadata and bundles API responses. When tested against the real SCLM REST API, both responses turned out to have fundamentally different shapes:

- **Metadata response:** Not a simple object with `boName`/`components`/`fields`. Instead, it is a **flat JSON array of nodes**, each representing one level of a hierarchical tree (Bundle → Module → Component → Parameter). Properties like `name`, `displayName`, `cardinality`, and `instancePath` are stored as key-value entries inside a `Property` array on each node. The hierarchy is expressed through `id`/`parentId` relationships and `listType` discriminators.

- **Bundles response:** Not a nested object with `records`/`components`/`rows`. Instead, it is an **array of arrays** — each inner array represents one record (one tracking ID) and contains a flat list of field objects with `Name`, `DisplayName`, `Value`, `InternalValue`, `DataType`, and `InstancePath`. Component grouping must be derived from the `InstancePath` segment, not from a pre-structured nesting.

This mismatch causes Jackson deserialization failures and prevents the tool from working against a live server. The tool must be updated so that its parsing layer correctly handles the real response shapes while preserving the existing export behavior (CSV modes, column ordering, filename templates, downloads lists, etc.).

**Success is measured by:** the tool successfully connecting to a live SCLM server, parsing real metadata and bundle responses, and producing the same CSV output structure (headers, column formats, tracking # column, downloads lists) as defined in the original functional spec.

---

## 2. Functional Requirements (The "What")

### 2.1 Metadata Response Parsing

The tool must correctly parse the real metadata response format when calling the `getBoMetaData` endpoint.

- The metadata response is a **JSON array of node objects**. Each node has:
  - `listType`: one of `"BundleProperties"`, `"ModuleProperties"`, `"ComponentProperties"`, or `"ParameterProperties"`.
  - `id`: the node's identifier (e.g., `"NAFBO/"`, `"NAFData/"`, `"NAFData/ReqNAFInfo/"`).
  - `parentId`: the parent node's `id` (null for the root node).
  - `Property`: an array of property objects, each with `Name`, `Value`, `InternalValue`, `DataType`, and `InstancePath`.
- The tool must reconstruct the BO hierarchy from these nodes:
  - `BundleProperties` → the BO itself (extract `name`, `displayName`, `usageType` from its `Property` array).
  - `ModuleProperties` → the module (extract `name`, `displayName`).
  - `ComponentProperties` → a component (extract `name`, `displayName`, `cardinality` from its `Property` array).
  - `ParameterProperties` → a field/parameter within a component (extract `name`, `displayName`, `dataType` from its `Property` array; derive `instancePath` from the property's `InstancePath` value).
- The parsed result must feed into the same domain model used by the rest of the pipeline (BO name, usage type, components with their fields, cardinality, instance paths).
- **Acceptance Criteria:**
  - [ ] The tool deserializes the metadata response as a JSON array of node objects (not as a single BO object).
  - [ ] The tool correctly identifies `BundleProperties`, `ModuleProperties`, `ComponentProperties`, and `ParameterProperties` nodes.
  - [ ] The tool reconstructs the hierarchy using `id`/`parentId` relationships to associate parameters with their parent component.
  - [ ] The BO name and usage type are extracted from the `BundleProperties` node.
  - [ ] Each component's `name`, `displayName`, and `cardinality` are extracted from `ComponentProperties` nodes.
  - [ ] Each parameter's `name`, `displayName`, `dataType`, and `instancePath` are extracted from `ParameterProperties` nodes.
  - [ ] The parsed domain model is identical in structure to what the CSV export pipeline expects (components, fields, cardinality, instance paths).
  - [ ] When a `BundleProperties` node is missing, the tool reports a clear error.

### 2.2 Bundles Response Parsing

The tool must correctly parse the real bundles response format when calling the `bundles` endpoint.

- The bundles response is a **JSON array of arrays**. Each inner array represents one record (one tracking ID) and contains a flat list of field objects.
- Each field object has:
  - `Name`: the field's internal name (e.g., `"trackingNumber"`, `"contractNumber"`).
  - `DisplayName`: the field's display name (e.g., `"Tracking #"`, `"Contract Number"`).
  - `Value`: the field's display value.
  - `InternalValue`: the field's internal/raw value.
  - `DataType`: the data type (e.g., `"string"`, `"int"`, `"date"`, `"double"`).
  - `InstancePath`: the fully qualified path including instance IDs (e.g., `"MCP:/NAFData|16016628/ReqNAFInfo|16016628/trackingNumber"`).
- The tool must derive component grouping from `InstancePath`:
  - Strip the `MCP:/` prefix.
  - Strip instance IDs (the `|<number>` segments) from each path segment.
  - The second segment (after stripping) is the component internal name.
  - The last segment is the parameter internal name.
- The tracking ID for each record must come from the **request `trackingIds[]` array**, not from parsing a `trackingNumber` field in the response. The bundles API returns inner arrays in the same order as the request `trackingIds[]`, so the mapper must accept the request tracking IDs and assign them positionally. This is necessary because not all components return a `trackingNumber` field, so relying on the response field would fail for those components.
- As a fallback when the request tracking IDs are not available (e.g., offline mode without request context), the mapper may still extract the tracking ID from the `trackingNumber` response field if present.
- **Acceptance Criteria:**
  - [ ] The tool deserializes the bundles response as an array of arrays (not as a nested records/components/rows object).
  - [ ] Each inner array is treated as one record's fields.
  - [ ] The tool derives the component internal name from the InstancePath of each field.
  - [ ] The tool derives the parameter internal name from the InstancePath of each field.
  - [ ] Instance IDs (pipe-separated numbers) in the InstancePath are correctly stripped when determining component/parameter identity.
  - [ ] The tracking ID for each record is assigned from the request `trackingIds[]` array by position (first inner array = first tracking ID, etc.).
  - [ ] When request tracking IDs are not available, the tracking ID falls back to the `trackingNumber` response field.
  - [ ] The parsed records feed into the existing CSV writing pipeline correctly (the same domain model as before, grouped by component).

### 2.3 InstancePath Parsing

The tool must have a centralized, reusable utility for parsing InstancePath strings from both metadata and bundles responses.

- Metadata InstancePath format: `MCPDef:/<ModuleName>/<ComponentName>/<ParameterName>` (e.g., `MCPDef:/NAFData/ReqNAFInfo/trackingNumber`).
- Bundles InstancePath format: `MCP:/<ModuleName>|<instanceId>/<ComponentName>|<instanceId>/<ParameterName>` (e.g., `MCP:/NAFData|16016628/ReqNAFInfo|16016628/trackingNumber`).
- Parsing rules:
  - Strip `MCPDef:/` or `MCP:/` prefix.
  - Split by `/`.
  - For each segment, strip any `|<number>` suffix (instance ID).
  - Ignore empty segments.
  - Module = first segment, Component = second segment, Parameter = last segment.
- **Acceptance Criteria:**
  - [ ] The utility correctly parses metadata-style paths (`MCPDef:/...`).
  - [ ] The utility correctly parses bundles-style paths (`MCP:/...`) with instance IDs.
  - [ ] The utility strips instance IDs from path segments.
  - [ ] The utility returns module, component, and parameter names.
  - [ ] Null or malformed paths return an empty/null result without throwing.

### 2.4 DTO and Domain Model Separation

The tool must maintain a clean separation between API-level data transfer objects (DTOs) and internal domain models.

- JSON deserialization annotations (Jackson `@JsonProperty`, etc.) must only appear on DTO classes, never on domain models.
- Dedicated mapper classes must transform DTOs into domain models.
- The domain models used by the CSV export pipeline (`BoMetadata`, `ComponentMetadata`, `FieldMetadata`, `BundleRecord`, `BundleComponent`) must remain annotation-free.
- **Acceptance Criteria:**
  - [ ] DTO classes exist for the metadata response nodes and bundles response fields, with Jackson annotations.
  - [ ] Domain model classes have no Jackson annotations.
  - [ ] Mapper classes exist that convert DTOs to domain models.
  - [ ] The CSV export pipeline uses only domain models, never DTOs directly.

### 2.5 CSV Header Naming

CSV column headers must continue to follow the existing format, with a fallback chain when display names are unavailable.

- Primary format: `<ComponentDisplayName>.<ParameterDisplayName>` (from metadata).
- Fallback 1: If metadata display name is not available, use the `DisplayName` from the bundles response field.
- Fallback 2: If neither is available, use the internal parameter name.
- **Acceptance Criteria:**
  - [ ] CSV headers use metadata display names when available.
  - [ ] When metadata display names are missing, headers fall back to bundles `DisplayName`.
  - [ ] When both are missing, headers fall back to internal parameter names.
  - [ ] The `Tracking #` column remains the first column in every export CSV.

### 2.6 Offline / Test Mode

Offline mode must be updated to use the real-format example fixtures.

- Metadata must be read from `inputs/examples/BOMetaDataResponse.example.json` (or whatever location the fixtures reside).
- Bundles must be read from `inputs/examples/BundlesResponse.example.json` (or whatever location the fixtures reside).
- Offline mode must use the same DTOs, mappers, and export pipeline as online mode — no separate parsing path.
- **Acceptance Criteria:**
  - [ ] Offline mode reads metadata from the real-format example file.
  - [ ] Offline mode reads bundles from the real-format example file.
  - [ ] Offline mode uses the same DTO → domain model pipeline as online mode.
  - [ ] Offline mode produces CSV output files successfully.
  - [ ] Offline mode produces a downloads list CSV.

### 2.8 Record Ordering Guarantees

The tool must preserve deterministic ordering of both rows and columns in the CSV output.

- **Row ordering:** Records in the CSV output must appear in the same order as the `trackingIds[]` in the request. Within a batch, the inner arrays of the bundles response correspond positionally to the request tracking IDs, so the mapper must preserve this ordering.
- **Column ordering:** Columns in the CSV output must follow the `fieldPaths[]` order from the request (which is derived from the metadata component/field ordering, optionally overridden by a column order file). Fields in the bundles response that are not in the requested `fieldPaths` should be ignored rather than appended.
- **Acceptance Criteria:**
  - [ ] CSV rows appear in the same order as the request `trackingIds[]`.
  - [ ] CSV columns appear in the same order as the request `fieldPaths[]` (derived from metadata, optionally overridden by column order file).
  - [ ] Response fields not present in the requested `fieldPaths` are not included in the CSV output.
  - [ ] Within multi-cardinality components, rows for the same tracking ID are grouped together, ordered by their instance ID.

---

### 2.7 Existing Behavior Preserved

All existing export behaviors must continue to work correctly with the new parsing layer.

- **Acceptance Criteria:**
  - [ ] The three CSV modes (`per-component`, `merged-single`, `single-only`) produce correct output.
  - [ ] Column ordering files (`config/columns/{BoType}.csv`) are respected.
  - [ ] Column override files (`config/overrides/{BoType}.csv`) are respected.
  - [ ] Filename templates resolve correctly.
  - [ ] Batch processing and incremental CSV writing still work.
  - [ ] Downloads CSV is generated for each BO type.
  - [ ] Backup management and retention are unaffected.
  - [ ] Session management (login, re-login, logout) is unaffected.

---

## 3. Scope and Boundaries

### In-Scope

- New DTO classes for the real metadata response (node array with `listType`/`id`/`parentId`/`Property`).
- New DTO classes for the real bundles response (array of arrays of field objects with `Name`/`DisplayName`/`Value`/`InternalValue`/`DataType`/`InstancePath`).
- A centralized `InstancePath` parsing utility that handles both `MCPDef:/` and `MCP:/` formats, including instance ID stripping.
- Mapper classes that convert DTOs to existing domain models.
- Removing Jackson annotations from domain model classes.
- Removing or replacing the old simplified sample fixtures (`boMetaData.sample.json`, `bundles.sample.json`).
- Updating offline mode to use the real-format example fixtures.
- Updating the `BundleParser` / `MetadataParser` classes to use the new DTO-based parsing.
- Updating the README to document the real bundles response shape.
- Updating all affected tests.
- Threading request `trackingIds[]` through the bundles mapper so record identity comes from the request rather than parsing the response `trackingNumber` field.
- Ensuring CSV row ordering follows the request `trackingIds[]` order and column ordering follows the `fieldPaths[]` order.

### Out-of-Scope

- Changes to the endpoints file format or the endpoints adapter.
- Changes to the config file format.
- Changes to CSV export modes, filename templates, or delimiter handling.
- Changes to session management or retry logic.
- Changes to backup management or directory structure.
- Adding new export features or BO processing capabilities.
- Downloading actual attachment files.
