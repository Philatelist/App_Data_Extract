# Software Best Practices — Audit Results

**Date:** 2026-04-16
**Score:** 58% — Grade **D**

## Results

| #   | Check | Severity | Status | Evidence |
| --- | ----- | -------- | ------ | -------- |
| 1   | SBP-01: Linting is configured and enforced | high | FAIL | `pom.xml` contains no Checkstyle, SpotBugs, or PMD plugin; only `maven-compiler-plugin`, `maven-shade-plugin`, and `maven-surefire-plugin` are configured |
| 2   | SBP-02: Formatting is automated | medium | FAIL | `pom.xml` contains no Spotless or google-java-format plugin; no pre-commit hooks found |
| 3   | SBP-03: Type safety is enforced | high | PASS | Java 17 inherently strongly typed; 7 `@SuppressWarnings("unchecked")` annotations across 3 files (`EndpointRegistry.java` ×2, `ReportFetcher.java` ×1, `ConfigLoader.java` ×4), all justified by YAML/JSON unchecked casts from `SnakeYAML`/`Jackson` raw maps — no raw `Object` fields or excessive suppression |
| 4   | SBP-04: Test infrastructure exists | critical | PASS | 18 `*Test.java` files under `src/test/java/com/clmextract/`; JUnit Jupiter 5.10.3 declared in `pom.xml`; `maven-surefire-plugin` 3.3.1 configured |
| 5   | SBP-05: CI/CD pipeline exists | high | FAIL | No `.github/workflows/`, `.gitlab-ci.yml`, `Jenkinsfile`, or equivalent CI configuration found anywhere in the repository |
| 6   | SBP-06: Error handling patterns are consistent | high | PASS | Sampled 30+ catch blocks across `App.java`, `RequestExecutor.java`, `RetryPolicy.java`, `BackupManager.java`, `ExportOrchestrator.java`, `BoPipeline.java`, `ManifestCsvWriter.java`, `SessionManager.java`: all errors are either logged (logger.warn/error) or re-thrown/wrapped as RuntimeException; one intentionally silent `NumberFormatException` in `BundlesMapper.java:178` has comment "leave as 0"; `Thread.currentThread().interrupt()` correctly called on `InterruptedException` in multiple places |
| 7   | SBP-07: Dependencies are managed | medium | WARN | All dependency versions explicitly pinned in `pom.xml` (Maven convention — no separate lock file); no Renovate, Dependabot, or equivalent automated update configuration found |

## Scoring Detail

| Metric | Value |
| ------ | ----- |
| max_points | 13 (critical=3, high×4=8, medium×2=2) |
| Deductions | SBP-01 high FAIL: −2; SBP-02 medium FAIL: −1; SBP-05 high FAIL: −2; SBP-07 medium WARN: −0.5 |
| Total deductions | 5.5 |
| Final score | (13 − 5.5) / 13 × 100 = **57.7%** |
| Grade | **D** (40–59) |

## Recommendations

1. **SBP-01 / SBP-02 (highest priority):** Add `maven-checkstyle-plugin` with a ruleset (Google or Sun checks) and `spotless-maven-plugin` with `google-java-format` to `pom.xml`. Bind both to the `verify` lifecycle phase so they run on every build.
2. **SBP-05:** Add a GitHub Actions workflow (`.github/workflows/ci.yml`) with at minimum a `mvn verify` step to run compile + tests on every push and pull request.
3. **SBP-07:** Add a Dependabot configuration (`.github/dependabot.yml`) with `package-ecosystem: maven` to receive automated PR notifications when dependency versions fall behind.
