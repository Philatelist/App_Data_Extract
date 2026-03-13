# Functional Specification: BO Type Discovery and Usage Type Filtering

- **Roadmap Item:** Phase 5 — Multi-BO Processing (BO type resolution enhancement)
- **Status:** Approved
- **Author:** Claude

---

## 1. Overview and Rationale (The "Why")

Currently the tool requires administrators to explicitly list every BO type they want to export in `config.yml`. This is a significant obstacle in two common scenarios:

1. **Unknown environments:** An administrator exploring a new CLM system does not know what BO types exist. They must first discover the BO types manually before they can configure the tool.
2. **Category-based extraction:** A migration lead wants to export only "Contract" BOs (ignoring system/directory types). Today there is no way to express this intent in config — the admin must enumerate every matching BO type by name.

This specification introduces two new discovery-driven modes that activate when no explicit BO names are provided:

- **Discovery mode (all):** The tool queries the server for all available BO types and exports all of them.
- **Discovery mode (filtered):** The tool queries all available BO types, inspects each BO's metadata to read its `usageType`, and exports only the BOs that match a configured usage type filter.

Existing behaviour — when explicit BO names are configured — is completely unchanged.

**Success is measured by:** the tool completing a full export run against the correct set of BO types in each configuration mode, logging discovery decisions clearly, and failing fast with a clear message when the filter value is invalid.

---

## 2. Functional Requirements (The "What")

### 2.1 Three Configuration Modes

The tool's BO type resolution behaviour is determined by the `boTypes` and `boUsageTypeFilter` config values. There are exactly three modes:

| Mode | Config condition | Behaviour |
|------|-----------------|-----------|
| **Explicit** | `boTypes` contains at least one entry with a non-blank `name` | Export exactly those BO types. No discovery. |
| **Discovery — all** | `boTypes` is absent, empty (`[]`), or contains only blank names; `boUsageTypeFilter` is absent | Discover all BO types from the server and export all of them. |
| **Discovery — filtered** | `boTypes` is absent/empty; `boUsageTypeFilter` is set to a valid value | Discover all BO types, inspect each BO's usageType, export only BOs that match the filter. |

**Acceptance Criteria:**
- [ ] When `boTypes` lists `NAFBO` and `GPEBO`, only those two BOs are processed. `boUsageTypeFilter` is ignored even if present.
- [ ] When `boTypes: []`, the tool enters discovery mode.
- [ ] When `boTypes` contains one entry with a blank name (`- name: `), the tool treats this as empty and enters discovery mode.
- [ ] When `boTypes` is omitted entirely, the tool enters discovery mode.

---

### 2.2 Discovery — All BO Types (Mode 2)

When discovery mode is triggered and no `boUsageTypeFilter` is set:

1. The tool logs: `"No explicit boTypes configured. Discovering all BO types from server."`
2. The tool calls the `/BOTypes` endpoint (authenticated with the active session) which returns a JSON array of BO type name strings.
3. The tool logs the count and full list of discovered BO types.
4. All discovered BO types are exported through the normal export pipeline.

**If `/BOTypes` returns an empty list:**
- The tool logs a warning: `"No BO types found on server. Nothing to export."`
- The tool exits cleanly (exit code 0). No CSV files are written.

**Acceptance Criteria:**
- [ ] When discovery mode is active and no filter is set, the tool calls `/BOTypes` and processes all returned BO types.
- [ ] The tool logs the number and names of all discovered BO types.
- [ ] When `/BOTypes` returns an empty array, the tool logs a warning and exits with code 0 without writing any CSV files.

---

### 2.3 Discovery — Filtered by Usage Type (Mode 3)

When discovery mode is triggered and `boUsageTypeFilter` is set:

**Config example:**
```yaml
boTypes: []
boUsageTypeFilter: Contract
```

**Allowed values for `boUsageTypeFilter`:** `Directory`, `NonContract`, `Contract`

**Behaviour:**

1. The tool logs: `"No explicit boTypes configured. Discovering BO types with usageType filter: [value]."`
2. The tool calls `/BOTypes` to retrieve the full list of available BO types.
3. The tool logs the total count of discovered BO types.
4. For each discovered BO type, the tool calls `/BOMetaData` (with the BO type passed as a request header) to retrieve its metadata.
5. The tool inspects the `BundleProperties.Property` list in the metadata response and finds the property where `Name = "usageType"`.
6. If the `Value` of `usageType` matches the configured filter, the BO is included. Otherwise it is excluded.
7. The tool logs: `"Retained K of N BO types after usageType filter '[value]'."` listing the retained BO types.
8. The retained BO types are exported through the normal export pipeline.

