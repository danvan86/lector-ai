# Review – M6 Benchmark

Scope: commits `cf2b453..fffe10a` (7 commits on `danvan86/m6-benchmark`) against [`spec.md`](spec.md).
Method: read every source, test, build, and workflow file in scope, and the corpus; ran `./gradlew clean build
--rerun-tasks --no-build-cache`; ran `:benchmark:run` (matrix, run, merge, report) on good and bad input, and one real
`run` of `dledger-snakeyaml-2` in recipe-only mode (real checkout, Maven, recipe index); fetched the base and head of all
22 cases from their repositories; checked the authors of the 16 historical PRs; probed detekt and Kover on `benchmark`
with a temporary violation (reverted, `git status` clean).

Outcome per finding: **Fixed** · **Not changed** (with reason) · **Needs decision** (with options).

Severity: **High** = breaks a requirement or the external contract · **Medium** = real gap, should fix before the next milestone · **Low** = cleanup / nit.

## Summary

- 17 of 19 acceptance criteria are met. AC19 is met with a caveat (F1). AC17 (first committed results) can't be verified
  yet: it needs the workflow on `main`, one run, and a human review.
- No High findings. The main gap is that the benchmark's fix loop gets no release notes, while `lector pr` passes the
  PR body's (F4; decided: leave them out and say so in the report).
- A run that fails with anything other than a `ToolError` leaves no result and shows up as "missing", like a timeout (F3).
- The harness test named for cut requests only ever sees zero of them (F1).

## Acceptance criteria

| ID | Result | Evidence |
|---|---|---|
| AC1 | Met | `./gradlew clean build --rerun-tasks --no-build-cache`: exit 0, 541 unit + 19 end-to-end tests; `build --dry-run` lists no `:benchmark:run` or `:benchmark:report`; tests use fakes only |
| AC2 | Met | `settings.gradle.kts:15`; `cli/build.gradle.kts` has no `benchmark` dependency; no `lector/benchmark` class in any jar under `cli/build/install/lector/lib` |
| AC3 | Met | `corpus.json`: 22 cases; `Corpus.parse` requires every field (`CorpusTest`); all 22 base and head commits fetch from their repositories |
| AC4 | Met | 16 PR origins (12 Dependabot, 4 Renovate), 6 `synthetic` |
| AC5 | Met | `HarnessTest` "a head commit without a failure…": invalid in every mode, no metrics; `StatsTest`: excluded from the numbers |
| AC6 | Met | `Harness.kt:139` passes no budgets, so `Budgets()` as in `PrSettings`; `HarnessTest` "default budgets" |
| AC7 | Met | `PlanTest`; `matrix` on the real corpus prints 7 jobs per case |
| AC8 | Met | `HarnessTest` "every run records…"; real run: `toolCommit` and `model` in `r.json` |
| AC9 | Met | `RunResult.kt:46`; `HarnessTest`; the real run's JSON has every field |
| AC10 | Met | `ReportTest` on the fixture; `StatsTest` "green rate per repetition" |
| AC11 | Met | `StatsTest`: 89% doesn't hold, 90% holds |
| AC12 | Met | `fixtures/results/report.md` has rejections per rule, attempts, runtime, review, tool commit, model, runner, date |
| AC13 | Met | `ReviewedRun` (`Results.kt:72`); `fixtures/results/review.json` |
| AC14 | Met | `.github/workflows/benchmark.yml`: `workflow_dispatch` only, `ubuntu-latest`, Ollama image as `services:`, matrix from `matrix` |
| AC15 | Met | `report` job: `merge`, `:benchmark:report`, `upload-artifact` of the date directory |
| AC16 | Met | `lector heal --help`: `(default: qwen2.5-coder:7b)`; `action/action.yml:23` |
| AC17 | Can't verify | No `benchmark/results/`: the workflow must be on `main` to run, then a human review |
| AC18 | Met | No `schedule` in `benchmark.yml`, no GPU label, no dashboard, no `lector` subcommand, no YAML library in the catalog |
| AC19 | Met, with caveats | `FixLoopTest` (oversized cluster), `OllamaClientTest`; `requestsWithDiagnosticsLeftOut` in `results.json`. The harness's count is only tested at 0 (F1) |

## Findings

### Tests

**F1 · Medium · The harness test for cut requests never sees one.** `HarnessTest.kt:133` is named "a rejection is
counted by guardrail rule, and a cut request is counted", but the benchmark's `ScriptedLlm` always fits
(`Fakes.kt:113`), and the test asserts `requestsWithDiagnosticsLeftOut == 0`. Hard-coding `0` in `LoopNumbers.of` would
pass every test. AC19 asks that the run's metrics count these requests. Expected: let the fake take a `fits` function,
and assert a count of 1 for a cluster that doesn't fit.

Outcome: **Fixed.** The benchmark's `ScriptedLlm` takes a `fits` function; a new harness test sends a cluster that doesn't fit and asserts 1 cut request (fails with a hard-coded 0).

**F2 · Low · Some assertions are weaker than they read.** `assertEquals(false, metrics.green)` and `assertEquals(true, …)`
in `HarnessTest` and `BenchmarkCommandTest`, and `assertTrue(realHarness(…) is Harness)` (`BenchmarkCommandTest.kt:166`),
which can't fail once it compiles. Expected: `assertFalse`/`assertTrue`; the real-harness test asserts what it built
(the Maven adapter detects a `pom.xml`).

Outcome: **Fixed.** `assertTrue`/`assertFalse` for booleans; the real-harness test just builds it, with a comment that it fails if the index or the adapter can't load.

