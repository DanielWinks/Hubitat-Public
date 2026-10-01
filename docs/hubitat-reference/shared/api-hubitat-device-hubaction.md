# Hub action

- **ID:** `api-hubitat-device-hubaction`
- **Section:** Shared APIs
- **Class:** `hubitat.device.HubAction`

> Use HubAction from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getAction` | `String getAction()` | Returns: raw request text stored by this action |
| `getCallbackMethod` | `String getCallbackMethod()` | Returns: callback method name, or null when no callback option is set |
| `getURI` | `URI getURI()` | Returns: URI built from the Host header, defaulting to port 80, or null when no host exists |
| `HubAction` | `HubAction()` | Create an empty action. |
| `HubAction` | `HubAction(Map params, String dni = null, Map options = null)` | Create a LAN HTTP request from a parameter map. Parameters: params - map with these optional request fields: method: optional truthy value converted into the request line; defaults |
| `HubAction` | `HubAction(String val)` | Wrap an existing request without assigning a protocol. Parameters: val - raw request text |
| `HubAction` | `HubAction(String request, Protocol protocol, Map options = null)` | Create an action from request text and a protocol. Parameters: request - raw request or protocol payload protocol - action protocol options - optional action metadata with the sche |
| `HubAction` | `HubAction(String request, Protocol protocol, String dni, Map options = null)` | Create an action from request text, a protocol, and a device network id. Parameters: request - raw request or protocol payload protocol - action protocol dni - device network id to |
| `toString` | `String toString()` | Returns: the raw request text |

## Properties

| Name | Type | Description |
|---|---|---|
| `deviceNetworkId` | `String` | — |
| `options` | `Map` | Optional action metadata with app-defined keys: callback: optional callback method name (String) or callback MetaMethod; getCallbackMethod() returns its name. Other keys are retain |
