# Plan: Remove child→app queues and drains (Sonos Advanced)

Status: proposal, revision 4, 2026-11-26. Baseline commit `918bcc58`.

## 1. Target contract

1. Child drivers (player, group) call parent app methods **directly**: no driver-side
   outbox, no flush callback, no `runInMillis` hop whose only job is to release a gate.
2. A parent method that a child calls (a **child entry point**) only reads and writes
   app data, reads child wrappers (`getDataValue`, `currentValue`,
   `getDeviceNetworkId`) and calls `asynchttp*`. It then ends with one of these:
   - a plain `return` (the return value is the acknowledgement), or
   - `runInMillis(1, '<callback>', [data: ..., overwrite: false])`. Any work that writes
     to a child goes in that callback, which runs in a new execution.
3. "Writes to a child" means driver-defined methods (`playerPlay`, `zgtElectionResult`,
   `acknowledgeParentCommand`, ...), child-app methods (`storeCatalogSnapshot`),
   `child.sendEvent`, app-level `sendEvent(dni, ...)` and `updateDataValue`.
4. No `PENDING_*` queue, `*DrainScheduled` flag, received-ID dedupe, ack or retry loop
   exists only to carry child→app traffic. Hubitat's scheduler is the only deferral
   mechanism. Order comes from sequence numbers in the data, not from queues (§2.3).
