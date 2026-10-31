# DataType.Type

- **ID:** `api-hubitat-matter-datatype-type`
- **Section:** Protocols
- **Class:** `hubitat.matter.DataType$Type`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getIsDiscrete` | `Boolean getIsDiscrete()` | Returns the is discrete value stored in isDiscrete. Returns: is discrete value |
| `getLength` | `Integer getLength()` | Returns the length value stored in length. Returns: length value |
| `getType` | `Type getType(Integer typeInt)` | Looks up a Matter data type by its integer code. Parameters: typeInt - integer type code. Returns: matching type, or null when no type uses the code. |
| `getType` | `Type getType(String typeHexString)` | Looks up a Matter data type by its hexadecimal code. Parameters: typeHexString - hexadecimal type code converted to an integer before lookup. Returns: matching type, or null when n |
| `getTypeEnum` | `String getTypeEnum()` | Returns the type enum value stored in toString. Returns: type enum value |
| `getTypeInt` | `int getTypeInt()` | Returns the type int value stored in typeInt. Returns: type int value |
| `getTypeLabel` | `String getTypeLabel()` | Returns the type label value stored in typeLabel. Returns: type label value |

## Properties

| Name | Type | Description |
|---|---|---|
| `ARRAY` | `DataType$Type` | — |
| `BOOLEAN_FALSE` | `DataType$Type` | — |
| `BOOLEAN_TRUE` | `DataType$Type` | — |
| `END_CONTAINER` | `DataType$Type` | — |
| `FLOAT4` | `DataType$Type` | — |
| `FLOAT8` | `DataType$Type` | — |
| `INT16` | `DataType$Type` | — |
| `INT32` | `DataType$Type` | — |
| `INT64` | `DataType$Type` | — |
| `INT8` | `DataType$Type` | — |
| `LIST` | `DataType$Type` | — |
| `NULL` | `DataType$Type` | — |
| `STRING_OCTET1` | `DataType$Type` | — |
| `STRING_OCTET2` | `DataType$Type` | — |
| `STRING_OCTET4` | `DataType$Type` | — |
| `STRING_OCTET8` | `DataType$Type` | — |
| `STRUCTURE` | `DataType$Type` | — |
| `UINT16` | `DataType$Type` | — |
| `UINT32` | `DataType$Type` | — |
| `UINT64` | `DataType$Type` | — |
| `UINT8` | `DataType$Type` | — |
| `UTF81` | `DataType$Type` | — |
| `UTF82` | `DataType$Type` | — |
| `UTF84` | `DataType$Type` | — |
| `UTF88` | `DataType$Type` | — |
