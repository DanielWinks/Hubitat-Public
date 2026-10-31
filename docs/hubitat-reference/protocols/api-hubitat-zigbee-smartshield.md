# SmartShield

- **ID:** `api-hubitat-zigbee-smartshield`
- **Section:** Protocols
- **Class:** `hubitat.zigbee.SmartShield`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Methods

| Name | Signature | Description |
|---|---|---|
| `populateFromCatchall` | `SmartShield populateFromCatchall(Map descAsMap)` | Populates this message from a parsed Zigbee catchall description. Parameters: descAsMap - required parsed catchall properties: clusterInt (Integer) - cluster identifier parsed from |
| `populateFromReadAttribute` | `SmartShield populateFromReadAttribute(Map descAsMap)` | Populates this message from a Zigbee read-attribute description. Parameters: descAsMap - required read-attribute properties: cluster (String) - hexadecimal cluster identifier parse |
| `SmartShield` | `SmartShield()` | Creates a SmartShield message with its declared field defaults. |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `clusterId` | `int` | — |
| `command` | `int` | — |
| `data` | `List` | — |
| `destinationEndpoint` | `int` | — |
| `direction` | `int` | — |
| `isClusterSpecific` | `boolean` | — |
| `isManufacturerSpecific` | `boolean` | — |
| `manufacturerId` | `int` | — |
| `messageType` | `int` | — |
| `number` | `Integer` | — |
| `options` | `int` | — |
| `profileId` | `int` | — |
| `senderShortId` | `int` | — |
| `sourceEndpoint` | `int` | — |
| `text` | `String` | — |
