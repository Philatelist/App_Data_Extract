# AI Development Tooling — Audit Results

**Date:** 2026-04-16
**Score:** 53% — Grade **D**

## Results

| #     | Check                                              | Severity | Status | Evidence                                                                                                                                                             |
| ----- | -------------------------------------------------- | -------- | ------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| AI-01 | CLAUDE.md ecosystem provides adequate AI context  | critical | FAIL   | No CLAUDE.md files exist anywhere in the repo (root, service-level, or `.claude/rules/`)                                                                            |
| AI-02 | Custom slash commands exist                        | medium   | PASS   | 9 custom commands found under `.claude/commands/awos/`: architecture, implement, product, roadmap, spec, tasks, tech, verify, hire                                  |
| AI-03 | Skills are configured                              | low      | FAIL   | No `.claude/skills/` directory exists; no SKILL.md files found                                                                                                      |
| AI-04 | MCP servers configured                             | low      | PASS   | `.mcp.json` defines 1 server: `awos-recruitment` (HTTP at `https://recruitment.awos.provectus.pro/mcp`)                                                             |
| AI-05 | Hooks are configured                               | low      | FAIL   | `.claude/settings.json` contains only `extraKnownMarketplaces`; `.claude/settings.local.json` contains only `permissions` and MCP enable flags — no hooks defined  |
| AI-06 | CLAUDE.md files are meaningful and well-structured | high     | SKIP   | No CLAUDE.md files exist in the repo                                                                                                                                 |
| AI-07 | Agent can run and observe the application          | critical | PASS   | README.md fully documents build (`mvn clean package`) and run (`java -jar target/clm-data-extract-1.0.0.jar --config config.yml`) commands; offline mode documented |

## Scoring Detail

| Item           | Severity | Deduction |
| -------------- | -------- | --------- |
| AI-01 FAIL     | critical | -3.0 pts  |
| AI-03 FAIL     | low      | -0.5 pts  |
| AI-05 FAIL     | low      | -0.5 pts  |
| **Total deducted** |       | **-4.0 pts** |

Max points (non-SKIP checks): critical×2 = 6, medium×1 = 1, low×3 = 1.5 → **8.5 pts**
Score = (8.5 - 4.0) / 8.5 × 100 = **52.9% → Grade D**

## Summary

The project has a solid foundation for AI-assisted development through its custom slash commands (9 awos workflow commands) and MCP server integration, but is missing the most critical element: there are no CLAUDE.md files anywhere. Without CLAUDE.md, AI agents lack documented project purpose, coding conventions, non-obvious constraints, and architectural context that cannot be reliably inferred from source code alone. The `.claude/agents/java-backend.md` agent provides some context for Java implementation tasks, but it references conventions without capturing them. Hooks and skills are also absent, leaving automated guardrails and specialized workflow support unconfigured.

### Top Priorities

1. **Create a root `CLAUDE.md`** covering project purpose, key commands (build, test, run offline), coding conventions (package structure, config patterns, vertical-slice principle), and the CLM API domain model (metadata/bundles response shapes, InstancePath format).
2. **Add hooks** in `.claude/settings.json` for pre/post-tool guardrails (e.g., preventing accidental writes to `output/` or `config.yml`).
3. **Configure at least one skill** to capture specialized domain knowledge (e.g., a skill for the CLM API data pipeline).
