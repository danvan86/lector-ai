# Review – M8 Dependabot and fallback

Scope: commits `f33e3be..ca39c06` (8 commits on `danvan86/m8-dependabot-fallback` against `origin/main`) against
[`spec.md`](spec.md).
Method: read every source, test, build, action, and docs file in scope; ran `./gradlew clean build --rerun-tasks
--no-build-cache`; ran the installed `lector pr` on bad input; read `dependabot/fetch-metadata`'s source (v3.1.0) for
what it does on PRs whose head isn't Dependabot's; probed detekt (`System.out.printf` in `sources`), Kover (an untested
branch in `git`), and the task graph (all reverted, `git status` clean).

Outcome per finding: **Fixed** · **Not changed** (with reason) · **Needs decision** (with options).

Severity: **High** = breaks a requirement or the external contract · **Medium** = real gap, should fix before the next milestone · **Low** = cleanup / nit.

## Summary

- 31 of 35 acceptance criteria are met. AC34 and AC35 can't be verified from the repo: they need a release and a test
  repository on GitHub. AC23 is met only for a companion PR that is still open (F2). AC31 is met, but the documented
  workflow turns red on a Dependabot PR once anyone but Dependabot has pushed to it (F1).
- The main contract gap: `dependabot/fetch-metadata` fails its step when the PR's head commit isn't Dependabot's, so the
  documented merge order (companion PR first) and in-place mode both end in a red check before lector-ai runs (F1).
- The main lifecycle gap: a merged or hand-closed companion PR leaves `heal/<branch>` behind for good (F2), and a new
  head without commits leaves a companion PR on the old head (F3).
- Tests are thorough on the sources, the selection, and the GitHub adapter. The companion lifecycle tests only cover a
  companion PR that is still open, which is how F2 got through.

## Acceptance criteria

| ID | Result | Evidence |
|---|---|---|
| AC1 | Met | `./gradlew clean build --rerun-tasks --no-build-cache`: exit `0`; 539 unit and integration tests (core 194, jvm 125, cli 111, sources 35, recipes 33, git 23, llm 18), 21 end-to-end |
| AC2 | Met | no `project(` in `core/build.gradle.kts`; `grep -ri dependabot core/src/main`: no match |
| AC3 | Met | `sources/.../DependabotSource.kt:52`, `FallbackSource.kt:19`, both `: UpdateSource` |
| AC4 | Met | `DependabotSourceTest` "every entry of a grouped update is a change" on captured `maven-group.json` (4 entries) |
| AC5 | Met | `DependabotSourceTest` grouped `maven`, `gradle-plugin`, `npm` (`npm_and_yarn` → none) |
| AC6 | Met | `DependabotSourceTest` "a Gradle plugin maps to its marker coordinates" on captured `gradle-plugin.json` |
| AC7 | Met | `DependabotSourceTest` "the update type is not read from Dependabot"; `DependabotSource` reads only `dependencyName`, `packageEcosystem`, `prevVersion`, `newVersion` |
| AC8 | Met | `DependabotSourceTest` release-notes tests on captured bodies |
| AC9 | Met | `DependabotSourceTest` "… without the metadata is a tool error that names the setup step"; `PrCommandTest`: exit 2, stderr and comment |
| AC10 | Met | `FallbackSourceTest` "every package whose resolved version differs is a change …" |
| AC11 | Met | `PrFlowTest:145` (real `MavenAdapter`), `PrCommandTest:218`: one worktree after the run |
| AC12 | Met | `PrPipelineTest` "a source that can't read the changes fails the run, before verify, and the comment says why"; `PrCommandTest` exit 2 |
| AC13 | Met | `EcosystemAdapter` has no `manifestDiff`; `grep -r manifestDiff core/src`: no match |
| AC14 | Met | `PrPipelineTest` "the first source that recognizes the PR takes it", "a PR no source recognizes is skipped without a comment"; `RootCommand.kt:29` order |
| AC15 | Met | `PrPipelineTest`, `PrCommandTest`: PR by `mallory` without label skipped, no comment |
| AC16 | Met | `PrPipelineTest` "a named source takes the PR …", "a PR from a fork is skipped …, also with a source named" |
| AC17 | Met | `PrPipeline.kt:104`: the own-commit check runs after any source took the PR |
| AC18 | Met | `PrPipelineTest` "with no mode set, a companion source's fixes go to heal-branch …", "a set mode overrides …"; `PrCommandTest` |
| AC19 | Met | `PrPipelineTest`, `PrFlowTest:128-131`: `heal/renovate/spring-boot` on the head, PR into the source branch |
| AC20 | Met | `PrPipelineTest` "a new source head rebuilds heal-branch …" |
| AC21 | Met | `GitHubHostTest` "a force-push goes only to a companion branch …", "… push never forces" |
| AC22 | Met | `PrPipelineTest` "a run without a commit creates no companion PR …" (but see F3) |
| AC23 | Met, with caveats | `PrPipelineTest` "a closed or merged source PR gets its companion PR closed …" with an open companion PR only; a merged or closed companion PR leaves its branch (F2) |
| AC24 | Met | `PrPipelineTest` "a green run closes the companion PR, deletes its branch …" |
| AC25 | Met | `PrPipelineTest`, `PrFlowTest:138`: comment on #7 links #20 / #21 |
| AC26 | Met | `PrPipelineTest`: description has the summary and `#7` |
| AC27 | Met | `PrPipelineTest`: `20 to "ai-assisted"` in companion mode, `7 to "ai-assisted"` in place |
| AC28 | Met | `GitHubHostTest` against the local server: find, create, update, close, delete |
| AC29 | Met | `action/action.yml:16-30` |
| AC30 | Met | `docs/setup.md:67`, `:104` |
| AC31 | Met, with caveats | `docs/setup.md` sections 1, 3; the workflow it documents goes red on Dependabot PRs with a foreign head (F1) |
| AC32 | Met | `grep -r 'pull_request_target\|workflow_run'` outside `specs/`: only concept 8.1's warning |
| AC33 | Met | `git/src/main`: no merge, review, or approve endpoint |
| AC34 | Can't verify | Needs a published image, a GitHub App, and a test repository |
| AC35 | Can't verify | Same |

