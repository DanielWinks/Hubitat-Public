# LockCodes

- **ID:** `capability-lockcodes`
- **Section:** Drivers
- **Class:** ``
- **Kind:** capability

> Firmware-defined capability contract.

Source reference: capability.lockCodes

## Methods

| Name | Signature | Description |
|---|---|---|
| `deleteCode` | `deleteCode(NUMBER Code position)` | Command defined by LockCodes. |
| `getCodes` | `getCodes()` | Command defined by LockCodes. |
| `setCode` | `setCode(NUMBER Code position, STRING PIN code, STRING Name)` | Command defined by LockCodes. |
| `setCodeLength` | `setCodeLength(NUMBER Pin code length)` | Command defined by LockCodes. |

## Properties

| Name | Type | Description |
|---|---|---|
| `codeChanged` | `ENUM` | Defined values: added, changed, deleted, failed |
| `codeLength` | `NUMBER` | No fixed values are declared. |
| `lastCodeName` | `STRING` | No fixed values are declared. |
| `lockCodes` | `JSON_OBJECT` | No fixed values are declared. |
| `maxCodes` | `NUMBER` | No fixed values are declared. |
