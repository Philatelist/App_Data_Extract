# Tasks: PDF Conversion Failure Report

Spec: `context/spec/016-pdf-conversion-failure-report/`

---

- [x] **Slice 1: Consolidated PDF failure report with reasons**

  > `PdfConverter` returns a failure reason; `AttachmentDownloader` replaces per-file companion `.txt` files with a single `pdf_conversion_failures.txt` in the output folder.

  - [x] In `PdfConverter`, define a `ConversionResult` record (`boolean success`, `String reason`). Change `boolean convert(Path, Path)` to `ConversionResult convert(Path, Path)`. Return `ConversionResult(true, "")` for the `.pdf` pass-through; return `ConversionResult(false, <specific reason>)` for each failure path — e.g. `"LibreOffice is not installed on this host"`, `"Conversion process timed out after 60 seconds"`, `"Conversion process exited with error code N"`. Update `PdfConverterTest`: change all `convert()` call-site assertions from `boolean` to `.success()`; add one test asserting `.reason()` is non-empty on failure and empty on success. **[Agent: java-backend]**
  - [x] In `AttachmentDownloader`: (1) update the `pdfConverter.convert()` call site to use `result.success()` and `result.reason()`; (2) remove the per-file companion `.txt` writing block entirely; (3) accumulate a `List<ConversionFailure>` (record: `String originalFileName, String savedAsFileName, String reason`) across all records and ZIP entries; (4) add a private `writeFailureReport(Path outputDir, List<ConversionFailure> failures)` method that writes `pdf_conversion_failures.txt` in the format from `technical-considerations.md §2.2`; (5) call it at the end of `downloadAttachments()` only when `!failures.isEmpty()`. Update `AttachmentDownloaderTest`: remove companion-`.txt` assertions; add tests covering single failure (file created, correct content), multiple failures (all in one file), and zero failures (file absent). **[Agent: java-backend]**
  - [x] Verify: `mvn test -Dtest=PdfConverterTest,AttachmentDownloaderTest -q` — all tests pass. **[Agent: java-backend]**

---

- [x] **Slice 2: PACKAGING always runs + remove legacy Signed PDFs step**

  > EXPORT_ATTACHMENTS failures no longer cascade to fail PACKAGING; `STEP_EXPORT_PDF` is removed from the pipeline and disappears from the dashboard.

  - [x] In `RunExecutor`: (1) remove `STEP_EXPORT_PDF` from the `STEPS` list and remove the `updateStep(runId, STEP_EXPORT_PDF, RunStatus.SKIPPED)` call; (2) in the EXPORT_ATTACHMENTS `catch` block, replace the `failRemaining(runId, STEP_EXPORT_ATTACHMENTS)` call with just `updateStep(runId, STEP_EXPORT_ATTACHMENTS, RunStatus.FAILED)` — do not call `failRemaining` — so execution falls through to the PACKAGING step. **[Agent: java-backend]**
  - [x] Update `RunExecutorTest` and `RunExecutorAttachmentScopeTest`: remove any assertion that checks for `STEP_EXPORT_PDF` in the step map; add a test verifying that the PACKAGING step is not FAILED when EXPORT_ATTACHMENTS throws an exception (PACKAGING should proceed to SUCCESS or SKIPPED based on `enableZipPackaging`). **[Agent: java-backend]**
  - [x] Verify: `mvn test -Dtest=RunExecutorTest,RunExecutorAttachmentScopeTest -q` — all tests pass. Start the app with `--serve --config config.yml`, trigger a run, confirm no "Signed PDFs" step appears in the dashboard step list. Delete any screenshots. **[Agent: java-backend]**

---

- [x] **Slice 3: Feature Testing & Regression**

  > Verifies the whole feature end-to-end against functional-spec.md, run after all implementation slices are complete.

  - [x] Read `functional-spec.md` acceptance criteria in full. Generate acceptance-level tests that verify the entire feature as a whole — not individual slices. Cover applicable layers (unit for pure logic, integration for service interactions, e2e for user flows) based on the project's testing stack. Write tests with RED validation (must fail before implementation is confirmed done). Annotate each test with `@spec: 016-pdf-conversion-failure-report` and `@regression` if suitable for long-term regression. **[Agent: general-purpose]**
  - [x] Run all generated tests. All must pass. Fix any failures before proceeding. **[Agent: general-purpose]**
