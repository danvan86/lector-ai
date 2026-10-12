# Review – M5 Renovate in CI

Scope: commits `335b5bb..3e130f8` (11 commits on `danvan86/m5-renovate-ci` against `origin/main`) against [`spec.md`](spec.md).
Method: read every source, test, build, Docker, action, and workflow file in scope; ran `./gradlew clean build --rerun-tasks
--no-build-cache`; ran the installed `lector pr` on bad input; probed detekt, Kover, and `checkActionImage` with
temporary changes and checked the task graph (all reverted, `git status` clean).

Outcome per finding: **Fixed** · **Not changed** (with reason) · **Needs decision** (with options).

Severity: **High** = breaks a requirement or the external contract · **Medium** = real gap, should fix before the next milestone · **Low** = cleanup / nit.

## Summary

- 33 of 37 acceptance criteria are met. AC32 is not met (the MSAL check is open, by design a release blocker). AC25 and
  AC34 can't be verified from the repo: they need a release and a test repository on GitHub. AC24 is met only with a
  caveat: the release workflow publishes the Ollama images without the licensing gate (F1).
- The main contract gap: the report comment is found by its marker alone, so anyone who can comment on the PR can take
  it over or make every later run fail (F2).
- The main design question: after lector-ai pushes, the check on the PR's new head is a green skip, so the red of exit
  `1` stays on a commit GitHub no longer shows (F4).
- Tests are thorough: every pipeline branch, the trigger checks, the GitHub adapter against a local server and a real
  bare remote, and the time budget. One small gap (F7).

## Acceptance criteria

| ID | Result | Evidence |
|---|---|---|
| AC1 | Met | `./gradlew clean build --rerun-tasks --no-build-cache`: exit `0`; 479 unit tests (core 175, jvm 125, cli 98, llm 18, recipes 33, sources 16, git 14), 19 end-to-end |
| AC2 | Met | `core/.../UpdateSource.kt`, `GitHost.kt`; no `project(` in `core/build.gradle.kts`; `grep -rni "renovate\|github" core/src/main`: no match |
| AC3 | Met | `settings.gradle.kts:15`; `sources/build.gradle.kts`, `git/build.gradle.kts` depend on `:core` only; `cli/build.gradle.kts:11-12` |
| AC4 | Met | `RenovateSourceTest` "reads every change of a monorepo group", "reads every row of a large group" (13 rows) |
| AC5 | Met | `RenovateSourceTest` "extracts the release notes from the body …" |
| AC6 | Met | `RenovateSourceTest` digest, pin digest, pin, lock file maintenance; `PrPipelineTest` "a PR without a version change is skipped without a comment" |
| AC7 | Met | `PrPipelineTest:40-77`, one test per check, each `assertNothingHappened` |
| AC8 | Met | author from `pull_request.user.login` (`GitHubHost.kt:90`); `github.actor` only in `release.yml`'s registry login and concept 8.1's warning |
| AC9 | Met | `PrPipelineTest` "a green verify stops the run with no recipe, no commit, and no new comment" |
| AC10 | Met | `PrPipelineTest` "grouped changes with the same recipe run it once", "different recipes run in the order of the PR's table" |
| AC11 | Met | `PrPipelineTest` "with diagnostics left, the recipe commit and every accepted fix are pushed …" |
| AC12 | Met | `GitHubHostTest` "… push never forces", "… to one that moved is rejected" (real bare remote); `PrPipelineTest` "a rejected push ends the run without a comment or a label" |
| AC13 | Met | `PrCommandTest` "what is left after the fix loop exits with 1" (but see F4 for what the PR shows) |
| AC14 | Met | `JdkSelectionTest`: 11 → 17, `java.version` 21 → 21, none → 21, 26 → `ToolError` |
| AC15 | Met | `PrPipelineTest` "the base commit a shallow clone left out is fetched …"; `PrFlowTest` on a shallow clone |
| AC16 | Met | `java.net.http.HttpClient` in `GitHubHost.kt:65`; no `kohsuke`, `jgit`, or `gh` in the catalog, build files, or Dockerfiles |
| AC17 | Met | `PrPipelineTest`: every new commit is `lector-ai[bot] <…>`; `GitWorkingTreeTest` "commit uses the given identity" |
| AC18 | Met, with caveats | `GitHubHostTest` "the first comment is created with the marker, and every later one updates it"; the marker alone decides (F2) |
| AC19 | Met | `PrCommentTest` "the comment lists recipes, fixes, what is left, …, and the guardrail rejections per rule" |
| AC20 | Met | `PrPipelineTest`: label after a push, none without a commit or after a rejected push |
| AC21 | Met | `grep -rniE "merge\|review\|approv" git/src/main`: only the KDoc saying it never does |
| AC22 | Met | `docker/lector/Dockerfile`: `eclipse-temurin` base, JDKs 17/21/25, Maven, `git`, `cli/build/install/lector`; `.dockerignore` admits only the distribution |
| AC23 | Met | `docker/ollama/Dockerfile`: `ARG MODEL`, `ollama pull` at build time |
| AC24 | Met, with caveats | `.github/workflows/release.yml` builds and pushes all three images on `v*`; the Ollama job skips the MSAL gate (F1) |
| AC25 | Can't verify | Needs a release tag, which the MSAL check blocks |
| AC26 | Met | `action/action.yml`: `using: docker`, `image: docker://ghcr.io/danvan86/lector:0.1.0`; `checkActionImage` probe failed on `0.0.9` |
| AC27 | Met | `action/action.yml` inputs: token, bot-name, bot-email, bot-logins, ollama-url, model, five budgets, maven-settings; no key, no mode |
| AC28 | Met | No config file lookup in any `src/main` |
| AC29 | Met | `docs/setup.md` §3: `pull_request`, `services: ollama`, `concurrency` per PR with `cancel-in-progress`, `actions/create-github-app-token` |
| AC30 | Met | `docs/setup.md` §2 (`gitIgnoredAuthors`), §1 (permissions), §4 (runners for both variants), Limitations |
| AC31 | Met | `pull_request_target` only in concept 8.1's warning and in specs, no workflow or example uses it |
| AC32 | Not met | `docs/licensing.md` exists with the model and image licenses, but `**MSAL check:** open`: no answer from Moderne, no legal review |
| AC33 | Met | `RenovateSourceTest`, `PrPipelineTest`, `GitHubHostTest`, `JdkSelectionTest`, `PrCommentTest`, `PrCommandTest`, `PrFlowTest` |
| AC34 | Can't verify | Needs the test repository on GitHub with Renovate, the App, and published images |
| AC35 | Met | Only `RenovateSource` and `GitHubHost`; `BranchPolicy` has only `IN_PLACE` |
| AC36 | Met | `ProcessesTest` "a process sees only the allowlisted variables, never a token or an action input" |
| AC37 | Met | `FixLoopTest:207`, `:229`; `PrPipelineTest` "a build past the time budget ends the loop, and the earlier fixes are still pushed and reported" |

