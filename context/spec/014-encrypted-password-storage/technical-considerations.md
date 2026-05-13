# Technical Specification: Encrypted Password Storage

- **Functional Specification:** [014-encrypted-password-storage/functional-spec.md](functional-spec.md)
- **Status:** Approved
- **Author(s):** Alex

---

## 1. High-Level Technical Approach

Four distinct changes across the backend and frontend, with **zero new Maven dependencies** — all cryptography uses Java 17's built-in `javax.crypto`:

1. **`CredentialEncryptor`** — new utility class: AES-256-GCM encrypt/decrypt, `ENC(...)` detection, master key derivation from `CLM_EXTRACT_KEY` env var.
2. **`ConfigLoader.load()`** — decrypt both passwords after parsing; fail fast if encrypted values present but no key set.
3. **`ConfigController`** — `getConfig()` never sends password values to the browser; `putConfig()` encrypts on new value, preserves-on-blank.
4. **`admin.js`** — password fields show "Password is set" placeholder on load; send value only when admin types a new one.

---

## 2. Proposed Solution & Implementation Plan

### 2.1 `CredentialEncryptor` — new class

**Location:** `com.clmextract.config.CredentialEncryptor`

**Responsibilities:**

| Method | Signature | Purpose |
|---|---|---|
| `isEncrypted` | `static boolean (String value)` | Returns `true` if value starts with `ENC(` and ends with `)` |
| `encrypt` | `static String (String plaintext, String masterKey)` | Encrypts using AES-256-GCM; returns `ENC(base64encoded)` |
| `decrypt` | `static String (String encryptedValue, String masterKey)` | Decrypts an `ENC(...)` value; throws `ConfigValidationException` on failure |
| `getMasterKey` | `static Optional<String> ()` | Reads `CLM_EXTRACT_KEY` from `System.getenv()` |

**Encryption scheme:**
- Algorithm: `AES/GCM/NoPadding` (authenticated encryption, no separate MAC needed)
- Key derivation: `SHA-256(masterKey.getBytes(UTF_8))` → 32-byte AES-256 key
- IV: random 12 bytes generated per `encrypt()` call; prepended to the ciphertext in the base64 blob
- Token format: `ENC(Base64(IV_12 + ciphertext + GCM_tag_16))`

**No new Maven dependencies** — `javax.crypto.Cipher`, `javax.crypto.spec.SecretKeySpec`, `javax.crypto.spec.GCMParameterSpec`, and `java.security.MessageDigest` are all standard Java 17.

---

### 2.2 `ConfigLoader` changes

**`load(String configPath)` (used by CLI):**

After `parse()`, before `validate()`:
1. Call `CredentialEncryptor.getMasterKey()`.
2. For each of `server.password` and `sftp.password`:
   - If value is `ENC(...)` and master key present → decrypt and replace in `AppConfig`.
   - If value is `ENC(...)` and master key absent → throw `ConfigValidationException("Encrypted credentials found but CLM_EXTRACT_KEY is not set")`.
   - If value is plaintext and master key present → log WARN: `"server.password is stored in plaintext — save your configuration via the Admin Panel to encrypt it."`.
   - If value is plaintext and master key absent → no-op (backward compat).
3. `validate()` then runs against the already-decrypted `AppConfig` — no changes to `validate()` needed.

**`loadRaw(String configPath)` (used by web controllers):**
- No change. Returns raw values including `ENC(...)` tokens. Web controllers handle the token format explicitly (see §2.3).

---

### 2.3 `ConfigController` changes

**`getConfig()`:**

Replace the current verbatim password serialisation with:

| JSON key | Value sent |
|---|---|
| `serverPassword` | always `""` (empty string) |
| `serverPasswordIsSet` | `true` if stored value is non-blank (either plaintext or `ENC(...)`) |
| `sftp.password` | always `""` |
| `sftp.passwordIsSet` | `true` if stored value is non-blank |

The plaintext or encrypted token is never transmitted to the browser.

**`putConfig()`:**

For `serverPassword` and `sftp.password` in the request body:

