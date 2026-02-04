# Technical Specification

## Technology Stack

- Java 17
- Maven
- Single runnable shaded JAR

### Libraries
- Jackson (JSON processing)
- OpenCSV (CSV generation)
- java.net.http.HttpClient (HTTP)
- Log4j2 (logging)

### CLI
- Manual args parsing (no CLI framework)

---

## Endpoints File Handling

- The endpoints file `inputs/endpoints.json` is provided by the user.
- The file must be consumed in its original schema.
- Implement an adapter layer that maps the user-provided schema to internal operations:
  - login
  - logout
  - discover BOs
  - fetch metadata
  - fetch tracking numbers
  - bulk / bundles fetch

- If the endpoints schema is ambiguous, allow a minimal mapping section in the main configuration to resolve which endpoint name corresponds to which internal operation.

---

## Credentials

- Credentials are stored as plain text in `config.yml`.
- Environment-variable resolution for secrets is out of scope.

---

## Backups

- Store previous export outputs under `backups/`.
- Enforce retention based on configurable number of days.

---

## Downloads CSV

- For each BO, generate a downloads CSV.
- The filename must be generated using a **dedicated configuration property** `downloadsFilenameTemplate`.
- Default example: `{BO}_AttachmentsToDownload_{DDMMYYYY}_{HHMMSS}.csv`.
- The CSV must contain **only** `Attachments.File Path` values for that BO.

---

## Reliability

- Support configurable batch size for bulk requests.
- Implement a retry policy with increasing delays (e.g., 1s, 2s, 4s, ...) up to a configurable maximum number of attempts.
- Abort the entire run if any BO fails.

---

## Offline Test Mode

- Provide a flag that disables server calls.
- In offline mode, read sample JSON files:
  - `inputs/samples/boMetaData.sample.json`
  - `inputs/samples/bundles.sample.json`

- Outputs must still be generated to validate export logic.