Probes: `System.out.printf` in `sources` main failed `:sources:detektMain` (`ForbiddenMethodCall`); an untested branch in
`sources` and in `git` failed `:sources:koverVerify` and `:git:koverVerify`; `action.yml` on `lector:0.0.9` failed
`checkActionImage`. `./gradlew build --dry-run` lists every ktlint, detekt, Kover, and test task of `sources` and `git`,
`checkActionImage`, and `:cli:endToEndTest`.

Installed `lector pr`: missing `--event`, an `ftp://` API URL, an extra argument, a blank `--bot-name`, and
`--max-attempts 0` are usage errors (exit `2`, stderr, empty stdout); a non-PR event and a missing repository directory
are tool errors with a clear message (exit `2`). A missing event file is exit `2` with an unclear message (F5).

## Findings

### Release and CI

**F1 · High · A version tag publishes the Ollama images even while the MSAL check is open.** `release.yml`: the check of
the tag and of `**MSAL check:** done` is a step of the `tool-image` job; `ollama-image` has no `needs` and runs in
parallel. A `v0.1.0` tag today fails `tool-image` and still pushes `lector-ollama:qwen2.5-coder-7b` (and tries the 30B).
The release blocker (spec section 8, `AGENTS.md`: "it is refused until the MSAL check … is done") covers the model
weights and the Ollama base image too, and they are redistributed for the first time in exactly this job. Expected: a
`check` job with the tag and licensing checks that both image jobs `need`.

Outcome: **Fixed.** A `check` job runs the tag and licensing checks, and both image jobs `need` it (`13752cf`); the check script, run locally, fails on `MSAL check: open` and on a wrong tag, and passes on `done`.

**F3 · Medium · Dependabot will propose moving the JDK 17 and 21 stages to JDK 25.** `.github/dependabot.yml` watches
`/docker/lector` with no `ignore` rule, and Dependabot proposes major Docker tag updates by default:
`eclipse-temurin:17.0.20.1_1-jdk-noble` → `25.…-jdk-noble`. Merged, `LECTOR_JDK_17` points to a JDK 25 and every project
on 17 builds with 25. No build or test looks at the Dockerfile's tags, so CI stays green. Expected: ignore
`version-update:semver-major` for `eclipse-temurin` (a new LTS stage is a deliberate change anyway).

Outcome: **Fixed.** `.github/dependabot.yml` ignores major updates of `eclipse-temurin` (`13752cf`).

### Contracts

**F2 · Medium · The report comment is found by its marker, whoever wrote it.** `GitHubHost.kt:164`: the first comment
whose body starts with `<!-- lector-ai -->` is the report. On a public repository anyone can comment on a Renovate PR.
A comment that starts with the marker makes every later run `PATCH` someone else's comment: GitHub answers 403 and each
run ends with exit `2`, or, where the App may edit it, the report lands in a stranger's comment. Expected: only a comment
by the App's bot counts (`user.login` is the bot name, `<app-slug>[bot]`, which the action already passes).

