# DataType

- **ID:** `api-hubitat-matter-datatype`
- **Section:** Protocols
- **Class:** `hubitat.matter.DataType`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getLength` | `Integer getLength(int type)` | Returns the byte width declared for a Matter data type. Parameters: type - Matter data type code. Returns: byte width, or null when the type code is not registered. |
| `isDiscrete` | `boolean isDiscrete(int type)` | Reports whether the registered Matter type is discrete. Parameters: type - Matter data type code. Returns: the type table flag; an unknown type has no registered value. |
| `pack` | `String pack(Long data, int type, boolean littleEndian = false)` | Encodes an integer using the byte width declared for its Matter data type. throws: NullPointerException when type is not registered and has no byte width. Parameters: data - Intege |

## Properties

| Name | Type | Description |
|---|---|---|
| `ARRAY` | `int` | — |
| `BOOLEAN_FALSE` | `int` | — |
| `BOOLEAN_TRUE` | `int` | — |
| `END_CONTAINER` | `int` | — |
| `ENUM8` | `int` | — |
| `FLOAT4` | `int` | — |
| `FLOAT8` | `int` | — |
| `INT16` | `int` | — |
| `INT32` | `int` | — |
| `INT64` | `int` | — |
| `INT8` | `int` | — |
| `LIST` | `int` | — |
| `NULL` | `int` | — |
| `STRING_OCTET1` | `int` | — |
| `STRING_OCTET2` | `int` | — |
| `STRING_OCTET4` | `int` | — |
| `STRING_OCTET8` | `int` | — |
| `STRUCTURE` | `int` | — |
| `UINT16` | `int` | — |
| `UINT32` | `int` | — |
| `UINT64` | `int` | — |
| `UINT8` | `int` | — |
| `UTF81` | `int` | — |
| `UTF82` | `int` | — |
| `UTF84` | `int` | — |
| `UTF88` | `int` | — |
