# Architecture – Verify pipeline

Milestone: [M1](../features.md#m1--verify-and-report-jvmmaven-no-ai-) · Spec: [`m1-verify-and-report/spec.md`](../../specs/done/m1-verify-and-report/spec.md) · Progress: [`progress.md`](../../specs/done/m1-verify-and-report/progress.md)

> **Summary for agents**
>
> - `lector verify <repo>` builds and tests a repository through an `EcosystemAdapter`, then `attribute()` in `core` sorts every `Diagnostic` into the four lists of a `Verification`: caused by the update, pre-existing, flaky, unattributed.
> - The contract: the report goes to stdout (text, or one line of JSON with `--format json`), logs go to stderr. Exit `0` when nothing is caused by the update, `1` when something is, `2` on a tool error.
> - `core` knows no build tool: it holds the data model, the port, attribution, the process runner, and the Git worktree for the base commit. Everything specific to Maven or Gradle is in `ecosystems:jvm`. Only `cli` (`adapters()` in `RootCommand.kt`) knows `GradleAdapter` and `MavenAdapter`.
> - A new ecosystem is a new `ecosystems:*` module that implements `EcosystemAdapter` (`detect`, `verify`, `runTests`, both with a `timeout` since M5, plus `ecosystem` and `versionScheme` and the optional recipe capabilities since M2, see [Recipe migration](recipe-migration.md), `ground`, `clusterKey`, `isBuildFile` since M3, see [Fix loop](fix-loop.md), and `languageSupport`, `isEditable`, `testDisablingConfig`, `testsRun` since M4, see [Guardrails](guardrails.md)), registered in `adapters()` in `RootCommand.kt`. A second build tool of an existing ecosystem is a second adapter in its module, as Gradle is since M7 ([Gradle adapter](gradle-adapter.md)). Its parsers are tested on output captured from real runs.
> - Easiest rule to break: test failures carry `Diagnostic.test`, everything else has `test == null`, and attribution relies on it. A run with any `test == null` diagnostic says nothing about the tests. Never skip or filter tests in `verify`; `-Dtest` belongs in `runTests` only.

## Overview

M1 answers one question for a dependency-update PR: did the update break the build or the tests, and where? Compile and build errors always count. A failing test only counts if it fails again on a rerun and passes on the base commit, so flaky and pre-existing failures never fail the job.

```mermaid
flowchart LR
    cli["VerifyCommand<br/>(cli)"] --> attr["attribute()<br/>(core)"]
    attr --> port["EcosystemAdapter<br/>(core)"]
    maven["MavenAdapter<br/>(ecosystems:jvm)"] -. implements .-> port
    gradle["GradleAdapter<br/>(ecosystems:jvm, M7)"] -. implements .-> port
    attr --> base["GitBaseCommit<br/>(core)"]
    maven --> mvn["mvnw / mvn<br/>(process)"]
    gradle --> gradlew["gradlew<br/>(process)"]
    base --> git["git worktree<br/>(process)"]
    attr --> report["TextReport / JsonReport<br/>(cli)"]
```

## Modules and files

| Module | File | Role |
|---|---|---|
| `core` | `Diagnostic.kt` | `Diagnostic(file, line, column, code, message, severity, tool, test)`. `file` is relative to the repository root with `/`. `file`, `line`, `column`, `code`, and `test` are nullable. `Severity` has only `ERROR`: warnings aren't collected. |
| | `Verification.kt` | The four lists. No classification lives on `Diagnostic`. |
| | `EcosystemAdapter.kt` | The port: `detect(repo)`, `verify(repo, timeout)`, `runTests(repo, tests: List<Diagnostic>, timeout)`. A run longer than `timeout` is stopped with a `ProcessTimedOut`. M2 added the members for recipes, M3 those for the fix loop, M4 those for the guardrails. |
| | `Attribution.kt` | `attribute(adapter, repo, base, timeLeft)` and the `BaseCommit` interface. `timeLeft` gives each build and test run its timeout when it starts (default: no limit; the fix loop passes its time budget). |
| | `Processes.kt` | `runProcess(command, workDir, timeout, environment)`, `ToolError`, and `ProcessTimedOut` (a `ToolError`). The process gets a scrubbed environment, see [Processes](#processes). |
| | `GitBaseCommit.kt` | `GitBaseCommit.resolve(repo, ref)` and `checkout { }` in a temporary worktree. |
| `ecosystems:jvm` | `MavenAdapter.kt` | Detects `pom.xml` at the root, builds the Maven command lines, runs Maven, collects diagnostics. |
| | `JdkSelection.kt` | Picks the `JAVA_HOME` for a project from the installed JDKs (M5). |
| | `MavenConsole.kt` | Console output → javac and kotlinc errors, failed goals, unreadable POMs. |
| | `TestReports.kt` | JUnit XML reports → test failures, parsed with the shared `xmlDocumentBuilder()` (`XmlDocumentBuilder.kt`). `MAVEN_REPORTS` reads surefire and failsafe, `GRADLE_REPORTS` (M7) `build/test-results/<task>/`. |
| | `GradleAdapter.kt` and the other `Gradle*.kt` | The Gradle adapter (M7), see [Gradle adapter](gradle-adapter.md). |
| `cli` | `VerifyCommand.kt` | The `verify` subcommand: attribution and report. |
| | `RepositoryCommand.kt` | Shared with `migrate` and `heal`: `<repo>`, `--base`, `--format`, `--maven-settings`, `--gradle-init` (M7), validation, adapter detection, exit code. `ensure()` and `toolError()` are also used by `pr`, and so is `List<EcosystemAdapter>.use`, which closes the `AutoCloseable` adapters (the Gradle daemons) after every run. |
| | `Reports.kt` | `TextReport` and `JsonReport`. |
| | `RootCommand.kt` | Wires `VerifyCommand` (and the other subcommands) with `adapters()`: `GradleAdapter`, then `MavenAdapter`, both with the JDKs from `installedJdks()`, every `LECTOR_JDK_<release>` environment variable. |

```mermaid
flowchart TB
    cli["cli<br/>(composition root)"] --> jvm["ecosystems:jvm"]
    cli --> core
    jvm --> core
```

`core` depends on no project. `ecosystems:jvm` depends on `core`. External dependencies: SLF4J in `core` and `ecosystems:jvm`, `maven-artifact` in `ecosystems:jvm` (M2), `kotlinx-serialization-json` in `cli` and, for the Gradle model, in `ecosystems:jvm` (M7) (versions in `gradle/libs.versions.toml`).

## Attribution

`attribute()` in `Attribution.kt` is language-neutral. The adapter only runs builds and tests.

```mermaid
flowchart TB
    verify["adapter.verify(repo)"] --> split{"test == null?"}
    split -- yes --> caused["causedByUpdate"]
    split -- no --> rerun["adapter.runTests(repo, tests)"]
    rerun -- passes --> flaky["flaky"]
    rerun -- fails again --> hasBase{"--base given?"}
    hasBase -- no --> unattributed["unattributed"]
    hasBase -- yes --> base["base.checkout { adapter.runTests(it, stillFailing) }"]
    base -- fails there too --> pre["preExisting"]
    base -- passes there --> caused
    base -- build problem --> unattributed
```

| Rule | Where |
|---|---|
| Diagnostics without `test` (compile errors, build errors) are caused by the update, listed before test failures. | `attribute()` |
| Tests are matched by `test` name, not by message. | `attribute()` |
| A rerun with a build problem counts every test as still failing (never flaky). | `failedTests()` |
| A base run with a build problem leaves the still-failing tests unattributed. | `failedTests()` |
| The base is checked out only if tests still fail, and runs only those tests. | `attribute()` |
| The base worktree lives in a temporary directory and is removed in `finally`, also when the build throws. | `GitBaseCommit.checkout()` |

## Maven adapter

`MavenAdapter` runs `<repo>/mvnw` if it exists, otherwise `mvn` from `PATH`, in the repository's real path (`toRealPath()`, so macOS `/var` → `/private/var` doesn't break relative paths). Every run gets `-B -fae`, plus `--settings <absolute path>` if `--maven-settings` is given.

**JDK selection** (`JdkSelection`, M5): with installed JDKs (`MavenAdapter(jdks = …)`, release → `JAVA_HOME`), each run gets `JAVA_HOME` of the smallest JDK that is at least the release the root `pom.xml` declares in `maven.compiler.release`, else `java.version` (a `${…}` reference to another property is resolved, `1.8` is `8`), else 21. A project that needs a newer JDK than any installed is a `ToolError`. Without installed JDKs (outside the tool image), the environment's `JAVA_HOME` applies. The tool image sets `LECTOR_JDK_17`, `LECTOR_JDK_21`, and `LECTOR_JDK_25`.

| Call | Goals and properties |
|---|---|
| `verify`, stage 1 | `clean test-compile` |
| `verify`, stage 2, only if stage 1 has no diagnostics | `verify` |
| `runTests` with only surefire tests | `clean test -Dtest=<classes> -Dsurefire.failIfNoSpecifiedTests=false` |
| `runTests` with failsafe tests | `clean verify -Dtest=<classes> -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=<classes> -Dit.failIfNoSpecifiedTests=false`. Without surefire tests, `-Dtest=lector.NoUnitTests`, a filter that matches no class |

`runTests` reruns whole test classes (fully qualified), because surefire can't select every method by name (parameterized tests). Surefire always gets a filter: `verify` runs surefire before failsafe, and without one it would run every unit test, on the base commit too, and a failing unit test would stop failsafe in its module. After every run, the adapter parses the console output and the test reports. A run that exits non-zero without any parsed diagnostic becomes one `maven` diagnostic with the last 20 lines of output, so a failure never looks green. Each Maven run is stopped after the `timeout` the caller gives, and never runs longer than an hour (`MAVEN_TIMEOUT`); stage 2 of `verify` gets what stage 1 left. Recipe runs and `effective-pom` get the hour.

### Parsers

| Source | Recognized | Result |
|---|---|---|
| Console, `[ERROR]` lines | `/path/File.java:[l,c] msg` (javac, Kotlin 1.x summary), `/path/File.kt: (l, c) msg` (Kotlin 1.x), `file:///path/File.kt:l:c msg` (Kotlin 2.x) | `javac` or `kotlinc` (by file extension), indented follow-up lines appended to the message, duplicates removed |
| Console | `Failed to execute goal …` plus its `[ERROR]` lines up to `-> [Help`, an empty line, or the next failure | `maven`, no location. Skipped when it only summarizes compile errors or says `There are test failures.` |
| Console | `The project … has N errors` and its problems (unresolvable parent POM) | `maven`, no location, one per problem |
| `<module>/target/surefire-reports/TEST-*.xml`, `failsafe-reports` | `<testcase>` with `<failure>` or `<error>`; `<skipped>`, `<flakyFailure>` aren't failures | `surefire` / `failsafe`, `code` = exception type, `test` = `class#method`, file and line from the first stack frame in the test class, found under `<module>/src/<set>/<lang>/<package>/` |
| An unreadable report (malformed, with a DOCTYPE, or can't be opened) | — | `maven` build error, so no failure gets lost |

Only `[ERROR]` lines count; warnings are ignored. Paths outside the repository stay absolute. Message text comes from the tools in the runner's locale.

### Processes

`runProcess()` starts every process (Maven, `git`, `javap`) without the tool's environment: builds and tests run code from the updated dependency, so they must not see the push token or the action's inputs. A process gets only the allowlisted variables (`PATH`, `HOME`, `USER`, `TMPDIR`, `TZ`, `LANG`, `LANGUAGE`, `JAVA_HOME`, `CI`, the proxy variables in both cases, and anything starting with `LC_` or `MAVEN_`), plus the `environment` the caller adds: the selected `JAVA_HOME` for Maven and Gradle, the tool's `GRADLE_*` and `ORG_GRADLE_PROJECT_*` variables for Gradle (filtered in `GradleAdapter`, not in `core`), the token for `GitHubHost`'s own `git fetch` and `git push` ([PR pipeline](pr-pipeline.md)). Stdin is closed, stdout and stderr go to a temporary file, and a process past its timeout is killed with its children and becomes a `ProcessTimedOut`.

## CLI contract

`lector verify <repo> [--base <git-ref>] [--format text|json] [--maven-settings <file>] [--gradle-init <file>]`

| Situation | stdout | stderr | Exit |
|---|---|---|---|
| Nothing caused by the update (also with pre-existing, flaky, or unattributed failures) | Report | Logs | `0` |
| At least one diagnostic caused by the update | Report | Logs | `1` |
| `<repo>` is not a directory, `--maven-settings` or `--gradle-init` is not a file | empty | `Error: …` | `2` |
| No adapter detects the repository | empty | `Error: No supported ecosystem in <repo>. Supported: Maven (pom.xml at the root) and Gradle (settings.gradle(.kts) or build.gradle(.kts) at the root).` | `2` |
| A Gradle build the adapter can't build (`pom.xml` next to it, no wrapper, Gradle 7, Android, a missing JDK) | empty | `Error: …`, see [Gradle adapter](gradle-adapter.md#cli-and-action-contract) | `2` |
| `--base` is not a commit (checked before the build) | empty | `Error: Base ref <ref> is not a commit in <repo>…` | `2` |
| Maven, Gradle, or Git can't start or times out | empty | `Error: Cannot run …` / `… did not finish within …` | `2` |
| Any other exception or error while verifying (an `Error`, like a native library that doesn't load, too) | empty | `Error: Unexpected failure: <exception>` | `2` |
| Invalid usage (unknown option, extra argument, missing `<repo>`, `--format xml`) | empty | Usage and `Error: …` | `2` |

Logs are SLF4J on stderr; every process logs `Running <command> in <dir>` at info, and its output at debug.

**Text report:** four sections in a fixed order, `Caused by the update (n)`, `Pre-existing (n)`, `Flaky (n)`, `Unattributed (n)`, separated by an empty line; `none` for an empty section. In the first section, diagnostics without file and test come first under `Build errors:`, the rest under `In the code:`. An item is `- [tool] <test> at <file:line:column>: <message>`, with further message lines indented.

**JSON report:** one line, `{"causedByUpdate":[…],"preExisting":[…],"flaky":[…],"unattributed":[…]}`. Each diagnostic has all eight fields, `null` included; `severity` is lowercase (`"error"`).

```mermaid
sequenceDiagram
    participant P as Pipeline
    participant V as VerifyCommand
    participant A as attribute()
    participant M as MavenAdapter / GradleAdapter
    participant G as GitBaseCommit
    P->>V: lector verify repo --base sha
    V->>M: detect(repo)
    V->>G: resolve(repo, sha)
    V->>A: attribute(adapter, repo, base)
    A->>M: verify(repo)
    A->>M: runTests(repo, failing tests)
    A->>G: checkout { runTests(worktree, still failing) }
    A-->>V: Verification
    V-->>P: report on stdout, exit 0 / 1
```

## Tests

| Level | Task | What runs | Examples |
|---|---|---|---|
| Unit | `test` | Attribution with a fake adapter and a fake base; parsers on captured output; adapter command lines, `JAVA_HOME`, and timeouts with a fake process runner; JDK selection on POMs in temporary directories; the command through Clikt's `test()` and `executeInProcess()` | `AttributionTest`, `MavenConsoleTest`, `TestReportsTest`, `MavenAdapterTest`, `JdkSelectionTest`, `VerifyCommandTest`, `ReportsTest` |
| Unit, real processes | `test` | `runProcess` with `sh` (timeouts, and `env` started with a token, `GITHUB_TOKEN`, and `INPUT_*` sees none of them), `GitBaseCommit` on a temporary Git repository, `MavenAdapter` with a fake `mvnw` script | `ProcessesTest`, `GitBaseCommitTest` |
| End-to-end | `endToEndTest` (in `check`) | The start script: `verify` exits `2` for invalid usage, for an unsupported repository, and for an invalid `--base` | `LectorEndToEndTest` |
| Integration | `:cli:integrationTest` (opt-in, not in `check`) | Real Maven on a base/head repository: the head bumps commons-lang3 3.12.0 → 3.18.0. One test breaks, one fails on both commits. Checks the lists, exit `1`, one worktree left, clean `git status`. Needs `mvn` and network. The same with real Gradle since M7 ([Gradle adapter](gradle-adapter.md#tests)). | `VerifyIntegrationTest`, `GradleIntegrationTest` |

Fixtures: `ecosystems/jvm/src/test/resources/fixtures/` (Maven projects that break in one way each, plus captured reports in `failing-tests/*/target/`), `ecosystems/jvm/src/test/resources/maven-output/` (captured console logs; how they were made is in `fixtures/README.md`), and `cli/src/test/resources/fixtures/dependency-update/` (`base/` and the `head/` overlay). Tests tagged `integration` are excluded from `test`; Kover's instrumentation is disabled for `integrationTest`, otherwise Kover would make `check` depend on it.

## Decisions and known limits

| Decision / limit | Reason | Revisit |
|---|---|---|
| `Diagnostic.test` field | Attribution reruns and matches tests by name. It names the test; it doesn't classify. | — |
| `runTests` takes diagnostics, reruns whole classes | Maven must know surefire from failsafe; surefire can't select parameterized methods by name. Gradle reruns each class in the `Test` task its diagnostic's `tool` names (M7). | — |
| `clean` before compile and reruns | Removes stale test reports from earlier runs. Costs a recompile per rerun. | M3, if the fix loop needs faster cycles |
| Source files found only in the standard layout | `src/<set>/<lang>/<package>/<File>`. Otherwise a test failure has no location. | When a fixture needs it |
| Only errors, no warnings | An old warning would otherwise fail the job. | With baseline comparison of warnings |
| Messages in the runner's locale | Maven, javac, and kotlinc print localized text. The parsers only use locale-independent markers. The tool image sets `LANG=C.UTF-8`, which the JDK reads as English (`user.language=en`). | — |
| Unix paths only | The parsers expect absolute `/…` paths. CI runs Linux. | If Windows runners are needed |
