# Security Guardrails — Audit Results

**Date:** 2026-04-16
**Score:** 0% — Grade **F**

## Results

| #      | Check                                          | Severity | Status | Evidence                                                                                                                      |
| ------ | ---------------------------------------------- | -------- | ------ | ----------------------------------------------------------------------------------------------------------------------------- |
| SEC-01 | .env files are gitignored                      | critical | FAIL   | No `.gitignore` exists at the repo root (only `.idea/.gitignore` for IDE metadata); no `.env` pattern protection in place    |
| SEC-02 | AI agent hooks restrict access to sensitive files | critical | FAIL   | `.claude/settings.json` contains only `extraKnownMarketplaces` — no `hooks` key; `settings.local.json` has permissions but no hooks blocking sensitive file reads |
| SEC-03 | .env.example or template exists                | high     | SKIP   | No `System.getenv`, `@Value`, or `environment.getProperty` usage found in Java source under `src/main/java/com/clmextract`  |
| SEC-04 | No secrets in committed files                  | critical | FAIL   | `config.yml` (git-tracked) contains `password: "Temp12345"` for production server `rhi.selectica.com` (line 6); `config-offline.yml` contains `password: admin` for localhost (low risk but still committed) |
| SEC-05 | Sensitive file types in .gitignore coverage    | high     | FAIL   | No root `.gitignore` exists at all — `target/`, `*.jks`, `*.p12`, `*.pem`, `*.key`, `.DS_Store` are all unprotected; `.DS_Store` is already present as an untracked file |

## Scoring Detail

| Check  | Severity | Status | Deduction |
| ------ | -------- | ------ | --------- |
| SEC-01 | critical | FAIL   | 3.0 pts   |
| SEC-02 | critical | FAIL   | 3.0 pts   |
| SEC-03 | high     | SKIP   | —         |
| SEC-04 | critical | FAIL   | 3.0 pts   |
| SEC-05 | high     | FAIL   | 2.0 pts   |

**Max points (non-SKIP):** 11 (SEC-01: 3 + SEC-02: 3 + SEC-04: 3 + SEC-05: 2)
**Total deductions:** 11
**Score:** (11 − 11) / 11 × 100 = **0%** — Grade **F**

## Key Findings

### Critical: Production credentials committed to git

`config.yml` is tracked by git and contains a plaintext password for the live production CLM server at `rhi.selectica.com`:

```yaml
server:
  url: https://rhi.selectica.com/services/rest/methods
  username: "exportuser@selectica.com"
  password: "Temp12345"
```

This credential should be treated as compromised and rotated immediately. `config.yml` should be added to `.gitignore` and replaced with a `config.example.yml` containing placeholder values.

### Critical: No root .gitignore

The repository has no `.gitignore` at the root. This means build artifacts (`target/`), IDE files (`.DS_Store` is already present untracked), and any future key/certificate files have no protection against accidental commits.

### Critical: No AI agent hooks for sensitive file access

`.claude/settings.json` has no `hooks` configuration. There is no `PreToolUse` hook blocking reads of `config.yml`, `*.yml`, or other files that currently contain secrets. Any Claude Code agent session has unrestricted read access to all project files including the committed credentials.

## Recommended Remediation (Priority Order)

1. **Immediately rotate** the `exportuser@selectica.com` password in the CLM system — it is in git history.
2. **Create a root `.gitignore`** covering at minimum: `config.yml`, `config-offline.yml`, `target/`, `*.jks`, `*.p12`, `*.pfx`, `*.pem`, `*.key`, `.DS_Store`, `inputs.zip`, `output/`.
3. **Create `config.example.yml`** with placeholder values (e.g., `password: "CHANGE_ME"`) so developers know the required structure.
4. **Add hooks to `.claude/settings.json`** blocking `Read`, `Glob`, and `Bash` tool access to `config.yml`, `config-offline.yml`, and any `*.yml` files containing credentials.
5. **Remove `config.yml` and `config-offline.yml` from git tracking:** `git rm --cached config.yml config-offline.yml`.
