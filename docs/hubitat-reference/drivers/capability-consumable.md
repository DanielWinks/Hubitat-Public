# Consumable

- **ID:** `capability-consumable`
- **Section:** Drivers
- **Class:** ``
- **Kind:** capability

> Firmware-defined capability contract.

Source reference: capability.consumable

## Methods

| Name | Signature | Description |
|---|---|---|
| `setConsumableStatus` | `setConsumableStatus(STRING argument1)` | Command defined by Consumable. |

## Properties

| Name | Type | Description |
|---|---|---|
| `consumableStatus` | `ENUM` | Defined values: missing, order, maintenance_required, good, replace |
