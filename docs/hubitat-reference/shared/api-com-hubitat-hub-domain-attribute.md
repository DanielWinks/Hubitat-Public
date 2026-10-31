# Attribute

- **ID:** `api-com-hubitat-hub-domain-attribute`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.domain.Attribute`

> Use Attribute from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `Attribute` | `Attribute()` | Create an empty attribute. |
| `Attribute` | `Attribute(Long id, String name)` | Create an attribute with its id and name. Parameters: id - attribute id name - attribute name |
| `clone` | `Attribute clone()` | Copy this attribute with a separate allowed-values list. throws: CloneNotSupportedException if the superclass cannot be cloned Returns: copied attribute |
| `equals` | `boolean equals(Object obj)` | Compare attribute name, type, and allowed values. Parameters: obj - object to compare Returns: true when fields match; allowed values are compared without regard to order |
| `fromJson` | `Attribute fromJson(Map parsedJson)` | Create an attribute by copying matching properties from parsed JSON. Parameters: parsedJson - map of attribute property names to values Returns: populated attribute, or null when t |
| `getCapability` | `boolean getCapability()` | Returns: true when this attribute describes a capability |
| `getDataType` | `String getDataType()` | Returns: attribute data type name, or null when unset |
| `getDeviceTypeId` | `Long getDeviceTypeId()` | Returns: owning device type id, or null when unset |
| `getId` | `Long getId()` | Returns: attribute id, or null when unset |
| `getName` | `String getName()` | Returns: attribute name, or null when unset |
| `getPossibleValueJson` | `String getPossibleValueJson()` | Serialize the allowed values as JSON. Returns: JSON array text, or JSON null when no values are set |
| `getPossibleValues` | `List<String> getPossibleValues()` | Returns: allowed value strings, or null when unset |
| `getValues` | `List<String> getValues()` | Returns: allowed value strings, or null when unset |
| `getVersion` | `Long getVersion()` | Returns: entity version, or null when unset |
| `isCapability` | `boolean isCapability()` | Returns: true when this attribute describes a capability |
| `setCapability` | `void setCapability(boolean capability)` | Parameters: capability - whether this attribute describes a capability |
| `setDataType` | `void setDataType(String dataType)` | Parameters: dataType - attribute data type name |
| `setDeviceTypeId` | `void setDeviceTypeId(Long deviceTypeId)` | Parameters: deviceTypeId - owning device type id |
| `setId` | `void setId(Long id)` | Parameters: id - attribute id |
| `setName` | `void setName(String name)` | Parameters: name - attribute name |
| `setPossibleValues` | `void setPossibleValues(List<String> possibleValues)` | Parameters: possibleValues - allowed value strings, or null to clear them |
| `setPossibleValuesJson` | `void setPossibleValuesJson(String json)` | Parse allowed values from JSON array text. Parameters: json - JSON array of strings; null or empty text clears the values |
| `setVersion` | `void setVersion(Long version)` | Parameters: version - entity version |
| `toMap` | `Map toMap()` | Returns: map containing id, dataType, and name fields |
| `toMap` | `Map toMap(boolean full)` | Convert the attribute to a map. id: nullable Long attribute id. dataType: nullable String type name. name: nullable String attribute name. values: when full is true, a List of allo |
| `toString` | `String toString()` | Returns: attribute name, or null when unset |
