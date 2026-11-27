# AGENTS.md

## Project Overview

This repository contains Hubitat Elevation home automation apps, drivers, and legacy libraries written in Groovy by Daniel Winks. Hubitat is a local home automation platform; production code is deployed as raw Groovy and runs directly on a Hubitat hub. New code should be standalone and self-contained; library inclusion is a legacy pattern retained for existing packages.

## Repository Structure

```
Apps/                          # Hubitat Apps (Groovy scripts with definition(), preferences, mappings)
  Backups/                     # Hub backup automation
  DeviceGroups/                # Parent/child app pattern for device grouping
Drivers/                       # Hubitat Drivers grouped by category
  Component/                   # Child device drivers (Sonos components, generic components, Gemini)
  HTTP/                        # HTTP-controlled virtual devices (switches, sensors, power monitors)
  Virtual/                     # Virtual devices (auto-off switches, presence sensors)
  ZigBee/window_shade/         # ZigBee device drivers (Third Reality blinds)
  calendar/                    # iCalendar integration driver
  weather/                     # NWS forecast driver
Libraries/                     # Legacy reusable Groovy libraries; do not include in new code
Bundles/                       # Auto-generated HPM bundle packages (DO NOT EDIT)
PackageManifests/              # Hubitat Package Manager manifest files
Resources/                     # Static assets (images)
Readme/                        # Additional documentation
.github/workflows/             # GitHub Actions CI/CD (release automation, bundle creation)
repository.json                # Root HPM repository registry
```

## Language and Runtime

