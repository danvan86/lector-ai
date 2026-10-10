# Review – M0 Project skeleton

Scope: commits `3275050..1738eee` against [`spec.md`](spec.md).
Method: read every source and build file, ran `./gradlew build`, ran the installed `lector` start script, and added temporary changes to probe behavior (all reverted).

Outcome per finding: **Fixed** · **Not changed** (with reason) · **Needs decision** (with options).

Severity: **High** = breaks a requirement or the CLI contract · **Medium** = real gap, should fix before M1 · **Low** = cleanup / nit.

## Summary

- 26 of 27 acceptance criteria are met. AC18 (CI green) can't be verified until the branch is pushed. AC7 and AC16 are met as worded, with caveats (F8, F9, F10).
- The code is small and mostly simple. The main design issue is a side effect in the `LectorCommand` constructor that was added to satisfy coverage (F7).
- The tests cover the happy paths at both levels. The **error paths aren't tested**: invalid usage and a malformed version resource (F1, F2). Unit and end-to-end tests exist but run as one task and can't be run separately (F3).

## Acceptance criteria

| ID | Result | Evidence |
|---|---|---|
| AC1 | Met | `./gradlew build` exits with 0 |
| AC2 | Met | `settings.gradle.kts`: `rootProject.name = "lector-ai"`, `include("core", "cli")` |
| AC3 | Met | `core/build.gradle.kts` has no `project(` |
| AC4 | Met | `cli/build.gradle.kts:7` `implementation(project(":core"))` |
| AC5 | Met | Only `cli` has `*.kt` files, all in `package gmbh.vanella.lector.cli` |
| AC6 | Met | Both modules apply `id("lector.kotlin-conventions")` from `buildSrc` |
| AC7 | Met, with caveats | Module build files use only `libs.*`. But the JUnit version isn't in the catalog at all (F8), and the foojay plugin version is in `settings.gradle.kts` (F9) |
| AC8 | Met | `jvmToolchain(25)` in the convention plugin |
| AC9 | Met | foojay resolver in `settings.gradle.kts` |
| AC10 | Met | `cli/build/install/lector/bin/lector` exists after `installDist` |
| AC11 | Met | `lector --version` prints `0.1.0`, exits with 0, stderr is empty |
| AC12 | Met | No arguments prints `Usage: lector [<options>]` and `--version` |
| AC13 | Met | No `0.1.0` in any `*.kt` file |
| AC14 | Met | slf4j-api + slf4j-simple. An end-to-end test shows a debug log on stderr while stdout stays clean |
| AC15 | Met | `LectorCommandTest`, `LectorEndToEndTest` (JUnit 5 + `kotlin.test`) |
| AC16 | Met, with caveat | A violation in a module source fails `ktlintMainSourceSetCheck`. `buildSrc/**/*.kts` isn't linted (F10) |
| AC17 | Met | `.github/workflows/build.yml`: `pull_request` + `push` to `main`, `setup-java` with JDK 25 |
| AC18 | Can't verify | Branch not pushed, no CI run yet |
| AC19 | Met | `.github/dependabot.yml`: `gradle`, `github-actions` |
| AC20 | Met | `LICENSE` is the Apache License 2.0 text |
| AC21 | Met | `core` has no sources |
| AC22 | Met | Help shows no `Commands:` section (also covered by a unit test) |
| AC23 | Met | No `Dockerfile`, no `action/`, only `core` and `cli` |
| AC24 | Met | None of Shadow, GraalVM, Git-tag versioning, ArchUnit/Konsist, Logback, Renovate, detekt-formatting, or CodeQL |
| AC25 | Met | A `println` in `cli` fails `detektMain` with `ForbiddenMethodCall` |
| AC26 | Met | An untested function in `cli` fails `koverVerify` |
| AC27 | Met | No baseline files, no `ignoreFailures`, no coverage exclusions |

## Findings

### Tests

**F1 · High · Invalid usage isn't tested.** The spec's detekt rule calls exit codes "a contract", but no test covers the failure contract. The behavior is correct today: `lector --bogus` and `lector foo` exit with 1, print the error to stderr, and leave stdout empty. Nothing protects it from regressing. Missing at both levels:
- unit: `command.test("--bogus")` → `statusCode == 1`, error in `stderr`, `stdout` empty
- end-to-end: the same through the start script, which also proves the exit code makes it through the shell script

