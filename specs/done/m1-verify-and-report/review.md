# Review – M1 Verify and report (JVM/Maven, no AI)

Scope: commits `36dc2a3..fd6822c` (`origin/main..HEAD`, 9 commits) against [`spec.md`](spec.md).
Method: read every source, test, build, and fixture file in scope; ran `./gradlew clean build --rerun-tasks --no-build-cache` and `:cli:integrationTest`; ran the installed start script on a fixture and on bad input; probed behavior with temporary changes and real Maven runs on copies of the fixtures under `.context/probe/` (all reverted, `git status` clean).

Outcome per finding: **Fixed** · **Not changed** (with reason) · **Needs decision** (with options).

Severity: **High** = breaks a requirement or the external contract · **Medium** = real gap, should fix before the next milestone · **Low** = cleanup / nit.

## Summary

- 36 of 36 acceptance criteria are met. AC13 is met only with a caveat (F2): the attribution layer runs only the still-failing tests, but the Maven adapter runs the whole unit-test suite when it reruns only integration tests.
- The main contract issue is F1: any exception other than `ToolError` ends the JVM with exit `1`, which the pipeline reads as "caused by the update".
- Usage errors (unknown option, extra argument, invalid `--format`) also exit `1` (F3). M0 documented that on purpose, so it needed a decision: they now exit `2`.
- Most parts are solid: attribution as a pure function over the port, the process runner, the base worktree, and parsers tested against real captured Maven output.

## Acceptance criteria

| ID | Result | Evidence |
|---|---|---|
| AC1 | Met | `./gradlew clean build --rerun-tasks --no-build-cache`: `BUILD SUCCESSFUL`, 70 tasks executed |
| AC2 | Met | `core/.../Diagnostic.kt:14-23`: all seven fields, `file`/`line`/`column` nullable (plus `code`, and the extra `test`, documented in concept 2.3) |
| AC3 | Met | `core/.../Verification.kt:7-12` |
| AC4 | Met | `Diagnostic.kt`: no status field; `test` names the test |
| AC5 | Met | `core/.../EcosystemAdapter.kt:6-21`: `detect`, `verify`, `runTests` only |
| AC6 | Met | `grep -c "project(" core/build.gradle.kts` → `0` |
| AC7 | Met | `grep -riE 'maven\|mvn\|ecosystems\|surefire\|javac' core/src/main` → no match |
| AC8 | Met | `AttributionTest`: "a compile error is caused by the update without any rerun" |
| AC9 | Met | `AttributionTest`: "a test that passes on the rerun is flaky…" |
| AC10 | Met | `AttributionTest`: "a test that fails on head and on base is pre-existing" |
| AC11 | Met | `AttributionTest`: "a test that fails on head and passes on base…" |
| AC12 | Met | `AttributionTest`: "without a base commit a still-failing test is unattributed" |
| AC13 | Met, with caveats | `AttributionTest`: "the base runs only the still-failing tests, once…". The Maven adapter still runs all unit tests when only integration tests are rerun (F2) |
| AC14 | Met | `:cli:integrationTest` passes; it asserts one worktree afterwards (`VerifyIntegrationTest.kt:46`); `GitBaseCommitTest` covers removal after a failure too |
| AC15 | Met | `settings.gradle.kts:16`, `cli/build.gradle.kts:8` |
| AC16 | Met | `MavenAdapterTest`: detects / doesn't detect |
| AC17 | Met | `MavenAdapterTest`: "uses the Maven wrapper…", "runs the wrapper as a process…" |
| AC18 | Met | `MavenAdapterTest`: "doesn't run the tests when the code doesn't compile" and "…fails without an error it understands" |
| AC19 | Met | `MavenAdapterTest`: `-B -fae` in both stages |
| AC20 | Met | `MavenAdapterTest`: "passes the Maven settings file to every run"; `VerifyCommandTest`: "--maven-settings is handed to the adapters" |
| AC21 | Met | `MavenConsoleTest` (two modules), `TestReportsTest` (two modules) |
| AC22 | Met | `MavenConsoleTest`: javac on `javac-errors.log` |
| AC23 | Met | `MavenConsoleTest`: kotlinc 2.x and 1.9 |
| AC24 | Met | `TestReportsTest`: surefire and failsafe with file and line |
| AC25 | Met | `MavenConsoleTest`: dependency resolution and missing parent; the real start script on a copy of `missing-dependency` prints it under "Build errors" and exits `1` |
| AC26 | Met | `ReportsTest`; real run: "Caused by the update (1)" first |
| AC27 | Met | `VerifyCommandTest` parses stdout as JSON; real run with `--format json`: one JSON line on stdout, nothing else |
| AC28 | Met | `VerifyCommandTest`: "pre-existing and flaky failures exit with 0" |
| AC29 | Met | `VerifyCommandTest`, integration test (`statusCode` 1), real run exits `1` |
| AC30 | Met | `VerifyCommandTest`, `LectorEndToEndTest`: unsupported repository and invalid `--base` exit `2`. Usage errors exit `1` (F3), outside this criterion |
| AC31 | Met | `./gradlew :cli:integrationTest --rerun-tasks`: 1 test, 0 failures |
| AC32 | Met | `cli/build.gradle.kts:43` excludes the tag; `build --dry-run` contains no `integrationTest` |
| AC33 | Met | `VerifyIntegrationTest.kt:47`: `git status --porcelain` empty |
| AC34 | Met | `-Dtest=` only in `MavenAdapter.runTests` (`MavenAdapter.kt:50`); `MavenAdapterTest`: "the verify command never skips, filters, or ignores tests" |
| AC35 | Met | `grep -rn DependencyChange core cli ecosystems --include='*.kt'` → no match |
| AC36 | Met | Version catalog: only `kotlinx-serialization-json` added; no Gradle, SARIF, Embedder, extension, or LLM dependency |

