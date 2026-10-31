# DataType

- **ID:** `api-hubitat-zigbee-zcl-datatype`
- **Section:** Protocols
- **Class:** `hubitat.zigbee.zcl.DataType`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getLength` | `Integer getLength(int type)` | Returns the byte width declared for a Zigbee ZCL data type. Parameters: type - ZCL data type code. Returns: byte width, or null when the type code is not registered. |
| `isDiscrete` | `boolean isDiscrete(int type)` | Reports whether the registered Zigbee ZCL type is discrete. Parameters: type - ZCL data type code. Returns: the type table flag. |
| `pack` | `String pack(Long data, int type, boolean littleEndian = false)` | Encodes an integer using the byte width declared for its Zigbee ZCL data type. throws: NullPointerException when type is not registered and has no byte width. Parameters: data - In |
| `pack` | `String pack(String data, int type)` | Encodes a string as a Zigbee ZCL character or octet string. Parameters: data - string value to encode. type - ZCL string type that selects the length-prefix width. Returns: hexadec |

## Properties

| Name | Type | Description |
|---|---|---|
| `ARRAY` | `int` | — |
| `ATTRIBUTE_ID` | `int` | — |
| `BACNET_OID` | `int` | — |
| `BAG` | `int` | — |
| `BITMAP16` | `int` | — |
| `BITMAP24` | `int` | — |
| `BITMAP32` | `int` | — |
| `BITMAP40` | `int` | — |
| `BITMAP48` | `int` | — |
| `BITMAP56` | `int` | — |
| `BITMAP64` | `int` | — |
| `BITMAP8` | `int` | — |
| `BOOLEAN` | `int` | — |
| `CLUSTER_ID` | `int` | — |
| `DATA16` | `int` | — |
| `DATA24` | `int` | — |
| `DATA32` | `int` | — |
| `DATA40` | `int` | — |
| `DATA48` | `int` | — |
| `DATA56` | `int` | — |
| `DATA64` | `int` | — |
| `DATA8` | `int` | — |
| `DATE` | `int` | — |
| `ENUM16` | `int` | — |
| `ENUM8` | `int` | — |
| `FLOAT2` | `int` | — |
| `FLOAT4` | `int` | — |
| `FLOAT8` | `int` | — |
| `IEEE_ADDRESS` | `int` | — |
| `INT16` | `int` | — |
| `INT24` | `int` | — |
| `INT32` | `int` | — |
| `INT40` | `int` | — |
| `INT48` | `int` | — |
| `INT56` | `int` | — |
| `INT64` | `int` | — |
| `INT8` | `int` | — |
| `NO_DATA` | `int` | — |
| `SECKEY128` | `int` | — |
| `SET` | `int` | — |
| `STRING_CHAR` | `int` | — |
| `STRING_LONG_CHAR` | `int` | — |
| `STRING_LONG_OCTET` | `int` | — |
| `STRING_OCTET` | `int` | — |
| `STRUCTURE` | `int` | — |
| `TIME_OF_DAY` | `int` | — |
| `UINT16` | `int` | — |
| `UINT24` | `int` | — |
| `UINT32` | `int` | — |
| `UINT40` | `int` | — |
| `UINT48` | `int` | — |
| `UINT56` | `int` | — |
| `UINT64` | `int` | — |
| `UINT8` | `int` | — |
| `UNKNOWN` | `int` | — |
| `UTCTIME` | `int` | — |
