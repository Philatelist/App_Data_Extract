# Architecture (High Level)

## Runtime
- Java 17
- Single runnable shaded JAR

## Main Modules
- Configuration loader
- Endpoints-file adapter (supports user-provided original format)
- Session manager
- API client / request executor
- Export orchestrator
- Metadata model
- Batching and filtering logic
- CSV writer
- Backups manager (retention enforcement)
- Logs manager
- Downloads list generator

## Processing Rules
- Business Objects (BOs) must be processed strictly sequentially.
- Full cleanup of in-memory state must occur between BOs.