1. **If the incoming value is non-blank** (admin typed a new password):
   - If `CLM_EXTRACT_KEY` is set: call `CredentialEncryptor.encrypt(newValue, masterKey)` and store the resulting `ENC(...)` token.
   - If no key: log WARN and store plaintext.
2. **If the incoming value is blank or absent:** load the existing stored value (via `ConfigLoader.loadRaw()`) and write it back unchanged — preserve whatever is currently on disk.

The YAML write step uses the already-resolved value from the logic above.

---

### 2.4 `admin.js` / `admin.html` changes

**`populateForm(config)` in `admin.js`:**
- `serverPassword` field: if `config.serverPasswordIsSet == true`, set `placeholder="Password is set (leave blank to keep)"` and leave the input value empty. If false, show placeholder `"Enter password"`.
- `sftp.password` field: same pattern using `config.sftp.passwordIsSet`.
- Do not populate password fields with the value from the server response — it is always `""`.

**`collectConfig()` in `admin.js`:**
- `serverPassword`: send the field's current value only if non-blank; otherwise omit the key or send `""` — both cause the server to preserve the existing value.
- `sftp.password`: same.

---

### 2.5 Startup check — serve mode

In `WebServer.start()`, after loading the initial config via `ConfigLoader.loadRaw()`, add an explicit check: if any password field is `ENC(...)` and `CredentialEncryptor.getMasterKey()` is empty, log an ERROR and throw to prevent the server from starting.

This mirrors the fail-fast behaviour already specified for CLI mode in §2.2.

---

## 3. Impact and Risk Analysis

**System dependencies:**

- `ConfigLoader.load()` — the single authoritative decryption point for CLI and export-pipeline use. All callers of `load()` already receive a fully resolved `AppConfig` with plaintext passwords.
- `ConfigLoader.loadRaw()` — used by `ConfigController`, `AdminController`, and `RunExecutor` (for live config reload). `AdminController` calls inject the admin's own CLM session and do not use `config.getPassword()` for authentication — safe. `RunExecutor` in web mode reuses the operator's CLM session via `injectSessionId()` (spec 012) — also safe. Any future caller that passes `loadRaw()` output to CLM authentication code must be updated to use `load()` instead.

**Potential risks & mitigations:**

| Risk | Mitigation |
|---|---|
| SnakeYAML serialises `ENC(...)` token with unexpected quoting | `SnakeYAML.dump()` handles special characters automatically; integration test will verify round-trip |
| Admin loses access if `CLM_EXTRACT_KEY` env var is removed after encryption | Documented requirement: key must remain set for the application to start when encrypted values are present; fail-fast error message names the variable explicitly |
| AES-GCM IV reuse with the same key (catastrophic for GCM) | IV is 12 random bytes generated via `SecureRandom` per `encrypt()` call; collision probability is negligible for the low call frequency of config saves |
| Plaintext key in env var visible to process list inspection | Acceptable for this deployment model (single-machine JAR); out of scope per functional spec (no HSM/KMS) |
| `CLM_EXTRACT_KEY` not set in CI/test environments causing test failures | Unit tests pass the key directly to `CredentialEncryptor` methods — no env var dependency in tests |

---

## 4. Testing Strategy

- **`CredentialEncryptor`** — unit tests: encrypt → decrypt round-trip; `isEncrypted()` for both valid and invalid inputs; `decrypt()` throws on tampered ciphertext (GCM authentication failure); `getMasterKey()` returns empty when env var absent.
- **`ConfigLoader.load()`** — unit tests: load a config with `ENC(...)` password + key set → resolves to plaintext; load with `ENC(...)` + no key → throws `ConfigValidationException`; load with plaintext + key set → logs warning and proceeds; load with plaintext + no key → proceeds normally.
- **`ConfigController.getConfig()`** — unit test: response never contains plaintext or encrypted token in `serverPassword`; `serverPasswordIsSet` is `true` when config has a non-blank value.
- **`ConfigController.putConfig()`** — unit tests: saving a non-blank password with key set writes `ENC(...)` to YAML; saving a blank password preserves the existing stored value; saving a non-blank password with no key writes plaintext and logs warning.
- **`admin.js`** — browser test: password field is empty on load when `serverPasswordIsSet == true`; placeholder text is shown; submitting with empty field does not overwrite the stored password.
