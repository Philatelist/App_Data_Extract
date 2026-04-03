# Tasks: 009 — Yes/No Value Translation

- [ ] **Slice 1: Config parsing for `yesNoTranslation`**
  - [ ] Add `yesNoTranslationEnabled` (`boolean`, default `false`), `yesNoTrueValue` (`String`, default `"YES"`), `yesNoFalseValue` (`String`, default `"NO"`) fields with getters and setters to `AppConfig`. **[Agent: general-purpose]**
  - [ ] In `ConfigLoader.load()`, read the `yesNoTranslation` sub-map and set all three fields (with defaults applied when keys are absent). **[Agent: general-purpose]**
  - [ ] Add tests to `ConfigLoaderTest`: (a) absent section → disabled + default values, (b) `enabled: true` with no trueValue/falseValue → defaults `"YES"`/`"NO"`, (c) `enabled: true` with custom `trueValue: "Yes"` / `falseValue: "No"` → stored correctly, (d) `enabled: false` → disabled. **[Agent: general-purpose]**
  - [ ] Run `mvn test -Dtest=ConfigLoaderTest` and verify all new tests pass. **[Agent: general-purpose]**

- [ ] **Slice 2: Yes/No translation in `BundlesMapper`**
  - [ ] Extend the `BundlesMapper` constructor with three new parameters: `boolean yesNoTranslationEnabled`, `String yesNoTrueValue`, `String yesNoFalseValue`. Update the no-arg constructor to delegate with `false, "YES", "NO"`. Update `BundleParser(AppConfig config)` to pass the new values from config. **[Agent: general-purpose]**
  - [ ] In `BundlesMapper.map()`, build a `Map<String, Map<String, String>> fieldTypeMap` (componentInternalName → fieldInternalName → dataType) from the `BoMetadata` before processing records. **[Agent: general-purpose]**
  - [ ] Add a private `applyYesNoTranslation(String value, String fieldType)` method. Apply it as the final step in both value processing call sites, after `applyDelimiterReplacement`. **[Agent: general-purpose]**
  - [ ] Add tests to `BundlesMapperTest`: (a) translation disabled → `"true"` on yesNoRadioButtons passes through, (b) enabled → `"true"`→`"YES"`, `"false"`→`"NO"`, (c) case-insensitive: `"True"`→`"YES"`, `"FALSE"`→`"NO"`, (d) non-boolean value on yesNoRadioButtons field → unchanged, (e) empty string → unchanged, (f) `"true"` on non-yesNoRadioButtons field → unchanged. **[Agent: general-purpose]**
  - [ ] Run `mvn test -Dtest=BundlesMapperTest` and verify all new tests pass. **[Agent: general-purpose]**

- [ ] **Slice 3: End-to-end build and smoke test**
  - [ ] Run `mvn package -DskipTests` and confirm the JAR builds cleanly. **[Agent: general-purpose]**
  - [ ] Add `yesNoTranslation: {enabled: true}` to `config.yml`, run the JAR, and verify that at least one `YES` or `NO` value appears in the output CSV where a boolean field was expected. Confirm the run completes without error. **[Agent: general-purpose]**
