# Project Topology — Audit Results

**Date:** 2026-04-16
**Score:** 100% — Grade **A**

## Results

| #   | Check | Severity | Status | Evidence |
| --- | ----- | -------- | ------ | -------- |
| 1   | TOPO-01: Repository structure type | medium | PASS | Single `pom.xml` at repo root; one build root → single-service repo (CLI tool packaged as fat JAR via maven-shade-plugin, main class `com.clmextract.App`) |
| 2   | TOPO-02: Application layer inventory | medium | PASS | One layer: Data/ETL CLI — plain Java 17 + Maven, no framework; root path `/` (src at `src/main/java/com/clmextract`); primary language: Java |
| 3   | TOPO-03: Database and storage detection | medium | SKIP | No migration directories, no ORM annotations/configs, no docker-compose, no JDBC/JPA/Redis/Mongo references found in source |
| 4   | TOPO-04: Infrastructure layer detection | medium | SKIP | No Dockerfile, docker-compose, Terraform, Kubernetes manifests, Helm, Serverless, CDK, or CloudFormation files found |
| 5   | TOPO-05: Language inventory | medium | PASS | Java: 79 files (61 main + 18 test); XML: 2 files (log4j2 configs); YAML: 3 files (config.yml, config-offline.yml, inputs/endpoints.yml); no other language files detected |
| 6   | TOPO-06: Inter-layer communication patterns | medium | SKIP | Single-layer project; no `.proto`, `.graphql`, OpenAPI/Swagger specs, or message-queue configs found; HTTP calls are outbound-only (REST client consuming an external CLM API defined in `inputs/endpoints.yml`) |

## Scoring

- Non-SKIP checks: TOPO-01 (medium=1pt), TOPO-02 (medium=1pt), TOPO-05 (medium=1pt)
- max_points = 3; deductions = 0
- Score = 3/3 = **100% — Grade A**

## Topology Summary

- **Structure:** single-service
- **Layers:**
  - Data/ETL CLI: plain Java 17 + Maven (no framework) at `/` (primary language: Java)
- **Storage:** not detected
- **Infrastructure:** not detected
- **Languages:** Java (79 files), XML (2 files), YAML (3 files)
- **Communication:** not detected (single-layer; outbound REST calls to external CLM API configured via `inputs/endpoints.yml`)
- **Service directories:** `src/main/java/com/clmextract`
