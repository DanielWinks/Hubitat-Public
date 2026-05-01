---
description: Multi-agent code review for Hubitat Groovy apps, drivers, and libraries. Dispatches specialized subagents in parallel and posts inline + summary comments on the PR.
allowed-tools: Bash(gh *), Bash(git *), Read, Grep, Glob, Agent
---

# /review — Hubitat-Public PR review

You are reviewing a pull request against the `Hubitat-Public` repository. This codebase is a collection of Groovy apps, drivers, and libraries deployed directly to a Hubitat Elevation home automation hub. **There is no compile or test step** — code correctness is verified by deployment to actual hardware. Your review must therefore be a careful static analysis that catches the failure modes Hubitat Groovy is prone to.

## Read this first (project context)

Before doing anything else, read these files to load the conventions you'll be enforcing:

1. `CLAUDE.md` — code style rules, what-to-preserve list, async HTTP retry pattern
2. `.github/copilot-instructions.md` — additional convention notes
3. `Libraries/UtilitiesAndLoggingLibrary.groovy` — the logging methods, `tryCreateAccessToken()`, retry helpers that drivers/apps depend on

If a file in the diff `#include`s a library, also read that library so you understand what symbols (especially imports) the library was providing.

## Workflow

1. **Gather context.** Run `gh pr view --json number,title,body,baseRefName,headRefName,files`. Read the PR description carefully — it often explains why a change that looks suspicious is actually intentional.
2. **Get the diff.** Run `git diff "$PR_BASE_REF"...HEAD` (or `gh pr diff`) to see the full set of changes. Note added, deleted, and modified files separately.
3. **Decide if review is even needed.** If the diff only touches `Bundles/` (auto-generated), `PackageManifests/*/packageManifest.json` (release metadata), `Resources/`, `Readme/`, or `repository.json` version bumps — post a one-line summary noting the change is mechanical/release-only and exit without dispatching subagents.
4. **Dispatch the three review agents in parallel** (single message, multiple Agent tool calls). See "Agent rubrics" below.
5. **Filter findings.** Drop anything below 80% confidence. The goal is high signal; do not surface speculative concerns.
6. **Post results.** Inline `file:line` comments for each surviving finding via `gh pr review --comment` or `gh api`. Then post one summary comment grouping findings by severity. If zero findings clear the bar, post `"No blocking issues found."` so the user knows the review actually ran.

## Agent rubrics

Dispatch all three agents with the **full diff** plus the **base-branch versions** of any modified files (so they can see deletions and renames in context). Each agent returns findings in the format below.

### Agent 1 — Hubitat-correctness

**Focus:** platform-specific gotchas that pass static checks but break the running hub.

Block-level severity (always block-tag):

- **Removed `#include` without restoring imports.** When `#include dwinks.UtilitiesAndLoggingLibrary` (or any other library include) is removed, the file MUST add back any types the library was providing transitively. Check for these in particular and verify each is now imported explicitly if used:
  - `import groovy.transform.Field` — required for `@Field static final` declarations. **Most common breakage.**
  - `import groovy.json.JsonOutput` — required for `JsonOutput.toJson()` / `JsonOutput.prettyPrint()`
  - `import groovy.transform.CompileStatic` — required for `@CompileStatic` annotation
  - `import com.hubitat.app.ChildDeviceWrapper` — required when used as a declared type
  - `import com.hubitat.app.DeviceWrapper` — required when used as a method-param type
  - `import hubitat.scheduling.AsyncResponse` — required when used in async callback signatures
  Any missing import causes an "unparseable" error on the hub. Hubitat does NOT auto-import these.

- **`pauseExecution(...)` introduced.** This is a blocking call that halts the entire thread. Use `runIn(seconds, 'methodName')` for delays. The only exception is when the explicit intent is to block the thread (and the PR description says so).

- **Attribute or state value set to `null`.** `sendEvent(value: null)` and `state.x = null` do not persist properly on the hub. Reset patterns must use valid non-null values:
  - Strings → `''`
  - JSON strings → `'{}'`
  - Numbers → `0`
  - String-typed numbers → `'0'`
  - Enums → a valid enum value like `'off'`

- **Webhook path changed in `mappings { path("/...") }`.** External systems hold these URLs. Renaming silently breaks integrations. Adding new paths is fine; modifying or deleting existing ones is block-level.

- **Existing `state.*` keys removed or renamed.** Corrupts running installations on user hubs. New keys are fine; modifying existing ones is block-level.

- **Existing `settings.*` keys removed or renamed (or types changed).** Loses user configuration on existing installations.

- **Lifecycle hooks deleted or renamed.** `installed()`, `updated()`, `uninstalled()`, `initialize()` are called by the platform. Do not touch.

- **`dwinks` namespace changed** in `definition()`, `metadata { definition() }`, or `library()` blocks.

