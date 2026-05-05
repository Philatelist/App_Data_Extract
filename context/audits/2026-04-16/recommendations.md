# Audit Recommendations — 2026-04-16

## P0 — Fix Immediately

### 1. Rotate the committed production password

- **Dimension:** Security Guardrails
- **Check:** SEC-04
- **Effort:** Low
- **Details:** `config.yml` is tracked in git and contains `password: "Temp12345"` for `exportuser@selectica.com` at `rhi.selectica.com`. This credential is in git history and must be treated as compromised. Rotate the password in the CLM system immediately, then follow steps 2–3 below.

### 2. Create root `.gitignore` and untrack `config.yml`

- **Dimension:** Security Guardrails
- **Check:** SEC-01, SEC-05
- **Effort:** Low
- **Details:** The repository has no root `.gitignore`. Add one covering at minimum:
  ```
  config.yml
  config-offline.yml
  target/
  *.jks
  *.p12
  *.pfx
  *.pem
  *.key
  .DS_Store
  inputs.zip
  output/
  ```
  Then untrack the config files: `git rm --cached config.yml config-offline.yml`. Create `config.example.yml` with placeholder values so developers know the required structure.

### 3. Block AI agent access to credential files via hooks

- **Dimension:** Security Guardrails
- **Check:** SEC-02
- **Effort:** Low
- **Details:** Add a `PreToolUse` hook in `.claude/settings.json` that blocks `Read`, `Glob`, and `Bash` tool calls targeting `config.yml`, `config-offline.yml`, `*.pem`, `*.key`, and similar sensitive files. Example structure:
  ```json
  {
    "hooks": {
      "PreToolUse": [
        {
          "matcher": "Read",
          "hooks": [{ "type": "command", "command": "..." }]
        }
      ]
    }
  }
  ```
  Until hooks are added, any Claude Code agent session has unrestricted read access to the committed credentials.

### 4. Create root `CLAUDE.md`

- **Dimension:** AI Development Tooling
- **Check:** AI-01
- **Effort:** Medium
- **Details:** No CLAUDE.md files exist anywhere in the repo. Without this, AI agents lack context for project purpose, coding conventions, and the CLM API domain model. The file should cover:
  - Project purpose: what CLM Data Extract does, what CLM/Selectica is
  - Key commands: `mvn clean package`, `java -jar target/clm-data-extract-1.0.0.jar --config config.yml`, offline mode flag
  - Package structure and which layer handles what (the 10-package modular layout)
  - Non-obvious domain concepts: InstancePath format, bundle vs. metadata distinction, endpoint YAML schema
  - Conventions: config-driven approach, vertical-slice spec workflow, Java 17 style

---

## P1 — Fix Soon

### 5. Add CI/CD pipeline

- **Dimension:** Software Best Practices
- **Check:** SBP-05
- **Effort:** Low
- **Details:** No CI configuration exists. Add `.github/workflows/ci.yml` with at minimum:
  ```yaml
  on: [push, pull_request]
  jobs:
    build:
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v4
        - uses: actions/setup-java@v4
          with: { java-version: '17', distribution: 'temurin' }
        - run: mvn verify
  ```
  This ensures tests, linting (once added), and formatting are enforced on every commit.

### 6. Add linting and formatting to the Maven build

- **Dimension:** Software Best Practices
- **Check:** SBP-01, SBP-02
- **Effort:** Low
- **Details:** Add to `pom.xml`:
  - `maven-checkstyle-plugin` with Google or Sun checks, bound to `verify`
  - `spotless-maven-plugin` with `google-java-format`, bound to `verify`
  
  This gives the CI pipeline (step 5) code quality gates on every push.

### 7. Create `config.example.yml` with placeholders

- **Dimension:** Security Guardrails
- **Check:** SEC-04
- **Effort:** Low
- **Details:** After removing `config.yml` from git tracking (step 2), create `config.example.yml` with placeholder values:
  ```yaml
  server:
    url: "https://your-clm-server/services/rest/methods"
    username: "your-username@example.com"
    password: "CHANGE_ME"
  ```
  Document in the README that developers must copy this file to `config.yml` and fill in their credentials.

---

## P2 — Improve When Possible

### 8. Add Dependabot for Maven dependency updates

- **Dimension:** Software Best Practices
- **Check:** SBP-07
- **Effort:** Low
- **Details:** Add `.github/dependabot.yml`:
  ```yaml
  version: 2
  updates:
    - package-ecosystem: maven
      directory: "/"
      schedule:
        interval: weekly
  ```
  This surfaces outdated dependencies as PRs automatically.

### 9. Retrofit `java-backend` agent assignments to earlier specs

- **Dimension:** Spec-Driven Development
- **Check:** SDD-07
- **Effort:** Medium
- **Details:** 89% of agent-annotated sub-tasks use `general-purpose` even for Java implementation work. Only spec 010 uses `java-backend`. Update `context/spec/002–009/tasks.md` files to replace `general-purpose` with `java-backend` for Java coding sub-tasks. This improves agent routing quality for any future re-implementation of those features.

### 10. Refactor `MetadataParser` to separate fetch from parse

- **Dimension:** Code Architecture
- **Check:** ARCH-04
- **Effort:** Medium
- **Details:** `MetadataParser.fetch()` performs the HTTP call itself, blending data-access with domain parsing. Extract the HTTP call into the `http/` layer and have `MetadataParser` receive raw response data instead. Similarly, `BoPipeline.execute()` mixes component-skip business rules with orchestration — extract the filtering logic into a dedicated strategy or configuration object.
