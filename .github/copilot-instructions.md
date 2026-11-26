## Purpose

This repository contains Hubitat apps, drivers, and libraries (Groovy) authored by Daniel Winks. The guidance below is focused and actionable for an AI coding assistant making edits or adding features in this codebase.

## High-level architecture (big picture)

- Apps live in `Apps/` (Groovy scripts with `definition(...)`, `preferences`, `mappings`, etc.).
- Drivers live in `Drivers/` and are grouped (e.g., `Drivers/HTTP`, `Drivers/Virtual`, `Drivers/ZigBee`).
- Reusable code is in `Libraries/` (e.g., `UtilitiesAndLoggingLibrary.groovy`, `SMAPILibrary.groovy`).
- Bundles and PackageManifests provide packaging for Hubitat Package Manager (HPM). The root `repository.json` and files under `PackageManifests/` define packages and raw URLs used by HPM.
- Everything in the "Bundles" folder is auto-generated during packaging for HPM and should not be edited directly.

Typical runtime flow:

- Hubitat loads an App/Driver Groovy file. Apps register `preferences` and may expose HTTP mappings via `mappings {}` (cloud + local API URLs).
- Apps use `settings.*` for user-configured inputs and `state.*` for persistent runtime state.
- Scheduling uses `runIn()` / `unschedule()`; eventing uses `subscribe()` and Hubitat `Event` objects.
- Libraries are included via `#include` comments and implicitly expected to be present in `Libraries/`.

## Project-specific conventions and patterns

- Namespace: many files use the `dwinks` namespace. Preserve existing namespaces for backwards compatibility.
- Logging: apps typically include boolean preferences `logEnable`, `debugLogEnable`, `descriptionTextEnable`. Use helper functions from `UtilitiesAndLoggingLibrary` (e.g., `logDebug`, `logInfo`).
- OAuth/webhooks: Apps often implement `getLocalUri()`, `getCloudUri()` and call `tryCreateAccessToken()`; do not hardcode access tokens. If you add HTTP endpoints, follow the same `mappings { path(...) { action: [GET: "handler"] } }` and return responses via `render()`.
- Rule Machine integration: utilities like `RMUtils.getRuleList('5.0')` and `RMUtils.sendAction([...], 'runRuleAct', ...)` are used to interact with Hubitat Rule Machine—follow these calls when wiring rule-based behavior.
- State vs Settings: use `settings` for configuration (user inputs), `state` for mutable persistent data (timers, stages). Example: `state.stageSecs`, `settings.sunriseDuration` in `Apps/SunriseSimulation.groovy`.
- Scheduling: use `runIn(seconds, 'methodName')` and check `unschedule()` and `unschedule(method)` when aborting.
- Parent/child calls (see `AGENTS.md` § Parent/Child Calls and the Multi-Threaded Execution Model): the Sonos Advanced app, player and group drivers are `singleThreaded: false`. Children call `parent.*` directly; a child entry point only reads/writes app data and returns or ends with `runInMillis(1, '<callback>', [data:..., overwrite:false])` for any child write. `state` commits per key (read-modify-write loses updates even inside a lock): use static in-memory maps under `synchronized(appLock(key))` or `atomicState` through `mutateAppData`; never a `synchronized` method modifier. Order streams with driver-stamped `[epoch, sequence]` (`acceptSequence`/`applySequence`). No bridge data in attributes (`displayed:false` is a no-op); Hubitat suppresses unchanged `sendEvent` values unless `isStateChange: true`.

## Files and places to look for examples

- `Apps/SunriseSimulation.groovy` — good example of webhooks, scheduling (`runIn`), `state` usage, and `settings` inputs. See `getLocalUri()`, `getCloudUri()`, `sunriseStartWebhook()`.
- `Libraries/UtilitiesAndLoggingLibrary.groovy` — central logging helpers and common utilities used across apps and drivers.
- `PackageManifests/` and `repository.json` — how the repo exposes packages to HPM; update manifests when adding a package.

## Documentation for Hubitat developers

