---
name: hubitat-skill-maintainer
description: Audit and update this repository’s Codex skills when their guidance drifts from AGENTS.md, recent Hubitat code patterns, tests, or release workflows.
---

# Hubitat Skill Maintainer

Use this skill when reviewing repository skills, updating development guidance,
or investigating repeated failures that may indicate stale skill instructions.
This skill is invoked by relevant maintenance tasks; it is not a background
monitor and cannot detect drift without being run.

## Audit sources

Review these sources before proposing changes:

1. `AGENTS.md` for repository-wide policy.
2. Recent commits and diffs, emphasizing repeated patterns rather than one-off
   changes.
3. Representative current Apps, Drivers, Libraries, tests, and workflows.
4. `tests/README.md`, lint output, and test output for actual workflow rules.
5. Every repository-local skill under `skills/*/SKILL.md`.

Use `git log`, `git show`, `rg`, and `git diff` for evidence. Preserve unrelated
working-tree changes.

## Drift criteria

Flag guidance when it is:

- Directly contradicted by `AGENTS.md` or current repository policy.
- Repeatedly contradicted by recent implementations across more than one file.
- Responsible for a demonstrated lint, static-typing, test, or release failure.
- Referring to removed paths, obsolete settings, retired workflows, or legacy
  patterns as the default for new code.

Do not update a skill solely because of a single experimental implementation,
personal preference, or an unverified assumption about Hubitat behavior.

## Update workflow

1. Summarize the evidence and identify which skill sections are stale.
2. Keep `AGENTS.md` authoritative; make skills more actionable without
   duplicating the entire policy file.
3. Update only the affected skill instructions. Preserve useful boundaries,
   invocation descriptions, and unrelated user changes.
4. Keep each skill focused on one workflow. Split a skill only when its scope
   has become materially ambiguous.
5. Re-read the changed skills for contradictions, excessive specificity, and
   accidental expansion of permissions.
6. Validate frontmatter and skill structure with the available skill validator;
   if dependencies prevent validation, report that limitation explicitly.
7. Run `git diff --check` and report the evidence, files changed, and any
   remaining uncertainty.

## Safety and scope

This skill may edit repository-local skill files and, when explicitly requested,
`AGENTS.md`. It must not publish releases, mutate remote repository state,
change live Hubitat devices, or install global skills without separate explicit
authorization. Do not create scripts, references, or new skills unless they
provide a demonstrated reusable benefit.
