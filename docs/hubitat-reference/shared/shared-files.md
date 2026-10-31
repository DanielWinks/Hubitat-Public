# Hub files

- **ID:** `shared-files`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.executor.BaseExecutor`

> Use these methods directly in app or driver code; the hub supplies this execution context.

## Methods

| Name | Signature | Description |
|---|---|---|
| `deleteHubFile` | `void deleteHubFile(String fileName)` | Deletes a file from the hub's local file manager. Parameters: fileName - File name to delete. |
| `downloadHubFile` | `byte[] downloadHubFile(String fileName)` | Reads a file from the hub's local file manager. Parameters: fileName - File name to read. Returns: File contents as bytes, or null when the file is unavailable. |
| `getHubFiles` | `List<FileManagerEntry> getHubFiles(String folder = "")` | Lists files and directories in the hub's local file manager. Parameters: folder - Relative folder path; defaults to the file manager root. Returns: FileManagerEntry values for the  |
| `uploadHubFile` | `void uploadHubFile(String fileName, byte[] bytes)` | Writes bytes to a file in the hub's local file manager when the name is valid. Parameters: fileName - File name containing only ASCII letters, digits, dot, underscore, or hyphen. b |
