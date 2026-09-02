---
name: hubitat-test-and-lint
description: Verify Hubitat repository changes with targeted linting, Spock tests, and focused regression coverage using the local tests harness.
---

# Hubitat Test and Lint

Use after changing Groovy under `Apps/`, `Drivers/`, or `Libraries/`.

From `tests/`, run targeted lint first:

```bash
./gradlew run -Pargs="../path/to/file.groovy --no-color"
```

Then run the relevant test class or full suite:

```bash
./gradlew test --tests package.SpecName
./gradlew test
```

For release-ready changes, run `./gradlew lint`. Add focused harness specs
for state transitions, schedules/subscriptions, button events, HTTP parsing or
retries, device tracking, and pure calculations when practical.

Treat syntax, callback, capability, sandbox, and static-typing errors as
blockers. Separate changed-file failures from pre-existing repository failures.
Lint does not prove live Hubitat behavior; note when a real-hub smoke test is
needed for dynamic pages, device commands, subscriptions, or lifecycle work.
Finish with `git diff --check`.
