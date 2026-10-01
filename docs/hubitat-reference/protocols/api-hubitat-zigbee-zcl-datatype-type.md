# DataType.Type

- **ID:** `api-hubitat-zigbee-zcl-datatype-type`
- **Section:** Protocols
- **Class:** `hubitat.zigbee.zcl.DataType$Type`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getIsDiscrete` | `Boolean getIsDiscrete()` | Returns the is discrete value stored in isDiscrete. Returns: is discrete value |
| `getLength` | `Integer getLength()` | Returns the length value stored in length. Returns: length value |
| `getType` | `Type getType(Integer typeInt)` | Looks up a Zigbee ZCL data type by its integer code. Parameters: typeInt - integer type code. Returns: matching type, or null when no type uses the code. |
| `getType` | `Type getType(String typeHexString)` | Looks up a Zigbee ZCL data type by its hexadecimal code. Parameters: typeHexString - hexadecimal type code converted to an integer before lookup. Returns: matching type, or null wh |
| `getTypeEnum` | `String getTypeEnum()` | Returns the type enum value stored in toString. Returns: type enum value |
| `getTypeInt` | `Integer getTypeInt()` | Returns the type int value stored in typeInt. Returns: type int value |
| `getTypeLabel` | `String getTypeLabel()` | Returns the type label value stored in typeLabel. Returns: type label value |

## Properties

| Name | Type | Description |
|---|---|---|
| `ARRAY` | `DataType$Type` | — |
| `ATTRIBUTE_ID` | `DataType$Type` | — |
| `BACNET_OID` | `DataType$Type` | — |
| `BAG` | `DataType$Type` | — |
| `BITMAP16` | `DataType$Type` | — |
| `BITMAP24` | `DataType$Type` | — |
| `BITMAP32` | `DataType$Type` | — |
| `BITMAP40` | `DataType$Type` | — |
| `BITMAP48` | `DataType$Type` | — |
| `BITMAP56` | `DataType$Type` | — |
| `BITMAP64` | `DataType$Type` | — |
| `BITMAP8` | `DataType$Type` | — |
| `BOOLEAN` | `DataType$Type` | — |
| `CLUSTER_ID` | `DataType$Type` | — |
| `DATA16` | `DataType$Type` | — |
| `DATA24` | `DataType$Type` | — |
| `DATA32` | `DataType$Type` | — |
| `DATA40` | `DataType$Type` | — |
| `DATA48` | `DataType$Type` | — |
| `DATA56` | `DataType$Type` | — |
| `DATA64` | `DataType$Type` | — |
| `DATA8` | `DataType$Type` | — |
| `DATE` | `DataType$Type` | — |
| `ENUM16` | `DataType$Type` | — |
| `ENUM8` | `DataType$Type` | — |
| `FLOAT2` | `DataType$Type` | — |
| `FLOAT4` | `DataType$Type` | — |
| `FLOAT8` | `DataType$Type` | — |
| `IEEE_ADDRESS` | `DataType$Type` | — |
| `INT16` | `DataType$Type` | — |
| `INT24` | `DataType$Type` | — |
| `INT32` | `DataType$Type` | — |
| `INT40` | `DataType$Type` | — |
| `INT48` | `DataType$Type` | — |
| `INT56` | `DataType$Type` | — |
| `INT64` | `DataType$Type` | — |
| `INT8` | `DataType$Type` | — |
| `NO_DATA` | `DataType$Type` | — |
| `SECKEY128` | `DataType$Type` | — |
| `SET` | `DataType$Type` | — |
| `STRING_CHAR` | `DataType$Type` | — |
| `STRING_LONG_CHAR` | `DataType$Type` | — |
| `STRING_LONG_OCTET` | `DataType$Type` | — |
| `STRING_OCTET` | `DataType$Type` | — |
| `STRUCTURE` | `DataType$Type` | — |
| `TIME_OF_DAY` | `DataType$Type` | — |
| `UINT16` | `DataType$Type` | — |
| `UINT24` | `DataType$Type` | — |
| `UINT32` | `DataType$Type` | — |
| `UINT40` | `DataType$Type` | — |
| `UINT48` | `DataType$Type` | — |
| `UINT56` | `DataType$Type` | — |
| `UINT64` | `DataType$Type` | — |
| `UINT8` | `DataType$Type` | — |
| `UNKNOWN` | `DataType$Type` | — |
| `UTCTIME` | `DataType$Type` | — |
