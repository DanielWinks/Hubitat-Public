# Sonos Advanced Architecture

> Working map of the implementation in this repository as of 2026-11-24. This describes current code to support design iteration; it is not a promise that every current boundary is the desired long-term design.

## Scope and package shape

Sonos Advanced is a Hubitat package in the `dwinks` namespace. HPM metadata is in `PackageManifests/SonosAdvancedController/packageManifest.json`. The production app and drivers are standalone Groovy files; the root `Bundles/` directory is generated and must not be edited by hand.

| Part | Current responsibility |
| --- | --- |
| `Apps/SonosAdvancedApp.groovy` | Parent controller: UI and configuration, local discovery, Sonos protocol/network work, child-device lifecycle, state fanout, grouping, announcements, alarms, updates and package maintenance. It is `singleThreaded:true`. |
| `Drivers/Component/SonosAdvPlayer.groovy` | Primary speaker device commands, playback state, WebSocket connection/command handling, and player-to-app request/state callbacks. |
| `Drivers/Component/SonosAdvGroup.groovy` | Configured logical groups and group-level commands/announcements. It maintains group runtime state and queues requests to the app. |
| `Drivers/Component/SonosAdvSecondaries.groovy` | Secondary/paired speaker representation and related topology/device data. |
| `Drivers/Component/SonosAdvSnapshot.groovy` | Snapshot/restore command surface delegated to the parent app. |
| `Drivers/Component/SonosAdvFavorites.groovy`, `SonosAdvPlaylist.groovy` | Favorite and playlist controls and display state, delegated to the app. |
| `Drivers/Component/SonosAdvQueue.groovy`, `SonosAdvSleepTimer.groovy` | Queue and sleep timer control/display surfaces delegated to the app. |
| `Drivers/Component/SonosAdvAlarmManager.groovy`, `SonosAdvAlarmSwitch.groovy` | Household alarm manager and per-alarm switch devices; requests are sent to the app. |
| `Drivers/Component/SonosAdvBatteryStats.groovy` | Battery-statistics component state delivery. |
| `Bundles/SonosAdvancedController/dwinks.SMAPILibrary.groovy` | Legacy Sonos service/action definitions and SOAP/event helper implementation, distributed as a Hubitat library. The app does not declare a source-level include; verify how the installed package/library workflow supplies methods before changing this dependency. |

The app currently contains both end-user Sonos functionality and administrative package tooling (version checks, update/install, driver/library publishing, and duplicate cleanup). These are separate responsibilities in code, but not separate runtime components.

## Runtime topology

```mermaid
flowchart TD
  UI[Hubitat app UI / preferences] --> App[SonosAdvancedApp\nserialized parent controller]
  User[Rules, dashboards, user commands] --> Player[Player driver]
  User --> Group[Group driver]
  User --> Feature[Feature child drivers]
  Player -->|queued app request / state callback| App
  Group -->|queued app request| App
  Feature -->|parent delegation or request event| App
  App -->|local HTTP, SOAP/UPnP, SSDP/mDNS| Speakers[Sonos household]
  Speakers -->|UPnP notifications / WebSocket| App
  Speakers -->|WebSocket playback events| Player
  App -->|queued child attribute batches| Player
  App -->|state/display updates| Group
  App -->|child lifecycle and feature state| Feature
```

The app is the household coordinator and owns most authoritative integration workflows. Player drivers own the live player socket and command semantics that need to remain close to a device. Drivers expose Hubitat capabilities and commands, but many feature commands forward to the parent for household context or local Sonos operations.

## Planned subscription child

The target design adds a dedicated child device owned by the Sonos Advanced Controller (SAC) app. This child owns and processes household-level and group-level UPnP event subscriptions. It provides one place to manage subscription lifetimes and handle event notifications that describe shared household topology or state.

| Subscription service | Owner in target design | Lifetime |
| --- | --- | --- |
| `/AlarmClock/Event` | Subscription child | Maintained continuously |
| `/MusicServices/Event` | Subscription child | Maintained continuously |
| `/SystemProperties/Event` | Subscription child | Maintained continuously |
| `/ZoneGroupTopology/Event` | Subscription child | Maintained continuously |
| `/MediaRenderer/AVTransport/Event` | Subscription child | Created and destroyed as group membership changes |
| `/MediaRenderer/Queue/Event` | Player device | Maintained on each player device |
| `/MediaRenderer/RendererControl/Event` | Player device | Maintained on each player device |
| `/MediaServer/ContentDirectory/Event` | Player device | Maintained on each player device |

The continuously maintained subscriptions are household/group scope and remain active regardless of current group membership. AVTransport is the exception: its subscriptions follow active group membership and must be reconciled when topology changes, creating subscriptions for the relevant group-level targets and removing subscriptions that are no longer needed. The precise AVTransport target selection and whether a group is represented by its coordinator or by another target should follow the existing event semantics and be specified before implementation.

Queue, RendererControl, and ContentDirectory events remain on player devices because they are player-specific. Moving household/group subscriptions into the new child must not duplicate these player subscriptions or make the subscription child responsible for player-only state.

