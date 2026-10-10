# Progress – M1 Verify and report (JVM/Maven, no AI)

Spec: [`spec.md`](spec.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | `core`: `Diagnostic`, `Verification`, `EcosystemAdapter` port, attribution (compile errors, rerun, base comparison) with a fake adapter | ✅ |
| 2 | `core`: process runner with timeout, base commit in a Git worktree | ✅ |
| 3 | `ecosystems:jvm`: fixture repos, captured Maven output, parsers (javac, kotlinc, build errors, surefire/failsafe XML) | ✅ |
| 4 | `ecosystems:jvm`: `MavenAdapter` (detect, wrapper, two stages, `runTests`, settings) | ✅ |
| 5 | `cli`: `verify` command, text and JSON report, exit codes, end-to-end tests | ✅ |
| 6 | Tagged integration test with real Maven on a base/head fixture | ✅ |
| 7 | Quality gates verified | ✅ |
| 8 | Docs in sync (`features.md`, `AGENTS.md`, concept 2.3 and 5, `docs/architecture/verify-pipeline.md`, `project-skeleton.md`) | ✅ |

## Quality gates

Each gate was checked by adding a violation to the new code, running `./gradlew build --continue`, and reverting:

| Gate | Probe | Result |
|---|---|---|
| ktlint | extra space in `MavenConsole.kt`, `MavenAdapterTest.kt`, `ecosystems/jvm/build.gradle.kts` | fails in `ktlintMainSourceSetCheck`, `ktlintTestSourceSetCheck`, `ktlintKotlinScriptCheck` of `:ecosystems:jvm` |
| detekt | `println` in `ecosystems:jvm`, `System.out.println` in `core` | fails in `detektMain` with `ForbiddenMethodCall` |
| Kover | untested function with a branch in `ecosystems:jvm` and in `core` | fails in `koverVerify` |
| Tests | inverted assertion in `MavenAdapterTest` | fails in `:ecosystems:jvm:test` |
| Integration test | `verify` without `--base` | fails: the update's failure becomes unattributed |

`./gradlew build --dry-run` runs every gate task of `:ecosystems:jvm` once, and doesn't contain `:cli:integrationTest`.
Without the `disabledForTestTasks` setting in `cli/build.gradle.kts`, Kover pulls `integrationTest` into `build`.

## Acceptance criteria

| ID | Result | Evidence |
|---|---|---|
| AC1 | Met | `./gradlew build` exits with 0 |
| AC2 | Met | `Diagnostic.kt`. Also nullable: `code` (javac and kotlinc print none). One more field: `test`, see Deviations |
| AC3 | Met | `Verification.kt` |
| AC4 | Met | `Diagnostic` has no status field; `test` names the test, it doesn't classify |
| AC5 | Met | `EcosystemAdapter.kt`: `detect`, `verify`, `runTests` |
| AC6 | Met | `core/build.gradle.kts` has no `project(` |
| AC7 | Met | `grep -riE 'maven\|mvn\|ecosystems\|surefire\|javac' core/src/main` finds nothing |
| AC8–AC13 | Met | `AttributionTest` |
| AC14 | Met | `VerifyIntegrationTest`: one worktree after the run; `GitBaseCommitTest` |
| AC15 | Met | `settings.gradle.kts`, `cli/build.gradle.kts` |
| AC16 | Met | `MavenAdapterTest`: detects / doesn't detect |
| AC17 | Met | `MavenAdapterTest`: wrapper used, and run as a real process |
| AC18 | Met | `MavenAdapterTest`: no test stage after compile errors or an unexplained compile failure |
| AC19 | Met | `MavenAdapterTest`: `-B -fae` in both stages |
| AC20 | Met | `MavenAdapterTest`, `VerifyCommandTest` |
| AC21 | Met | `MavenConsoleTest` (javac, two modules), `TestReportsTest` (surefire and failsafe, two modules) |
| AC22–AC25 | Met | `MavenConsoleTest`, `TestReportsTest` on captured output |
| AC26 | Met | `ReportsTest`, `VerifyCommandTest` |
| AC27 | Met | `VerifyCommandTest` parses stdout as JSON, stderr empty |
| AC28–AC30 | Met | `VerifyCommandTest`, `RootCommandTest`, `LectorEndToEndTest` |
| AC31 | Met | `./gradlew :cli:integrationTest` passes (about 15 s with a warm Maven repository) |
| AC32 | Met | `test` excludes the tag; `build --dry-run` has no `integrationTest` |
| AC33 | Met | `VerifyIntegrationTest`: `git status --porcelain` is empty |
| AC34 | Met | `-Dtest=` and `-Dit.test=` only in `runTests`; `MavenAdapterTest` checks the verify commands |
| AC35 | Met | no `DependencyChange` |
| AC36 | Met | only `kotlinx-serialization-json` added |

## Deviations and decisions

- **`Diagnostic.test`**: attribution has to rerun and match failing tests by name, so test failures carry the test name (`com.example.FooTest#bar`). It is not a classification. `docs/concept.md` 2.3 lists it.
- **`runTests(repo, tests: List<Diagnostic>)`** takes the test failures, not names: Maven needs to know whether a test ran in surefire or failsafe. Reruns run whole test classes, because surefire can't select parameterized test methods by name.
- **A run that breaks the build proves nothing about tests**: if the rerun has a build problem, all tests count as still failing; if the base commit doesn't build, they stay unattributed.
- **A failed Maven run without a parsable error** becomes a build error with the end of the output, so it can never look green.
- **Exit codes**: tool errors (unsupported repository, missing paths, invalid `--base`, Maven not found) exit with 2. Clikt's own usage errors (unknown option, invalid `--format`) still exit with 1, as in M0, which overlaps with "caused by the update". Open for a decision.
