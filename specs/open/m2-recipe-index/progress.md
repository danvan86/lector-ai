# Progress – M2 Recipe index and OpenRewrite run

Spec: [`spec.md`](spec.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | `core`: `DependencyChange`, `VersionScheme`, `Recipe`, `RecipeIndex` port, optional adapter capabilities | ✅ |
| 2 | `ecosystems:jvm`: Maven version scheme (`ComparableVersion`, X-ranges, `updateType`) | ✅ |
| 3 | `recipes`: index format, reader, recipe selection on a hand-written index | ✅ |
| 4 | `recipes`: index generator at build time from `rewrite-recipe-bom`, index report, generated-index test | ✅ |
| 5 | `core`: migration flow with downgrade rules, Git working tree (clean check, capture, reset) | ✅ |
| 6 | `ecosystems:jvm`: resolved versions and recipe runner (`runNoFork`) | ⬜ |
| 7 | `cli`: `migrate` command, recipe section in text and JSON report, end-to-end tests | ⬜ |
| 8 | Tagged integration test: Spring Boot 3.3 → 3.4 fixture | ⬜ |
| 9 | Quality gates verified | ⬜ |
| 10 | Docs in sync | ⬜ |
