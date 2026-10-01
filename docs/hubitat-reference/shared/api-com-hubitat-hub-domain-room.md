# Room

- **ID:** `api-com-hubitat-hub-domain-room`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.domain.Room`

> The hub returns or binds this object to app or driver code; use the documented members on the object you receive.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getDevices` | `List<Device> getDevices()` | Load the room's devices once and sort them by display name without regard to case. Returns: non-null list of existing devices referenced by deviceIds |
| `isHasImage` | `boolean isHasImage()` | Returns: true when this room has a nonzero image id |
| `toMap` | `Map toMap()` | Convert this room to the map returned by room-facing APIs. Returns: map with these keys: id: Long room id; nullable. name: String room name; initialized to an empty String, may be  |

## Properties

| Name | Type | Description |
|---|---|---|
| `deviceIds` | `List<Long>` | — |
| `devices` | `List<Device>` | — |
| `id` | `Long` | — |
| `image` | `Image` | — |
| `imageId` | `Long` | — |
| `name` | `String` | — |
| `usedByString` | `String` | — |
