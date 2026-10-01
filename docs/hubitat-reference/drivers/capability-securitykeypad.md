# SecurityKeypad

- **ID:** `capability-securitykeypad`
- **Section:** Drivers
- **Class:** ``
- **Kind:** capability

> Firmware-defined capability contract.

Source reference: capability.securityKeypad

## Methods

| Name | Signature | Description |
|---|---|---|
| `armAway` | `armAway()` | Command defined by SecurityKeypad. |
| `armHome` | `armHome()` | Command defined by SecurityKeypad. |
| `armNight` | `armNight()` | Command defined by SecurityKeypad. |
| `deleteCode` | `deleteCode(NUMBER Code position)` | Command defined by SecurityKeypad. |
| `disarm` | `disarm()` | Command defined by SecurityKeypad. |
| `getCodes` | `getCodes()` | Command defined by SecurityKeypad. |
| `setCode` | `setCode(NUMBER Code position, STRING PIN code, STRING Name)` | Command defined by SecurityKeypad. |
| `setCodeLength` | `setCodeLength(NUMBER Pin code length)` | Command defined by SecurityKeypad. |
| `setEntryDelay` | `setEntryDelay(NUMBER Entrance delay)` | Command defined by SecurityKeypad. |
| `setExitDelay` | `setExitDelay(NUMBER Exit delay)` | Command defined by SecurityKeypad. |

## Properties

| Name | Type | Description |
|---|---|---|
| `codeChanged` | `ENUM` | Defined values: added, changed, deleted, failed |
| `codeLength` | `NUMBER` | No fixed values are declared. |
| `lastCodeName` | `STRING` | No fixed values are declared. |
| `lockCodes` | `JSON_OBJECT` | No fixed values are declared. |
| `maxCodes` | `NUMBER` | No fixed values are declared. |
| `securityKeypad` | `ENUM` | Defined values: disarmed, armed home, armed away, armed night, unknown |
