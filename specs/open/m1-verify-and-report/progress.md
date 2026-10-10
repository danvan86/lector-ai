# Progress – M1 Verify and report (JVM/Maven, no AI)

Spec: [`spec.md`](spec.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | `core`: `Diagnostic`, `Verification`, `EcosystemAdapter` port, attribution (compile errors, rerun, base comparison) with a fake adapter | ✅ |
| 2 | `core`: process runner with timeout, base commit in a Git worktree | ✅ |
| 3 | `ecosystems:jvm`: fixture repos, captured Maven output, parsers (javac, kotlinc, build errors, surefire/failsafe XML) | ✅ |
| 4 | `ecosystems:jvm`: `MavenAdapter` (detect, wrapper, two stages, `runTests`, settings) | ⬜ |
| 5 | `cli`: `verify` command, text and JSON report, exit codes, end-to-end tests | ⬜ |
| 6 | Tagged integration test with real Maven on a base/head fixture | ⬜ |
| 7 | Quality gates verified | ⬜ |
| 8 | Docs in sync | ⬜ |
