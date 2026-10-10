# M1 – Verify and report (JVM/Maven, no AI) · Spec

Milestone: [M1 in features.md](../../../docs/features.md#m1--verify-and-report-jvmmaven-no-ai-) · Decisions: [brainstorming.md](brainstorming.md) · Status: **Ready to implement**

## Goal

`lector verify <repo>` tells you whether a dependency update broke a Maven project, where, and which failures are actually the update's fault.

**Why:** everything after M1 builds on this signal. The recipe run (M2) is checked with it, the fix loop (M3) works on its output and measures progress with it, and the PR report (M5) is made from it. If the signal blames the update for a bug that was already there, every later step works on the wrong thing. So M1 is not only "run Maven and parse errors". It also decides what the tool is responsible for (concept section 5, "Attribution").

## What to build

### 1. Data model in `core`

- `Diagnostic`: one problem with `file`, `line`, `column`, `code`, `message`, `severity`, `tool`. `file`, `line`, and `column` are nullable for failures without a source location (dependency resolution, plugin errors).
- `Verification`: the attributed result of a verify run, with four separate lists of `Diagnostic`s: **caused by update**, **pre-existing**, **flaky**, **unattributed**.

**Why:** one `Diagnostic` type for everything keeps every consumer simple: report, fix loop, PR comment. The classification is a result of comparing runs, not a property of a single problem, so it lives in `Verification`, not as a status field on `Diagnostic`.

### 2. `EcosystemAdapter` port in `core`, with only what M1 needs

- `detect(repo)`: does this adapter handle the repository?
- `verify(repo)`: build and test, return `Diagnostic`s.
- `runTests(repo, tests)`: run only the given tests, return `Diagnostic`s (used for the rerun and the baseline).

**Why:** the port is the extension point every ecosystem implements (concept section 2.1). It grows when a milestone needs a capability: `ground` in M3, `language` and `editableScope` in M4, `recipeRunner` in M2.

### 3. Attribution logic in `core`

- **Compile errors** in main or test code always count as **caused by update**.
- **Failing tests** are rerun once. A test that passes on the rerun is **flaky**.
- Tests that still fail are run on the **base commit**, but only if a base is given, and only those tests. A test that fails there too is **pre-existing**. A test that passes there is **caused by update**.
- Without a base commit, still-failing tests are **unattributed**.
- The base commit is built in a **separate Git worktree** in a temp directory, which is removed afterwards.

**Why:** a failing test doesn't mean the update broke it. It may be a pre-existing bug, a developer issue, or a flaky test, and the tool must never "fix" those (brainstorming Q2, Q14). Comparing against the base commit is the only reliable way to tell the difference. Running the base only for the still-failing tests means no extra build in the common case. A separate worktree leaves the head's working tree untouched, which matters once the fix loop has local commits (M3). The logic is language-neutral, so it belongs in `core` (architecture rule 1).

### 4. Maven adapter in a new module `ecosystems:jvm`

- **Detect:** a `pom.xml` at the repository root.
- **Invocation:** `./mvnw` if the repository has one, otherwise `mvn` from the environment.
- **Two stages,** both with batch mode and fail-at-end: first compile main and test code (`test-compile`). Only if that is clean, run the full `verify`.
- **Multi-module** projects work, and paths are reported relative to the repository root.
- **JDK:** whatever `JAVA_HOME` points to.
- **Maven settings:** the environment's settings as they are. An optional `--maven-settings <file>` is passed through to Maven.

**Why:** the wrapper respects the Maven version the project chose. Two stages separate compile errors (the main kind of update breakage) from test failures, and give the fix loop a cheap stage to re-run. Fail-at-end makes every module report its problems, not only the first broken one. Per-repo JDK selection needs the container image and belongs to M5. Regulated environments already configure a Maven mirror, so we don't invent our own configuration.

### 5. Parsers that turn Maven output into `Diagnostic`s

- **javac** errors from Maven's console output.
- **kotlinc** errors from Maven's console output (`kotlin-maven-plugin`).
- **Test failures** from surefire and failsafe XML reports. File and line come from the first stack frame inside the test class.
- **Build errors without a location** (dependency resolution, plugin failures) as `Diagnostic`s without `file`, `line`, or `column`.

**Why:** console output works with any compiler configuration, without changing the build. The XML reports are a stable format, and Gradle uses the same one (M7). Kotlin is supported from the start because Kotlin Maven projects are common (decision Q3). Build errors without a location are often the root cause, so they must not get lost.

### 6. CLI command `verify`

- `lector verify <repo> [--base <git-ref>] [--format text|json] [--maven-settings <file>]`
- **Text report** (default): one section per category. "Caused by the update" comes first, with build errors without a location listed in their own group. Then pre-existing, flaky, and unattributed.
- **JSON report** (`--format json`): the same `Verification` in a machine-readable form, written with kotlinx.serialization.
- The report goes to stdout. Logs go to stderr.
- **Exit codes:** `0` when nothing is caused by the update. `1` when at least one diagnostic is caused by the update. `2` on a tool error: no supported ecosystem, Maven not found, invalid base ref.

**Why:** the CI job only fails for problems the tool is responsible for. Pre-existing and flaky failures are still visible to reviewers, but they don't turn the job red. JSON is what later steps and tests consume. Text is what people read in CI logs. `--base` is explicit for now, and M5 passes the PR's base SHA automatically.

### 7. Tests and fixtures

- **Parser unit tests** on captured output files: javac errors, kotlinc errors, surefire and failsafe XML, and build errors without a location.
- **Attribution unit tests** with a fake adapter: compile errors, flaky, pre-existing, caused by update, unattributed.
- **Fixture repos** under `src/test/resources/fixtures/`, including a multi-module project.
- **One tagged integration test** runs real Maven on a fixture with a base commit and a head commit. The head has one test broken by the update and one test that was already failing on base.

**Why:** parser and attribution tests are fast and cover the edge cases. One real Maven run proves the whole path: invocation, worktree, parsing, attribution. It is tagged because it needs Maven and network access for dependencies.

## What not to do

| Don't | Why |
|---|---|
| Fix, skip, or exclude tests in any way (`-DskipTests`, test filters, ignoring failures) | M1 only observes. Bypassing tests would hide the very signal M1 exists to produce. |
| Change any file of the target repository | `verify` is read-only. Build output in ignored directories (`target/`) is the only change allowed. |
| Treat every failing test as caused by the update | That's the core decision of this milestone: only baseline-confirmed failures and compile errors count. |
| Guess the base commit (e.g. merge base with the default branch) | Decided against: it guesses the default branch and breaks on shallow CI clones. Without `--base`, failures are unattributed. |
| Check out the base commit in the head's working tree | Decided against: separate worktree only. |
| Run the full suite on the base commit | Only the still-failing tests run there (decision Q15). |
| Add a status field to `Diagnostic` | Decided against: the classification lives in `Verification`. |
| Add `DependencyChange` | First used in M2 (`--package`, `--from`, `--to`). Unused types are guesses. |
| Add `ground`, `editableScope`, `language`, `recipeRunner`, or `manifestDiff` to the port | Later milestones (M2–M4, M8). |
| Pick a JDK per repository | M5, with the container image. |
| Produce SARIF | Not needed before M5. |
| Use the Maven Embedder or inject a Maven extension (EventSpy) | Decided against: Maven runs as a process, and its output is parsed. |
| Make the goals configurable | Decided against: the two fixed stages work without configuration. |
| Support Gradle | M7. |
| Call an LLM | No AI in M1 (architecture rule 2). |

## Acceptance criteria

The review agent checks every criterion and reports it as *met*, *not met*, or *can't verify*, with evidence.

| ID | Criterion | Covers | How to verify |
|---|---|---|---|
| AC1 | `./gradlew build` passes | all | Run it from the repo root: exit code `0` |
| AC2 | `Diagnostic` has `file`, `line`, `column`, `code`, `message`, `severity`, `tool`; `file`, `line`, `column` are nullable | 1 | Type definition in `core` |
| AC3 | `Verification` has four separate lists: caused by update, pre-existing, flaky, unattributed | 1 | Type definition in `core` |
| AC4 | `Diagnostic` has no status or classification field | 1, Don't | Type definition in `core` |
| AC5 | The `EcosystemAdapter` port in `core` has exactly `detect`, `verify`, `runTests` | 2, Don't | Interface definition in `core` |
| AC6 | `core` still has no project dependencies | 2 | `core/build.gradle.kts` contains no `project(` |
| AC7 | Attribution logic lives in `core` and has no Maven-specific code | 3 | No Maven or `ecosystems` references in `core` sources |
| AC8 | A compile error is classified as caused by update | 3 | Attribution unit test |
| AC9 | A test that fails, then passes on rerun, is classified as flaky | 3 | Attribution unit test |
| AC10 | A test that fails on head and on base is classified as pre-existing | 3 | Attribution unit test |
| AC11 | A test that fails on head and passes on base is classified as caused by update | 3 | Attribution unit test |
| AC12 | Without `--base`, a still-failing test is classified as unattributed | 3 | Attribution unit test |
| AC13 | The base run executes only the still-failing tests, and only when there are some | 3 | Attribution unit test: the fake adapter records the calls |
| AC14 | The base commit is built in a separate worktree that is removed afterwards | 3 | Integration test; after the run, `git worktree list` in the fixture shows only the main worktree |
| AC15 | The Maven adapter lives in module `ecosystems:jvm`, and `cli` depends on it | 4 | `settings.gradle.kts`, `cli/build.gradle.kts` |
| AC16 | Detection succeeds for a root `pom.xml` and fails otherwise | 4 | Unit test |
| AC17 | `./mvnw` is used when present, otherwise `mvn` | 4 | Unit test on the command line the adapter builds |
| AC18 | The test stage runs only if the compile stage has no errors | 4 | Unit or integration test |
| AC19 | Maven runs in batch mode with fail-at-end in both stages | 4 | Unit test on the command line the adapter builds |
| AC20 | `--maven-settings <file>` is passed through to Maven | 4, 6 | Unit test on the command line the adapter builds |
| AC21 | Diagnostics in a multi-module fixture have paths relative to the repository root | 4 | Parser or integration test with the multi-module fixture |
| AC22 | javac errors are parsed into `Diagnostic`s with file, line, column, and message | 5 | Parser unit test on captured output |
| AC23 | kotlinc errors are parsed into `Diagnostic`s with file, line, column, and message | 5 | Parser unit test on captured output |
| AC24 | surefire and failsafe XML failures are parsed into `Diagnostic`s with test file and line | 5 | Parser unit test on captured reports |
| AC25 | A dependency-resolution failure becomes a `Diagnostic` without a location | 5 | Parser unit test on captured output |
| AC26 | `lector verify <repo>` prints a text report with the four categories, caused by update first | 6 | Integration test or CLI test |
| AC27 | `--format json` prints the `Verification` as valid JSON to stdout, with nothing else on stdout | 6 | CLI test parses stdout as JSON |
| AC28 | Exit code `0` when nothing is caused by the update, even with pre-existing or flaky failures | 6 | CLI or integration test |
| AC29 | Exit code `1` when at least one diagnostic is caused by the update | 6 | CLI or integration test |
| AC30 | Exit code `2` for an unsupported repository and for an invalid `--base` ref | 6 | CLI tests |
| AC31 | The tagged integration test runs real Maven on a base/head fixture and finds one failure caused by the update and one pre-existing failure | 7 | Run the tagged test task: passes |
| AC32 | The tagged integration test is not part of `./gradlew build` | 7 | Build configuration excludes the tag from the default test task |
| AC33 | After `verify`, the target repository has no changes outside ignored build output | Don't | Integration test: `git status --porcelain` in the fixture is empty |
| AC34 | No command line built by the adapter skips, filters, or ignores tests | Don't | No `skipTests`, `maven.test.skip`, `testFailureIgnore`, or `-Dtest=` in the `verify` command; `-Dtest=` only in `runTests` |
| AC35 | No `DependencyChange` type exists yet | Don't | Search `core` sources |
| AC36 | No Gradle support, SARIF output, Maven Embedder, Maven extension, or LLM client is added | Don't | Dependencies in the version catalog and module build files |

## Docs to update

- `docs/features.md`: set M1 to ✅ when done.
- `AGENTS.md`: add the command for the tagged integration test to **Commands**.
- `docs/concept.md` section 5: adjust the interface outline if the port's final shape differs from `detect`, `verify`, `runTests`.
