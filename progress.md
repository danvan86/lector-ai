# Progress – M0 Project skeleton

Spec: [`specs/open/m0-project-skeleton/spec.md`](specs/open/m0-project-skeleton/spec.md)

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

## Quality gates

`./gradlew build` is green: 8 tests (unit + end-to-end), ktlint, detekt (`detektMain`, `detektTest`), Kover (`cli`: 82 % lines).

Each gate was checked by adding a violation, running `./gradlew build`, and reverting:

| Gate | Probe | Result |
|---|---|---|
| ktlint | extra whitespace in `Main.kt` | fails in `ktlintMainSourceSetCheck` |
| detekt | `println` in `Main.kt` | fails in `detektMain` with `ForbiddenMethodCall` |
| Kover | untested function in `cli` | fails in `koverVerify` (lines and branches below bounds) |

## Open

- AC18: the CI workflow runs once the branch is pushed. Not verified yet.
