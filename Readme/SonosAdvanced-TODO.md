# Sonos Advanced App & Drivers - Reliability, Stability & Performance TODO

## Context

Review of the Sonos Advanced App (`Apps/SonosAdvancedApp.groovy`), all Sonos component drivers (`Drivers/Component/SonosAdv*.groovy`), and supporting libraries (`Libraries/SMAPILibrary.groovy`, `Libraries/UtilitiesAndLoggingLibrary.groovy`) to identify bugs and improvements for reliability, stability, and performance.

---

## HIGH Priority - Bugs & Crash Risks

### 1. Double comma syntax error in `getDeviceDescriptionLocalSync()` [DONE]

- **File:** `Apps/SonosAdvancedApp.groovy:1439`
- **Issue:** `uri: "http://${ipAddress}/xml/device_description.xml",,` has a trailing double comma in the map literal. Groovy may treat this as a null entry or fail at parse time.
- **Status:** Fixed in commit `72c5ff9` by removing the extra comma from the map literal. The TODO status was refreshed in `938d675`.

### 2. Null pointer in `dequeueAudioClip()` when rightChannel is null [DONE]

- **File:** `Drivers/Component/SonosAdvPlayer.groovy:4504-4506`
- **Issue:** `rightChannel` (line 4470) can be null if no right channel child device exists. Line 4504 checks `clipMessage.rightChannel` (the message data), but line 4506 calls `rightChannel.playerLoadAudioClip()` on the potentially-null device wrapper.
- **Status:** Fixed in commit `442a1a3` by always sending the left-channel clip first, guarding the right-channel playback call with `rightChannel`, and logging a warning when a right-channel clip is requested but no child device is available.

### 3. Unsafe chained calls after `getDeviceFromRincon()` - multiple locations [DONE]

- **File:** `Drivers/Component/SonosAdvPlayer.groovy:832, 840, 850`
- **Issue:** Pattern `parent?.getDeviceFromRincon(getGroupCoordinatorId()).muteGroup()` uses safe-nav on `parent` but not on the return value of `getDeviceFromRincon()` which can return null.
- **Status:** Fixed in commit `809ff19` by adding safe navigation to the coordinator lookup result across the affected group-control delegation paths.

### 4. Null `getDataValue('playerIds')` in `getAllPlayersForGroupDevice()` [DONE]

- **File:** `Apps/SonosAdvancedApp.groovy:1414`
- **Issue:** `device.getDataValue('playerIds').split(',')` will NPE if the data value is null.
- **Status:** Fixed in commit `227a147` by guarding both `groupCoordinatorId` and `playerIds`, and by reusing `getAllPlayersForGroupDevice()` from `updateGroupDevices()` instead of tokenizing `playerIds` directly.

### 5. Null coordinator in `updateGroupDevices()` passed to `notifyGroupDeviceActivated()` [DONE]

- **File:** `Apps/SonosAdvancedApp.groovy:1606-1607`
- **Issue:** `rinconMap[coordinatorId]` can be null. It's passed directly to `notifyGroupDeviceActivated(gd, coordinator)` without null check.
- **Status:** Addressed across commits `c8103fd` and `3d32971`. The activation path now tolerates a missing coordinator by logging a warning and replaying held state instead of assuming the coordinator exists.

### 6. Null `groupId` in `getGroupCoordinatorForPlayerDeviceLocal()` [DONE]

- **File:** `Apps/SonosAdvancedApp.groovy:1528`
- **Issue:** If the httpPost callback fails or device is unreachable, `groupId` remains null. `groupId.tokenize(':')[0]` will NPE.
- **Status:** Fixed in commit `c8103fd` by guarding missing `localUpnpHost`, returning early when `groupId` is null or empty, validating the tokenized result, and only resolving the coordinator when a usable group ID is present.

## MEDIUM Priority - Reliability & Error Handling

### 7. Silent exception swallowing in error callbacks

- **Files:** `Apps/SonosAdvancedApp.groovy:2585-2586, 2609-2611`
- **Issue:** Empty catch blocks `catch(Exception e){}` hide failures in error reporting itself. At minimum, these should log at trace level.
- **Fix:** Add `logTrace("Could not read error details: ${e.message}")` in catch blocks.

### 8. `localControlCallback()` logs errors but never retries [DONE]

- **File:** `Apps/SonosAdvancedApp.groovy:2581-2592`
- **Issue:** Failed SOAP/HTTP commands are only logged, never retried. For transient network issues, this means commands are silently lost.
- **Fix:** Add retry logic using the existing `handleAsyncHttpFailureWithRetry()` pattern from UtilitiesAndLoggingLibrary, at least for idempotent operations.
- **Status:** Fixed in the working tree by adding request-aware retry metadata for `localControlCallback()`, automatically retrying idempotent local GET requests, and allowing POST retries only when explicitly marked `retryable`.

### 9. State updated before device operation in group rename [DONE]

