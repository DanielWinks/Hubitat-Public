# Matter

- **ID:** `api-com-hubitat-matter-matter`
- **Section:** Protocols
- **Class:** `com.hubitat.matter.Matter`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Methods

| Name | Signature | Description |
|---|---|---|
| `attributePath` | `Map<String, String> attributePath(Integer ep, Integer cluster, Integer attr)` | Builds a Matter attribute path map. Result keys: ep (String) - endpoint identifier prefixed with 0x cluster (String) - cluster identifier prefixed with 0x and formatted to four hex |
| `attributePath` | `Map<String, String> attributePath(String ep, Integer cluster, Integer attr)` | Builds a Matter attribute path map. Result keys: ep (String) - endpoint identifier prefixed with 0x cluster (String) - cluster identifier prefixed with 0x and formatted to four hex |
| `attributePathByName` | `Map<String, String> attributePathByName(Integer ep, String clusterName, String attributeName)` | Builds an attribute path after resolving the cluster and attribute names. Parameters: ep - endpoint identifier clusterName - Matter cluster name attributeName - Matter attribute na |
| `attributePathByName` | `Map<String, String> attributePathByName(String ep, String clusterName, String attributeName)` | Builds an attribute path after resolving the cluster and attribute names. Parameters: ep - endpoint identifier clusterName - Matter cluster name attributeName - Matter attribute na |
| `attributeWriteRequest` | `Map<String, String> attributeWriteRequest(Integer ep, Integer cluster, Integer attr, Integer type, String data)` | Builds a Matter attribute write request map. Result keys: ep (String) - endpoint identifier prefixed with 0x cluster (String) - cluster identifier prefixed with 0x and formatted to |
| `attributeWriteRequest` | `Map<String, String> attributeWriteRequest(String ep, Integer cluster, Integer attr, Integer type, String data)` | Builds a Matter attribute write request map. Result keys: ep (String) - endpoint identifier prefixed with 0x cluster (String) - cluster identifier prefixed with 0x and formatted to |
| `cleanSubscribe` | `String cleanSubscribe(Integer minReportTime, Integer maxReportTime, List<Map<String, String>> attributePaths)` | Builds a clean Matter attribute subscription hub command. Each map is an attribute path with ep, cluster, and attr keys as returned by attributePath(String, Integer, Integer). Para |
| `clusterLookup` | `Matter.MatterCluster clusterLookup(def cluster)` | Looks up a cluster enum from an integer or hexadecimal identifier. Parameters: cluster - numeric cluster identifier Returns: matching cluster enum constant, or null when no local c |
| `cmdField` | `Map<String, String> cmdField(String type, def String, def num, String data)` | Builds a command-field map by copying its named values unchanged. Result keys: type (String) - supplied type string num (Object) - supplied tag value, which may be null data (Strin |
| `cmdField` | `Map<String, String> cmdField(int type, int num, String data = "")` | Builds a Matter command-field map from numeric type and tag values. Result keys: type (String) - type identifier formatted as hexadecimal with a minimum width of one byte num (Stri |
| `cmdFields` | `String cmdFields(List<Map<String, String>> fields)` | Wraps the command-field section in Matter structure markers. The method reads type, num, and value from each map and calls the field encoder, but does not append the encoded value  |
| `cmdFields` | `String cmdFields(String fields)` | Wraps hexadecimal command-field text in Matter structure markers. Parameters: fields - hexadecimal encoded field text inserted between the start and end markers Returns: command-fi |
| `cmdFieldStr` | `String cmdFieldStr(String type, String num, String data = "")` | Parses decimal type and tag text, then encodes one Matter command field. throws: NumberFormatException if type or num is not decimal integer text Parameters: type - base-10 Matter  |
| `cmdFieldStr` | `String cmdFieldStr(int type, int num, String data = "")` | Encodes one Matter command field with a context-specific tag. Boolean and null types omit data bytes; UTF-8 and octet-string type 1 values receive a one-byte length prefix. Paramet |
| `colorTemperatureRefresh` | `String colorTemperatureRefresh()` | Builds a read request for the color-temperature attribute. Returns: Matter hub command string |
| `convertHexToInt` | `Integer convertHexToInt(String value)` | Parses a hexadecimal string as an integer. throws: NumberFormatException if the text is not a valid hexadecimal integer Parameters: value - hexadecimal digits to parse Returns: par |
| `convertToHexString` | `String convertToHexString(Integer value, Integer width)` | Formats an integer as uppercase hexadecimal with a minimum width. Widths below one use one digit; values longer than the requested width are not truncated. Parameters: value - inte |
| `electricMeasurementPowerRefresh` | `String electricMeasurementPowerRefresh()` | Builds a read request for the electrical active-power attribute. Returns: Matter hub command string |
| `eventPath` | `Map<String, String> eventPath(Integer ep, Integer cluster, Integer attr, Boolean isUrgent = false)` | Builds a Matter event path map. Result keys: ep (String) - endpoint identifier prefixed with 0x cluster (String) - cluster identifier prefixed with 0x and formatted to four hexadec |
| `eventPath` | `Map<String, String> eventPath(String ep, Integer cluster, Integer attr, Boolean isUrgent = false)` | Builds a Matter event path map. Result keys: ep (String) - endpoint identifier prefixed with 0x cluster (String) - cluster identifier prefixed with 0x and formatted to four hexadec |
| `getCluster` | `Class<MatterCluster> getCluster()` | Returns the enum class that defines this helper's cluster constants. Returns: cluster enum class returned by this helper |
| `getClusterAttributeByName` | `Long getClusterAttributeByName(Long clusterId, String attributeName)` | Looks up an attribute identifier by cluster and attribute name. Parameters: clusterId - Matter cluster identifier attributeName - Matter attribute name Returns: attribute identifie |
| `getClusterAttributeByName` | `Long getClusterAttributeByName(String clusterName, String attributeName)` | Looks up an attribute identifier by cluster and attribute name. Parameters: clusterName - Matter cluster name attributeName - Matter attribute name Returns: attribute identifier, o |
| `getClusterAttributeName` | `String getClusterAttributeName(Long clusterId, Long attributeId)` | Looks up an attribute name in a Matter cluster. Parameters: clusterId - Matter cluster identifier attributeId - Matter attribute identifier Returns: attribute name, or null when no |
| `getClusterCommandByName` | `Long getClusterCommandByName(Long clusterId, String attributeName)` | Looks up a command identifier by cluster and command name. Parameters: clusterId - Matter cluster identifier attributeName - Matter command name Returns: command identifier, or nul |
| `getClusterCommandByName` | `Long getClusterCommandByName(String clusterName, String attributeName)` | Looks up a command identifier by cluster and command name. Parameters: clusterName - Matter cluster name attributeName - Matter command name Returns: command identifier, or null wh |
| `getClusterCommandName` | `String getClusterCommandName(Long clusterId, Long commandId)` | Looks up a command name in a Matter cluster. Parameters: clusterId - Matter cluster identifier commandId - Matter command identifier Returns: command name, or null when no matching |
| `getClusterIdByName` | `Long getClusterIdByName(String clusterName)` | Looks up a Matter cluster identifier by its generated class name. Parameters: clusterName - Matter cluster name Returns: numeric cluster identifier, or null when its generated clus |
| `getClusterName` | `String getClusterName(Long clusterId)` | Looks up the cluster name for a numeric identifier. Parameters: clusterId - Matter cluster identifier Returns: cluster name, or null when no matching cluster is available |
| `getClusterNames` | `List<String> getClusterNames()` | Returns the Matter cluster names exposed by the controller library. Returns: generated cluster class names, excluding BaseCluster |
| `getEvent` | `Map getEvent(String description)` | Converts a recognized Matter description to a device event map. name (String) identifies the event. value (Object) is the decoded value; source branches return strings or numbers,  |
| `getMatterEndpoints` | `List<Map<String, String>> getMatterEndpoints()` | Returns the endpoint information reported for the attached Matter device. Each list item contains: endpointId (String) - endpoint identifier in hexadecimal form label (String, opti |
| `getMatterFingerprints` | `List<Fingerprint> getMatterFingerprints()` | Returns fingerprints reported for the attached Matter device. Returns: fingerprints reported for the attached MAT device, or an empty list for other device types |
| `hex2String` | `String hex2String(String arg)` | Decodes each pair of hexadecimal digits into one character. throws: NumberFormatException if a pair is not hexadecimal throws: StringIndexOutOfBoundsException if the text ends with |
| `integerTo8bitUnsignedHex` | `String integerTo8bitUnsignedHex(int value)` | Formats an integer as uppercase hexadecimal with a minimum width of two digits. Parameters: value - integer to format; values longer than one byte are not truncated Returns: upperc |
| `invoke` | `String invoke(Integer ep, Integer cluster, Integer cmd, Integer timeOut = 0, List<Map<String, String>> fields)` | Builds a Matter invoke hub command from ordered field maps. Each map requires type, num, and data, all Strings; field order is preserved. Parameters: ep - endpoint identifier clust |
| `invoke` | `String invoke(Integer ep, Integer cluster, Integer cmd, Integer timeOut = 0, String data = "1518")` | Builds a Matter invoke hub command using pre-encoded field data. Parameters: ep - endpoint identifier cluster - numeric cluster identifier cmd - Matter command identifier timeOut - |
| `invoke` | `String invoke(String ep, Integer cluster, Integer cmd, Integer timeOut = 0)` | Builds a Matter invoke hub command from an endpoint String. Parameters: ep - endpoint identifier as hexadecimal text cluster - numeric cluster identifier cmd - Matter command ident |
| `invoke` | `String invoke(String ep, Integer cluster, Integer cmd, Integer timeOut = 0, List<Map<String, String>> fields)` | Builds a Matter invoke hub command from an endpoint String and ordered field maps. Each map requires type, num, and data, all Strings; field order is preserved. Parameters: ep - en |
| `levelRefresh` | `String levelRefresh()` | Builds a read request for the endpoint level attribute. Returns: Matter hub command string |
| `lock` | `String lock(String pinCode = null)` | Builds a Matter command to lock the door lock endpoint. Parameters: pinCode - optional PIN encoded in the lock or unlock command Returns: Matter hub command string |
| `off` | `String off()` | Builds a Matter command to turn the endpoint off. Returns: Matter hub command string |
| `on` | `String on()` | Builds a Matter command to turn the endpoint on. Returns: Matter hub command string |
| `onOffRefresh` | `String onOffRefresh()` | Builds a read request for the endpoint on/off attribute. Returns: Matter hub command string |
| `parseArray` | `List<String> parseArray(String tlvString)` | Decodes supported scalar values from a Matter ARRAY TLV. The returned list contains decoded UTF-8 strings, byte-swapped hexadecimal strings for integer and floating-point values, o |
| `parseDescriptionAsMap` | `Map parseDescriptionAsMap(String description)` | Parses an event, invoke-response, or read-attribute description into fields. Invoke response: source comma-separated fields except value are preserved as String values; clusterInt  |
| `readAttributes` | `String readAttributes(List<Map<String, String>> attributePaths)` | Builds a Matter attribute read hub command. Each map is an attribute path with ep, cluster, and attr keys as returned by attributePath(String, Integer, Integer). Parameters: attrib |
| `setColor` | `String setColor(Map value)` | Builds a Matter command from hue and saturation values. value map keys: hue (Number) - hue multiplied by 2.54 and encoded as one byte saturation (Number) - saturation multiplied by |
| `setColorTemperature` | `String setColorTemperature(Number value, Number transition = null)` | Builds a Matter command to set color temperature. Parameters: value - value used to build the Matter command transition - optional transition time; null uses the declared default R |
| `setColorXY` | `String setColorXY(Map value)` | Builds a Matter command from x/y color coordinates. value map keys: x (Number) - x coordinate multiplied by 65536 and encoded as two bytes y (Number) - y coordinate multiplied by 6 |
| `setHue` | `String setHue(Integer value, Number transition = null)` | Builds a Matter command to set hue. Parameters: value - value used to build the Matter command transition - optional transition time; null uses the declared default Returns: Matter |
| `setLevel` | `String setLevel(Number level, Number transition = null)` | Builds a Matter command to set the endpoint level. Parameters: level - value used by this Matter operation transition - optional transition time; null uses the declared default Ret |
| `setSaturation` | `String setSaturation(Integer value, Number transition = null)` | Builds a Matter command to set color saturation. Parameters: value - value used to build the Matter command transition - optional transition time; null uses the declared default Re |
| `subscribe` | `String subscribe(Integer minReportTime, Integer maxReportTime, List<Map<String, String>> attributePaths)` | Builds a Matter attribute subscription hub command. Each map is an attribute path with ep, cluster, and attr keys as returned by attributePath(String, Integer, Integer). Parameters |
| `TLVparser` | `Map TLVparser(String value)` | Parses a hexadecimal Matter TLV sequence into its tag map. Each tag identifier (Byte) maps to an element Map. type (String) is the TLV type byte as hexadecimal. isContextSpecific ( |
| `TLVparser` | `Map TLVparser(byte[] tlv)` | Parses a Matter TLV byte sequence into its tag map. Each tag identifier (Byte) maps to an element Map. type (String) is the TLV type byte as hexadecimal. isContextSpecific (Boolean |
| `unlock` | `String unlock(String pinCode = null)` | Builds a Matter command to unlock the door lock endpoint. Parameters: pinCode - optional PIN encoded in the lock or unlock command Returns: Matter hub command string |
| `unsubscribe` | `String unsubscribe()` | Builds the Matter unsubscribe hub command. Returns: Matter hub command string |
| `updateFirmware` | `HubAction updateFirmware()` | Starts the hub-authorized Matter OTA workflow for this node. Returns: action that starts the Matter firmware update |
| `writeAttributes` | `String writeAttributes(List<Map<String, String>> attributeWriteRequests)` | Builds a Matter attribute write hub command. Each map is an attribute write request with ep, cluster, attr, and data keys as returned by attributeWriteRequest(String, Integer, Inte |

## Properties

| Name | Type | Description |
|---|---|---|
| `device` | `DeviceWrapper` | — |
