# Review – M11 Fix-loop yield

Scope: commits `f59043f..f8b3df5` (on `origin/main` at `151edb2`; the milestone base `2f543e1` is an ancestor) against [`spec.md`](spec.md).
Method: read every production and build change of the range and the non-frozen tests, checked `tests.lock` and the
harness diff, ran the full build from a clean state without caches, ran `:cli:integrationTest` for T-C2 with real Maven,
ran the installed `lector` on bad input and on two multi-module Maven projects built for the probe (under `.context/`,
outside the repository), and probed detekt and Kover with a temporary violation (reverted, `git status` clean).

Outcome per finding: **Fixed** · **Not changed** (with reason) · **Needs decision** (with options).

Severity: **High** = breaks a requirement or the external contract · **Medium** = real gap, should fix before the next milestone · **Low** = cleanup / nit.
Category: `domain` · `robustness` · `gate` · `test` · `design` · `docs`. Earliest catch: `spec` · `test design` · `implementation` · `review`.

## Summary

- 27 of 32 acceptance criteria are met, AC9 with a caveat (F1). AC26–AC30 can't be verified: they need the three
  benchmark runs on GitHub-hosted runners and the user's `review.json`.
- The main issue is in the spec's rule, not the code: in a Maven multi-module build, a pre-existing build error in one
  module makes Maven skip the modules that depend on it, so their tests never run, yet the head "ran tests" and the
  update ends green with exit `0` (F1).
- The main test gap: the re-ask isn't bounded by the time budget, though `tests.md` says an implementation test covers
  it (F2).
- Findings by category: domain 1 · robustness 1 · design 1 · docs 3. By earliest catch: spec 1 · implementation 4 · review 1.

## Acceptance criteria

| ID | Result | Evidence |
|---|---|---|
| AC1–AC7 | Met | `FixLoopProgressTest` T-A1–T-A7 green in `./gradlew clean build --rerun-tasks --no-build-cache` |
| AC8 | Met | `BuildErrorAttributionTest` T-B1 green |
| AC9 | Met, with caveats | T-B2, T-B3, T-B10 green. F1: tests that ran in other modules count as "the head ran tests" while Maven skipped the modules behind the build error |
| AC10 | Met | T-B4, T-B5; `PrPipelineTest` and `HealCommandTest` "… once per run"; real run: one `git worktree add` and one base `verify` in the probe's log |
| AC11 | Met | T-B6 green; `Attribution.kt` `pathless` replaces the given, absolute, and real path |
| AC12 | Met | T-B7, T-B8, T-B9, T-B11 green; `ProcessTimedOut` is a `ToolError` (`Processes.kt:21`), so `RunBase` catches it |
| AC13 | Met | T-C1, T-C3 green; `./gradlew :cli:integrationTest --tests '*SigningIntegrationTest*'`: 1 test, 0 failures; every probe run logs `mvn -B -fae -Dgpg.skip=true …` |
| AC14–AC17 | Met | `FixLoopRepeatTest` T-D1–T-D7 green |
| AC18–AC20 | Met | `FixLoopFeedbackTest` T-E1–T-E5 green |
| AC21 | Met | T-F1 green |
| AC22, AC24 | Met | `FixLoopSuggestionTest` T-G1–T-G4, `PrCommentSuggestionTest`, `FixLoopReportSuggestionTest` green |
| AC23 | Met | T-H1, T-H3, T-H4 green; `PrComment.kt` `fence` is one backtick longer than the longest run |
| AC25 | Met | `.github/workflows/benchmark.yml:14-17` input `model`, default `qwen2.5-coder:7b`; `:23` `MODEL: ${{ inputs.model }}`, used for the image (`:58`, `:68`) and the runs (`:122`) |
| AC26–AC30 | Can't verify | No benchmark results under `benchmark/results/` yet; the runs and the review are open (progress.md, Open) |
| AC31 | Met | `git diff --exit-code 2f543e1 -- ecosystems/typescript 'ecosystems/jvm/src/main/kotlin/**/Gradle*' 'ecosystems/jvm/src/main/resources/**/*.gradle'` exits 0; `Budgets` (`FixLoop.kt:24`) and `DEFAULT_MODEL` unchanged |
| AC32 | Met | `./gradlew clean build --rerun-tasks --no-build-cache --no-configuration-cache`: BUILD SUCCESSFUL, 232 tasks; 24 end-to-end tests |

`tests.lock`: all 12 hashes match. Harness: `BuildErrorFixture.kt` unchanged since the freeze (`788c266`),
`FixLoopFixture.kt` unchanged since `2f543e1`.

## Findings

### Domain

**F1 · High · domain · spec · A test the update broke in a Maven module that is skipped behind a pre-existing build error ends green with exit `0`.**
Probe: a two-module project, `a` with an enforcer rule bound to `verify` that fails on base and head, `b` depending on
`a` with a test that the head breaks. `lector verify --base HEAD~1` exits `0`: "Caused by the update (0)", the enforcer
failure as pre-existing. Maven's `-fae` skips `b` after `a` fails, so `BTest` never runs, but `a`'s test ran, so
`testsRun` is 1 and R1's "the head ran tests" holds (`Attribution.kt:293`). Before M11 the same run exited `1`. A
compile error in `b` is still found, because the adapter's `test-compile` step compiles every module first; test
failures in skipped modules are not. Consequence: in `pr`, a broken update is reported green and the bot's PR looks
safe to merge (the contract: exit `1` means the update caused a problem). It needs a pre-existing build error in a module
others depend on: a linter or enforcer rule that already fails on the base. The spec's R1 reasoning ("a build that stops
before the tests") covers the whole build, not part of a reactor. Gradle runs dependent tests regardless of another
project's lint task, so it is Maven's.