Outcome: **Fixed.** Unit and end-to-end tests for `--bogus` and an extra argument: exit code 1, error on stderr, stdout empty.

**F2 · Medium · A malformed version resource crashes with an unclear error.** `Version.kt:11` returns `Properties.getProperty("version")`, a platform type, as non-null `String`. Probe result: a resource without a `version` key throws `NullPointerException: getProperty(...) must not be null`. The missing-resource case is handled and tested; the missing-key case is neither. Expected: the same clear message style as the missing-resource case, plus a test.

Outcome: **Fixed.** `AppVersion.read` fails with `… has no version property`, tested with a fixture resource.

**F3 · Medium · Unit and end-to-end tests aren't separated.** Both live in the single `test` task, and `test` depends on `installDist`. Consequences:
- You can't run just the fast unit tests. Every `./gradlew test` builds the distribution.
- The difference between "unit" and "integration" exists only in the class name. Later milestones add slower integration tests (fixture repos, Ollama), and AGENTS.md already expects some of them to be opt-in.
A JUnit tag or a separate test suite (`integrationTest`, wired into `check`) would make the levels explicit.

Outcome: **Fixed.** End-to-end tests are tagged `e2e` and run in `:cli:endToEndTest`, part of `check`. `test` no longer builds the distribution.

**F4 · Low · Exit codes and streams aren't asserted on the help paths.** `no arguments prints the help` (unit and end-to-end) and `help lists no subcommands` only check text. Neither checks `statusCode == 0` or that help goes to stdout and not stderr. Today no arguments and `--help` both exit with 0.

Outcome: **Fixed.** The help tests check exit code 0, stdout, and an empty stderr.

**F5 · Low · The end-to-end helper can hang the build.** `LectorEndToEndTest.lector()` reads all of stdout before stderr and then calls `waitFor()` without a timeout. If the process fills the stderr pipe buffer (a stack trace, verbose logging) or never exits, the test, and with it CI, hangs instead of failing. Use `redirectError` to a file (or read both streams concurrently) and `waitFor(timeout)`.

Outcome: **Fixed.** Both streams go to files, and `waitFor` has a 30 s timeout.

**F6 · Low · Nit.** `assertEquals(false, result.output.contains("Commands:"))` reads better as `assertFalse(...)`.

Outcome: **Fixed.** `assertFalse`.

### KISS / SOLID / DRY

**F7 · Medium · The constructor has a side effect, added for coverage (SRP).** `LectorCommand.init` logs `"Starting lector {}"`. The command now logs "Starting" whenever an instance is created, including in every unit test, and that has nothing to do with the command's job of parsing arguments. The log belongs in `main`, the composition root. It was moved into the constructor only because `main` isn't measured by Kover. Better: keep the log in `main` and accept the one uncovered line, or extract a small `run(args)` function that `main` calls and the tests can exercise.

Outcome: **Fixed.** The log moved to `rootCommand`, the composition root in `Main.kt`. `LectorCommand` has no side effects.

**F8 · Medium · The JUnit version is implicit and outdated (DRY: the catalog as the single source).** No catalog entry pins JUnit. It comes in transitively through `kotlin-test-junit5`, which resolves to **JUnit 5.10.1**, and the comment in `libs.versions.toml:14` relies on that. Dependabot can't update a version that isn't declared. Add `junit-bom` to the catalog and import it as a platform in the convention plugin.

Outcome: **Fixed.** `junit-bom` 5.14.4 is in the catalog and imported as a platform by the convention plugin.

**F9 · Low · One version lives outside the catalog.** `settings.gradle.kts:3` pins foojay `1.0.0`. Gradle can't read the catalog in a settings `plugins {}` block, so this is unavoidable, but the spec doesn't mention the exception. Document it, for example in the spec or as a comment next to the line.

Outcome: **Fixed.** A comment in `settings.gradle.kts` explains why.

