# CLAUDE.md

## Project Overview

This repository contains Hubitat Elevation home automation apps, drivers, and libraries written in Groovy by Daniel Winks. Hubitat is a local home automation platform; all code runs directly on a Hubitat hub with no traditional build or compile step.

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
Libraries/                     # Reusable Groovy libraries included via #include
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
- **No build system** -- files are raw Groovy deployed directly to a Hubitat hub

## Key Conventions

### File Types

- **App**: Groovy file with a `definition(...)` block and `preferences` -- lives in `Apps/`
- **Driver**: Groovy file with a `metadata { definition(...) }` block -- lives in `Drivers/`
- **Library**: Groovy file with a `library(...)` block -- lives in `Libraries/`

### Namespace

All files use the `dwinks` namespace. Preserve this for backward compatibility.

### Library Inclusion

Libraries are included via `#include` directives at the top of files:

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

Use the logging methods from `UtilitiesAndLoggingLibrary` -- never use `System.out` or raw `log.*` calls:

- `logDebug(message)`, `logInfo(message)`, `logWarn(message)`, `logError(message)`, `logTrace(message)`

Apps and drivers expose boolean preferences: `logEnable`, `debugLogEnable`, `descriptionTextEnable`. Logs auto-disable after 30 minutes via `logsOff()`.

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

### HTTP Endpoints / Webhooks

Follow the established pattern:

```groovy
mappings { path("/endpoint") { action: [GET: "handlerMethod"] } }

String getLocalUri() { return getFullLocalApiServerUrl() + "/endpoint?access_token=${state.accessToken}" }
String getCloudUri() { return "${getApiServerUrl()}/${hubUID}/apps/${app.id}/endpoint?access_token=${state.accessToken}" }
```

Always use `tryCreateAccessToken()` for OAuth -- never hardcode tokens.

### Async HTTP with Retry

Use the retry utilities from `UtilitiesAndLoggingLibrary`:

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
- **Use `@Field static final`** for constants
- **Include MIT license header** on all source files

## What to Preserve (Do Not Break)

- Existing `mappings` paths (webhook URLs used by external systems)
- Existing `state` keys (breaking these corrupts running installations)
- Existing `settings` keys and types (breaking these loses user configuration)
- Existing scheduling and subscription patterns
- The `dwinks` namespace across all files
- Lifecycle hooks: `installed()`, `updated()`, `uninstalled()`, `initialize()`

## Testing

There is no automated test framework. Code must be tested by deploying to an actual Hubitat hub. Some apps expose test endpoints (e.g., GeminiTextRewriter has `/test`). Rely on logging for debugging.

## Adding a New Package

1. Add Groovy files under `Apps/` or `Drivers/` (and `Libraries/` if needed)
2. Include libraries via `#include dwinks.LibraryName`
3. Create `PackageManifests/<PackageName>/packageManifest.json` with raw GitHub URLs
4. Add entry to root `repository.json` if creating a new top-level package
5. Do not edit `Bundles/` -- these are auto-generated by GitHub Actions

## CI/CD Workflows

GitHub Actions in `.github/workflows/`:

- `release-sonos-advanced.yml` -- Automated version bump and release for Sonos Advanced
- `release-gemini-text-rewriter.yml` -- Automated release for Gemini Text Rewriter
- `SonosAdvancedBundles.yml` -- HPM bundle creation for Sonos
- `ThirdRealityBundles.yml` -- HPM bundle creation for Third Reality
- `UtilitiesAndLoggingLibrary.yml` -- Library release workflow

Release process: version increment via workflow input (patch/minor) -> update version across files -> update packageManifest.json -> create ZIP bundles -> publish GitHub release -> update repository.json.

## Security Constraints

- Never commit access tokens or secrets
- Do not introduce external network calls without explicit justification
- Follow the `tryCreateAccessToken()` pattern for OAuth
- API keys should be stored in `settings` (user input), never in source

## Reference Files

- `Apps/SunriseSimulation.groovy` -- Good example of webhooks, scheduling, state, and settings patterns
- `Libraries/UtilitiesAndLoggingLibrary.groovy` -- Core utilities used across all apps and drivers
- `.github/copilot-instructions.md` -- Additional AI coding assistant guidance

## Documentation

Always use the `llms-txt-mcp` (or `mcpdoc`) tools to look up library APIs
before writing code. Do not rely on training data for framework-specific APIs.
