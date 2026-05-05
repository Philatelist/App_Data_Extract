# Code Audit Report

**Date:** 2026-04-16
**Scope:** all dimensions
**Overall Score:** 74% — Grade **C**
**Previous Audit:** none

---

## Summary

| #   | Dimension               | Score | Grade | Delta | Critical | High | Medium | Low |
| --- | ----------------------- | ----- | ----- | ----- | -------- | ---- | ------ | --- |
| 1   | Project Topology        | 100%  | A     | —     | 0        | 0    | 0      | 0   |
| 2   | Documentation           | 100%  | A     | —     | 0        | 0    | 0      | 0   |
| 3   | End-to-End Delivery     | 100%  | A     | —     | 0        | 0    | 0      | 0   |
| 4   | Spec-Driven Development | 93%   | A     | —     | 0        | 0    | 1      | 0   |
| 5   | Code Architecture       | 89%   | B     | —     | 0        | 1    | 0      | 0   |
| 6   | Software Best Practices | 58%   | D     | —     | 0        | 2    | 2      | 0   |
| 7   | AI Development Tooling  | 53%   | D     | —     | 1        | 0    | 0      | 2   |
| 8   | Security Guardrails     | 0%    | F     | —     | 3        | 1    | 0      | 0   |

> Column counts reflect number of FAIL or WARN findings per severity level.

---

## Dimension: Project Topology

**Score:** 100% — Grade **A**

| #   | Check                              | Severity | Status | Evidence                                                           |
| --- | ---------------------------------- | -------- | ------ | ------------------------------------------------------------------ |
| 1   | TOPO-01: Repository structure type | medium   | PASS   | Single `pom.xml` at root → single-service CLI repo                |
| 2   | TOPO-02: Application layer         | medium   | PASS   | Data/ETL CLI: Java 17 + Maven at `/`                              |
| 3   | TOPO-03: Database/storage          | medium   | SKIP   | No ORM, no migrations, no docker-compose, no DB client            |
| 4   | TOPO-04: Infrastructure            | medium   | SKIP   | No Dockerfile, Terraform, Kubernetes, or serverless configs       |
| 5   | TOPO-05: Language inventory        | medium   | PASS   | Java (79 files), XML (2 files), YAML (3 files)                    |
| 6   | TOPO-06: Inter-layer communication | medium   | SKIP   | Single-layer; outbound REST only via `inputs/endpoints.yml`       |

---

## Dimension: Documentation Quality

**Score:** 100% — Grade **A**

| #   | Check                             | Severity | Status | Evidence                                                                                               |
| --- | --------------------------------- | -------- | ------ | ------------------------------------------------------------------------------------------------------ |
| 1   | DOC-01: Root README               | critical | PASS   | 152-line README with name, description, prerequisites, build, run, config table, exit codes           |
| 2   | DOC-02: Service-level READMEs     | high     | PASS   | Single-service; root README covers the service fully                                                  |
| 3   | DOC-03: API documentation         | high     | SKIP   | No inbound API; outbound endpoints catalogued in `inputs/endpoints.yml`                               |
| 4   | DOC-04: No stale documentation    | medium   | PASS   | All 5 sampled claims verified accurate (JAR path, `--config` flag, offline samples, config dirs)      |

---

## Dimension: End-to-End Delivery

**Score:** 100% — Grade **A**

| #   | Check                            | Severity | Status | Evidence                                                                                        |
| --- | -------------------------------- | -------- | ------ | ----------------------------------------------------------------------------------------------- |
| 1   | E2E-01: Cross-layer branches     | high     | SKIP   | Single-service repo — not applicable                                                           |
| 2   | E2E-02: No layer-split branches  | medium   | SKIP   | Single-service repo — not applicable                                                           |
| 3   | E2E-03: Spec-to-delivery tracing | high     | PASS   | Bidirectional: 3 sampled branches simultaneously update `context/spec/*/tasks.md` and Java code |
| 4   | E2E-04: No orphaned artifacts    | medium   | SKIP   | Single-layer — not applicable                                                                  |
| 5   | E2E-05: Shared ownership enablers| medium   | SKIP   | Single-service repo — not applicable                                                           |

