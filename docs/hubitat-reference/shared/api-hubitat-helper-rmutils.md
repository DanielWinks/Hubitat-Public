# RM utils

- **ID:** `api-hubitat-helper-rmutils`
- **Section:** Shared APIs
- **Class:** `hubitat.helper.RMUtils`

> Use RMUtils from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getRuleList` | `List getRuleList(String version = '4.1')` | Read the saved Rule Machine rule list for a supported app version. throws: IllegalArgumentException when version is not supported Parameters: version - Rule Machine version; suppor |
| `sendAction` | `void sendAction(List rules, String action, String appLabel, String version = '4.1')` | Post a Rule Machine action event to the current location. throws: IllegalArgumentException when version is not supported Parameters: rules - saved rules; expected to be a list of s |
