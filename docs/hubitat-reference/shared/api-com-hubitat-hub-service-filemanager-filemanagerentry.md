# File manager entry

- **ID:** `api-com-hubitat-hub-service-filemanager-filemanagerentry`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.service.fileManager.FileManagerEntry`

> The hub returns or binds this object to app or driver code; use the documented members on the object you receive.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getDate` | `String getDate()` | Returns: file modification time as an epoch-millisecond decimal string, or null for a directory |
| `getId` | `String getId()` | Returns: signed 64-bit absolute-path hash represented as a decimal string |
| `getName` | `String getName()` | Returns: entry name |
| `getSize` | `String getSize()` | Returns: entry size represented as a decimal string; zero for a directory |
| `getType` | `String getType()` | Returns: entry type; either file or dir |
| `toMap` | `Map<String, Object> toMap()` | Converts this entry to the mutable File Manager response payload. The returned map contains: id: signed 64-bit path hash as a decimal java.lang.String type: file or dir java.lang.S |