- See online documentation at [Hubitat Developer Documentation](https://docs2.hubitat.com/en/developer) for general Hubitat development practices.
- See online documentation at [Hubitat Developer Documentation](https://docs2.hubitat.com/en/developer/best-practices) for Hubitat-specific Performance Related development practices.
- More examples, documentation, and patterns can be found in the [Hubitat Community Forums](https://community.hubitat.com/).
- Documentation for Groovy 2.4.21 (used by Hubitat) can be found at [Groovy Documentation](http://docs.groovy-lang.org/docs/groovy-2.4.21/html/documentation/).

## Editing and release workflow (developer workflow)

- Production files are raw Groovy for Hubitat, and the `tests/` Gradle project provides local linting and Spock regression tests. Do not rely on hub deployment as the only way to test a change.
- Packaging: to publish an app/driver for HPM, add a package manifest under `PackageManifests/<PackageName>/packageManifest.json` and update the root `repository.json` if creating a new top-level package.

## How an AI should modify code

- Preserve public APIs: do not change `mappings` paths or existing `state` keys unless intentionally part of the change.
- Respect Hubitat lifecycle hooks: keep `initialize()`, `installed()`, `updated()` (when present) and subscription patterns intact.
- Use the repo's logging utilities and preference flags rather than adding ad-hoc printlns or `System.out`.
- When adding web endpoints, follow the `getLocalUri()/getCloudUri()` pattern and `tryCreateAccessToken()` usage as in `SunriseSimulation.groovy`.
- Whenever a code change affects behavior, update or add its affected regression tests in the same change, then run the relevant test specs and lint. Treat test updates as required; if the harness cannot cover a behavior, explain why and add structural or static coverage where practical.

## Hubitat defect reports and reproduction boundaries

- Treat the app, driver, and hub versions stated by a reporter as the working
  facts unless their report contradicts itself. Do not ask them to re-verify a
  version they already supplied.
- Never inspect, query, retrieve logs or source from, or issue commands to a
  reporter's or another third party's hub or devices. IDs in a report do not
  authorize access to that installation.
- Reproduce only on the user's own connected Hubitat hubs when the user has
  identified them as available for testing. Prefer their C-7 for timing or
  resource-sensitive defects; use their C-8 as a comparison or fallback.
- If more runtime evidence is needed, instrument locally and reproduce on the
  user's test hub. Do not make diagnostics on the reporter's device a prerequisite
  when the report and source are sufficient to proceed.

## Using connected Hubitat MCP servers

When both Hubitat MCP servers are available, use the general `hubitat` MCP for
hub inventory, devices, state, events, rooms, modes, apps, scenes, and actions.
Use the dedicated `hubitat_logs` MCP for historical log searches, summaries,
and live log streaming. Prefer its `hubitat_get_logs`,
`hubitat_get_log_summary`, and `hubitat_get_live_logs` tools over opening the
logs page or fetching `/logs/past/json` through a browser. Use both servers when
a task needs log evidence plus hub or device context. If the appropriate MCP is
unavailable, choose a suitable fallback and never place MCP access tokens in
source, committed configuration, or responses.

## Browser UI automation

For browser-based tasks, always use the Codex built-in browser (IAB) through
CUA. Do not launch or use Firefox, Chrome, or another native/external browser
session. Prefer purpose-built MCP/API tools when they fully support the task;
when browser interaction is needed, use the built-in browser exclusively.

## Examples to copy/paste

- Local/cloud endpoint pattern (copy from `Apps/SunriseSimulation.groovy`):

  String getLocalUri() { return getFullLocalApiServerUrl() + "/sunriseStart?access_token=${state.accessToken}" }
  String getCloudUri() { return "${getApiServerUrl()}/${hubUID}/apps/${app.id}/sunriseStart?access_token=${state.accessToken}" }

- Webhook mapping example:

  mappings { path("/sunriseStart") { action: [GET: "sunriseStartWebhook"] } }

  Map sunriseStartWebhook() {
  sunriseStart()
  return render(contentType: "text/html", data: "<p>Arrived!</p>", status: 200)
  }

## Safety and constraints

- Do not introduce network calls to external services without an explicit test plan; these apps run on a private Hubitat hub.
- Do not commit access tokens or secrets. Follow the existing `tryCreateAccessToken()` pattern.
- Always use concrete types and avoid dynamic typing (e.g., `def`), as Hubitat Groovy is statically typed at runtime.
- Always use parentheses for method calls, e.g., `logInfo("message")` not `logInfo "message"`.
- Always use braces for control structures, e.g., `if (condition) { ... }` not `if (condition) ...`.
- When modifying `preferences`, preserve existing keys and types to avoid breaking user configurations.
- When modifying `state`, preserve existing keys to avoid breaking runtime state.
- When modifying `mappings`, preserve existing paths to avoid breaking webhooks.
- When modifying scheduling or subscriptions, preserve existing patterns to avoid breaking behavior.
- Utilize @Field for static constants to improve performance and clarity.
- Utilize @CompileStatic where possible to improve performance and catch type errors at compile time.

## Pull request review guidance

- Prioritize concrete defects, behavior regressions, security problems, and compatibility risks over style preferences.
- Preserve public Hubitat contracts: mappings, state keys, setting names and types, lifecycle hooks, scheduling, subscriptions, and the `dwinks` namespace.
- Treat generated files under `Bundles/` as outputs. Fix their source files and let the bundle workflows regenerate them.
- For Groovy changes, consider the Hubitat Groovy 2.4 runtime and sandbox restrictions. Check the tests and lint rules under `tests/` when a change affects behavior or static compatibility.
- Report only actionable findings, with the file and line and a concrete failure scenario. If no actionable issue is found, say so and note relevant review gaps.

## What to update when adding a new package

1. Add Groovy file(s) under `Apps/` or `Drivers/` and corresponding `Libraries/` if needed.
2. Create `PackageManifests/<NewPackage>/packageManifest.json` with raw URL(s) to the files in `main` branch.
3. Add an entry to `repository.json` if this package should be visible at the top-level registry.

## If something is unclear

- Ask the repo owner (maintainer) which namespace and package manifest values to use before making breaking package changes.

---

If you'd like, I can: (1) run a quick scan of all `Apps/` and `Libraries/` to auto-extract common `preferences` keys and logging patterns, or (2) include a short checklist template for adding a new App/Package.