Outcome: **Fixed.** `GitHubHost` takes the bot's login (the `--bot-name`) and only a marked comment by it is the report; a new test fails without the check (`effe654`).

**F4 · Medium · After a push, the PR's check is green even when something is left.** The run that pushes exits `1`, but
its push starts a new run on the new head, which skips the tool's own commit with exit `0` (spec 2.1). GitHub shows the
checks of the head commit, so the PR shows a green lector-ai check while the comment says "2 problems … are left to fix".
Exit `1` is visible only when nothing was pushed. The spec wants both: "skip, exit `0`" for our own head, and "`1`
something left (the check turns red)". The setup guide's table lists both rows without saying they meet.

Outcome: **Decided: keep the skip and document it** (option A). The other options were a verify-only run on the tool's
own commit (an honest red check for one more full build per push) and an exit code read back from the comment (state in
PR comments, which Q10 decided against). The concept (8.1), the setup guide, and spec 2.8 now say that the check on the
tool's own commit is green, and that the comment and the repository's own CI show what is left.

**F5 · Low · A missing event file is an "Unexpected failure".** `lector pr --event /nonexistent.json …` exits `2` with
`Error: Unexpected failure: java.nio.file.NoSuchFileException: /nonexistent.json`. `--event` is `.path()` without
`mustExist`, so `GitHubHost.eventJson` reads a file that isn't there. Expected: `.path(mustExist = true, canBeDir = false,
mustBeReadable = true)`, like `--release-notes`: a usage error before anything runs.

Outcome: **Fixed.** `--event` is `.path(mustExist = true, canBeDir = false, mustBeReadable = true)`: a usage error naming the file (`e1d1b9e`).

**F6 · Low · `--bot-logins` keeps the spaces around each login.** `--bot-logins "renovate[bot], my-renovate[bot]"` gives
`" my-renovate[bot]"`, so that bot's PRs are skipped as "not one of the bot logins". A list in a workflow file is easily
written with spaces. Expected: trim each login and drop empty ones.

Outcome: **Fixed.** Logins are trimmed, empty ones dropped, and a list without any bot is a usage error (`e1d1b9e`).

### Tests

**F7 · Low · The root command's help test doesn't check `pr`.** `RootCommandTest` "help lists the verify, migrate, and
heal commands" asserts three of four commands. The end-to-end test checks all four, but only in `endToEndTest`. Expected:
assert `pr` too, and name the test after all four.

Outcome: **Fixed.** The test asserts `pr` and is named after all four commands (`e1d1b9e`).

### KISS / SOLID / DRY

**F8 · Low · Three expressions read badly after ktlint wrapped them, and the short-commit length is defined twice
(DRY).** `PrComment.kt:25-30` (`commit.take(\n SHORT_COMMIT,\n)` inside a template), `PrComment.kt:51-55`
(`result(…)` split inside a template), `JdkSelection.kt:60-63` (`nodes.item(\n it,\n)`). `SHORT_COMMIT = 7` is a private
constant in `core`'s `PrComment.kt` and an internal one in `cli`'s `FixLoopReport.kt`. Expected: local values or small
functions so the formatter keeps each line whole; one public `SHORT_COMMIT` in `core`.

Outcome: **Fixed.** `PrComment.fixes`, a local `result`, and `JdkSelection.children` keep each line whole; `SHORT_COMMIT` is one public constant in `core` (`d823ffb`).

### Leftovers

**F9 · Low · An empty fixture named `.md`.** `sources/src/test/resources/renovate/.md` (0 bytes) came with `fa21820`, and
no test reads it. Expected: delete it.

Outcome: **Fixed.** Deleted (`d823ffb`).

## What's good

- The ports are small and honest: `UpdateSource` returns `PackageUpdate`s and leaves the update type to the ecosystem's
  version scheme, `GitHost` is the only holder of the token, and `core` knows neither Renovate nor GitHub.
- The token reaches `git` only through `GIT_CONFIG_*` environment variables, after an empty value that drops
  `actions/checkout`'s header, and every other process gets an allowlisted environment. `ACTIONS_RUNTIME_TOKEN` and
  `INPUT_*` stay out too.
- The time budget is a port parameter that `attribute` fills before each run, so a hanging build in an attempt becomes a
  rejected attempt and the loop still reports and pushes (AC37), while the adapter's own one-hour limit stays a tool
  error.
- `PrPipelineTest` runs on a real shallow clone with fakes for the host, source, adapter, and model; `GitHubHostTest`
  pushes to a real bare remote that moved on. Both test the contract, not the call sequence.
- `checkActionImage` is a gate, not a comment: the action can't run an older tool image than the release.
- The release workflow refuses a tag until `docs/licensing.md` says `done`, instead of trusting that the file exists.
