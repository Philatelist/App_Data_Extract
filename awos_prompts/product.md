# Product Definition

## Overview (Non-Technical)

Build a command-line export tool for business users that signs into a web system, runs a configurable sequence of data collection actions, and produces structured export files.

Users provide a configuration file defining:
- connection details,
- which data types to export,
- export options.

The tool automatically:
- signs in before work begins,
- writes processing logs,
- keeps backups for a configurable number of days,
- creates a separate downloads list per exported data type.