```mermaid
flowchart TD
  SAC[SAC app] -->|creates / configures| Sub[Household and group subscription child]
  Sub -->|persistent subscriptions| Shared[AlarmClock, MusicServices, SystemProperties, ZoneGroupTopology]
  Sub -->|topology-driven lifecycle| AVT[AVTransport subscriptions]
  Topology[Current group membership] -->|reconcile on changes| Sub
  Player[Player child devices] -->|player-specific subscriptions| Local[Queue, RendererControl, ContentDirectory]
```

This is planned architecture. The current source has app-side UPnP subscription and notification processing; it does not yet implement this dedicated subscription child. During migration, document which app responsibilities move, the child-to-app event/data contract, subscription renewal/recovery behavior, and how current persisted subscription identifiers are handled.

## Main data and protocol paths

### Discovery and child lifecycle

The app discovers players through SSDP and mDNS, stores temporary discovery results, and presents selection/configuration pages. Identifiers are reconciled across MAC, RINCON/player ID, IP, and Hubitat DNI to avoid duplicate devices. A quick topology query can enrich discovered device properties. Selected primary players are created as player children; secondary speakers and optional feature children are managed from topology/configuration and preferences. Lifecycle/configuration logic subscribes to child request/state events and arms schedules and refresh work.

The app persists configured device selections and user-defined groups in settings/state. A whitelist of active persistence keys is used to prune orphaned persisted values on save, with explicit migration handling for selected legacy keys. Treat existing setting/state names as install data, not private implementation details.

### Playback state

Player WebSocket events update the player driver. The driver reports playback state to the app through its app-request/state callback path. The app normalizes and coalesces player state updates, then queues attribute batches for relevant player and group children. Drains are bounded and scheduled to limit app-gate time and Hubitat event churn; unchanged values are skipped and failed child delivery can be retried. Group playback state is derived from coordinator/topology context and published separately from raw per-player state. Track-position updates are optional.

UPnP AVTransport/RenderingControl events and ZoneGroupTopology (ZGT) observations also feed app-side state/topology processing. Cached AVTransport state can be replayed when a player becomes a coordinator. Album-art URLs are normalized/resized at fanout. Avoid adding a second source of truth for attributes without defining precedence against WebSocket and UPnP ownership.

### Commands and concurrency

Player and group drivers enqueue requests to the app; app drains process those requests and dispatch local Sonos commands. Player requests and group requests have distinct queues. Group commands may be deferred behind active topology operations; playback commands can wait for grouping to settle. Grouping itself is a stateful operation: the app builds a target, sends topology commands, observes fresh topology, checks stability/deadlines/retries, and publishes completion/failure.

Hubitat serializes the app's entry points because it is single-threaded. A synchronous child-to-parent call holds the child's execution while waiting on the app gate; app-to-child calls can create the reverse lock ordering. Hot paths therefore enqueue work and return, with app drains and child event/state drains scheduled separately. In particular, do not call parent app methods from a player's high-frequency `parse` path or drain a queue by synchronously calling child commands while holding the app gate. Keep bridge traffic in private queues/callback payloads; user-visible attributes should represent product state, not internal transport.

Player drivers use in-memory concurrent runtime queues for hot work where concurrent `parse` executions are possible. Queue updates are guarded by locks and drained using claim/swap patterns. App queues are serialized through the app gate and generally live in `state`. Any refactor of queue ownership must preserve restart recovery, stale-request handling, bounded drains, and acknowledgement semantics.

### Announcements and audio clips

The app owns per-group announcement queues and policy, including priority, retention, chime behavior, and serialization. It selects a playback strategy based on the source and current household topology: standard stream/temporary stream holder or synchronized audio clips; AirPlay bypasses stream-holder handoff. Audio clip work is queued per player and dispatched to a local app endpoint, with callbacks/status events driving progression. Group announcement progress is correlated back to queue items; completion restores playback/topology as applicable before advancing. Queue state and identifiers are persisted to support recovery and stale-work rejection.

Preserve the separation between command acceptance, clip/announcement progress, restore completion, and queue advancement. These are asynchronous transitions, not one synchronous operation.

### Alarms and feature children

Alarm management is opt-in. The app communicates with the local Sonos AlarmClock UPnP service, refreshes household alarms, and manages one alarm switch per alarm plus a household Alarm Manager device. Alarm drivers send requests; state updates are batched. Favorites, playlists, queue, sleep timer, snapshot, and secondary devices are smaller child-facing surfaces whose Sonos context/actions are largely coordinated by the app.

### Network control and maintenance

Local Sonos control is implemented in the app using asynchronous HTTP/SOAP requests, UPnP event subscriptions, WebSocket/topology flows, retry metadata, and response validation. Request callbacks carry identifiers/context so stale responses can be ignored. The SMAPI library in the bundle defines service maps and SOAP/event helpers; the app also has a substantial local HTTP/retry boundary.

The same app includes optional update checking and installation/maintenance flows that authenticate to the Hubitat hub, inspect installed app/driver/library code, compare package versions, upload updates, publish libraries, and clean duplicates. These flows are administrative and should remain isolated from playback/discovery state changes if the app is reorganized.

