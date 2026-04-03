# Tasks: 008 — Delimiter Character Handling in CSV Values

- [ ] **Slice 1: Config parsing and validation for `delimiterReplacement`**
  - [ ] Add `delimiterReplacementEnabled` (`boolean`, default `false`) and `delimiterSubstituteChar` (`String`, default `null`) fields with getters and setters to `AppConfig`. **[Agent: general-purpose]**
  - [ ] In `ConfigLoader.load()`, read the `delimiterReplacement` sub-map and set both fields on config. **[Agent: general-purpose]**
  - [ ] In `ConfigLoader.validate()`, add two checks: (a) if enabled and substituteChar is null/empty → fail fast; (b) if enabled and any char in substituteChar equals the delimiter → fail fast with message including the delimiter char. **[Agent: general-purpose]**
  - [ ] Add tests to `ConfigLoaderTest`: (a) absent section → disabled + null substituteChar, (b) `enabled: true` + valid substituteChar loads, (c) enabled + absent substituteChar fails, (d) enabled + empty substituteChar fails, (e) enabled + substituteChar containing delimiter char fails, (f) enabled + substituteChar with no delimiter chars passes. **[Agent: general-purpose]**
  - [ ] Run `mvn test -Dtest=ConfigLoaderTest` and verify all new tests pass. **[Agent: general-purpose]**

- [ ] **Slice 2: Quoting bug fix — data-writing CSV writers produce valid CSV in quoting mode**
  - [ ] Remove `.withQuoteChar(ICSVWriter.NO_QUOTE_CHARACTER)` from `PerComponentCsvWriter`, `DownloadsCsvWriter`, and `ParentCsvWriter` so OpenCSV uses its default double-quote character. **[Agent: general-purpose]**
  - [ ] Run `mvn test` and confirm no existing tests are broken by the quoting change. **[Agent: general-purpose]**

- [ ] **Slice 3: Replacement logic in `BundlesMapper`**
  - [ ] Add `delimiter` (`char`) and `delimiterSubstituteChar` (`String`, nullable) fields to `BundlesMapper` via constructor. At the call site(s) where `BundlesMapper` is instantiated, pass `config.getDelimiter()` and (`config.isDelimiterReplacementEnabled() ? config.getDelimiterSubstituteChar() : null`). **[Agent: general-purpose]**
  - [ ] Add a private `applyDelimiterReplacement(String value)` method: if `delimiterSubstituteChar` is null or value is null, return as-is; otherwise `value.replace(String.valueOf(delimiter), delimiterSubstituteChar)`. Apply it as the last step in both value processing call sites (lines 150 and 172). **[Agent: general-purpose]**
  - [ ] Create `BundlesMapperTest`: (a) replacement disabled — value with delimiter unchanged, (b) replacement enabled — all occurrences replaced (`"a;b;c"` → `"a||b||c"`), (c) value without delimiter unchanged, (d) replacement applied after HTML decoding (`"&amp;;bar"` → `"&||bar"` with delimiter `;` and substituteChar `||`). **[Agent: general-purpose]**
  - [ ] Run `mvn test -Dtest=BundlesMapperTest` and verify all tests pass. **[Agent: general-purpose]**

- [ ] **Slice 4: End-to-end build and smoke test**
  - [ ] Run `mvn package -DskipTests` and confirm the JAR builds cleanly. **[Agent: general-purpose]**
  - [ ] Run the JAR with `delimiterReplacement.enabled: true` and `substituteChar: "||"`. Confirm: (a) no unquoted delimiter appears as a column break in any output CSV, (b) at least one replaced value is visible in the output. **[Agent: general-purpose]**
  - [ ] Run the JAR with `delimiterReplacement` absent (quoting mode). Confirm any value containing the delimiter is wrapped in double-quotes and does not break column structure. **[Agent: general-purpose]**
