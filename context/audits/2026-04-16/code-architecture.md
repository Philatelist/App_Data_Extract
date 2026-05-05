# Code Architecture — Audit Results

**Date:** 2026-04-16
**Score:** 89% — Grade **B**

## Results

| #   | Check | Severity | Status | Evidence |
| --- | ----- | -------- | ------ | -------- |
| ARCH-01 | Declared or recognizable architectural pattern | high | PASS | Modular layered pattern clearly recognizable: `config/`, `http/`, `session/`, `endpoint/`, `metadata/`, `api/`, `export/`, `csv/`, `backup/`, `logging/`; README describes the tool but does not formally name the pattern; structure maps to a pipeline/layered CLI pattern with clear separation by technical concern |
| ARCH-02 | Module boundaries are respected | high | PASS | Import direction is consistently top-down: `App` → `export` → `config`, `http`, `session`, `metadata`, `csv`; `csv` imports `metadata` and `config` but not `export`; `http` imports only `config` and `endpoint`; no circular dependencies found in 10-file sample |
| ARCH-03 | Single Responsibility Principle in modules | medium | PASS | All 10 packages have focused names and consistent contents; no god modules — largest is `export/` with 17 files (all export-pipeline concerns) and `csv/` with 13 files (all CSV writers/resolvers); `api/` sub-packages (`dto/`, `mapper/`) decompose cleanly |
| ARCH-04 | Separation of concerns across layers | high | WARN | Mostly clean; `metadata/MetadataParser.fetch()` performs the HTTP call itself (blending data-access with domain parsing); `export/BoPipeline.execute()` mixes orchestration logic with component-skip business rule filtering and direct `Path` construction; 2 files show notable concern mixing |
| ARCH-05 | Consistent file and directory naming conventions | medium | PASS | All 57 Java files use PascalCase; all package directories use lowercase (no deviations); fully consistent |
| ARCH-06 | Reasonable file sizes | medium | PASS | 0 of 55 Java source files exceed 500 lines (0%); largest file is `ColumnResolver.java` at 361 lines, `ConfigLoader.java` at 332 lines |

## Summary

The codebase follows a clean modular layered architecture appropriate for a single-service Java CLI ETL tool. The 10 packages map precisely to technical layers: entry point, configuration loading, HTTP infrastructure, session management, endpoint registry, metadata parsing, API DTOs/mappers, export orchestration, CSV writing, and backup. Import direction is consistently inward (higher-level packages depend on lower-level ones) with no circular dependencies detected in the sampled files.

The single WARN on ARCH-04 is a minor concern: `MetadataParser.fetch()` performs the HTTP call directly rather than receiving raw data (blending data-access with parsing), and `BoPipeline.execute()` embeds component-filtering business rules alongside orchestration flow. These are pragmatic decisions acceptable in a small CLI tool, but they slightly blur the separation between infrastructure and domain.

## Scoring Calculation

| Check | Severity | Status | Deduction |
|-------|----------|--------|-----------|
| ARCH-01 | high | PASS | 0 |
| ARCH-02 | high | PASS | 0 |
| ARCH-03 | medium | PASS | 0 |
| ARCH-04 | high | WARN | 1 pt |
| ARCH-05 | medium | PASS | 0 |
| ARCH-06 | medium | PASS | 0 |

- Max points: high(2)+high(2)+medium(1)+high(2)+medium(1)+medium(1) = **9**
- Total deductions: **1** (ARCH-04 WARN at high severity)
- Score: (9 − 1) / 9 × 100 = **88.9% → 89% → Grade B**