5. **The Sonos Advanced app, `SonosAdvPlayer` and `SonosAdvGroup` are all
   `singleThreaded: false`.** This reverses the current AGENTS.md rule ("do not flip the
   app") and today's `918bcc58` player flip; step 9 rewrites the docs.

## 2. Execution model

### 2.1 Why this is deadlock-free

A deadlock needs a cycle in which every party holds a gate while it waits for another.
With the app, players and groups all ungated, none of them holds a gate, so no deadlock
cycle can form among them. The Discovery, Favorites and Playlists child apps stay
`singleThreaded: true`. They wait on nothing when they call the ungated parent, and
when the parent calls them it holds nothing while it waits. That rules out the
120 s gate timeout as a failure mode.

Contract rule 2 still matters, for different reasons:
- **Short child executions:** a child thread never waits on child work done for
  another device.
- **No recursion:** a child → app → same-child call would otherwise recurse through
  the same device.
- **A checkable shape:** every child→app path ends in a return or a single deferred
  callback.

### 2.2 The cost: concurrent data access

Measured on the C-7 (platform 2.5.3.136, §5): `state` commits **per changed key** when
an execution ends, so executions writing different keys don't clobber each other. Any
read-modify-write of one key in `state` loses updates, **even inside a static lock**: the
lock is released before the commit. `atomicState` read-modify-write is safe only inside a
static lock. Groovy `synchronized` *method modifiers* in a Hubitat script lock only the
per-execution instance, so they don't exclude anything. Only `synchronized(<static
object>)` blocks do.

So a key may stay in `state` only if it is written by plain overwrite, never
read-modify-write, or if exactly one method writes it and that method cannot run
concurrently with itself.

**App** (488 `state` references, 75 distinct keys written, 6 useless `synchronized`
methods): classify every key into one of these storage patterns.

| Class | Examples | Storage |
|---|---|---|
| **Overwrite-only or single-writer** | update-check info, policy settings written by configure, last-error diagnostics | Stay in `state`. No lock needed: commits are per key. |
| **Low-frequency shared read-modify-write** | preference snapshots by DNI, ZGT election list | `atomicState` inside `appLock(key)`. A lock around `state` does not work (S6). |
| **In-memory working data**: re-derivable caches *and* transient work that need not survive a reboot | AVT cache by DNI, ZGT topology and payload, GRC cache, player readings, held GRC, sequence high-water marks, **group-announcement queues, audio-clip queues**, catalog snapshots | `@Field static ConcurrentHashMap<String, Map>` keyed by `app.id`, read-modify-write under `appLock(key)`. A reboot or code save empties these; subscriptions refill the caches, and pending announcements and clips are dropped. |
| **Recoverable work state** | active group operation, deferred operation commands, alarm jobs in flight | `atomicState` read-modify-write inside `appLock(key)` through `mutateAppData(String key, Closure)`; never touched through `state` |

Helpers, kept local to the app file:
- `@Field static final ConcurrentHashMap<String, Object> APP_LOCKS`
- `Object appLock(String key)` returns `APP_LOCKS.computeIfAbsent("${app.id}:${key}")`
- `Map appMemory(String key)`
- `Object mutateAppData(String key, Closure fn)`

Each key has exactly one access path, enforced by lint (step 7). The lint rule also
fails any read-modify-write of `state` (`state.x = f(state.x)`, or `state[k]` read,
then mutated, then reassigned) outside the single-writer allow-list.

**Player driver** (44 `state` references, no `synchronized` method modifiers). Its 42
`synchronized(getRuntimeState())` blocks already lock a static, per-DNI
`ConcurrentHashMap`, so they stay correct. Move the hot or transient `state` keys into
`runtimeState`:
- `playbackState` and `playbackStateObservedAt` (written in `parse`)
- the stale-callback tokens `favoriteLoadRequestId`, `playlistLoadRequestId`,
  `playlistAddRequestId` and `queueBrowseRequestId`
- `htControlTosLinkConnected` and `htControlObservedAt`

`lastError*` and the hidden-state HTML display keys stay in `state`; they are single-key,
last-writer-wins diagnostics.

**Group driver** (5 `state` references, all clears). No changes needed beyond the flag.

### 2.3 Ordering without queues

Callbacks and child executions can now run in parallel. Every ordered stream therefore
carries a sequence number and is checked twice, under one lock per stream:
1. **Accept gate (entry point):** drop an arrival whose sequence is not newer than the
   last one accepted.
2. **Apply gate (`runInMillis` callback):** drop a callback whose sequence is older than
   the last one applied. This handles callbacks that run out of order.

**One sequence source for every stream:** the child driver stamps each payload with
`[epoch, sequence]`.
- `sequence` comes from a per-DNI `AtomicLong` in driver memory. It is stamped when the
  payload is first observed: on receipt in `parse` for reports, and when the command is
  issued for commands.
- `epoch` is a per-driver-load identifier, `@Field static final Long DRIVER_EPOCH`, set
  when the class loads. A hub reboot or driver code save resets the counters and gets a
  new epoch, so the app accepts the first payload with an unfamiliar epoch and resets
  that stream's high-water mark.
- The UPnP NOTIFY `SEQ`/`SID` headers are **not** used. Their per-subscription reset and
  wraparound rules aren't worth the extra handling.

| Stream | Payloads | Stream key |
|---|---|---|
| UPnP-derived reports | ZGT (`setZgtPayload`), AVT (`putAvtEvents`), RenderingControl (`playerReading`), GroupRenderingControl (`groupRendering`), AlarmClock and AlarmRunning (`putPlayerUpnpEvents`), topology operation events | `sourceDni:channel` |
| WebSocket-derived reports | `playerAttributeUpdate`, `groupAnnouncementProgress`. Announcement phases are also monotonic per `correlationId`. | `sourceDni:channel`; `ann:correlationId` |
| Commands | `playerCommandRequest`, `groupAppRequest` | `cmd:targetDni` |

High-water marks live in app memory. After an app reboot or code save the map is empty,
so the first arrival on each stream is accepted.

Commands:
- **Latest-wins commands** (transport, volume, mute, shuffle, repeat, set track) drop
  anything older than the last processed command for that target.
- **Additive commands** (group membership, line-in, favorite or playlist load) are
  processed under the target lock in the order they arrive, with no drop. Their start
  order still isn't guaranteed (§5, Q1).

## 3. Inventory and target per channel

### Player → app

| Channel (app method) | Driver today | App today | Target |
|---|---|---|---|
| `playerAppRequest` (refreshPolicy, requestZgtElection, needCurrentlyPlayingStates, enqueueAudioClip, playerReading, groupRendering) | `pendingParentRequestsByDni` + `flushParentAppRequests` (`SonosAdvPlayer.groovy:909, 2250-2296`) | Dispatched inline per action (`SonosAdvancedApp.groovy:3241`) | Driver: `sendParentAppRequest` calls `parent.playerAppRequest(payload)` directly; delete the map and the flush. App: see the per-action rows below. |
| ↳ `refreshPolicy` | | Inline `player.updateDataValue` | `runInMillis(1, 'refreshPlayerPolicyCallback', [data: [playerDni]])` |
| ↳ `requestZgtElection`, `needCurrentlyPlayingStates` | | Already `runInMillis(1)` | `readyForZgt` read-modify-write goes under `appLock` |
| ↳ `enqueueAudioClip` | | state + `asynchttpPost` | Clip queue moves to app memory under its lock |
| ↳ `playerReading` | | state + `queueVolumeRecalculation` (pending map + 500 ms drain) | Accept gate, store the reading in memory, then `runInMillis(1, 'recalculateVolumeStateCallback', [data: [players: [id], epoch, sequence]])`. Delete `PENDING_VOLUME_RECALCULATION` and `drainVolumeRecalculation` (see §5, Q2). |
| ↳ `groupRendering` | | Inline `applyDirectGroupRendering`, which calls `sendEvent(dni)` on members | Accept gate and GRC cache update, then `runInMillis(1, 'publishGroupRenderingCallback', [data: [sourceId, epoch, sequence]])` with the apply gate |
| `playerCommandRequest` | `pendingParentCoordinatorCommands` + active + `drainParentCoordinatorCommandQueue` + ack + retry (`:2885-2970`) | `PLAYER_COMMAND_QUEUE` + received-ID dedupe + `runIn(1)` drain + sequence sort (`:7156-7230`) | Driver: calls `parent.playerCommandRequest([playerDni, command, args, createdAt, epoch, sequence])` directly; the boolean return is the acknowledgement. Delete active, retry and ack. App: validate, then `runInMillis(1, 'processPlayerCommandRequest', [data: [request: r], overwrite: false])`, ordered as in §2.3. Keep the group-operation deferral as product logic, under the operation lock. |
| `playerGroupTopologyOperationUpdate` | runtime list + flush + retry (`:3280-3325`) | `PENDING_GROUP_TOPOLOGY_EVENTS` + drain (`:6972-6990`) | Driver: calls directly with `epoch` and `sequence`. App: `runInMillis(1, 'groupTopologyOperationEventHandler', [data: payload, overwrite: false])` under `appLock('groupOperation')`. |
| `groupAnnouncementProgress` | Direct | Progress queue + 1 s debounce drain (`:1531-1570`) | App: `runInMillis(1, 'groupAnnouncementProgressCallback', [data: item, overwrite: false])`, with monotonic phases under `ann:correlationId`. |
| `playerAttributeUpdate` | Direct | Pending map + `drainPlayerAttributeUpdates` (`:2924-2965`) | App: filter and accept gate, then `runInMillis(1, 'applyPlayerAttributeUpdateCallback', [data: [playerDni, attributes, epoch, sequence], overwrite: false])` with the apply gate. |
| `putPlayerUpnpEvents` (AlarmClock, AlarmRunning) | `runInMillis(1)` hop (`:4238`) | `PENDING_UPNP_NOTIFICATION_QUEUE` + 500 ms drain with merge (`:6248-6285`) | Driver: calls directly with `epoch` and `sequence`. App: accept gate, then `runInMillis(1, 'processUpnpNotificationCallback', [data: copy, overwrite: false])` (see §5, Q3). |
| `setZgtPayload` | `runInMillis(1)` hop (`:4227`) | state + projection, then `runInMillis(1, 'publishZgtPlayerAttributes')` | Driver: calls directly with `epoch` and `sequence`. App: accept gate; topology moves to memory under the lock. Confirm `putZgtTopology` and `getPlayerNameForRincon` only read. |
| `putAvtEvents`, `requestCoordinatorEvents` | `runInMillis(1)` hops (`:4255, 4285`) | state + `runInMillis(1, 'applyAvtCallback')` | Driver: delete the hops (keep a local hop only if `ensureFavoritesAndPlaylistsLoaded` does I/O); AVT carries `epoch` and `sequence`. App: AVT cache in memory, plus accept and apply gates. |
| `putCatalogSnapshot` | runtime pending map + relay + 30 s retry (`:7849-7890`) | Pending snapshots + `runIn(1)` drain + undelivered retry (`:800-830`) | Driver: calls directly. App: latest snapshot per catalog in memory, then `runInMillis(1, 'deliverCatalogSnapshot', [data: [catalogType]])`. |
| `playerPreferencesUpdated` | `runInMillis(1)` hop | state only | Driver: calls directly from `updated()`. App: lock the snapshot map. |

**Dead code to delete:**
- App: `playerStateUpdate`, `scheduleQueuedPlayerStateUpdateDrain`,
  `drainQueuedPlayerStateUpdates`, `mergePlayerStateUpdateQueues`,
  `playerStateUpdateCoalesceKey`, `rearmPlayerStateUpdateDrainAfterRestart` and its
  `systemStartHandler` call, `playerStateUpdateHandler` and its `sonosPlayerStateUpdate`
  unsubscribe, `handlePolledPlayerRequest`, `acknowledgePlayerCommandRequest`, and the
  `acknowledgeSource` path.
- Driver: `publishPlayerStateUpdate`, `enqueueParentStateUpdate` and
  `dispatchParentStateUpdateDirect`, which become direct parent calls at the call site.

### Group → app

| Channel | Driver today | App today | Target |
|---|---|---|---|
| `groupAppRequest` | Already direct; `enqueueGroupAppRequest` is misnamed. Dead: `pendingGroupRequests`, `PARENT_REQUEST_*`, the two poll and flush stubs | `GROUP_COMMAND_QUEUE` + received-ID dedupe + `runIn(1)` drain | Rename to `sendGroupAppRequest`; the existing `groupCommandSequence` becomes the per-DNI `AtomicLong` with `DRIVER_EPOCH`; delete the dead code. App: validate, `mergeGroupVolumePreferences` under the lock, then `runInMillis(1, 'processGroupCommandRequest', [data: [request: r], overwrite: false])`, ordered as in §2.3. Keep `drainDeferredGroupOperationCommands`. `initializationPurge` runs under the lock and returns. |
| `groupPreferencesUpdated`, initialization purge | `runInMillis(1)` hops | state | Driver: calls directly. App: lock. |

### Out of scope (kept: product behavior, not child→app transport)

- Announcement and clip queue *behavior* (priority, chime, serialization) and
  group-operation deferral. Only their storage moves (§2.2).
- Player-local `transportCommandQueue`, `upnpRequestQueues` and
  `pendingGroupedPlayerVolumes`. They already sit under `synchronized(getRuntimeState())`.
- App→group delivery (`PENDING_GROUP_STATE_QUEUE`). With everything ungated, this can
  become a direct `sendEvent` in a later change.
- Alarm Switch, Alarm Manager and Discovery child apps: audit them next with the same
  rule.

## 4. Implementation sequence

Each step is one commit that changes both the app and the drivers.

1. **App data layer.** Add the lock and memory helpers. Classify all 75 written keys
   (§2.2) and route every access through its pattern; this moves announcement and clip
   queues into memory. Remove the six `synchronized` method modifiers.
2. **Driver data layer.** Move the player's hot and transient `state` keys into
   `runtimeState` (§2.2). Add `DRIVER_EPOCH` and the per-DNI `AtomicLong` sequence maps,
   and stamp `[epoch, sequence]` on every parent payload where it is first observed.
3. **Flip all three to `singleThreaded: false`:** app, player and group. Add a spec that
   asserts the flags.
4. **App entry points.** Convert every entry point in §3 to "accept gate, store, then
   `runInMillis(1)` or return". Add apply gates in the callbacks. Delete the app queues,
   drains, flags and dedupe lists.
5. **Drivers.** Delete the player outbox, flush, coordinator-command queue, ack and retry,
   topology and catalog outboxes, and every `relay*` and `schedule*ParentHandoff` hop.
   Delete the dead group code.
6. **Persistence cleanup.**
   - Add the removed and relocated keys to `pruneOrphanedAppPersistence`, including the
     old announcement and clip queue state keys.
   - Migrate the low-frequency shared and recoverable keys from `state` to `atomicState`
     once, in `configure()`.
   - Prune the player's relocated `state` keys in `clearPendingCommandsForInitialization`.
   - Add `unschedule()` for the retired callbacks.
   - Do not add no-op stubs.
7. **Lint rules.**
   - `ChildEntryPointPurity`: methods listed in the app's `CHILD_ENTRY_POINTS` must not
     reach any child-write call except through a `runInMillis` target.
   - `AppDataAccess`: classified keys are never read or written through `state`, and
     `synchronized` method modifiers are banned in apps and drivers.
   - Driver hop rule: fails on a `runInMillis(1, …)` whose target only calls `parent.*`.
8. **Tests.**
   - Rewrite the roughly 22 player-spec and 29 app-spec drain and flush references.
   - Add specs for each entry point's shape.
   - Add specs for the accept and apply gates: an older sequence, a new epoch, an
     app restart with empty high-water marks, and an out-of-order callback.
   - Add specs for latest-wins and additive command handling, and for monotonic
     announcement phases.
   - Run two threads against the lock helpers to show updates aren't lost.
9. **Docs.** Rewrite the AGENTS.md section "Parent/Child Async and Deadlock Avoidance".
   Drop the bullets "do not flip the app" and "never call `parent.*` from `parse()`",
   and replace them with this execution model, the contract, the data-layer rule and the
   sequence-ordering rule. Update `architecture.md` (diagram lines 32–38, §§85–95, 119–122)
   and the `skills/` guidance.
10. **Hub verification on the C-7, then the C-8.** Run these scenarios:
    - grouping churn
    - TTS bursts across 3 or more players
    - volume fades on a grouped coordinator
    - rapid pause→play
    - an alarm edit burst
    - a reboot during a group operation (it must recover)
    - a reboot during an announcement (queues come back empty; no stuck stream holder)

    Confirm in logs that no gate timeouts appear, no updates are lost, and no stale
    payloads are applied.

## 5. Hub findings and open questions

Probe app `Q0 State Semantics Probe` (C-7, code id 239, app id 141, `singleThreaded:
false`). It was run twice on 2026-11-26, with identical results.

| # | Scenario | Result | Meaning |
|---|---|---|---|
| S1 | A slow execution writes `k1` while an overlapping fast one writes `k2` and `k3`; a slow read-only execution overlaps both | `k1=A k2=B k3=B` | **Commits are per changed key.** Executions that only read write nothing back. |
| S2 | Two overlapping `state.counter++` | `1` | Unlocked `state` read-modify-write loses updates |
| S3 | A slow execution loads `state.k4`, then commits `state.k5`; meanwhile a fast one writes `atomicState.k4` | `k4=ATOMIC` | A `state` commit doesn't overwrite an `atomicState` key it didn't change |
| S5a | Three `atomicState` read-modify-writes inside `synchronized(static lock)` | `3` | **The locked `atomicState` pattern works** |
| S5b | Three `atomicState` read-modify-writes without a lock | `1` | `atomicState` alone is not atomic for read-modify-write |
| S5c | Three read-modify-writes of a `@Field static ConcurrentHashMap` under a lock | `3` | **The in-memory pattern works** |
| S6 | Three `state` read-modify-writes inside `synchronized(static lock)` | `2` | **A lock does not make `state` safe**, because the commit happens after the lock is released |
| S4 | 8, then 24 `runInMillis(1)` callbacks, each pausing 1.5 s | All 24 overlapped. Starts were dispatched in submission order, about 60 ms apart. | **Q1:** at least 24 run in parallel on the C-7. Start order followed submission order here, but execution overlaps, so ordering must come from the sequence checks, not from the scheduler. |

**Not covered by the probe:**
- Driver `state` was not probed; the plan assumes it behaves the same. The player keys
  that move to `runtimeState` avoid the question.
- Mutating a nested map in place without reassigning it was not probed. The data
  helpers always copy and reassign.

**Still open:**
- **Q2: volume recalculation fan-out.** If running one recalculation per reading is too
  heavy during fades, use `runInMillis(1, 'recalculateAllVolumeState', [overwrite: true])`.
- **Q3: AlarmClock bursts.** Same fallback: `overwrite: true` plus a household-wide
  refresh.
