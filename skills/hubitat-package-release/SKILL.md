---
name: hubitat-package-release
description: Prepare and verify Hubitat Package Manager manifests, repository metadata, generated bundles, and release workflow changes for this repository.
---

# Hubitat Package Release

Use only for packaging, publishing, or release work. New packages should use
standalone Apps and Drivers; libraries and `#include` are legacy maintenance
concerns.

1. Identify the package source files and any intentional legacy dependencies.
2. Create or update `PackageManifests/<PackageName>/packageManifest.json` with
   correct raw GitHub URLs and metadata.
3. Update `repository.json` only for top-level package changes.
4. Inspect the relevant GitHub workflow and preserve its inputs and naming.
5. Never manually edit `Bundles/`; regenerate generated content through the
   established GitHub Actions workflow.
6. Run targeted lint/tests and inspect URLs, paths, secrets, and unrelated
   changes before release.

Preserve namespaces, source paths, mappings, settings, state keys, and public
commands unless the release explicitly includes a migration. Never publish,
tag, create releases, or mutate remote repository state without explicit user
authorization.