## Findings

### Tests and contract

**F1 · High · An unexpected exception exits with `1`, which means "caused by the update".** `VerifyCommand.kt:46-50` only maps `ToolError` to exit `2`, and `TestReports.kt:45-50` only catches `SAXException`. Probe: a repository with a `mvnw` that exits 0 and a report `target/surefire-reports/TEST-x.xml` with mode `000`. The installed `lector verify` prints a `FileNotFoundException` stack trace to stderr and exits `1`. Consequence: an I/O problem, or any bug in later code, makes the pipeline report a broken update, and from M3 on the fix loop would try to "fix" it. Expected: an unreadable report becomes the same build error as a malformed one (an `IOException` next to the `SAXException`), and `verify` maps any other exception to exit `2` with a one-line message on stderr.

Outcome: **Fixed.** `TestReports` turns an `IOException` into the same "Can't read test report" build error as a malformed report. `VerifyCommand.run` maps every exception to exit `2` (`ToolError` with its message, anything else as `Error: Unexpected failure: <exception>`), with one `@Suppress("TooGenericExceptionCaught")` and its reason. New tests in `TestReportsTest` and `VerifyCommandTest`. The probe now prints the build error and no stack trace.

**F2 · Medium · Rerunning only integration tests runs the whole unit-test suite, on head and on the base commit.** `MavenAdapter.kt:44-57` adds no surefire filter when there are no unit tests, so `clean verify -Dit.test=…` runs every unit test first. The test name "runs only integration tests when only integration tests are given" (`MavenAdapterTest.kt:171`) says otherwise. Probe with real Maven on a copy of `failing-tests`: `mvn -B -fae clean verify -Dit.test=com.example.app.GreetingIT -Dit.failIfNoSpecifiedTests=false` also ran `CalculatorTest` (3 tests, 2 failing). Consequence: it goes against spec section 3 and the "don't run the full suite on the base commit" rule (Q15), and costs two extra unit-suite runs. A unit test that fails in the integration test's module also stops failsafe there. The integration test then doesn't run and counts as passed: flaky on head, "caused by the update" on base. Expected: when no unit tests are selected, give surefire a filter that matches no class (`-Dtest=<sentinel>` with `-Dsurefire.failIfNoSpecifiedTests=false`). Probe with that filter: only `GreetingIT` ran.

