# CLM Data Extract -- Product Summary

**Vision:** Enable CLM administrators to reliably extract structured contract data from an SCLM REST API system into flat CSV files, supporting data migration, compliance audits, and business reporting.

**Target Audience:** CLM platform administrators responsible for system operations, data migration, audit preparation, and producing data extracts for downstream business teams.

**Core Features:**

- YAML-driven configuration (server, credentials, BO types, fields, output options)
- Automatic SCLM REST API authentication and session management
- Metadata discovery and tracking number retrieval per BO type
- Bulk data retrieval via the bundles endpoint with configurable batch sizes
- CSV export -- one flat file per BO type with all requested fields
- Downloads list -- one CSV per BO type listing attachment file paths for later retrieval
- Timestamped run directories with progress logging
- Configurable backup retention with automatic cleanup