---

## Dimension: Spec-Driven Development

**Score:** 93% — Grade **A**

| #      | Check                                      | Severity | Status | Evidence                                                                                            |
| ------ | ------------------------------------------ | -------- | ------ | --------------------------------------------------------------------------------------------------- |
| SDD-01 | AWOS is installed and set up               | critical | PASS   | 9 commands in `.awos/commands/`; 9 wrappers in `.claude/commands/awos/`; `context/product/` + `context/spec/` exist |
| SDD-02 | Product context documents are complete     | high     | PASS   | All 3 docs present: product-definition (vision + personas), roadmap (5 phases), architecture (11 modules) |
| SDD-03 | Architecture reflects codebase             | high     | PASS   | Java 17, Maven, SnakeYAML, Jackson, OpenCSV, Log4j2, java.net.http all confirmed in `pom.xml`      |
| SDD-04 | Features implemented through specs         | critical | PASS   | 6/6 recent feature branches (005–010) touched `context/spec/` — 100% spec-to-branch ratio         |
| SDD-05 | Spec directories structurally complete     | high     | PASS   | 9/10 spec dirs have full triad; `009-yes-no-translation` is partial (tasks.md only)                |
| SDD-06 | No stale or abandoned specs                | medium   | PASS   | All non-draft specs (002 In Review, 007–008 Approved) have fully completed task lists              |
| SDD-07 | Tasks have meaningful agent assignments    | medium   | WARN   | 89% of sub-tasks annotated, but 89% of those use `general-purpose` for Java implementation tasks; only spec 010 uses `java-backend` |

---

## Dimension: Code Architecture

**Score:** 89% — Grade **B**

| #       | Check                                    | Severity | Status | Evidence                                                                                    |
| ------- | ---------------------------------------- | -------- | ------ | ------------------------------------------------------------------------------------------- |
| ARCH-01 | Recognizable architectural pattern       | high     | PASS   | 10 clearly-named packages form a coherent modular layered CLI pattern                      |
| ARCH-02 | Module boundaries respected              | high     | PASS   | Consistently top-down imports; no circular dependencies in 10-file sample                  |
| ARCH-03 | SRP in modules                           | medium   | PASS   | No god modules; `export/` (17 files) and `csv/` (13 files) are focused                    |
| ARCH-04 | Separation of concerns                   | high     | WARN   | `MetadataParser.fetch()` blends HTTP I/O with domain parsing; `BoPipeline.execute()` mixes orchestration with business rule filtering |
| ARCH-05 | Consistent naming conventions            | medium   | PASS   | All 57 Java files PascalCase; all directories lowercase — zero deviations                  |
| ARCH-06 | Reasonable file sizes                    | medium   | PASS   | 0% of files exceed 500 lines; largest is `ColumnResolver.java` at 361 lines               |

---

## Dimension: Software Best Practices

**Score:** 58% — Grade **D**

| #      | Check                          | Severity | Status | Evidence                                                                                          |
| ------ | ------------------------------ | -------- | ------ | ------------------------------------------------------------------------------------------------- |
| SBP-01 | Linting configured             | high     | FAIL   | No Checkstyle, SpotBugs, or PMD plugin in `pom.xml`                                             |
| SBP-02 | Formatting automated           | medium   | FAIL   | No Spotless or google-java-format plugin; no pre-commit hooks                                   |
| SBP-03 | Type safety enforced           | high     | PASS   | Java 17 strongly typed; 7 `@SuppressWarnings("unchecked")` all justified by SnakeYAML/Jackson raw casts |
| SBP-04 | Test infrastructure exists     | critical | PASS   | 18 `*Test.java` files; JUnit Jupiter 5.10.3 + maven-surefire 3.3.1 configured                  |
| SBP-05 | CI/CD pipeline exists          | high     | FAIL   | No `.github/workflows/`, `.gitlab-ci.yml`, `Jenkinsfile`, or equivalent CI config               |
| SBP-06 | Error handling consistent      | high     | PASS   | All sampled catch blocks log or re-throw; `interrupt()` correctly called; one silent swallow with comment |
| SBP-07 | Dependencies managed           | medium   | WARN   | Versions pinned in `pom.xml`; no Renovate/Dependabot configured                                |

