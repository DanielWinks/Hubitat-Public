# Hubitat Lint and Test Suite

A Groovy + JVM static analyzer and unit-test harness for the Hubitat Apps,
Drivers, and Libraries in this repository. The goal is to catch as many
"won't install on the hub" failures as possible **before** we copy code onto a
real Hubitat hub.

## What it checks

The linter walks every `*.groovy` file under `Apps/`, `Drivers/`, and
`Libraries/` (skipping `Bundles/`, which are auto-generated), parses it with
Groovy's own AST, and runs a pipeline of rules:

| Rule              | Catches |
| ----------------- | ------- |
| `Syntax`          | Missing braces, unbalanced parens, malformed code |
| `Metadata`        | Missing/incomplete `definition()`, `metadata{}`, `preferences{}`, `library()` blocks; required fields like `name`, `namespace`, `author` |
| `Imports`         | Imports outside the curated Hubitat sandbox allowlist (`tests/src/main/resources/allowed-imports.txt`) |
| `CompileStatic`   | `@CompileStatic` methods with untyped parameters, missing return types, untyped `catch (e)`, untyped closure params |
| `MethodReference` | `subscribe`/`runIn`/`runInMillis`/`schedule`/`runEvery*`/`asynchttp*` callbacks and `mappings { path { action: [...] } }` handlers that don't resolve to a defined method (locally or in any `#include`'d library) |
| `DriverDefinition`| Unknown capability names, `command 'foo'` declarations with no matching method, attribute types that aren't recognized, unusual input types |
| `Sandbox`         | `package` declarations, `System.exit`, `Runtime.exec`, `new File`, reflection (`Class.forName`), `@Grab`/`@Grapes` |

Cross-file resolution: before running rules, the linter parses every file and
builds a `ProjectIndex` that maps each library's fully-qualified name
(e.g. `dwinks.UtilitiesAndLoggingLibrary`) to the methods it declares. Rules
that look at method-name references consult this index, so a callback like
`runIn(1800, 'logsOff')` in an App that `#include`s a library defining
`logsOff` is not flagged as missing.

## What it doesn't check (yet)

* **Full @CompileStatic type resolution** - we catch pattern-based issues
  (untyped params, untyped catch, etc.), but to surface every CompileStatic
  error we'd need stub jars for the entire Hubitat platform API. Today only a
  handful of stubs exist for the test harness. The linter will catch most
  obvious problems without them.
* **Runtime semantics** - the linter cannot tell whether your event handler
  actually does the right thing. That's the job of the Spock specs (see below).

## What the test harness covers

`src/test/groovy/dwinks/hubitat/stubs/` contains a JVM stand-in for the
Hubitat runtime:
* `HubitatScriptHarness` - base class providing `state`, `settings`,
  `device`, `app`, `log`, plus capture-only stubs for `subscribe`, `runIn`,
  `schedule`, `sendEvent`, `asynchttp*`, etc. Calls are recorded into lists
  on the harness so specs can assert "after invoking `installed()`,
  `runIn(1800, 'logsOff')` should have been scheduled".
* `ScriptLoader` - loads a Hubitat App, Driver, or Library file as a
  `HubitatScriptHarness` subclass via `GroovyShell`. It strips `#include`
  directives, optionally inlines library content, and supplies no-op
  implementations for the top-level Hubitat idioms (definition/metadata/
  preferences/mappings/library) so the script doesn't fail to parse outside
  the hub.
* Stub classes under `com.hubitat.app.*`, `com.hubitat.hub.domain.*`,
  `hubitat.scheduling.*`, `hubitat.device.*`, and `groovy.util.slurpersupport.*`
  so production sources that import those types compile in our test JVM.

The functional specs in `src/test/groovy/dwinks/hubitat/functional/` exercise
real library methods - for example, `UtilitiesAndLoggingLibrarySpec` already
catches a real bug in `convertIPToHex` (single-digit octets are not
zero-padded), and `SunPositionLibrarySpec` verifies `getPosition()` returns
azimuth/altitude in plausible ranges.

## Running locally

```bash
cd tests
gradle test          # run all Spock specs
gradle lint          # run the linter against ../Apps ../Drivers ../Libraries
gradle assemble      # build a runnable distribution under build/install/
```

To lint a specific path:

```bash
gradle run -Pargs="../Apps/SunriseSimulation.groovy"
gradle run -Pargs="../Apps --no-color"
gradle run -Pargs="../Drivers --github"   # GitHub Actions annotations
```

Exit codes:
* `0` - no ERROR-level violations
* `1` - one or more errors found
* `2` - bad arguments

## Adding a new rule

1. Implement `dwinks.hubitat.lint.rules.Rule` in `src/main/groovy/dwinks/hubitat/lint/rules/`.
2. Register it in `HubitatLinter.main(...)` (the `rules` list).
3. Add a Spock spec under `src/test/groovy/dwinks/hubitat/lint/`.

## Adding a Hubitat capability or attribute type

Edit `src/main/resources/hubitat-capabilities.txt` or update
`DriverDefinitionRule.ACCEPTED_ATTRIBUTE_TYPES`.

## Adding an import to the allowlist

Edit `src/main/resources/allowed-imports.txt`. Entries can be exact FQNs or
package wildcards (`hubitat.*`, `java.util.concurrent.*`).

## CI

`.github/workflows/lint-and-test.yml` runs both the linter and the Spock
specs on every push and pull request. Lint findings show up as inline
GitHub annotations on the Files Changed view.
