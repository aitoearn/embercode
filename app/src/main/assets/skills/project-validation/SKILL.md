---
name: project-validation
description: Validate software changes with the project's own checks, focused regression tests, builds, and runtime smoke tests. Use before declaring implementation work complete or preparing a release.
compatibility: Requires the project's existing build and test tools.
---

Read the repository guidance and build manifests first. Reuse checked-in wrappers and scripts. Do not install a second toolchain when the project already declares one.

Validate from narrow to broad:

1. Inspect the final diff for accidental files, secrets, generated output, stale references, and unrelated edits.
2. Run the focused test for the changed behavior.
3. Run the affected module's tests and static analysis.
4. Build the actual target artifact.
5. Exercise the changed path in its real runtime when an emulator, simulator, browser, or local service is available.
6. For release work, inspect the packaged artifact rather than inferring its contents from source files.

Never claim a check passed unless its command completed successfully. Distinguish failures caused by the change from missing credentials, unavailable infrastructure, or pre-existing failures. Report the exact failing command and the first useful error.
