---
name: hubitat-groovy-development
description: Develop or refactor Hubitat Groovy Apps and Drivers in this repository using its standalone-code, lifecycle, logging, compatibility, and static-typing conventions.
---

# Hubitat Groovy Development

Use for new features and refactors in `Apps/` and `Drivers/`.

- Production code targets Hubitat Groovy 2.4.21 and is deployed as raw Groovy.
- New Apps and Drivers must be standalone. `#include` and shared libraries are
  legacy; do not add new library dependencies. Inline needed helpers.
- Preserve namespaces, mappings, state keys, settings, and child-device IDs
  unless the task explicitly includes a compatibility-aware migration.
- Use concrete types, parentheses on calls, braces on control flow, and
  `@Field static final` constants.
- Apply `@CompileStatic` to pure math, parsers, formatters, and typed
  transformations. Isolate dynamic Hubitat device/settings/state/scheduling
  access behind narrow dynamic boundaries.
- Use `installed()`, `updated()`, `initialize()`, and `configure()` where
  applicable; settings updates should cleanly resubscribe and reschedule.
- Use one `logLevel` enum preference (`trace`, `debug`, `info`, `warn`, `error`,
  `off`) for new logging, defaulting to `info`, and local logging helpers.
  Use the exact Hubitat labels `Trace`, `Debug`, `Info`, `Warn`, `Error`, and
  `Off` in the dropdown; do not add explanatory text to those labels.
- Model momentary controls as button events plus explicit app state.
- For local integrations, use defensive HTTP parsing, bounded retries, stale
  callback protection, and explicit online/offline state where useful.
- Organize larger files with comment sections for imports/constants, logging,
  metadata/preferences, lifecycle, events, device commands, HTTP, and helpers.
- Sonos Advanced is multi-threaded (`singleThreaded: false` for the app, player and
  group drivers). Children call `parent.*` directly; a child entry point only reads/writes
  app data and returns, or ends with `runInMillis(1, '<callback>', [data:..., overwrite:false])`
  for anything that writes a child. No child-to-app queue, drain, ack or retry loop.
- Shared data: `state` commits per key, so a read-modify-write of one `state` key loses
  updates even inside a lock. Use `@Field static ConcurrentHashMap` working data under
  `synchronized(appLock(key))`, or `atomicState` through `mutateAppData`. Never use a
  `synchronized` method modifier (it locks only the per-execution instance).
- Order ordered streams with `[epoch, sequence]` stamped by the driver (per-DNI `AtomicLong`,
  `DRIVER_EPOCH`) and checked with `acceptSequence` (entry) and `applySequence` (callback).
- Keep bridge data out of attributes and location events (attributes and event history are
  user-visible; `displayed:false` is a no-op). Hubitat suppresses unchanged-value events;
  only `isStateChange: true` forces one. Do not keep an app-side last-sent cache for that.
  See `AGENTS.md` "Parent/Child Calls and the Multi-Threaded Execution Model".

Inspect nearby examples and test stubs for exact Hubitat API shapes. Run
targeted lint and relevant tests after coding.
