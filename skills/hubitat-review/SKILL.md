---
name: hubitat-review
description: Review Hubitat Apps, Drivers, and legacy libraries for correctness, compatibility, static typing, security, and test coverage without implementing fixes unless requested.
---

# Hubitat Review

Perform a read-only, evidence-based review of the complete changed file and
relevant history.

Check:

1. Syntax, metadata, imports, callbacks, capabilities, and sandbox rules.
2. Lifecycle cleanup, subscriptions, schedules, repeated callbacks, and state
   transitions.
3. Existing mappings, settings, state keys, commands, and child-device IDs;
   flag migrations explicitly.
4. New standalone-code compliance; treat `#include` as legacy.
5. Local logging helpers and one level dropdown for new configurable logging.
6. `@CompileStatic` opportunities and dynamic Hubitat boundaries. Watch for
   Groovy 2.4 issues such as `/` yielding `BigDecimal` and `Math.round()`
   overload mismatches.
7. HTTP validation, bounded retries, stale-callback protection, and secrets.
8. Focused regression tests for changed behavior.

Report findings by severity with file/line references and runtime impact.
Distinguish confirmed defects from suggestions. Do not modify files or live
Hubitat devices during a review.