## State ownership (current)

| Data | Primary owner | Notes |
| --- | --- | --- |
| User configuration (selected players/groups, feature toggles, announcement and update preferences) | App `settings` | Preference names/types are compatibility-sensitive. |
| Discovery in progress and probe counters | App `atomicState` | Low-frequency discovery coordination. |
| Pending app work (player/group commands, child state/events, topology, announcements, clips, retries) | App `state` | App is serialized; scheduled drains provide bounded processing and some restart recovery. |
| Live socket, pending WebSocket commands and hot player queues | Player driver runtime state / static concurrent structures and driver `state` | Some hot concurrent paths require lock-protected in-memory queues. Check each driver before relocating data. |
| Current product attributes | Child driver state/events | App delivers state batches; driver attributes are visible to Hubitat users and automations. |
| Group runtime cache / operation state | Group driver plus app operation state | Group driver queues commands; app owns topology orchestration. |

The tables above are a map, not a complete schema. The `ACTIVE_*` whitelists in the app and drivers are the code-level inventory of deliberately retained persistence keys.

## Refactoring boundaries and invariants

These are constraints visible in the implementation and repository guidance; proposed design changes should make any deliberate change explicit.

1. **Keep Hubitat entry points short.** Network callbacks, player parsing, and child command entry points should enqueue/defer work when processing could call across the app/driver boundary or do substantial work.
2. **Preserve parent-child contracts as a unit.** App and driver releases are version-synchronized. Change both sides together; do not add old-version fallback shims.
3. **Preserve persisted names or migrate intentionally.** Settings and state can survive code updates on installed hubs. A rename/removal needs an explicit migration and tests.
4. **Keep state provenance clear.** Define the owner and freshness/precedence of WebSocket, UPnP, topology, and derived group values before splitting or duplicating processing.
5. **Keep asynchronous operations explicit.** Requests, acknowledgements, callbacks, deadlines, retries, queue progression, and stale-work cancellation are observable states that need their own contracts.
6. **Bound fanout and drains.** Preserve batching/coalescing and failure retry behavior to avoid long app-gate holds and excess event writes.
7. **Keep production artifacts standalone where practical.** New app/driver code should not introduce shared-library includes. Existing library/package behavior is legacy and should be migrated only with an explicit compatibility plan.
8. **Keep protocol parsing and pure transformations separable.** Typed parsers, formatters, and calculations are good candidates for isolated helpers and regression tests; Hubitat APIs and dynamic device dispatch belong at narrow edges.
9. **Do not edit generated bundles.** Update source and package metadata/workflows as appropriate; let CI regenerate `Bundles/`.

## Current code seams to iterate on

These are candidate seams inferred from the current file layout, not a prescribed target architecture:

- The app is roughly 12,000 lines and combines UI, discovery, protocol transport, event processing, command queues, group operation state machines, announcements, alarms, and package administration. Its section boundaries are the likely first decomposition map.
- Several independent queues and drain schedulers live in the app. A refactor can clarify a queue contract (producer, payload, coalescing key, retry/ack policy, persisted lifetime) without changing the app/driver boundary first.
- State ingestion spans player WebSocket callbacks and app-side UPnP/ZGT processing. A normalization/ownership map should precede extraction to avoid races or stale overwrites.
- Group topology operations and announcement restoration both observe/change household topology. Their coordination boundary and cancellation behavior deserve explicit state diagrams before moving code.
- The app mixes user control with Hubitat code maintenance and self-update behavior. These can be documented and tested as separate subsystems even while hosted by one app.
- `SMAPILibrary` remains a large legacy protocol catalog/helper. Decide whether it is a maintained package dependency or a future inline/extracted protocol module; do not assume the source-level app alone shows deployment behavior.
- The planned subscription child creates a new protocol ownership boundary: household/group event subscriptions and notification processing move there, while the player driver retains Queue, RendererControl, and ContentDirectory subscriptions. AVTransport subscription lifecycle must track group topology dynamically.

## Useful code and test entry points

- Main controller: `Apps/SonosAdvancedApp.groovy`
- Player and group behavior: `Drivers/Component/SonosAdvPlayer.groovy`, `Drivers/Component/SonosAdvGroup.groovy`
- Secondary/topology device: `Drivers/Component/SonosAdvSecondaries.groovy`
- Protocol definitions: `Bundles/SonosAdvancedController/dwinks.SMAPILibrary.groovy`
- Package inventory/version: `PackageManifests/SonosAdvancedController/packageManifest.json`
- App and player specs: `tests/src/test/groovy/dwinks/hubitat/functional/SonosAdvancedAppSpec.groovy`, `SonosAdvPlayerSpec.groovy`
- Group and feature specs: sibling `SonosAdv*Spec.groovy` files in `tests/src/test/groovy/dwinks/hubitat/functional/`
- Existing behavior notes: `Readme/SonosAdvanced-Playback-Volume.md` and `Readme/SonosFavorites-README.md`

When refining this document, record the intended target separately from current behavior, then update the relevant contracts, persistence migration plan, and regression coverage as design decisions become firm.