- **File:** `Apps/SonosAdvancedApp.groovy:485-489`
- **Issue:** `state.userGroups.remove(editingGroup)` runs before `setDeviceNetworkId()`. If the device op fails, state is corrupted.
- **Fix:** Reorder to perform device operation first, then update state on success.
- **Status:** Fixed in the working tree by keeping the old group entry until `setDeviceNetworkId()` succeeds, returning early on rename failure, and treating a label update failure as non-fatal.

### 10. Static discovery maps shared across all app instances

- **File:** `Apps/SonosAdvancedApp.groovy:52-54`
- **Issue:** `discoveredSonoses` and `discoveredSonosSecondaries` are `@Field static` — shared across ALL instances of the app. Multiple Sonos Advanced installs will overwrite each other's discovery data.
- **Fix:** Key the maps by app instance ID, or use instance-scoped state instead of static fields.

### 11. Unbounded growth of static maps in SonosAdvPlayer [DONE]

- **File:** `Drivers/Component/SonosAdvPlayer.groovy:264-280`
- **Issue:** Static ConcurrentHashMaps for `audioClipQueue`, `eventTimestamps`, `lastPlaybackState`, `lastWsEventLog`, `lastZgtXmlHash` grow unbounded. With many devices or long uptime, these consume memory without cleanup.
- **Status:** Fixed in the working tree by adding a rate-limited cleanup pass that prunes orphaned static cache entries against the parent app's active Sonos player registry, plus explicit per-device cleanup on driver uninstall.

### 12. Mutex deadlock risk in WebSocket subscription management [DONE]

- **File:** `Drivers/Component/SonosAdvPlayer.groovy:2350-2432`
- **Issue:** Semaphore uses `tryAcquire()` without timeout parameter. If the thread holding the lock dies between acquire and release, subsequent operations are permanently blocked.
- **Fix:** Use `tryAcquire(timeout, TimeUnit)` variant and ensure `finally` blocks always release.
- **Status:** Fixed by switching the subscribe/unsubscribe mutexes to bounded `tryAcquire(..., TimeUnit.SECONDS)` waits, releasing them through guarded `finally` paths in the async callbacks, and preventing timeout fallback releases from over-incrementing permit counts.

### 13. `pendingGroupDeviceUpdates` lost on flush failure [DONE]

- **File:** `Drivers/Component/SonosAdvPlayer.groovy:3715-3763`
- **Issue:** `pendingGroupDeviceUpdates.remove(dni)` happens before processing. If flush fails midway, queued updates are permanently lost.
- **Status:** Fixed in working tree by snapshotting pending updates before flush, clearing only successfully-forwarded values from the live queue, and rescheduling the flush when a forwarding attempt throws.

---

## LOW Priority - Performance & Code Quality

### 14. O(n) duplicate scan per discovered device

- **File:** `Apps/SonosAdvancedApp.groovy:1082-1083`
- **Issue:** `discoveredSonoses.values().any { it.deviceIp == ipAddress }` is O(n) on every discovery event.
- **Fix:** Maintain a secondary IP→device index for O(1) lookup.

### 15. `buildRinconMap()` and `getDeviceFromRincon()` both call `getChildDevices()`

- **File:** `Apps/SonosAdvancedApp.groovy:1301-1316`
- **Issue:** Both methods iterate all child devices. In methods that call both, this is redundant.
- **Fix:** Have `getDeviceFromRincon()` use a cached map or accept a pre-built map as parameter.

### 16. Redundant `getRightChannelRincon()` calls

- **File:** `Drivers/Component/SonosAdvPlayer.groovy:175`
- **Issue:** `getRightChannelRincon()` called twice in the same conditional check.
- **Fix:** Cache result in a local variable.

### 17. JsonSlurper caching creates unnecessary garbage [DONE]

- **File:** `Drivers/Component/SonosAdvPlayer.groovy:4799-4810`
- **Issue:** `putIfAbsent()` creates object before check. Use `computeIfAbsent()` instead for lazy initialization.
- **Fix:** Replace `putIfAbsent` pattern with `computeIfAbsent`.
- **Status:** Fixed in the working tree by switching the per-device `JsonSlurper` cache to `ConcurrentHashMap.computeIfAbsent(...)`, which avoids allocating a new parser when one is already cached.

### 18. More methods should use `@CompileStatic`

- **File:** `Drivers/Component/SonosAdvPlayer.groovy` (throughout)
- **Issue:** Only a few methods use `@CompileStatic`. Adding it to more pure-logic methods would improve performance and catch type errors at compile time.
- **Fix:** Add `@CompileStatic` to methods that don't rely on dynamic dispatch.

---

## Verification

- Deploy modified files to a Hubitat hub and verify:
  - Discovery finds all Sonos players without errors
  - Group creation/deletion works
  - Audio clip playback works (including stereo pairs)
  - WebSocket subscriptions reconnect properly
  - No null pointer exceptions in hub logs during normal operation
  - Group volume/mute commands delegate correctly to coordinators
