# Progress – M5 Renovate in CI

Spec: [`spec.md`](spec.md)

Status: ⬜ open · 🟨 in progress · ✅ done

Built on the M4 branch (`danvan86/m4-guardrails`): the fix loop runs the guardrails, and the comment reports their
rejections.

| # | Step | Status |
|---|---|---|
| 1 | Core groundwork: scrubbed environment for every process, commit identity, several changes per fix loop, recipe lookup and run split | ✅ |
| 2 | JDK selection in the Maven adapter | ✅ |
| 3 | Ports `UpdateSource` and `GitHost`, and the PR pipeline in `core`: trigger checks, verify first, recipes, fix loop, push, comment, label | ✅ |
| 4 | Renovate adapter in `sources` | ✅ |
| 5 | GitHub adapter in `git`: event, comment, label, fetch, push | ✅ |
| 6 | `lector pr` in `cli`, with end-to-end tests | ✅ |
| 7 | Container images, GitHub Action, release workflow | ✅ |
| 8 | Setup guide and `docs/licensing.md` | ✅ |
| 8b | Time budget as the deadline of an attempt's builds and test runs (AC37, added to the spec on `main` during M5) | ✅ |
| 9 | Quality gates verified | ✅ |
| 10 | Docs in sync | ✅ |

## Quality gates

`./gradlew clean build --rerun-tasks --no-build-cache` exits `0` with 473 unit tests (core 170, jvm 124, cli 98, llm 18,
recipes 33, sources 16, git 14), 19 end-to-end tests, and 100 % lines and branches per module. The opt-in
`:cli:integrationTest` (real Maven, network) passes too. The real-model test was not run for M5.

Final check, each on its own: `ktlintCheck`, `test`, `:cli:endToEndTest`, `koverVerify`, and
`clean build --rerun-tasks --no-build-cache` exit `0`. `detektMain detektTest` exited `1` once, with its log overwritten
by the next check; four reruns after `clean` exit `0`. M4 saw the same once.

Probes, all in one `build --continue`, each failing on its own gate, then reverted:

| Gate | Probe | Failed with |
|---|---|---|
| ktlint | bad formatting in `sources` main and test sources, and in `git/build.gradle.kts` | `:sources:ktlintMainSourceSetCheck`, `:sources:ktlintTestSourceSetCheck`, `:git:ktlintKotlinScriptCheck` |
| detekt | `System.out.println` in `git` main, `println` in `sources` test | `ForbiddenMethodCall` in `:git:detektMain`, `:sources:detektTest` |
| Kover | an untested function in `sources` | `:sources:koverVerify` |
| Tests | a wrong assertion in `git` | `:git:test` |
| `checkActionImage` | `action/action.yml` on image tag `0.0.9` | `action/action.yml runs … but the tool's version is 0.1.0` |

`./gradlew build --dry-run` lists every gate task of `sources` and `git`, `checkActionImage`, and `endToEndTest` once.

By hand:

- `docker build -f docker/lector/Dockerfile .`: `lector --version` prints `0.1.0`; JDKs 17.0.20.1, 21.0.12.1, 25.0.4.1,
  Maven 3.9.16, git 2.43; no `rewrite-*.jar` in the image; 1.6 GB.
- The tool image ran `pr` the way the action does (workspace mounted at `/github/workspace`, event and token in the
  environment, empty `LECTOR_MAVEN_SETTINGS`) on the Guava fixture with real Maven: verify green, one `GET` of the PR's
  comments with the token, exit `0`.
- `docker build --build-arg MODEL=qwen2.5-coder:0.5b docker/ollama`: the model is served right after the container
  starts, `OLLAMA_KEEP_ALIVE=-1`. The small model only proves the Dockerfile; the release builds 7B and 30B.

## Acceptance criteria

