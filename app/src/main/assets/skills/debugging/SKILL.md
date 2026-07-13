---
name: debugging
description: Diagnose defects, crashes, hangs, incorrect output, failed builds, and regressions from evidence. Use when the cause is unknown and a reproducible root-cause investigation is needed.
compatibility: Requires only the project's existing tools and test commands.
---

Start from the observed failure. Record the exact command, input, environment, expected behavior, and actual behavior before changing code.

Find the narrowest reliable reproduction. Read the failing path and all callers that share it. Check recent relevant changes, logs, exit codes, persisted state, concurrency boundaries, and platform-specific behavior. Form one testable hypothesis at a time and use the cheapest check that can disprove it.

Fix the earliest shared cause that explains the evidence. Avoid compensating guards in each caller when one invariant can be restored at the boundary that owns it. Preserve unrelated behavior and existing user changes.

Verification:

1. Run the smallest check that failed before the fix.
2. Add or update one regression test that would fail without the fix.
3. Run the nearest broader suite, static checks, and build for the affected target.
4. Report the root cause, the proof, and any remaining uncertainty.