Outcome: **Fixed.** `runTests` always passes a surefire filter: the unit test classes, or `lector.NoUnitTests`, which matches no class. `MavenAdapterTest` pins the command. The real-Maven probe with that command ran only `GreetingIT`. `verify-pipeline.md` explains why.

**F3 · Medium · Usage errors exit with `1`, the code for "caused by the update".** The installed start script exits `1` for `verify <repo> extra`, `verify <repo> --bogus`, `verify <repo> --format xml`, and `verify` without `<repo>`, with the error on stderr and nothing on stdout. Spec section 6 reserves `1` for "at least one diagnostic is caused by the update". M0 documented `1` for invalid usage (`docs/architecture/project-skeleton.md:9`), and `progress.md` lists it as open.

Outcome: **Fixed** (decision: map to `2`). `LectorCommand.execute()` replaces Clikt's `main`: it prints the error like `main` does and exits with `TOOL_ERROR` for a `UsageError`, and with the error's own status otherwise. `Main.kt` calls it. Tests run `execute` in-process through Clikt's `echoMessage` and `exitProcess` hooks: unknown option, extra argument, missing `<repo>`, invalid `--format` exit `2`; `--version` exits `0`; a problem caused by the update still exits `1`. A new end-to-end test covers `verify --format xml`. M0's contract in `project-skeleton.md` is updated.

**F4 · Low · The usage-error test only asserts "not 0".** `VerifyCommandTest.kt:152`: `assertTrue(result.statusCode != 0)`. The exit code is part of the contract, so the test must pin it. Expected: `assertEquals` on the actual code.

Outcome: **Fixed.** The test now runs through `execute` and pins `assertEquals(2, …)` (see F3).

**F5 · Low · The timeout test doesn't check that child processes are killed.** `Processes.kt:51` kills the descendants first, but `ProcessesTest` only times out a plain `sleep`. Maven forks surefire JVMs. A fork that survives keeps running in CI and holds files in the worktree. Expected: a test that times out `sh` with a background child and asserts that the child is gone.

Outcome: **Fixed.** New `ProcessesTest` case: `sh` starts a background `sleep`, and the test asserts that the child is gone after the timeout. With the descendant kill removed, the test failed. Then the line was restored.

### Quality gates

**F6 · Low · detekt lets `System.out.printf`, `format`, and `append` through.** Probe: a file in `ecosystems/jvm` calling all three; `:ecosystems:jvm:detektMain` passes. `config/detekt/detekt.yml:21-31` forbids only `print` and `println` on `PrintStream`. Expected: forbid `printf`, `format`, `append`, and `write` too.

Outcome: **Fixed.** `ForbiddenMethodCall` also forbids `PrintStream.printf`, `format`, `append`, and `write`. Re-probed in `core` main and `cli` test sources: all four calls fail `detektMain` and `detektTest`; the probes were reverted. `AGENTS.md` and `project-skeleton.md` list the calls.

### Leftovers

**F7 · Low · A fully qualified name with odd wrapping in a test.** `VerifyCommandTest.kt:36-39` writes `gmbh.vanella.lector.core.Verification()` across three lines instead of importing it.

Outcome: **Fixed.** `Verification` is imported.

## Found while fixing

Nothing new. The clean build and the integration test passed on the first run after the fixes, and no stray files appeared.

## What's good

- Attribution is one pure function over the port (`Attribution.kt`), tested with fakes for every category. It is conservative: a rerun or base run with a build problem says nothing about the tests, so they stay still failing or unattributed and never become flaky or pre-existing by accident.
- The process runner writes the output to a file, closes stdin, and kills the descendants on timeout. Every Maven and Git call goes through it.
- `--base` is resolved before the build, so a wrong ref fails in seconds. The base worktree lives in a temp directory, is removed in `finally`, and a lock failure is tested.
- A non-zero Maven exit without a parsed problem becomes a diagnostic with the end of the output, so a failure never looks green.
- The parsers are tested on output captured from real Maven runs, and the capture method is documented in `fixtures/README.md`. Kotlin 1.x, 2.x, and a compiler crash are covered.
- The XML parser rejects a DOCTYPE, which rules out external entities in reports.
- Gates were probed per file set in `progress.md`, and `integrationTest` is kept out of `build` with a comment saying why Kover would pull it in.