**Edge cases:**
- If a BO's metadata does not contain a `usageType` property in `BundleProperties`, that BO is **excluded** from the results and a warning is logged: `"BO type [name]: no usageType found in metadata — excluded."`
- If the `/BOMetaData` call fails for a BO during filtering, the run **fails with a clear error message** and exits with a non-zero status code.

**Acceptance Criteria:**
- [ ] Only BO types whose `usageType` matches `boUsageTypeFilter` are exported.
- [ ] The tool logs the number of BOs discovered, filtered, and retained.
- [ ] BO types with no `usageType` property in their metadata are excluded with a warning.
- [ ] If `/BOMetaData` fails for any BO during filtering, the run fails with a clear error message.
- [ ] If the filter retains zero BO types, the tool logs a warning and exits cleanly (exit code 0).

---

### 2.4 `boUsageTypeFilter` Validation

The `boUsageTypeFilter` config value is validated at startup, before any API calls are made.

- **Valid values:** `Directory`, `NonContract`, `Contract` (case-sensitive).
- **Invalid value:** The tool fails immediately with a clear error message: `"Invalid boUsageTypeFilter value: '[value]'. Allowed values: Directory, NonContract, Contract."` and exits with a non-zero status code.
- **Absent value:** No filter is applied (Mode 2 — all).

**Acceptance Criteria:**
- [ ] Setting `boUsageTypeFilter: Contract` passes validation.
- [ ] Setting `boUsageTypeFilter: contract` (lowercase) fails validation with a clear error message.
- [ ] Setting `boUsageTypeFilter: SomeOtherType` fails validation with a clear error message.
- [ ] Omitting `boUsageTypeFilter` entirely is valid (no filter applied).

---

### 2.5 Existing Explicit Mode — Unchanged

When `boTypes` contains at least one entry with a non-blank name, the tool behaves exactly as it does today:

- No call to `/BOTypes` is made.
- `boUsageTypeFilter` is ignored.
- Each explicitly named BO type is processed in order.

**Acceptance Criteria:**
- [ ] The existing explicit BO export behaviour is unchanged.
- [ ] Setting both explicit `boTypes` and `boUsageTypeFilter` does not cause an error — the filter is silently ignored and explicit mode runs.

---

### 2.6 Logging

All discovery events are logged to both the run log file and stdout, consistent with existing progress logging:

| Event | Log message |
|-------|-------------|
| Explicit mode | `"Explicit boTypes configured: processing N BO type(s): [list]."` |
| Discovery starts (no filter) | `"No explicit boTypes configured. Discovering all BO types from server."` |
| Discovery starts (with filter) | `"No explicit boTypes configured. Discovering BO types with usageType filter: [value]."` |
| BOTypes result | `"Discovered N BO type(s) from /BOTypes: [list]."` |
| After filtering | `"Retained K of N BO type(s) after usageType filter '[value]': [list]."` |
| BO missing usageType | `"BO type [name]: no usageType found in metadata — excluded."` |
| Empty result | `"No BO types to process. Exiting."` |

**Acceptance Criteria:**
- [ ] All discovery log messages appear in the timestamped run log file.
- [ ] All discovery log messages are mirrored to stdout.

---

## 3. Scope and Boundaries

### In-Scope

- Detecting explicit vs. discovery mode from `boTypes` config.
- Calling `/BOTypes` to retrieve all available BO type names.
- Calling `/BOMetaData` per discovered BO (only in filtered mode) to read `usageType` from `BundleProperties.Property`.
- Config validation for `boUsageTypeFilter` (fail fast on invalid value).
- Logging for all three modes and edge cases.
- Graceful exit when no BO types remain after filtering or discovery.
- Full processing of retained/discovered BO types through the existing export pipeline.

### Out-of-Scope

- Any changes to CSV format, column ordering, or quoting behaviour.
- Any changes to downloads manifest / attachment logic.
- Any changes to filename templates or BO prefix rules.
- Any changes to session lifecycle, authentication, or HTTP retry logic.
- Any changes to metadata or bundle parsing logic beyond reading `usageType` from already-parsed metadata.
- Per-type configuration overrides for auto-discovered BO types.
- Any other roadmap items.
