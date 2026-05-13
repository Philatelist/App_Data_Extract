# Functional Specification: Encrypted Password Storage

- **Roadmap Item:** Credential Security — encrypt passwords stored in `config.yml`
- **Status:** Approved
- **Author:** Alex

---

## 1. Overview and Rationale (The "Why")

Today, `config.yml` stores the CLM server password and the SFTP upload password in plaintext. Any person with read access to the file — whether on disk, in version control, or in a backup — can see credentials directly.

This feature encrypts those passwords before writing them to disk. The encrypted values are stored using an `ENC(...)` prefix. A master encryption key, supplied via an environment variable (`CLM_EXTRACT_KEY`), is the only thing that can decrypt them. The key never touches the file.

**Outcome:** `config.yml` can be shared, committed, or backed up without exposing credentials. Only the system running the application (with the correct environment variable set) can use the passwords.

---

## 2. Functional Requirements (The "What")

### 2.1 — Which passwords are encrypted

Both `server.password` (CLM REST API) and `sftp.password` (SFTP upload) are encrypted at rest.

**Acceptance Criteria:**
- [ ] After a save, both password fields in `config.yml` contain `ENC(...)` values, not plaintext.
- [ ] No other config fields are encrypted.

---

### 2.2 — Encryption master key via environment variable

The master key is supplied as `CLM_EXTRACT_KEY` in the environment. It is never written to `config.yml` or any other application file.

**Acceptance Criteria:**
- [ ] If `CLM_EXTRACT_KEY` is set, the application decrypts `ENC(...)` values at startup.
- [ ] If `CLM_EXTRACT_KEY` is not set and the config contains at least one `ENC(...)` value, the application logs a clear error ("Encrypted credentials found but CLM_EXTRACT_KEY is not set") and refuses to start.
- [ ] If `CLM_EXTRACT_KEY` is not set and no `ENC(...)` values exist, the application starts normally (plaintext-only mode — for first-time setup before a key is configured).

---

### 2.3 — Backwards compatibility with plaintext passwords

Existing `config.yml` files that contain a plaintext password continue to work without any migration step.

**Acceptance Criteria:**
- [ ] A plaintext password (no `ENC(...)` prefix) is used as-is.
- [ ] The application logs a warning when it detects a plaintext password: "server.password is stored in plaintext — save your configuration via the Admin Panel to encrypt it."
- [ ] The warning does not prevent the application from starting or running.

---

### 2.4 — Admin Panel: auto-encrypt on Save Configuration

In the Admin Panel, the password fields behave as follows:

- **On load:** The password field displays a placeholder ("••••••••" or "Password is set") when an encrypted value exists in config. The plaintext is never sent to the browser.
- **On save:** If the admin has typed a new value into the password field, it is encrypted server-side before being written to `config.yml`. If the field is left as its placeholder (unchanged), the existing stored value is preserved as-is.

**Acceptance Criteria:**
- [ ] Loading the Admin Panel never sends a decrypted password to the browser.
- [ ] After saving, the new password appears as `ENC(...)` in `config.yml`.
- [ ] If the admin leaves a password field blank/unchanged, the previously saved value is not overwritten.
- [ ] The Admin Panel form works identically whether `CLM_EXTRACT_KEY` is set or not — but saving a password without the key set logs a warning and stores it in plaintext.

---

### 2.5 — CLI mode

When the application is run from the command line (`--config config.yml`), the same mechanism applies. If `CLM_EXTRACT_KEY` is set in the environment, encrypted passwords are decrypted transparently before use.

**Acceptance Criteria:**
- [ ] A CLI run with an encrypted `config.yml` and `CLM_EXTRACT_KEY` set completes normally.
- [ ] A CLI run with an encrypted `config.yml` and no `CLM_EXTRACT_KEY` fails with a clear error message before making any API calls.

---

## 3. Scope and Boundaries

### In-Scope

- Encrypting `server.password` and `sftp.password` in `config.yml`
- `ENC(...)` detection and decryption at startup / config load time
- Master key supplied via `CLM_EXTRACT_KEY` environment variable
- Admin Panel: placeholder display for encrypted fields; auto-encrypt on save; preserve-on-blank behaviour
- Plaintext backwards compatibility with warning log
- CLI mode support

### Out-of-Scope

- Encrypting any other config fields (usernames, URLs, etc.)
- Key rotation or re-encryption workflows
- Hardware security modules (HSM) or external key management services
- Encrypting the master key itself
- User management, role management, or any session-handling changes beyond what is already in specs 011–013
