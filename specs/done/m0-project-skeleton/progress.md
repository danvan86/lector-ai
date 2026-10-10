# Progress – M0 Project skeleton

Spec: [`spec.md`](spec.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | Gradle wrapper, `core` + `cli` modules, version catalog, foojay resolver | ✅ |
| 2 | Convention plugin in `buildSrc`: Kotlin, JDK 25, JUnit, ktlint, detekt, Kover | ✅ |
| 3 | CLI `lector`: `--version`, help on no arguments, version from Gradle (test-first) | ✅ |
| 4 | `installDist` distribution, SLF4J to stderr, end-to-end test | ✅ |
| 5 | CI workflow, Dependabot, Apache 2.0 license | ✅ |
| 6 | Quality gates verified (ktlint, detekt, Kover fail the build) | ✅ |
| 7 | Docs in sync (`features.md`, `AGENTS.md`) | ✅ |
| 8 | Review and fixes ([`review.md`](review.md)) | ✅ |

## Quality gates

`./gradlew build` is green: 9 unit tests (`test`), 4 end-to-end tests (`endToEndTest`), ktlint (modules, root scripts, `buildSrc`), detekt (`detektMain`, `detektTest`), Kover (`cli`: 100 % lines and branches, `MainKt` excluded).

Each gate was checked by adding a violation, running `./gradlew build`, and reverting:

| Gate | Probe | Result |
|---|---|---|
| ktlint | extra whitespace in `Main.kt` | fails in `ktlintMainSourceSetCheck` |
| detekt | `println` in `Main.kt` | fails in `detektMain` with `ForbiddenMethodCall` |
| Kover | untested function in `cli` | fails in `koverVerify` (lines and branches below bounds) |

## Open

- AC18: the CI workflow runs once the branch is pushed. Not verified yet.