Outcome: **Needs decision.** Options: A) the Maven adapter reports the tests run as unknown when its verify skipped a
module (reactor summary `SKIPPED`), so the spec's existing "the adapter can't tell" clause keeps the build error caused
by the update; B) a new port method "did the verify build every module", for every adapter; C) keep it and document
the limit. Recommendation: A.

Decision (user, 2026-10-25): **A.**

Outcome: **Fixed.** The Maven adapter remembers per checkout whether its last `verify` skipped a module (reactor summary
line `… SKIPPED`, real Maven 3.9 wording) and then reports the tests run as unknown; the probe now exits `1` with the
enforcer failure caused by the update, and the base isn't verified. Test: `MavenAdapterTest` "the tests run are unknown
after a verify that skipped a module, until a verify skips none" (red before). Spec section 2, concept 5, setup guide,
and features.md name the case (`5d42a14`).

### Robustness

**F2 · Low · robustness · implementation · The re-ask of a repeated answer runs even when the time budget is used up.**
`FixLoop.kt:804` asks the model again without checking `timeLeft`; the loop checks the time only between attempts.
Builds in an attempt get `timeLeft` as their timeout, a model call doesn't. `tests.md` ("Edge cases", Time) says the
time budget during a re-ask is covered by implementation tests; none exists (`FixLoopReaskTest`, `FixLoopRepeatTest`).
Consequence: an attempt whose first answer arrives after the budget can spend another full model call (minutes on a
CPU runner) only to be stopped afterwards. Expected: no re-ask when no time is left; the attempt is rejected without a
build, its reason says so, and the loop stops with `TIME_BUDGET`. A test with a fake clock that runs out during the
first answer.

Outcome: **Fixed.** No re-ask once the budget is used up: the attempt is rejected without a build ("the first answer
repeated attempt N, and no time was left to ask again") and the loop stops with `TIME_BUDGET`. Test: `FixLoopReaskTest`
"a repeat that arrives after the time budget is not asked again, and the loop stops on time" (red before). The repeat
handling moved into `askAvoidingRepeats`, `ask` into it as a local function: `attempt` had reached detekt's complexity
limit and `Session` its function limit (`ae63651`).

### KISS / SOLID / DRY

**F3 · Low · design · implementation · `attribute` wraps a plain `BaseCommit` in a fresh `RunBase` per call, which silently gives up "once per run".**
`Attribution.kt:296` `(base as? RunBase ?: RunBase(base))`. Every production caller passes a `RunBase` today
(`RepositoryCommand.kt:98`, `PrPipeline.kt:125`, `Harness.kt:97`), but the parameter type is `BaseCommit`, so a new
caller that forgets the wrapper verifies the base on every attribution without a failing test. Better: `attribute`,
`FixLoop`, and the pipelines take `RunBase?`.

Outcome: **Not changed.** `FixLoopFixture` (frozen by M10) and six other test fakes pass a plain `BaseCommit` to
`FixLoop` and `attribute`; typing it `RunBase` changes frozen harness code for a caller that doesn't exist. Revisit when
a new entry point is added.

### Leftovers

**F4 · Low · docs · implementation · `--base`'s help still says only failing tests run on the base.**
`RepositoryCommand.kt:53`: "Failing tests are run there too, to tell new failures from old ones." Since M11 the base
also tells pre-existing build errors. Expected: name both.

Outcome: **Fixed.** "Failing tests and build errors are checked there too, …" (`6c62ccb`).

**F5 · Low · docs · review · `tests.md` names a freeze commit that isn't on the branch.**
"Frozen in commit `996527f`": after the rebase onto `origin/main` the freeze is `8b7d14a` (tests) and `788c266`
(`tests.lock`); `996527f` is no ancestor of `HEAD`. Expected: the commit on the branch.

Outcome: **Fixed.** `tests.md` names `8b7d14a`, and `996527f` as the pre-rebase hash (`6c62ccb`).

**F6 · Low · docs · implementation · The benchmark workflow's header comment runs past the file's 120-column wrap.**
`.github/workflows/benchmark.yml:2` is 199 characters; the rest of the file wraps at 120. Expected: rewrap.

Outcome: **Fixed.** Rewrapped (`6c62ccb`).

## Found while fixing

Nothing new. The fix round's only stumble was its own: a `+=` on a `MutableSet<Path>` doesn't compile, because `Path`
is an `Iterable<Path>` and the operator is ambiguous; `add`/`remove` instead.

## What's good

- `RunBase` makes "once per run" an object the run creates, not global state, and remembers a failure as well as a
  result; T-B8 and T-B11 prove a broken base is tried once.
- Changed lines are tracked as character spans through every later edit of the same answer, so a later edit above an
  earlier one moves its lines instead of leaving stale numbers (`EditTest`).
- Failed-edit reasons use the committed file's line numbers, the ones the model saw, and the near match is never
  applied (rule 4 kept).
- A guessed path is a failed edit before the scope guardrail, so `rejectionsPerRule` counts only real guardrail breaks.
- The suggestion's diff is taken before the reset and only when it is the new best, so a run diffs at most once per
  better candidate; a commit clears it.
- `-Dgpg.skip=true` sits on the one shared Maven command line, so no command can miss it.
