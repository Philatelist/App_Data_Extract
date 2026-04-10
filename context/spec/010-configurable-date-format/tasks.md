# Task List: Configurable Date Format Output

- **Spec:** `context/spec/010-configurable-date-format/`
- **Status:** In Progress

---

## Slice 1: Config parsing and fail-fast validation

After this slice: the tool reads the `dateFormat` section from `config.yml`, and exits at startup with a clear error if a pair is only half-configured. No behaviour change when the section is absent.

- [ ] Create `DateFormatConfig` class in `src/main/java/com/clmextract/config/DateFormatConfig.java` with four nullable String fields: `inputFormat`, `outputFormat`, `inputDateTimeFormat`, `outputDateTimeFormat`, and standard getters/setters. **[Agent: java-backend]**
- [ ] Add `private DateFormatConfig dateFormat` (default `null`) to `AppConfig` with getter/setter. **[Agent: java-backend]**
- [ ] In `ConfigLoader.load()`, read the `dateFormat` map using the existing `getMap()` helper; if present, populate a `DateFormatConfig` using the existing `getStringOrDefault()` helper for all four keys and set it on the config. **[Agent: java-backend]**
- [ ] In `ConfigLoader.validate()`, if `dateFormat` is non-null: throw `ConfigValidationException` if `inputFormat` is set without `outputFormat` or vice versa; apply the same check independently for the datetime pair. **[Agent: java-backend]**
- [ ] Write unit tests for the validation logic: partial date pair → exception with descriptive message; partial datetime pair → exception; both pairs absent → no error; both pairs fully present → no error. **[Agent: java-backend]**
- [ ] **Verify:** Run the tool with a `config.yml` that has `dateFormat.inputFormat` set but `dateFormat.outputFormat` absent. Confirm the tool exits before making any API call and the error message identifies the missing field. **[Agent: java-backend]**

---

## Slice 2: End-to-end date and datetime reformatting in CSV output

After this slice: all date and datetime fields are written in the configured output format; values that fail to parse are written as-is with a logged warning.

- [ ] Add `private final String dataType` field to `ResolvedColumn` (with all-args constructor overload and `getDataType()` getter). Update `ColumnResolver.buildColumn()` to pass `FieldMetadata.getDataType()` into the new constructor. Synthesized columns (SFTP filename, additional columns, source-component moves) pass `null`. **[Agent: java-backend]**
- [ ] Create `DateFormatter` in `src/main/java/com/clmextract/csv/DateFormatter.java`: constructed with a nullable `DateFormatConfig`; exposes `String format(String value, String dataType)`; maps `"genericDate"` → date pair and `"modernDate"` → datetime pair; uses `java.time.DateTimeFormatter`; on parse failure logs a `WARN` with the value and expected pattern and returns the original value unchanged. **[Agent: java-backend]**
- [ ] Update `CsvWriterFactory` to accept `DateFormatConfig` and pass a `DateFormatter` instance to `PerComponentCsvWriter` (and `MergedSingleCsvWriter` / `SingleOnlyCsvWriter` if they have their own `resolveValue` logic). **[Agent: java-backend]**
- [ ] Update `BoPipeline` to pass `config.getDateFormat()` to `CsvWriterFactory.create()`. **[Agent: java-backend]**
- [ ] Change `PerComponentCsvWriter.resolveValue()` from `static` to an instance method; after resolving the raw string value call `dateFormatter.format(rawValue, col.getDataType())` before writing to the row. **[Agent: java-backend]**
- [ ] Write unit tests for `DateFormatter`: correct reformat for date-only values; correct reformat for datetime values; returns value as-is when config is `null`; returns value as-is when the relevant pair is not configured; empty string input → returned as-is with warning; non-matching value → returned as-is with warning. **[Agent: java-backend]**
- [ ] **Verify:** Run the tool in offline mode with `dateFormat.inputFormat: "MM/dd/yyyy"` and `dateFormat.outputFormat: "dd/MM/yyyy"` configured. Open a generated CSV and confirm all date-type field values appear in `DD/MM/YYYY` format. Confirm a field with an empty value is written empty (not as an error). **[Agent: java-backend]**
