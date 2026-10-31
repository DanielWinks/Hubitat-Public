# Hub multi action

- **ID:** `api-hubitat-device-hubmultiaction`
- **Section:** Shared APIs
- **Class:** `hubitat.device.HubMultiAction`

> Use HubMultiAction from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `add` | `void add(HubAction hubAction)` | Append an existing action. Parameters: hubAction - action to append |
| `add` | `void add(HubMultiAction hubMultiAction)` | Append all actions from another multi-action. Parameters: hubMultiAction - multi-action whose entries are appended |
| `add` | `void add(List hubActions)` | Append each action in the supplied list. Parameters: hubActions - list of HubAction values to append |
| `add` | `void add(String cmd)` | Infer a protocol and append the command as a new action. Parameters: cmd - command string |
| `HubMultiAction` | `HubMultiAction()` | Create an empty multi-action. |
| `HubMultiAction` | `HubMultiAction(List<String> cmds)` | Create actions from command strings, inferring each command's protocol. Parameters: cmds - command strings to turn into HubAction entries |
| `HubMultiAction` | `HubMultiAction(List<String> cmds, Protocol protocol)` | Create actions from command strings using one protocol. Parameters: cmds - command strings to turn into HubAction entries protocol - protocol assigned to each action |
| `HubMultiAction` | `HubMultiAction(List<String> cmds, Protocol protocol, String deviceNetworkId)` | Create actions for a device using one protocol and device network id. Parameters: cmds - command strings to turn into HubAction entries protocol - protocol assigned to each action  |

## Properties

| Name | Type | Description |
|---|---|---|
| `actionList` | `List<HubAction>` | — |