**F10 · Medium · The quality gates have holes.**
- `System.out.println` / `System.out.print` aren't forbidden. Probe: a `System.out.println` in `cli` passes detekt (only Kover failed, because the probe function was untested). The stdout rule in `config/detekt/detekt.yml` only covers `kotlin.io.print`/`println`.
- `buildSrc` isn't linted. ktlint runs on module sources, module build scripts, and the root scripts, but not on `buildSrc/**/*.kts`, the most important build script. The spec says ktlint covers `*.kts`.

Outcome: **Fixed.** detekt forbids `PrintStream.print`/`println`. The root ktlint check covers `buildSrc/**/*.kts` (see F16 for why it didn't run before).

**F11 · Low · Duplicate detekt runs (one tool per concern, run once).** `check` runs the plain `detekt` task as well as `detektMain` and `detektTest`. Every finding is reported twice (seen in the probes: the same `FunctionOnlyReturningConstant` from `:cli:detekt` and `:cli:detektMain`). The spec asks for the type-resolving tasks. The plain task adds runtime and noise without adding coverage.

Outcome: **Fixed.** The plain `detekt` task is disabled.

**F12 · Low · The install path is duplicated (DRY).** `cli/build.gradle.kts:40-43` hard-codes `install/lector/bin/lector`, repeating both the `installDist` output directory and `applicationName`. If either changes, the end-to-end tests break with "file not found" instead of following along. Derive the path from `tasks.installDist` (its `destinationDir`) and `application.applicationName`.

Outcome: **Fixed.** `lector.bin` is derived from `installDist.destinationDir` and `applicationName`.

**F13 · Low · Version lookup is coupled to `LectorCommand` and has a test-only parameter.** `appVersion()` resolves its resource through `LectorCommand::class.java`, so `Version.kt` depends on the command class only for the classloader and package. The resource path also spells out the package twice: in the build script (`gmbh/vanella/lector/cli/version.properties`) and implicitly through the relative lookup. The `resource` parameter exists only for tests. Acceptable for M0; worth simplifying if the version logic grows.

Outcome: **Fixed.** An `AppVersion` object resolves the resource through its own class.

**F14 · Low · KISS: Clikt's terminal stack pulls in native libraries.** The `clikt` artifact brings Mordant with JNA and FFM backends (8 extra JARs in `lib/`) and needs `--enable-native-access=ALL-UNNAMED` for the start script and the test JVM. A CI tool doesn't need colors or terminal detection. `clikt-core` is still Clikt (spec item 4) and avoids the native libraries, the JVM flag, and the JDK 25 warning. Worth considering before the container image in M5.

Outcome: **Not changed.** Tried `clikt-core`: its default context exits with 0 and prints errors to stdout, which the new end-to-end tests caught. Fixing that means hand-wiring stdout/stderr and the exit code in `main`. Revisit with the container image in M5.

**F15 · Low · Leftover file.** The empty `.gitkeep` at the repo root (from the initial commit) is no longer needed.

Outcome: **Fixed.** Removed.

## Found while fixing

**F16 · Medium · The root build scripts weren't linted by `./gradlew build`.** The root project had no `check` task, so its `ktlintKotlinScriptCheck` never ran in `build`. The review (AC16, F10) assumed it did.

Outcome: **Fixed.** The root project applies `base`. Probes with a violation in `settings.gradle.kts` and in `buildSrc/build.gradle.kts` both fail the build.

**F17 · Medium · The Gradle daemon ran out of memory.** With the default 512 MB heap, detekt with type resolution and Kotlin compilation in one daemon caused an `OutOfMemoryError`, a heap dump in the repo root, and a detekt failure that passed on rerun.

Outcome: **Fixed.** `gradle.properties` sets `org.gradle.jvmargs=-Xmx2g`.

## What's good

- `core` has no project dependencies, so the dependency direction holds by construction.
- One convention plugin, one catalog, and the version only in `gradle.properties`, generated into a resource. No duplicated build logic between modules.
- The end-to-end tests run the real start script with `JAVA_HOME` pinned, so packaging, start script, resources, and the stdout/stderr split are all exercised.
- Every gate was shown to fail on a real violation (ktlint, detekt with type resolution, Kover).
- No baselines, suppressions, or exclusions.
