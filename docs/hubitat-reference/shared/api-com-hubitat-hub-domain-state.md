# State

- **ID:** `api-com-hubitat-hub-domain-state`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.domain.State`

> Use State from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `clone` | `State clone()` | Copy this state and its mutable date value. throws: CloneNotSupportedException if the superclass cannot be cloned Returns: copied state |
| `fromJson` | `State fromJson(Map parsedJson)` | Create a state from parsed JSON properties. Parameters: parsedJson - map of state property names to values Returns: state with copied properties, or null when the input is null |
| `getAttributeName` | `String getAttributeName()` | Returns: this state's attribute name |
| `getDataType` | `String getDataType()` | Returns: data type name, or null when unset |
| `getDate` | `Date getDate()` | Returns: state date, or null when unset |
| `getDeviceId` | `Long getDeviceId()` | Returns: owning device id, or null when unset |
| `getDoubleValue` | `Double getDoubleValue()` | Returns: stored value parsed as a Double |
| `getFloatValue` | `Float getFloatValue()` | Returns: stored value parsed as a Float |
| `getId` | `Long getId()` | Returns: state id, or null when unset |
| `getJsonValue` | `Object getJsonValue()` | Returns: stored value parsed as JSON |
| `getName` | `String getName()` | Returns: state name, or null when unset |
| `getNumberValue` | `BigDecimal getNumberValue()` | Returns: stored value parsed as a BigDecimal |
| `getStringValue` | `String getStringValue()` | Returns: stored value text |
| `getUnit` | `String getUnit()` | Returns: unit label, or null when unset |
| `getValue` | `String getValue()` | Returns: stored value text, or null when unset |
| `setAttributeName` | `void setAttributeName(String name)` | Parameters: name - attribute name |
| `setDataType` | `void setDataType(String dataType)` | Parameters: dataType - data type name |
| `setDate` | `void setDate(Date date)` | Parameters: date - state date |
| `setDeviceId` | `void setDeviceId(Long deviceId)` | Parameters: deviceId - owning device id |
| `setId` | `void setId(Long id)` | Parameters: id - state id |
| `setName` | `void setName(String name)` | Parameters: name - state name |
| `setUnit` | `void setUnit(String unit)` | Parameters: unit - unit label |
| `setValue` | `void setValue(String value)` | Parameters: value - stored value text |
| `toJsonMap` | `Map toJsonMap()` | Convert the state record to a JSON property map. id: nullable Long state id. date: nullable Date when the state was recorded. name: nullable String state name. unit: nullable Strin |
| `toString` | `String toString()` | Returns: reflective short-form representation of this state |