| ID | Status | Evidence |
|---|---|---|
| AC1 | met | `./gradlew build` exit `0` |
| AC2 | met | `UpdateSource.kt`, `GitHost.kt` in `core`; no `project(` in `core/build.gradle.kts`; no `renovate` or `github` in `core/src/main` |
| AC3 | met | `settings.gradle.kts`; `sources` and `git` depend on `:core` only; `cli` on both |
| AC4, AC5 | met | `RenovateSourceTest` on captured bodies: `monorepo-group.md`, `maven-group.md` (13 rows), release notes of `single-update.md` |
| AC6 | met | `RenovateSourceTest` (digest, pin digest, pin, lock file maintenance); `PrPipelineTest` "a PR without a version change is skipped" |
| AC7 | met | `PrPipelineTest`: author, fork, branch, own head commit, each without a comment |
| AC8 | met | the author comes from the event's `pull_request.user.login`; no `GITHUB_ACTOR` or `github.actor` in the sources or the setup guide |
| AC9–AC11, AC13 | met | `PrPipelineTest`: green stop, grouped and ordered recipes, push of recipe and fix commits, what is left; exit codes in `PrCommandTest` |
| AC12 | met | `GitHubHostTest`: no force in the push command, a moved branch is a rejected push against a real bare remote; `PrPipelineTest`: no comment after it |
| AC14 | met | `JdkSelectionTest` |
| AC15 | met | `PrPipelineTest` and `PrFlowTest` on a shallow clone; `GitHubHostTest` on the fetch command |
| AC16 | met | JDK `HttpClient` in `GitHubHost`; no GitHub client, JGit, or `gh` in the catalog, build files, or Dockerfile |
| AC17 | met | `PrPipelineTest`, `PrCommandTest`: commits by the bot identity |
| AC18 | met | `GitHubHostTest`: created once, then updated by its marker, also on a later page |
| AC19 | met | `PrCommentTest` |
| AC20 | met | `PrPipelineTest`: label only after a push |
| AC21 | met | no merge, review, or approve endpoint in `git` |
| AC22–AC24 | met | `docker/lector/Dockerfile`, `docker/ollama/Dockerfile`, `.github/workflows/release.yml` |
| AC25 | can't verify yet | needs a release tag; the release is blocked by the MSAL check (AC32) |
| AC26, AC27 | met | `action/action.yml`: `using: docker` on `ghcr.io/danvan86/lector:0.1.0`; no private key or mode input |
| AC28 | met | no config file lookup in the sources |
| AC29, AC30 | met | [`docs/setup.md`](../../../docs/setup.md) |
| AC31 | met | `pull_request_target` only in concept 8.1's warning and in the specs |
| AC32 | not met | `docs/licensing.md` exists and lists the model and image licenses, but the MSAL check is open: Moderne hasn't been asked and there is no legal review |
| AC33 | met | `RenovateSourceTest`, `PrPipelineTest`, `GitHubHostTest`, `JdkSelectionTest`, `PrCommentTest` |
| AC34 | can't verify yet | needs a test repository on GitHub with Renovate, a GitHub App, and published images |
| AC35 | met | only `RenovateSource` and `GitHubHost`; `BranchPolicy` has only `IN_PLACE` |
| AC36 | met | `ProcessesTest`: `env` started with a token, `GITHUB_TOKEN`, and `INPUT_*` sees none of them |
| AC37 | met | `FixLoopTest`: a build past the budget is stopped, rejected, reset, `TIME_BUDGET`; `PrPipelineTest`: the earlier fix is still pushed and reported; `AttributionTest`, `MavenAdapterTest`: each run gets the time left, at most an hour |

## Judgment calls

- **`UpdateSource` returns `PackageUpdate`s, not `DependencyChange`s.** The update type comes from the ecosystem's
  version scheme, which the source doesn't know. The pipeline turns each update into a `DependencyChange` with the
  detected adapter.
- **The ecosystem comes from Renovate's datasource, not its manager.** The PR body names no manager; the merge-confidence
  badges name the datasource. A row without badges (self-hosted Renovate) belongs to the repository's ecosystem; a row
  of a datasource the table doesn't know (Docker, GitHub Actions) is left out.
- **The pipeline takes a list of sources** and uses the first that recognizes the PR, like the adapters. Only Renovate is
  in it.
- **Fetch authenticates with the token too.** The spec names only the push, but a private repository's base commit
  needs it. The token reaches `git` through `GIT_CONFIG_*` environment variables, which also drop any header
  `actions/checkout` left behind.
- **`cancel-in-progress` in the example workflow is an expression**: a push by the bot must not cancel the run that is
  about to post its comment.
- **Exit codes of `pr`:** `0` for a skipped run, a green PR, and a rejected push (the new head gets its own run); `1`
  when the update left something after a run that worked on the PR; `2` for a tool error.
- **The real-model test reads the Ollama version from `docker/ollama/Dockerfile`**, which Dependabot updates. The catalog
  entry `ollama-image`, which nothing updated, is gone.
- **The release workflow requires `**MSAL check:** done`** in `docs/licensing.md`, not only the file: the file exists,
  the check doesn't. For the same reason, "Legal check" stays in M5 in `docs/features.md`.

- **AC37's deadline is a port parameter.** `EcosystemAdapter.verify` and `runTests` take a `timeout`, which `attribute`
  fills with the time left before each run. A `ProcessTimedOut` (a `ToolError`) while time is left is still a tool
  error: then it was the adapter's own one-hour limit.
- **The M3 review's D2** (reject an attempt that removes the PR's own package) is assigned to M4's spec (AC31) and is not
  in `main`'s code. It's not in M5's spec, so M5 doesn't add it.

## Open

- **AC32, release blocker:** the MSAL check needs Moderne's written answer and a legal review. Record them in
  `docs/licensing.md` and set its status line to `done`.
- **AC34, the M5 "done when":** a test repository on GitHub with Renovate, a GitHub App, and the documented workflow,
  after the first release. CI on GitHub hasn't run this branch yet either.
- **AC25 and the 30B image:** GHCR takes layers up to 10 GB, and the `qwen3-coder:30b` weights are one file of about
  19 GB. The release workflow builds it in its own job so the 7B image doesn't depend on it; expect that job to fail on
  push. Then record the error and reopen Q1/Q13, as the spec says.
- **Token isolation:** builds don't see the token in their environment, but they run as the same user in the same
  container as the tool, so a malicious build plugin could read it from `/proc`. Documented as a limitation in the setup
  guide and concept 8.2; a separate user or container for builds would close it.
- The NVIDIA CUDA libraries in the `ollama/ollama` base image are not in the license check yet.
