# State history entry

- **ID:** `api-com-hubitat-hub-domain-statehistoryentry`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.domain.StateHistoryEntry`

> The hub returns or binds this object to app or driver code; use the documented members on the object you receive.

## Methods

| Name | Signature | Description |
|---|---|---|
| `clone` | `StateHistoryEntry clone()` | Copy this entry and its mutable date value. throws: CloneNotSupportedException if the superclass cannot be cloned Returns: copied entry |
| `getDate` | `Date getDate()` | Returns: history date, or null when unset |
| `getDoubleValue` | `Double getDoubleValue()` | Returns: stored value parsed as a Double |
| `getFloatValue` | `Float getFloatValue()` | Returns: stored value parsed as a Float |
| `getJsonValue` | `Object getJsonValue()` | Returns: stored value parsed as JSON |
| `getNumberValue` | `BigDecimal getNumberValue()` | Returns: stored value parsed as a BigDecimal |
| `getStringValue` | `String getStringValue()` | Returns: stored value text |
| `getValue` | `String getValue()` | Returns: stored value text, or null when unset |
| `setDate` | `void setDate(Date date)` | Parameters: date - history date |
| `setValue` | `void setValue(String value)` | Parameters: value - stored value text |
| `toString` | `String toString()` | Returns: reflective short-form representation of this entry |
