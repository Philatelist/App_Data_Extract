# Tasks

Create detailed tasks for the implementation:

- Maven project skeleton with shaded JAR packaging
- Manual args parsing + configuration design
- Credentials stored as plain text in config.yml (env var resolution out of scope)
- Endpoints adapter / parser for user-provided original endpoints file
- Session manager (validate session / login / logout lifecycle)
- HTTP request executor (java.net.http.HttpClient)
- Metadata parsing
- Field-path planning
- Tracking IDs retrieval
- Explicit IDs and numeric ranges parsing
- Batched bulk data fetch
- Streaming CSV generation per component (OpenCSV)
- Merge single-cardinality components option
- Export only single-cardinality components option
- Downloads list generator
- Detailed REST-call logging (Log4j2)
- Backups creation and retention cleanup
- Offline test mode using sample JSON files
- Abort entire run if any BO fails
- README with build and run instructions
