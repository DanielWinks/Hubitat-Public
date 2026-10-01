# DeviceClass

- **ID:** `api-hubitat-zwave-deviceclass`
- **Section:** Protocols
- **Class:** `hubitat.zwave.DeviceClass`

> Use this protocol type to create, inspect, or parse protocol data in driver code.


## Properties

| Name | Type | Description |
|---|---|---|
| `basicTypes` | `Map<Integer, String>` | Maps each basic-type identifier declared in this table to its symbolic name. Key: Integer identifier used by the table. Value: String symbolic basic-type name. |
| `genericTypes` | `Map<Integer, Map<String, Object>>` | Maps each generic-type identifier to its descriptive fields and specific-device table. Outer key: Integer generic-type identifier. name: String symbolic generic-type name. help: St |
| `manufacturers` | `Map<Integer, String>` | Maps each manufacturer identifier declared in this table to its stored name. Key: Integer manufacturer identifier. Value: String manufacturer name. |
