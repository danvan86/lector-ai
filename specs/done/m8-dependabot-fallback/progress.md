# Progress – M8 Dependabot and fallback

Spec: [`spec.md`](spec.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | Source selection: sources own their triggers, `source` override, PR labels and state, bot logins default to both bots | ✅ |
| 2 | Dependabot source: `updated-dependencies-json`, ecosystem table, Gradle plugin markers, release notes, missing metadata | ✅ |
| 3 | Fallback source: resolved versions of base and head, every difference, unreadable versions in the comment | ✅ |
| 4 | GitHub adapter: find, create, update, close a PR, delete a branch, force-push only to `heal/*`, label by number | ✅ |
| 5 | Branch policy and companion PRs: `mode`, `heal/<branch>`, force-push and keep, cleanup on closed, merged, green; report | ✅ |
| 6 | Action inputs and setup guide | ✅ |
| 7 | Quality gates verified | ✅ |
| 8 | Docs in sync | ✅ |
| 9 | Review and fixes: [`review.md`](review.md) | ✅ |

## Quality gates

Final check from a clean state: `ktlintCheck`, `detektMain detektTest`, `test`, `:cli:endToEndTest`, `koverVerify` each
exit 0; `./gradlew clean build --rerun-tasks --no-build-cache` exits 0. 539 unit and integration tests, 21 end-to-end
tests, 0 failures. Coverage 100 % lines and branches in `core`, `sources`, `git`, `cli`. After the review: 544 unit and
integration tests, 21 end-to-end tests, 0 failures, coverage 100 % in every module.

Probes, each reverted afterwards (`git status` clean):

| Gate | Probe | Result |
|---|---|---|
| ktlint | extra spaces in `FallbackSource.kt` (main), `FallbackSourceTest.kt` (test), `sources/build.gradle.kts` (script) | `ktlintMainSourceSetCheck`, `ktlintTestSourceSetCheck`, `ktlintKotlinScriptCheck` fail |
| detekt | `System.out.println` in `GitHubApi.kt` (main), `println` in `NoContext.kt` (test) | `ForbiddenMethodCall` in `detektMain` and `detektTest` |
| Kover | an untested function in `DependabotSource.kt` | `:sources:koverVerify` fails |
| Tests | companion branch prefix changed in `Companions.kt` | `:core:test`, `:cli:test`, `:cli:endToEndTest` fail |

`./gradlew build --dry-run` runs every gate task of `core`, `sources`, `git`, and `cli` once.

## Acceptance criteria

| ID | Result | Evidence |
|---|---|---|
| AC1 | met | `./gradlew clean build --rerun-tasks --no-build-cache`: exit 0 |
| AC2 | met | `core/build.gradle.kts` has no `project(`; `grep -ri dependabot core/src/main`: no match |
| AC3 | met | `DependabotSource`, `FallbackSource` in `sources`, both `: UpdateSource` |
| AC4 | met | `DependabotSourceTest`: grouped `maven-group.json` gives four changes in order |
| AC5 | met | `DependabotSourceTest`: `maven` and `gradle` → `maven`, `npm_and_yarn` → none |
| AC6 | met | `DependabotSourceTest`: `org.springframework.boot` → `org.springframework.boot:org.springframework.boot.gradle.plugin` |
| AC7 | met | `DependabotSourceTest`: a mismatching `update-type` changes nothing; `DependabotSource` doesn't read the field |
| AC8 | met | `DependabotSourceTest`: release notes and changelog sections of captured bodies, commit lists left out |
| AC9 | met | `PrCommandTest`: exit 2, stderr and comment name `docs/setup.md, section 3` |
| AC10 | met | `FallbackSourceTest`: changed, added, removed, and transitive packages |
| AC11 | met | `PrCommandTest` and `PrFlowTest` (real `MavenAdapter`): `git worktree list` shows one worktree after the run |
| AC12 | met | `PrCommandTest`, `PrPipelineTest`: no verify, no recipe, no fix loop, reason in the comment, exit 2 |
| AC13 | met | `EcosystemAdapter` has no `manifestDiff`; `grep -r manifestDiff core/src`: no match |
| AC14 | met | `PrPipelineTest` (order, skip without comment), `RootCommand.sources` order Renovate, Dependabot, fallback |
| AC15 | met | `PrPipelineTest`, `PrCommandTest`: a PR by `mallory` without the label is skipped, no comment |
| AC16 | met | `PrPipelineTest`: the named source takes the PR; a fork is skipped with a source named |
| AC17 | met | `PrPipelineTest`: the own-commit check runs for whichever source takes the PR |
| AC18 | met | `PrPipelineTest`, `PrCommandTest`: Renovate in place, Dependabot and fallback companion; `mode` overrides both ways |
| AC19 | met | `PrPipelineTest`, `PrFlowTest`: `heal/<branch>` on top of the head with the recipe and fix commits, PR into the source branch |
| AC20 | met | `PrPipelineTest`: an open companion PR is updated, force-pushed, no second PR |
| AC21 | met | `GitHubHostTest`: `push` has no force flag; `forcePush` refuses anything outside `heal/` before running `git` |
| AC22 | met | `PrPipelineTest`: a run without commits opens no companion PR |
| AC23 | met | `PrPipelineTest` (closed and merged), `PrCommandTest`, end-to-end test of a closed PR |
| AC24 | met | `PrPipelineTest`, `PrCommandTest`: a green run closes the companion PR and deletes its branch |
| AC25 | met | `PrPipelineTest`, `PrFlowTest`: the comment is on the source PR and names `#20` / `#21` |
| AC26 | met | `PrPipelineTest`: the description has the summary and `#7` |
| AC27 | met | `PrPipelineTest`: label on 20 in companion mode, on 7 in place |
| AC28 | met | `GitHubHostTest` against the fake server: find, create, update, close, delete |
| AC29 | met | `action/action.yml`: `dependabot-metadata`, `mode`, `source`, `fallback-label`; `bot-logins` default `renovate[bot],dependabot[bot]` |
| AC30 | met | `docs/setup.md`: `types: [opened, synchronize, reopened, labeled, closed]`, `fetch-metadata` only `if` the author is `dependabot[bot]` |
| AC31 | met | `docs/setup.md`: Dependabot secrets, merge order, in-place for Dependabot, the opt-in label |
| AC32 | met | `grep -r 'pull_request_target\|workflow_run'`: only concept 8.1's warning and the specs |
| AC33 | met | `git/src/main`: no merge, review, or approve endpoint |
| AC34 | can't verify yet | Needs a real Dependabot PR in a test repository on GitHub |
| AC35 | can't verify yet | Needs a real labelled manual PR in a test repository on GitHub |

## Open

- AC34, AC35: the end-to-end runs on GitHub. They need a published tool image (the release waits for the MSAL check in
  `docs/licensing.md`), a GitHub App, and a test repository. M8 stays 🟨 in `docs/features.md` until then.

## Decisions during implementation

- **Metadata as JSON, not a file.** `--dependabot-metadata` (and `LECTOR_DEPENDABOT_METADATA`, which the action sets)
  takes the JSON itself, not a file as the spec wrote: a Docker container action can't write a file before the CLI
  runs, and writing it in the user's workflow would leave a file in the checkout. Validated when parsed.
- **The full change list is in the CI log.** `lector pr` has no JSON output, so "the full list in the JSON output" is
  the stdout of `lector pr`, which lists every change after the comment. The comment cuts the changes without a recipe
  to ten.
- **Added and removed packages** have an empty version and are no upgrade (rule 1), so they get no recipe.
- **Base versions are read once per run** for all changes (`recipeBlockers`): a labelled PR can change hundreds of
  packages, and rule 2 read them per change before.
- **The Gradle wrapper** (`gradle-wrapper` in Dependabot's metadata) is no package and is left out; any other Gradle
  name without `:` is a plugin. Checked on captured metadata.
- **Fixtures** are real Dependabot PRs, their `updated-dependencies-json` made by running `fetch-metadata`'s own parser
  on the PR's commit message.
- **The setup guide reads the App's client ID from a secret**, not a variable, so that it works in runs Dependabot
  started, which see only Dependabot secrets.
- **The guide's workflow** has a `cleanup` job for `closed` without the model, and doesn't cancel a running job for a
  label other than `lector`.
- **A companion PR is left alone by a run without commits** (spec: such a run only updates the comment).
- **Risk for review:** the fix loop gets every change of a labelled PR, transitive ones included, as the spec says.
  Hundreds of changes make a long prompt for a 7B model.