### Design and behavior

**F3 · Medium · A run that fails with anything but a `ToolError` leaves no result.** `Harness.run` catches only
`ToolError` (`Harness.kt:69`). A bug in a parser or an `IllegalStateException` from a library ends the job with a stack
trace, no run file is uploaded, and `merge` records the run as `missing: no result`, the same as a job that ran out of
time. The report then can't tell a tool bug from a timeout, and the reason is only in one of 154 job logs. Expected:
catch `Exception` in `Harness.run` and record it as an `error` with its type and message.

Outcome: **Fixed.** `Harness.run` also catches `Exception` and records `error` with `<Type>: <message>`; tested with a checkout that throws `IllegalStateException`.

**F4 · Medium · The benchmark's fix loop gets no release notes; `lector pr` gets the PR body's.** `Harness.kt:139` builds
`FixContext(changes, null, recipeDiff)`. `PrPipeline` passes `update.releaseNotes` from the source adapter. The spec says
the benchmark measures the product as shipped (What not to do: "Change budgets or prompts"). 4 of the 16 PRs are
Renovate PRs, whose bodies `lector pr` would read today (up to 58 000 characters); 12 are Dependabot PRs, which the
product can't read before M8.

Outcome: **Decided: leave them out and say so.** The report's header says the fix requests carry no release notes and
that the fix-loop numbers are a lower bound for Renovate PRs; the spec (section 2) records it; the M8 spec gets a
benchmark bullet to add the notes once the Dependabot source reads them. Options were: leave out and say so; release
notes per historical case in the corpus; read the PR body at run time.

**F5 · Low · The jobs build with the runner's Maven, not the tool image's.** The `run` job uses the Maven of
`ubuntu-latest`; the tool image pins Maven 3.10.0 (`docker/lector/Dockerfile`). A project that builds differently on the
two would be measured in an environment no user has. Better: run the jobs in the tool image.

Outcome: **Not changed.** The tool image isn't published before the MSAL check, and building it per benchmark run adds a job and a large push. Revisit when releases publish it: run the `run` jobs in the tool image.

### Contract and gates

**F6 · Low · Usage errors exit `1`, invalid input exits `2`.** `benchmarkCommand().main(args)` uses Clikt's default for
usage errors (`merge --date 12.10.2026`, an unknown option: status 1); `BenchmarkStep` maps
`IllegalArgumentException` to 2. AGENTS.md reserves 1 for "caused by the update".

Outcome: **Not changed.** The benchmark only runs through `./gradlew :benchmark:run`, and Gradle turns every non-zero exit into its own `1`; the workflow only tells success from failure. Mapping usage errors to `2` would copy `LectorCommand.execute` for no observable effect. Revisit if the benchmark gets its own start script.

**F7 · Low · `merge` without any run result fails with a confusing message.** If the Ollama image job fails, every run is
skipped and the `report` job still runs `merge`: "The runs must share one tool commit and one model, not []". Expected:
"No run results under <dir>".

Outcome: **Fixed.** `merge` fails with `No run results under <dir>` (exit 2) before merging; tested.

**F8 · Low · The review file isn't checked for its sample size or duplicates.** `Report.reviewSection` accepts a
`review.json` with 3 runs, or the same run twice, and reports the rate over them. The spec asks for at least 10 green
fix-loop runs, or all of them if there are fewer. Expected: the report fails on a duplicate run or a sample smaller than
`min(10, green fix-loop runs)`.

Outcome: **Fixed.** The report rejects a run reviewed twice and a review smaller than `min(10, green fix-loop runs)`; the fixture review covers all 6 green runs, and a test checks that 10 of 12 is enough.

### Leftovers

**F9 · Low · Every `:benchmark:run` prints four JNA warnings.** `cli` passes `--enable-native-access=ALL-UNNAMED`
(`cli/build.gradle.kts:22`) because Clikt's terminal detection loads JNA; the benchmark's `JavaExec` tasks don't. Four
`WARNING:` lines on stderr per job. Expected: the same JVM argument on both tasks.

Outcome: **Fixed.** Both `JavaExec` tasks pass `--enable-native-access=ALL-UNNAMED`; `:benchmark:run` prints no warnings.

**F10 · Low · The architecture docs are behind.** `docs/architecture/fix-loop.md:159` and `:241` still say errors and code
are never trimmed and list review D3 as open; there is no architecture doc for the benchmark.

Outcome: **Not changed.** Architecture docs are the next step (architecture-docs), which documents the benchmark and fixes the drift in `fix-loop.md`.

## Found while fixing

Nothing new. The clean build (`./gradlew clean build --rerun-tasks --no-build-cache`) after the fixes: 547 unit tests
(64 in `benchmark`), 19 end-to-end tests, Kover 100% per module, ktlint and detekt clean, `git status` clean.

## What's good

- The benchmark calls the product's own code: `applyRecipes` and the recipe group's diff moved into `core` and are shared
  with `PrPipeline`, so the two can't drift.
- Every run re-checks that its head is broken, and `counted` drops a whole case on any invalid run.
- `merge` puts runs in plan order and turns a lost job into a `missing` row instead of losing the report; the `report`
  job runs `if: always()`.
- `firstThatFit` checks without grounding, so a fit check never starts a process per change.
- The verdict is rounded to the shown decimal before the threshold, so number and verdict never disagree; tested at 89
  and 90.
- Strict JSON decoding (`ignoreUnknownKeys = false`), and a test that drops every field of the fixtures in turn.
- The workflow passes `inputs.cases` through the environment, never into the script.
