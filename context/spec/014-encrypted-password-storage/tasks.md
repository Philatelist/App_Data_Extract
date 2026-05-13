# Tasks: Encrypted Password Storage

- **Functional Specification:** `context/spec/014-encrypted-password-storage/functional-spec.md`
- **Technical Specification:** `context/spec/014-encrypted-password-storage/technical-considerations.md`
- **Status:** Ready

---

## Slice 1 — `CredentialEncryptor` utility (foundation)

*Smallest deliverable: a tested crypto primitive. No wiring yet — the app behaves identically after this slice.*

- [x] Create `com.clmextract.config.CredentialEncryptor` with four methods: `isEncrypted(String)`, `encrypt(String plaintext, String masterKey)`, `decrypt(String encryptedValue, String masterKey)`, `getMasterKey()` returning `Optional<String>` from `System.getenv("CLM_EXTRACT_KEY")`. Algorithm: AES-256-GCM; key = SHA-256 of master key bytes; IV = random 12 bytes prepended to the ciphertext in the base64 blob; token format `ENC(base64...)`. No new Maven dependencies — uses `javax.crypto` only. **[Agent: java-backend]**
- [x] Unit tests for `CredentialEncryptor`: encrypt→decrypt round-trip returns the original plaintext; `isEncrypted()` returns `true` for `ENC(...)` and `false` for plaintext; `decrypt()` throws `ConfigValidationException` when the ciphertext is tampered; `getMasterKey()` returns empty `Optional` when the env var is absent. **[Agent: java-backend]**
- [x] Verify: run `mvn test -pl . -Dtest=CredentialEncryptorTest` — all tests pass, build is green. **[Agent: java-backend]**

---

## Slice 2 — CLI decrypts passwords transparently

*After this slice: `java -jar clm-extract.jar --config config.yml` works with an encrypted config when `CLM_EXTRACT_KEY` is set; fails with a clear error when it isn't.*

- [x] Update `ConfigLoader.load(String configPath)`: after `parse()` and before `validate()`, call `CredentialEncryptor.getMasterKey()`; for each of `server.password` and `sftp.password` — if `ENC(...)` and key present: decrypt and replace in `AppConfig`; if `ENC(...)` and no key: throw `ConfigValidationException("Encrypted credentials found but CLM_EXTRACT_KEY is not set")`; if plaintext and key present: log WARN `"server.password is stored in plaintext — save your configuration via the Admin Panel to encrypt it."`; if plaintext and no key: no-op. **[Agent: java-backend]**
- [x] Unit tests for the updated `ConfigLoader.load()`: `ENC(...)` password + key set → resolves to plaintext; `ENC(...)` + no key → throws `ConfigValidationException`; plaintext + key set → proceeds normally, WARN logged; plaintext + no key → proceeds normally, no warning. **[Agent: java-backend]**
- [x] Verify: manually create a config with `server.password: ENC(...)` (generated via a small test main or unit helper), set `CLM_EXTRACT_KEY` in the environment, run the JAR in offline mode — observe startup succeeds and no "Encrypted credentials" error appears in the log. Then unset the variable and re-run — observe the clear error message before any CLM API calls are made. **[Agent: java-backend]**

---

## Slice 3 — Admin Panel: passwords never sent to browser, preserved on blank save

*After this slice: the Admin Panel loads without exposing any password; saving without touching the password fields leaves `config.yml` unchanged.*

- [x] Update `ConfigController.getConfig()`: replace `serverPassword: config.getPassword()` with `serverPassword: ""` and add `serverPasswordIsSet: !isNullOrBlank(config.getPassword())`; replace `sftp.password: sftpCfg.getPassword()` with `sftp.password: ""` and add `sftp.passwordIsSet: !isNullOrBlank(sftpCfg.getPassword())`. **[Agent: java-backend]**
- [x] Update `ConfigController.putConfig()`: for `serverPassword` — if the incoming value is blank/absent, load the existing stored value via `ConfigLoader.loadRaw()` and write it back unchanged; same logic for `sftp.password`. **[Agent: java-backend]**
- [x] Unit tests for `ConfigController`: `getConfig()` response contains `serverPassword: ""` and `serverPasswordIsSet: true` when config has a non-blank password; `putConfig()` with blank `serverPassword` preserves the existing stored value in the written YAML. **[Agent: java-backend]**
- [x] Update `admin.js` `populateForm()`: if `config.serverPasswordIsSet == true`, set the `server-password` input placeholder to `"Password is set (leave blank to keep)"` and leave the value empty; if false, show `"Enter password"`. Same for `sftp-password` using `config.sftp.passwordIsSet`. **[Agent: general-purpose]**
- [x] Update `admin.js` `collectConfig()`: send `serverPassword` only if the field value is non-blank; otherwise omit it from the payload. Same for `sftp.password`. **[Agent: general-purpose]**
- [x] Rebuild JAR and verify: load the Admin Panel — the password field is empty with the correct placeholder; click Save Configuration without touching the password field — open `config.yml` and confirm the stored password value is unchanged. **[Agent: general-purpose]**

---

## Slice 4 — Admin Panel encrypts new passwords on save

*After this slice: typing a new password in the Admin Panel and saving writes `ENC(...)` to `config.yml`.*

- [x] Update `ConfigController.putConfig()` non-blank password branch: if `CLM_EXTRACT_KEY` is set, call `CredentialEncryptor.encrypt(newValue, masterKey)` and store the `ENC(...)` token; if no key, log WARN and store plaintext. Apply same logic for `sftp.password`. **[Agent: java-backend]**
- [x] Unit tests: `putConfig()` with non-blank `serverPassword` and key set → written YAML contains `ENC(...)` value; with no key → written YAML contains plaintext and a WARN is logged; `getConfig()` after the write → `serverPasswordIsSet: true`, `serverPassword: ""`. **[Agent: java-backend]**
- [x] Rebuild JAR and verify: with `CLM_EXTRACT_KEY` set, open Admin Panel, type a new password in the CLM server password field, click Save Configuration — open `config.yml` and confirm the value is `ENC(...)`. Reload the Admin Panel — confirm the field is empty with the "Password is set" placeholder. **[Agent: general-purpose]**

---

## Slice 5 — Serve-mode startup fail-fast

*After this slice: starting the web server (`--serve`) with an encrypted config but no `CLM_EXTRACT_KEY` produces a clear error and the server does not start.*

- [x] Add startup check to `WebServer.start()`: after loading the initial config via `ConfigLoader.loadRaw()`, if any password field `isEncrypted()` and `CredentialEncryptor.getMasterKey()` is empty, log ERROR `"Encrypted credentials found but CLM_EXTRACT_KEY is not set"` and throw `IllegalStateException` to abort startup. **[Agent: java-backend]**
- [x] Unit test: `WebServer` startup with a config containing `ENC(...)` password and no key set throws before binding the port. **[Agent: java-backend]**
- [x] Verify: with `CLM_EXTRACT_KEY` unset, start the JAR with `--serve` and an encrypted `config.yml` — confirm the process exits immediately with the error message and port 8080 is not bound. Then set the key and restart — confirm the server starts normally. **[Agent: java-backend]**

---

## Subagent Recommendations

| Task/Slice | Issue | Recommendation |
|---|---|---|
| Slices 3–4: `admin.js` changes | Assigned to `general-purpose` — no vanilla JS/HTML specialist available | Add a `vanilla-frontend` agent for plain HTML/JS/CSS tasks |
| All verification sub-tasks | UI verification relies on manual JAR run + file inspection; no browser MCP available | Install browser MCP for automated Admin Panel verification |