- **Language**: Groovy 2.4.21 (Hubitat's embedded runtime)
- **Docs**: http://docs.groovy-lang.org/docs/groovy-2.4.21/html/documentation/
- **Platform docs**: https://docs2.hubitat.com/en/developer
- **Production deployment**: files are raw Groovy deployed directly to a Hubitat hub
- **Local verification**: the `tests/` Gradle project provides linting and Spock tests

## Key Conventions

### File Types

- **App**: Groovy file with a `definition(...)` block and `preferences` -- lives in `Apps/`
- **Driver**: Groovy file with a `metadata { definition(...) }` block -- lives in `Drivers/`
- **Library**: Groovy file with a `library(...)` block -- lives in `Libraries/`

### Namespace

All files use the `dwinks` namespace. Preserve this for backward compatibility.

### Libraries and Standalone Code

Libraries and `#include` directives are legacy. Do not add library inclusions to
new Apps or Drivers, and do not create new shared-library dependencies for new
features. Inline the small helper surface needed by a standalone file instead.
This avoids hidden merged-class methods, duplicate definitions, installation
ordering problems, and version drift on hubs. Preserve existing library files
and inclusions when maintaining legacy packages unless the task explicitly
migrates them.

Existing legacy code may use:

```groovy
#include dwinks.UtilitiesAndLoggingLibrary
#include dwinks.SMAPILibrary
```

### Key Libraries

| Library                             | Purpose                                                                |
| ----------------------------------- | ---------------------------------------------------------------------- |
| `UtilitiesAndLoggingLibrary.groovy` | Core logging, lifecycle hooks, HTTP retry, OAuth, scheduling utilities |
| `SMAPILibrary.groovy`               | Sonos Music API integration                                            |
| `SunPositionLibrary.groovy`         | Solar position calculations                                            |
| `httpLibrary.groovy`                | HTTP helper functions                                                  |
| `genericComponentLibrary.groovy`    | Component device patterns                                              |
| `childDeviceLibrary.groovy`         | Child device helpers                                                   |

### Logging

Use local logging helpers in new standalone code -- never use `System.out` or
raw `log.*` calls throughout application logic:

- `logDebug(message)`, `logInfo(message)`, `logWarn(message)`, `logError(message)`, `logTrace(message)`

Expose one `enum` preference named `logLevel` when adding configurable
logging (retain an existing setting name only when compatibility requires it).
Use the standard levels, ordered from most detailed to least:
`trace`, `debug`, `info`, `warn`, `error`, `off`. The selected level should
allow that level and more important messages. Prefer `info` as the default.
When exposing the dropdown, use these exact Hubitat log names as labels:
`Trace`, `Debug`, `Info`, `Warn`, `Error`, `Off` -- do not add explanatory
text or recommendations to the labels.
Legacy boolean settings such as `logEnable`, `debugLogEnable`, and
`descriptionTextEnable` may remain for compatibility but should not be added
to new code. Automatic log shutoff timers are also legacy; logging should be
controlled by the selected level.

### State vs Settings

- `settings.*` -- User-configured inputs (persistent, set via preferences UI)
- `state.*` -- Mutable runtime data (timers, caches, counters)

### Scheduling

```groovy
runIn(seconds, 'methodName')       // One-time delayed execution
schedule(cronExpression, 'method') // Recurring
unschedule()                       // Cancel all
unschedule('methodName')           // Cancel specific
```

### Event System

```groovy
subscribe(device, 'attribute', 'handlerMethod')
sendEvent(name: 'attribute', value: data)
```

### Parent/Child Calls and the Multi-Threaded Execution Model (Sonos Advanced)

The Sonos Advanced app, `SonosAdvPlayer` and `SonosAdvGroup` are all `singleThreaded: false`. Nothing in this
family holds an execution gate while waiting for another device, so the 120 s gate timeout (2026-11-20 incident: an
app gate held while it waited on a child gate, with the child holding its gate while it waited on the app) cannot
form. The Discovery, Favorites and Playlists child apps stay `singleThreaded: true`; they wait on nothing when they
call the ungated parent, and the parent holds nothing while it calls them. Any other still-gated child that calls
`parent.*` must never be called while an app lock (`appLock(...)`) is held.

**Contract.**

1. Child drivers call parent app methods **directly**. There is no driver-side outbox, flush callback, or
   `runInMillis` hop whose only job is to release a gate (the `ConcurrencyRule` lint fails a driver hop whose target
   only calls `parent.*`). A hop is fine when it has its own work, for example reading attributes after they settle.
2. A parent method a child calls (a **child entry point**, listed in the app's `CHILD_ENTRY_POINTS`) only reads and
   writes app data, reads child wrappers (`getDataValue`, `currentValue`, `getDeviceNetworkId`) and calls
   `asynchttp*`. It ends with a plain `return` (the return value is the acknowledgement) or with
   `runInMillis(1, '<callback>', [data: ..., overwrite: false])`. Anything that writes to a child goes in that
   callback, which runs in a new execution. "Writes to a child" means driver methods, child-app methods,
   `child.sendEvent`, app-level `sendEvent(dni, ...)` and `updateDataValue`. `ChildEntryPurity` follows the local call
   graph from every entry point and fails on a reachable child write.
   **Exception: replaceable snapshots.** When the entry point stores a whole latest-wins value in app memory and the
   callback only delivers what memory holds, use `overwrite: true` with a handler per snapshot kind and no `data`,
   so repeated reports keep one pending job instead of one per report. Today these are the catalog snapshots
   (`putCatalogSnapshot` → `deliverFavoritesCatalogSnapshot` / `deliverPlaylistsCatalogSnapshot`). Never use it for a
   callback that carries an individual event, delta or command in `data` (AVT, RC, attribute updates, group
   operations, announcements): overwrite would drop it.
3. No `PENDING_*` queue, `*DrainScheduled` flag, received-ID dedupe, ack or retry loop exists only to carry
   child-to-app traffic. Hubitat's scheduler is the only deferral mechanism. Order comes from sequence numbers.
4. Bridge data never becomes an attribute or location event. `sendEvent` generates an event only when the value
   changes (`isStateChange: true` forces one); `displayed:false` does nothing. Do not keep an app-side last-sent cache
   just to suppress duplicates. Intended product attributes (`trackName`, `groupVolume`, ...) are the product.
5. Do not flip a gated child app, or any driver the app calls while holding an app lock, without auditing it.

**Data layer.** `state` commits **per changed key** when an execution ends. Any read-modify-write of one `state` key
loses updates, **even inside a lock** (the lock is released before the commit). `atomicState` read-modify-write is
safe only inside a static lock. A Groovy `synchronized` *method modifier* locks only the per-execution instance and
excludes nothing; only `synchronized(<static object>)` blocks do (`ConcurrencyRule` bans the modifier). Pick one
storage pattern per key and never mix paths:

| Class | Storage |
| --- | --- |
| Overwrite-only or single-writer (update-check info, policy snapshots, last-error diagnostics, whole-value ZGT payload/topology) | `state`, no lock |
| Low-frequency shared read-modify-write (preference snapshots, ZGT election list, last-processed topology) and recoverable work state (active group operation, deferred operation commands) | `atomicState` through `mutateAppData(key) { current -> next }`, which locks per key (`ATOMIC_MIGRATED_STATE_KEYS`) |
| In-memory working data: re-derivable caches and transient work (AVT cache, readings, GRC, announcement and clip queues, catalog snapshots, sequence marks, command clear times, pending group state) | `@Field static ConcurrentHashMap` keyed by `app.id` (`appMemoryMap`/`withMemoryMap`), mutated under `appLock(key)`; a reboot or code save empties it (`APP_MEMORY_KEYS`; `AppDataAccess` forbids `state` access to these keys) |

Group operation, announcement and clip work share the single `appLock('work')` lock so lock order can never invert.
Hold an app lock only around app data and local calls; never around a call into a child that could call back from
another thread. A driver at `singleThreaded: false` can run `parse` concurrently: its hot queues live in
`@Field static ConcurrentHashMap` (`runtimeState`) with read-modify-write under `synchronized(runtimeState)`, and
hot or transient keys (`playbackState`, request tokens, `htControl*`) live in `runtimeState`, not `state`.

**Ordering.** Callbacks and child executions run in parallel (24 simultaneous `runInMillis` callbacks were measured
on a C-7), so every ordered stream carries `[epoch, sequence]` from the driver: `sequence` is a per-DNI `AtomicLong`
stamped where the payload is first observed, `epoch` is `DRIVER_EPOCH`, set when the driver class loads (a reboot or
code save resets the counters and gets a new epoch). The app checks each stream twice: `acceptSequence` in the entry
point (drop anything not newer than the last accepted) and `applySequence` in the callback (drop anything older than
the last applied). Partial payloads (attribute updates, AVT deltas, GRC/readings) are gated per attribute or event
name so a late delta cannot drop names a newer one did not carry. Marks live in app memory, so after an app restart
the first arrival on each stream is accepted. Absolute commands (play/pause/stop, set volume, mute, repeat, shuffle,
crossfade, set track) are latest-wins per target and family; additive commands (group membership, line-in,
favorite/playlist loads, announcements) are never dropped and keep arrival order. UPnP `SEQ`/`SID` are not used.

`updated()` does **not** run when app/driver code is saved, only on Done/Save Preferences (staff-confirmed). Devices
pick up driver code automatically, but apps must be re-saved (or rebooted) for `configure()`/`updated()` re-arming.
Never assume a code publish alone starts new schedules.

Community patterns this follows: `HubitatPublic` `componentSwitch` / `genericComponentParentDemo` (a short,
non-reentrant `parent.componentX(device)` call; parent pushes state by `child.parse`/`sendEvent`), CoCoHue
`bridgeAsyncPutV2` and VeSync `sendBypassRequest` (production integrations that queue through the app instead of
blocking on HTTP or child work inline).

### Standalone App/Driver Structure

New files should generally contain their own logging, lifecycle, device
helpers, and HTTP/retry helpers rather than relying on `#include`. Organize
larger files into visible comment sections such as imports/constants,
logging, metadata/preferences, lifecycle/configuration, event handlers,
device commands, HTTP integration, and pure calculation/helpers.

Use the standard lifecycle flow where applicable:

```groovy
void installed() { initialize() }
void updated() { configure() }
void initialize() { configure() }
void configure() { /* unschedule, unsubscribe, subscribe, schedule */ }
```

Settings belong in `settings.*`; mutable runtime state belongs in `state.*`.
Momentary controls such as snooze actions should use button capabilities and
explicit app state rather than modeling a button as a persistent switch.

### Recent Development Patterns

Recent work favors these patterns for new integrations and refactors:

- Standalone Apps and Drivers with small local helper implementations. Do not
  add new `#include` dependencies; migrate legacy inclusions only as part of an
  intentional compatibility-aware change.
- A single overall logging-level dropdown, with optional separate display-log
  filtering only when the UI needs it. Use `trace`, `debug`, `info`, `warn`,
  `error`, and `off`, and normalize unexpected values safely. Display the
  dropdown labels exactly as Hubitat does: `Trace`, `Debug`, `Info`, `Warn`,
  `Error`, `Off`.
- Explicit `installed()`, `updated()`, `initialize()`, and `configure()` flows,
  with settings updates re-subscribing and rescheduling cleanly.
- `@Field static final` constants and `@CompileStatic` for pure calculations,
  parsers, formatters, and typed transformations; isolate dynamic Hubitat API
  access at narrow boundaries.
- Local HTTP integrations with defensive response parsing, bounded retries,
  stale-callback protection, and clear online/offline state where applicable.
  ESPHome work uses native mDNS discovery plus HTTP control, and may create
  child presence/device-tracker devices when the integration exposes them.
- Regression tests for state transitions, HTTP behavior, retries, parsers,
  device tracking, and pure calculation logic.

### HTTP Endpoints / Webhooks

Follow the established pattern:

```groovy
mappings { path("/endpoint") { action: [GET: "handlerMethod"] } }

String getLocalUri() { return getFullLocalApiServerUrl() + "/endpoint?access_token=${state.accessToken}" }
String getCloudUri() { return "${getApiServerUrl()}/${hubUID}/apps/${app.id}/endpoint?access_token=${state.accessToken}" }
```

Always use `tryCreateAccessToken()` for OAuth -- never hardcode tokens.

### Async HTTP with Retry

For new standalone HTTP integrations, keep the retry flow local to the file.
Legacy code may use the library utilities:

```groovy
resetHttpRetryCounter()
asynchttpGet('callbackMethod', [uri: '...'])

void callbackMethod(AsyncResponse response, Map data) {
  if (isHttpResponseFailure(response)) {
    handleAsyncHttpFailureWithRetry(response, 'retryMethod')
    return
  }
  // Process successful response
}
```

## Code Style Rules

- **Always use parentheses** for method calls: `logInfo("message")` not `logInfo "message"`
- **Always use braces** for control structures: `if (condition) { ... }` not `if (condition) ...`
- **Use concrete types** instead of `def`: `String`, `Integer`, `Map`, `List`
- **Use `@CompileStatic`** where possible for performance and compile-time type checking
- **Apply `@CompileStatic` wherever code can be statically typed**, especially pure calculations, parsers, formatters, constants, and typed data transformations. Keep Hubitat dynamic-property/device-dispatch boundaries dynamic or isolate them behind typed helpers. Resolve Groovy 2.4 static-compiler issues explicitly; for example, use primitive `double` arithmetic when passing values to `Math.round()` rather than relying on `/` returning a compatible type.
- **Use `@Field static final`** for constants
- **Include MIT license header** on all source files

## What to Preserve (Do Not Break)

- Existing `mappings` paths (webhook URLs used by external systems)
- Existing `state` keys (breaking these corrupts running installations)
- Existing `settings` keys and types (breaking these loses user configuration), unless an intentional migration is part of the task
- Existing scheduling and subscription patterns
- The `dwinks` namespace across all files
- Lifecycle hooks: `installed()`, `updated()`, `uninstalled()`, `initialize()`

## App/Driver Version Sync (Never Support Old Versions)

Apps and drivers in this repository are kept in sync by Hubitat Package Manager (HPM) and their versions always match. The driver will never be older than the app, and the app will never be older than the driver. Therefore: **never add code to support an older app/driver version**. Specifically: no deprecated fallback method-chain shims, no bridge-attribute holdovers for old versions, no `MissingMethodException` compatibility fallbacks between sibling app/driver code, and no dual-shape or dual-contract readers. When the contract changes, change both sides in the same commit and delete the old side. The only exception is `settings.*`/`state.*` keys that live on running hubs, which still need a migration-safe read when an intentional migration is part of the task.

## Testing

Two layers of automation live under `tests/`:

1. **Static analyzer** (`./gradlew lint` from `tests/`) - parses every Groovy
   file with the official Groovy AST and runs rules covering syntax,
   metadata blocks, the Hubitat sandbox import allowlist, `@CompileStatic`
   correctness, capability/command/attribute consistency, method-name
   references like `runIn('foo')`, and the parent/child concurrency rules
   (`ConcurrencyRule`: no `synchronized` method modifiers, pure child entry
   points, no `state` access to memory/atomic keys, no pure parent hops).
2. **Spock unit tests** (`./gradlew test` from `tests/`) - load library files
   into a `HubitatScriptHarness` (which stubs `state`, `settings`, `log`,
   scheduling, events, etc.) and assert behavior of individual methods.

Beyond those, code still needs to be smoke-tested on a real Hubitat hub for
runtime semantics and UI behavior. Some apps expose test endpoints
(e.g., GeminiTextRewriter has `/test`). Rely on logging for debugging.

New behavior should have a focused regression test when practical, especially
for parsers, HTTP response handling, retries, state transitions, device
tracking, and pure calculations. See `tests/README.md` for the full list of rules, harness capabilities,
and how to add new tests. Whenever behavior changes in an App, Driver, or
Library, update or add the affected regression tests in the same change. Treat
test updates as required, and run the relevant specs after changing behavior.
If the current harness cannot exercise a behavior, explain the limitation and
add suitable structural or static coverage where practical.

## Adding a New Package

1. Add standalone Groovy files under `Apps/` or `Drivers/` (and add to `Libraries/` only when explicitly maintaining a legacy library)
2. Keep new code self-contained; use `#include` only when maintaining an existing legacy package or when an intentional migration requires it
3. Create `PackageManifests/<PackageName>/packageManifest.json` with raw GitHub URLs
4. Add entry to root `repository.json` if creating a new top-level package
5. Do not edit `Bundles/` -- these are auto-generated by GitHub Actions

## CI/CD Workflows

GitHub Actions in `.github/workflows/`:

- `release-sonos-advanced.yml` -- Automated version bump and release for Sonos Advanced
- `release-gemini-text-rewriter.yml` -- Automated release for Gemini Text Rewriter
- `SonosAdvancedBundles.yml` -- HPM bundle creation for Sonos
- `ThirdRealityBundles.yml` -- HPM bundle creation for Third Reality
- `UtilitiesAndLoggingLibrary.yml` -- Legacy library release workflow

Release process: version increment via workflow input (patch/minor) -> update version across files -> update packageManifest.json -> create ZIP bundles -> publish GitHub release -> update repository.json.

## Security Constraints

- Never commit access tokens or secrets
- Do not introduce external network calls without explicit justification
- Follow the `tryCreateAccessToken()` pattern for OAuth
- API keys should be stored in `settings` (user input), never in source

## Browser UI Automation

- For any browser-based task, always use the Codex built-in browser (IAB) through CUA. Do not launch or use Firefox, Chrome, or another native/external browser session.
- Prefer purpose-built MCP/API tools when they fully support the task; when browser interaction is needed, use the built-in browser exclusively.

## Hubitat Defect Reports and Reproduction Boundaries

- Treat a reporter's stated app, driver, and hub versions as the working facts for
  analysis unless the supplied report contradicts itself. Do not require the
  reporter to verify a version already stated in their report.
- Never inspect, query, retrieve logs or source from, or issue commands to a
  reporter's or other third party's hub or devices. Device IDs and app IDs in a
  report are evidence only, not authorization to access that installation.
- Reproduce reported defects only on the user's own connected Hubitat hubs when
  the user has identified those hubs as available for testing. Prefer the user's
  C-7 for timing or resource-sensitive defects because it has less capacity than
  the C-8; use the C-8 as a comparison or fallback.
- When more runtime evidence is needed, add focused diagnostics locally and
  reproduce on the user's test hub. Do not make additional diagnostics on the
  reporter's device a prerequisite when the report and repository source are
  sufficient to proceed.

## Using Connected Hubitat MCP Servers

When both Hubitat MCP servers are available in the agent session, choose the
server that matches the request:

- Use the general `hubitat` MCP for hub inventory, devices, current state,
  events, rooms, modes, apps, scenes, and device or app actions.
- Use the dedicated `hubitat_logs` MCP for historical logs, log summaries, and
  live logs. Prefer `hubitat_get_logs`, `hubitat_get_log_summary`, and
  `hubitat_get_live_logs` over opening the Hubitat logs page or fetching
  `/logs/past/json` in a browser. The logs MCP provides filtering, compact
  results, pagination, and a live stream.
- Use both servers when a task needs logs alongside device or app context.
- If the relevant MCP server is unavailable, say so and use another route only
  when it is appropriate for the task. Never copy MCP access tokens into source,
  repository files, or responses.

## Reference Files

- `Apps/SunriseSimulation.groovy` -- Standalone scheduling, state, settings, button events, and level-based logging
- `Apps/ESPHome_Device_Helper.groovy` -- Recent standalone app with mDNS discovery, HTTP integration, child devices, and level-based logging
- `Drivers/HTTP/ESPHomeRATGDOGarageDoor.groovy` -- Recent HTTP driver with lifecycle hooks, typed constants, and level-based logging
- `Libraries/UtilitiesAndLoggingLibrary.groovy` -- Legacy shared utility library; use as a maintenance reference, not a default dependency
- `.github/copilot-instructions.md` -- Additional AI coding assistant guidance

## Repository Skills

Reusable repository-local Codex skills live under `skills/`:

- `skills/hubitat-groovy-development/` -- standalone Hubitat Groovy implementation patterns
- `skills/hubitat-review/` -- read-only compatibility and quality review
- `skills/hubitat-test-and-lint/` -- targeted and full verification workflow
- `skills/hubitat-package-release/` -- HPM manifests and release workflow guidance
- `skills/hubitat-skill-maintainer/` -- detects and corrects drift in repository skill guidance

Use these skills for their matching workflows; keep this file as the source of
truth for repository-wide policy.

## Documentation

Use the available Hubitat documentation or repository test/stub definitions to
verify framework-specific APIs before writing code. Do not rely on memory when
the API behavior is uncertain, especially for scheduling, device capabilities,
HTTP callbacks, and dynamic-page inputs.