- **Hand-edits to files under `Bundles/`.** These are auto-generated by GitHub Actions (`SonosAdvancedBundles.yml`, `ThirdRealityBundles.yml`, `UtilitiesAndLoggingLibrary.yml`). Hand-edits will be overwritten on the next workflow run. The fix is to edit source files in `Apps/`, `Drivers/`, or `Libraries/` instead.

- **New file under `Apps/` or `Drivers/` introducing a new top-level package without a corresponding `PackageManifests/<name>/packageManifest.json` and an entry in root `repository.json`.** Adding files to existing packages is fine; introducing new packages requires manifest and registry updates.

### Agent 2 — Code quality

**Focus:** style, types, and convention compliance from `CLAUDE.md`.

High-severity:

- Raw `log.debug` / `log.info` / `log.warn` / `log.error` / `log.trace` calls instead of the wrapped `logDebug` / `logInfo` / `logWarn` / `logError` / `logTrace` from `UtilitiesAndLoggingLibrary`. **Exception:** the library itself, and the BidirectionalSync app/child app (which are explicitly standalone — see `MEMORY.md` note).
- `def` used as a type where a concrete type is known and clear (`String`, `Integer`, `Long`, `Boolean`, `Map`, `List`, `BigDecimal`).
- Method calls without parentheses (e.g. `logInfo "x"` instead of `logInfo("x")`). CLAUDE.md mandates parens.
- Control structures without braces (e.g. `if (x) doThing()` instead of `if (x) { doThing() }`). CLAUDE.md mandates braces.
- Constants declared without `@Field static final`.
- New Groovy source file missing the MIT license header.
- Async HTTP callback that doesn't follow the retry pattern from `UtilitiesAndLoggingLibrary`:
  ```groovy
  void callbackMethod(AsyncResponse response, Map data) {
    if (isHttpResponseFailure(response)) {
      handleAsyncHttpFailureWithRetry(response, 'retryMethod')
      return
    }
    // process success
  }
  ```
  Missing the failure check or the retry helper means transient HTTP errors silently swallow data.

Medium-severity:

- Missing `@CompileStatic` on a file that has no dynamic-only Groovy idioms in scope (closures with implicit delegate access, builders, etc.). `@CompileStatic` catches type errors at compile time.
- `descriptionTextEnable` toggle missing on user-visible info logs in drivers.
- New OAuth endpoint not using `tryCreateAccessToken()`.

### Agent 3 — Security & secrets

**Focus:** anything that leaks credentials or makes outbound calls without justification.

Block-level:

- API keys, OAuth tokens, passwords, or other secrets hardcoded into source. Must come from `settings` (user input via preferences UI) or be passed in at runtime. Never `static final String API_KEY = "..."`.
- New external network calls (asynchttpGet/Post to a domain not already used in the codebase) without justification in the PR description. Hubitat is a local-first platform; new outbound calls are a meaningful change.
- Logging that could leak access tokens, session cookies, full HTTP response bodies, or PII. Tokens in particular: `logDebug("response: ${response.data}")` is suspect if `response` may contain auth headers.
- OAuth handler not using `tryCreateAccessToken()`.

## Output format

Each finding from each agent must use this exact format:

```
[severity] [category] (confidence: NN)
file/path.groovy:LINE
<one-line description of the issue>
<optional: 1-3 lines of rationale or fix suggestion>
```

- `severity` ∈ `block`, `high`, `medium`
- `category` ∈ `hubitat-correctness`, `code-quality`, `security`
- `confidence` is an integer 0-100. Drop everything below 80.

After collecting and filtering all findings, post:

1. **Inline review comments** for each finding using `gh api repos/$REPO/pulls/$PR_NUMBER/comments` or `gh pr review --comment --body "..." -F -`. Anchor each comment to the specific `file:line` from the finding.
2. **A summary PR comment** using `gh pr comment $PR_NUMBER --body "..."` with this structure:
   ```
   ## Claude review summary

   **Block (N):** brief list
   **High (N):** brief list
   **Medium (N):** brief list

   See inline comments for details.
   ```

If zero findings survived filtering, post a single summary comment: `"No blocking issues found."` — this confirms the review actually ran.

## Constraints — what NOT to do

- **Do not** run `npm test`, `pytest`, `gradle`, or any test/build command. There is no build for this codebase.
- **Do not** edit code in the PR. This is a review, not a fix.
- **Do not** flag stylistic preferences not codified in `CLAUDE.md` (tabs vs spaces, line length, etc.).
- **Do not** fabricate findings to justify the review running. If the diff is clean, say so.
- **Do not** comment on `Bundles/` content — auto-generated.
- **Do not** comment on `PackageManifests/*/packageManifest.json` version bumps — release metadata.