The installed `lector pr` exits `2` with the usage line and a message on stderr, stdout empty, for `--mode sideways`,
`--source snyk`, `--dependabot-metadata '[1]'`, `--fallback-label ''`, and an incomplete event.

## Findings

### Contract and lifecycle

**F1 · High · The documented workflow fails red on a Dependabot PR whose head commit isn't Dependabot's.**
`dependabot/fetch-metadata` (v3.1.0, `src/dependabot/verified_commits.ts`) reads the PR's head commit and refuses with
`setFailed('PR is not from Dependabot, nothing to do.')` if its author isn't `dependabot[bot]`. The heal job runs it
first (`docs/setup.md:103-105`). Three normal cases put a foreign commit on top: in-place mode (lector-ai's own push), the
documented merge order (the companion PR merged into Dependabot's branch, a merge commit by a person), and a person
fixing the PR by hand. Each then gets a red check before lector-ai even runs. The guide promises the opposite: "the run
on its own commit skips, so its check on the PR's latest commit is green" (`docs/setup.md:161`). Expected: the metadata
step gets `continue-on-error: true`, and lector-ai's step runs only `if: steps.metadata.outcome != 'failure'`. A PR
Dependabot no longer owns the head of is then skipped green, like the own-commit skip. Say so in the guide.

Outcome: **Fixed.** The guide's metadata step has `continue-on-error: true` and lector-ai's step runs only `if: steps.metadata.outcome != 'failure'`; the table and the Dependabot section say such a head is skipped green. Only a real run can confirm it (AC34).

**F2 · High · A companion PR that is merged or closed by hand leaves `heal/<branch>` behind for good.**
`Companions.close` (`Companions.kt:39-44`) returns before `deleteBranch` when no open companion PR is found. Merging the
companion PR first is the documented order, so after it the source PR's `closed` run finds no open PR and keeps the
branch. Spec section 4: the tool "deletes `heal/<source-branch>` when the source PR is closed or merged". The pipeline
test only covers an open companion PR. Expected: on a closed or merged source PR, delete the companion branch also when
its PR is gone already (`deleteBranch` already treats a missing branch as no error). Test first with a merged companion.

Outcome: **Fixed.** `Companions.retire` deletes the companion branch of a closed or merged source PR also when its PR is gone already; new pipeline test with a merged companion, end-to-end test expects the `DELETE`.

**F3 · Medium · A new head without commits leaves the companion PR on the old head.**
Run 1 on Dependabot's PR opens companion PR #20 with a fix. Dependabot rebases; run 2 is still red but makes no commit
(no recipe change, the model fails this time). `PrDelivery.deliver` then only writes the comment (`PrDelivery.kt:25`),
which says "pushed nothing" and no longer links #20. #20 stays open on top of the old head: merging it brings the old
Dependabot commit back into the rebased branch. The spec has two rules that disagree here: "a run without commits only
updates the sticky comment" and "when the source PR gets a new head, the companion branch is rebuilt from that head".

Outcome: **Fixed** (decision: close it). A companion run without commits closes the open companion PR and deletes its branch, and the comment says why; an in-place run doesn't look. Spec section 4, concept 3.1, and the guide say so.

### KISS / SOLID / DRY

**F4 · Medium · A red labelled PR resolves the base's versions twice, and the second read can't change anything.**
`FallbackSource.read` reads the base's resolved versions in a worktree (`RepositoryContext.baseVersions`), then
`PrPipeline.heal` calls `recipeBlockers`, which reads them again in a new worktree (`RecipeStep.kt:96`). A dependency
resolution of a large build takes minutes. For the fallback, rule 2 can't fire anyway: each change's `fromVersion` is
the base's version. Expected: the run reads the base's versions at most once, through the context.

Outcome: **Fixed.** `RepositoryContext` keeps the base's versions after the first read, and `recipeBlockers` takes them through the context; the pipeline test counts one base read.

**F5 · Medium · The fix loop's prompt lists every change untrimmed.**
`Prompt.user` (`Prompt.kt:49-51`) puts every change into the intro, which is never trimmed. A labelled Spring Boot bump
changes a few hundred packages: 300 × ~70 characters is half of `MAX_CHARS` (39,936), so the API grounding and the
release notes are trimmed away first, and past ~550 changes Ollama cuts the prompt. `progress.md` named this as the open
risk. Expected: name the first ten changes and the number of the rest, as the comment does.

Outcome: **Fixed.** `Prompt.user` names the first ten changes and adds "and N more"; a test with 1000 changes checks the API still fits.

### Docs

**F6 · Low · The setup guide promises Gradle updates.** `docs/setup.md:188`: "lector-ai fixes `maven` and `gradle`
updates", while M7 is open and the guide's limitations say "Maven only". A Dependabot PR on a Gradle build exits `2`
("No supported ecosystem"). `:194` calls the resolved versions "the effective POM". Expected: Gradle with M7; plain
"resolved versions".

Outcome: **Fixed.** The guide says `gradle` updates need Gradle support (see the limitations) and drops "the effective POM".

### Quality gates and safety

**F7 · Low · `deleteBranch` has no `heal/` guard, unlike `forcePush`.** `GitHubHost.kt:220`. Deleting a branch is as
destructive as force-pushing one, and the only intended targets are companion branches. Expected: the same guard and a
test.

Outcome: **Fixed.** `deleteBranch` and `forcePush` share one `heal/` check; a test shows nothing reaches GitHub for another branch.

**F8 · Low · `action.yml`'s arguments aren't checked against the CLI.** No test parses the action's `args` with `lector
pr`. A typo in a new option name (four came with M8) exits `2` on every run, and only a real run shows it. A check needs
a YAML reader or brittle line parsing.

Outcome: **Not changed.** A check needs a YAML reader (a new dependency) or line parsing that breaks on the first multi-line value. The CLI-level tests cover `--mode=` and `--source=` as the action passes them, and AC34's real run checks the rest. Revisit when the action gets a smoke test in CI.

### Leftovers

**F9 · Low · Line wraps that ktlint forced instead of readable code.** `PrCommand.kt:192-198` (`if (companion ==\n
null\n)`), `GitHubHost.kt:170-175` (`if (!branch.startsWith(\nCOMPANION_PREFIX,\n)\n)`), `PrPipeline.kt:188-192`
(`change to\n lookUp(…)`), `GitHubHostTest` (`gmbh.vanella.lector.core\n.ProcessResult` instead of an import).
Expected: shorter expressions that need no wrap.

Outcome: **Fixed.** The closed text is one expression, the `heal/` check one call, the recipe lookups one line, the test imports `ProcessResult`.

## Found while fixing

**F10 · Low · `RecipeStep.readVersions` took a directory-handing function that only one caller still needed.** After F4,
rule 3 was the only user, always on the repository itself.

Outcome: **Fixed.** It takes the repository, and the null check of the adapter's capability is shared with
`recipeBlocker`.

## Result

`./gradlew clean build --rerun-tasks --no-build-cache`: exit `0`. 544 unit and integration tests (core 197, jvm 125,
cli 111, sources 35, recipes 33, git 24, llm 19), 21 end-to-end, 0 failures. Coverage 100 % lines and branches in every
module. Re-probed: removing the `heal/` check from `deleteBranch` fails `GitHubHostTest`; the base-read count failed
before the F4 fix.

## What's good

- Sources own their triggers and branch policy; `core` stays free of any bot. The `SourceContext` lets a source read
  resolved versions only when it needs them, so Renovate and Dependabot runs cost no extra resolution.
- The metadata is JSON in an environment variable, validated when parsed, so a bad value fails before any build.
- Fixtures are captured from real Dependabot PRs, with `fetch-metadata`'s own parser.
- `forcePush` refuses anything outside `heal/` before running `git`, and `push` has no force flag: AC21 holds by
  construction, not by caller discipline.
- The closed path needs no checkout and no model: the `cleanup` job is cheap.
- Base versions are read once for all changes (`recipeBlockers`), not per change.
