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

Update or add the affected regression specs in the same change whenever
behavior changes; test updates are required, not optional. Add focused harness
coverage for state transitions, schedules/subscriptions, button events, HTTP
parsing or retries, device tracking, and pure calculations. If the current
harness cannot exercise a behavior, explain the gap and add structural or
static coverage where practical. For release-ready changes, run `./gradlew lint`.

Treat syntax, callback, capability, sandbox, and static-typing errors as
blockers. Separate changed-file failures from pre-existing repository failures.
Lint does not prove live Hubitat behavior; note when a real-hub smoke test is
needed for dynamic pages, device commands, subscriptions, or lifecycle work.
For browser-based smoke tests, always use the Codex built-in browser (IAB)
through CUA; do not use Firefox, Chrome, or another native/external browser.
Finish with `git diff --check`.