---

## Dimension: AI Development Tooling

**Score:** 53% — Grade **D**

| #     | Check                                   | Severity | Status | Evidence                                                                                             |
| ----- | --------------------------------------- | -------- | ------ | ---------------------------------------------------------------------------------------------------- |
| AI-01 | CLAUDE.md ecosystem                     | critical | FAIL   | No CLAUDE.md files anywhere in the repo                                                             |
| AI-02 | Custom slash commands exist             | medium   | PASS   | 9 custom commands under `.claude/commands/awos/`                                                   |
| AI-03 | Skills configured                       | low      | FAIL   | No `.claude/skills/` directory or SKILL.md files                                                   |
| AI-04 | MCP servers configured                  | low      | PASS   | `.mcp.json` defines `awos-recruitment` MCP server                                                  |
| AI-05 | Hooks configured                        | low      | FAIL   | Neither settings file contains hooks configuration                                                  |
| AI-06 | CLAUDE.md files meaningful              | high     | SKIP   | No CLAUDE.md files to evaluate                                                                     |
| AI-07 | Agent can run and observe app           | critical | PASS   | README fully documents `mvn clean package` + `java -jar` run; offline mode documented              |

---

## Dimension: Security Guardrails

**Score:** 0% — Grade **F**

| #      | Check                                      | Severity | Status | Evidence                                                                                                          |
| ------ | ------------------------------------------ | -------- | ------ | ----------------------------------------------------------------------------------------------------------------- |
| SEC-01 | .env files gitignored                      | critical | FAIL   | No root `.gitignore` exists — no protection against committing secrets                                           |
| SEC-02 | AI hooks restrict sensitive file access    | critical | FAIL   | `.claude/settings.json` has no `hooks` key; agents can read all files including committed credentials            |
| SEC-03 | .env.example or template exists            | high     | SKIP   | No environment variable usage detected in Java source                                                            |
| SEC-04 | No secrets in committed files              | critical | FAIL   | `config.yml` (git-tracked) contains plaintext password `"Temp12345"` for `exportuser@selectica.com` at `rhi.selectica.com` |
| SEC-05 | Sensitive files in .gitignore coverage     | high     | FAIL   | No root `.gitignore` — `target/`, `*.jks`, `*.p12`, `*.pem`, `.DS_Store` all unprotected                       |

---

## Top Recommendations

| #   | Priority | Effort | Dimension               | Recommendation                                                              |
| --- | -------- | ------ | ----------------------- | --------------------------------------------------------------------------- |
| 1   | P0       | Low    | Security                | Rotate the `exportuser@selectica.com` password — treat it as compromised (in git history) |
| 2   | P0       | Low    | Security                | Create root `.gitignore` and `git rm --cached config.yml` to stop tracking credentials |
| 3   | P0       | Low    | Security                | Add `PreToolUse` hooks in `.claude/settings.json` blocking reads of `config.yml` and credential files |
| 4   | P0       | Medium | AI Development Tooling  | Create root `CLAUDE.md` covering project purpose, commands, domain model, and conventions |
| 5   | P1       | Low    | Software Best Practices | Add GitHub Actions CI workflow (`.github/workflows/ci.yml`) running `mvn verify` on push |
| 6   | P1       | Low    | Software Best Practices | Add `maven-checkstyle-plugin` + `spotless-maven-plugin` to `pom.xml` bound to `verify` phase |
| 7   | P1       | Low    | Security                | Create `config.example.yml` with placeholder credentials so developers know required structure |
| 8   | P2       | Low    | Software Best Practices | Add `.github/dependabot.yml` with `package-ecosystem: maven` for automated dependency updates |
| 9   | P2       | Medium | Spec-Driven Development | Retrofit `java-backend` agent assignments to earlier specs (002–009) — reduces `general-purpose` overuse |
| 10  | P2       | Medium | Code Architecture       | Refactor `MetadataParser.fetch()` to separate HTTP fetch from domain parsing                |
