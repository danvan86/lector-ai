# M5 – Renovate in CI (first end-to-end release) · Spec

Milestone: [M5 in features.md](../../../docs/features.md#m5--renovate-in-ci-first-end-to-end-release-) · Decisions: [brainstorming.md](brainstorming.md) · Status: **Ready to implement**

## Goal

A Renovate PR in a GitHub repository is checked and, if the update broke something, fixed automatically in the pipeline. The fix commits land on the Renovate branch and a sticky comment reports what happened. Nobody does anything by hand.

**Why:** this is the product (concept section 1). Everything before M5 runs from a CLI. M5 wires it into the place where dependency PRs actually show up, and it is the first release users can install. Guardrails exist since M4, so AI output may now reach a PR.

## What to build

### 1. Renovate source adapter in a new module `sources`

- `UpdateSource` port in `core`: turns a PR into `DependencyChange`s, release notes, and a branch policy.
- The Renovate adapter recognises a PR by the `renovate/` branch prefix and reads **every** change from the PR body's update table. Release notes come from the same body.
- Renovate manager names map to ecosystems with a lookup table (`maven` → `maven`, the package namespace from M2 Q6).
- Rows without a version change (digest, pin, lock-file maintenance) produce no `DependencyChange`. A PR with no changes is skipped.
- Branch policy: in-place commits.

**Why:** zero configuration for users (Q4). Renovate groups monorepo packages by default, so a PR with several changes is the normal case, not an edge case (Q15). Concept section 3 defines the port and the lookup table.

### 2. Pipeline for one PR

New CLI command `lector pr`, which the action calls. Flow:

1. **Trigger checks** (also in the tool, not only in the workflow): PR author is one of the configured bot logins (default `renovate[bot]`), the head repository is the base repository, the branch starts with `renovate/`, the head commit is not authored by our bot identity. Any check failing → skip, exit `0`, no comment.
2. **Checkout state:** the working tree is the PR head. The base commit is fetched if it's missing (shallow clone).
3. **JDK:** the adapter picks the smallest installed JDK (17, 21, 25) that is at least the project's `maven.compiler.release` / `<java.version>`. Default 21. Below 17 → 17. Above 25 → tool error (exit `2`).
4. **Verify first** (M1, with the PR's base SHA). Nothing caused by the update → stop, exit `0`. No recipe, for any update type.
5. **Otherwise:** M2's downgrade rules per change. Recipe lookup per change. Each distinct recipe runs once, in the order of the PR table, and becomes one commit. Then verify, then the fix loop (M3 with M4 guardrails) over all remaining diagnostics.
6. **Push** the recipe commits and every accepted fix commit to the Renovate branch with plain `git push` (never force). If the push is rejected because the branch moved, nothing is pushed and the run ends. The new head triggers a new run.
7. **Report:** the sticky comment and the `ai-assisted` label (section 4).
8. **Exit code** from the final verification: `0` nothing caused by the update left, `1` something left (the check turns red), `2` tool error. After a push, the run on the tool's own commit skips with `0` (step 1), so the PR's latest commit shows a green check; the comment and the repository's own CI show what is left ([review](review.md) F4, decided: keep the skip, document it).

**Time budget:** the fix loop gives each build and test run of an attempt the remaining time as its deadline. A run that hits it is stopped, the attempt is rejected and reset, and the loop ends with stop reason `TIME_BUDGET`. The report and the push of earlier accepted fixes happen as usual.

**Why:**
- Checking author **and** same repo, not `github.actor`, is the concept's security rule (section 8.1). Checking in the tool too means a misconfigured workflow can't run it on a fork.
- Verify first catches broken patch updates, and a green PR costs one build and nothing else (Q5 = B). Green means stop, so a PR the update didn't break stays exactly what the bot proposed (Q14).
- Each distinct recipe once, because monorepo groups usually map to the same recipe (Q15).
- Accepted fixes passed the guardrails and made measurable progress, so they are pushed even when the build isn't fully green (Q16).
- Never force-pushing means we never overwrite a Renovate rebase.
- Until M5 the time limit is only checked between attempts, so one attempt with a slow build can run past it and the CI job timeout kills the run without a report ([M3 review](../../done/m3-fix-loop/review.md) D1).
- A newer JDK compiles older release levels, so 17, 21, and 25 cover the projects we need. JDK 8 and 11 aren't needed (Q3).

### 3. GitHub host adapter in a new module `git`

- `GitHost` port in `core`: PR data, comment upsert, label, push.
- GitHub adapter: REST calls with `java.net.http.HttpClient` and kotlinx.serialization. No GitHub client library.
- Git operations use the `git` CLI. The token is used for the push.
- Commits use the GitHub App's bot identity (name and email from action inputs) instead of M3's `lector`. Commits are unsigned.
- **Scrubbed environment for build tools:** every build and test process (Maven, `javap`, and later adapters' tools) gets an allowlisted environment (`PATH`, `HOME`, `JAVA_HOME`, `CI`, proxy and registry variables, …). It never contains the token, `GITHUB_TOKEN`, or the action inputs (`INPUT_*`). Only the `git` push and the GitHub adapter see the token.

**Why:** a handful of REST calls doesn't justify a big dependency (Q8). The `git` CLI behaves like every other CI (Q6). Unsigned commits are the simplest option, and Renovate branches are usually not protected. The limitation is documented (Q7). Build plugins and tests run code from the updated dependency, so they must not be able to read the push token (added from M9 Q14).

### 4. Report on the PR

- **One sticky comment**, found by a hidden marker and updated on every run. It contains:
  - what the tool did: recipes, accepted fixes with their commits and rationales;
  - remaining diagnostics caused by the update ("left to fix");
  - pre-existing, flaky, and unattributed failures, listed separately;
  - guardrail rejections per rule.
- When a run stops because the build is green, an existing sticky comment is updated to say so. No new comment is created.
- The `ai-assisted` label is added when at least one commit was pushed.
- Never auto-merge, never approve.
- Every AI commit message carries the addressed error and a one-line rationale (M3).

**Why:** one comment keeps the PR readable (Q11). Reviewers must see what is left and what isn't the update's fault (concept section 5). The label makes AI-touched PRs filterable. Rule 5: never auto-merge.

### 5. Container images

- **Tool image:** `eclipse-temurin` base with JDKs 17, 21, 25, Maven, `git`, and the `lector` distribution. The tool itself runs on JDK 25. No recipe JARs and no model weights in it.
- **Ollama image:** based on the official Ollama image, with the model weights baked in at build time. The model is a build argument. Two variants: `qwen2.5-coder:7b` and `qwen3-coder:30b`.
- Both are published to **GHCR** by a release workflow when a version tag is pushed. The image tags match the tool version, and the Ollama tags name the model.
- Risk: the 30B weights are one file of about 18 GB. If GHCR rejects the layer, publish the 7B variant, record the error, and reopen Q1/Q13 rather than working around it.

**Why:** two images let the tool and the model be updated independently, and the large model layer isn't pulled again for every tool release (Q1). Everything the run needs is in the images, so nothing is downloaded per job except Maven dependencies (rule 6, concept 8.4). Recipe JARs stay out of the image because of the license (M2 Q2). GHCR sits next to the code (Q13). The 7B variant is what the end-to-end test runs on (Q17).

### 6. GitHub Action in `action/`

- Docker container action (`runs.using: docker`) pointing at the tool image of the same version.
- Inputs: the GitHub App token, the bot identity (name, email), allowed bot logins (default `renovate[bot]`), Ollama URL (default: the service name of the documented sidecar), model (default `qwen2.5-coder:7b`, M6 Q13), budgets, optional Maven settings file. No config file in the target repository.
- The action never receives the App's private key. The user's workflow creates the token with `actions/create-github-app-token`.

**Why:** the whole run happens inside our image, as rule 6 requires (Q2). Configuration lives in one place, versioned in the workflow file (Q12). Not handling the private key keeps it out of our code entirely (Q9).

### 7. Documentation

A user-facing setup guide containing:

- **Example workflow:** `on: pull_request` (never `pull_request_target`), the Ollama image as a `services:` sidecar, a concurrency group per PR with `cancel-in-progress`, a token from `actions/create-github-app-token`, and our action.
- **GitHub App setup:** which permissions it needs (contents and pull requests write), how to get the bot's name and email.
- **Renovate:** add the bot email to `gitIgnoredAuthors`, otherwise Renovate stops maintaining the branch.
- **Runners:** standard GitHub-hosted runners work with the 7B variant, which is the default and the one the benchmark measures (M6). The 30B variant needs a runner with enough memory, ideally a GPU (`--gpus all` in the service options), and is marked as unmeasured.
- **Limitations:** unsigned commits, Maven only, GitHub only, Renovate only.

**Why:** without `gitIgnoredAuthors`, Renovate abandons the branch after our first commit (concept 3.1). The workflow is where the security rules (8.1) and the loop prevention (Q10) are configured, so the example has to get them right.

### 8. Release blockers

- The **MSAL legal check** (M2 Q2) is done and its outcome is recorded in `docs/licensing.md`, together with the licenses of the redistributed model weights and the base images. The check covers the distribution model and its conditions in concept section 9. It includes Moderne's written answer on that model, or a note that none came.
- No release tag is pushed before that document exists.

**Why:** `rewrite-spring` is under the Moderne Source Available License and whether lector-ai is commercial is still open (M2). The model weights are redistributed inside our image for the first time in M5. I believe both Qwen models are Apache 2.0, but that is part of the check.

### 9. Tests and end-to-end run

- **Renovate parser tests** on captured PR bodies: single update, monorepo group, digest/pin rows, release notes extraction.
- **Trigger check tests:** wrong author, fork, wrong branch prefix, head commit by our bot.
- **Pipeline tests** with fakes (`GitHost`, `LlmClient`, adapter): green → stop with no commit and no comment; grouped changes → one commit per distinct recipe; partial result → recipe and accepted fixes pushed, remaining diagnostics in the comment; push rejected → nothing else happens.
- **GitHub adapter tests** against a local fake HTTP server: comment upsert by marker, label.
- **JDK selection tests.**
- **End-to-end:** a test repository on GitHub with Renovate and the documented workflow, running on a standard GitHub-hosted runner with the 7B Ollama image. A real Renovate PR that breaks the build gets a fix commit and a sticky comment with no manual steps.

**Why:** fakes cover the branches of the pipeline quickly. The end-to-end run is the M5 "done when" and proves the security setup, the token, the sidecar, and Renovate's behaviour together.

## What not to do

| Don't | Why |
|---|---|
| Use `pull_request_target`, or gate on `github.actor` | Concept 8.1: both have been exploited. |
| Push with `GITHUB_TOKEN` | Concept 8.2: pushes with it don't trigger checks. |
| Handle the App's private key in our action | Decided against (Q9). |
| Force-push, or touch the Renovate bump commit | Never overwrite the bot's work. |
| Auto-merge, approve, or request reviewers | Rule 5. |
| Ship one combined image with tool, Ollama, and weights | Decided against (Q1). |
| Download JDKs, models, or recipe JARs into the image or per job, beyond Maven's own dependency resolution | Rule 6, Q3. Recipe JARs never ship (M2). |
| Add JDK 8 or 11 | Not needed (Q3). |
| Use a composite action or a reusable workflow | Decided against (Q2). |
| Read Renovate data from `postUpgradeTasks` or a manifest diff | Decided against (Q4). The manifest diff is M8. |
| Run the recipe on a green build | Decided against (Q14). |
| Skip grouped PRs, or only handle their biggest change | Decided against (Q15). |
| Push only the recipe commit when the build isn't fully green | Decided against (Q16). |
| Use JGit, `gh`, or a GitHub client library | Decided against (Q6, Q8). |
| Sign commits (API commits, GPG, SSH) | Decided against for M5 (Q7). Documented as a limitation. |
| Store processed SHAs in PR comments | Decided against (Q10). |
| Post a new comment per run, or use check runs for the report | Decided against (Q11). |
| Read a config file from the target repository | Decided against (Q12). |
| Add a `mode` input or companion PRs | M8. Only in-place exists in M5. |
| Support Dependabot, GitLab, or Gradle | M8, Later, M7. |
| Publish a release before `docs/licensing.md` exists | Release blocker (section 8). |

## Acceptance criteria

The review agent checks every criterion and reports it as *met*, *not met*, or *can't verify*, with evidence.

| ID | Criterion | Covers | How to verify |
|---|---|---|---|
| AC1 | `./gradlew build` passes | all | Run it from the repo root: exit code `0` |
| AC2 | `UpdateSource` and `GitHost` are ports in `core`; `core` has no project dependencies and no Renovate- or GitHub-specific code | 1, 3 | Interface definitions; `core/build.gradle.kts` has no `project(`; case-insensitive search of `core/src/main` for `renovate`, `github`: no matches |
| AC3 | Modules `sources` and `git` exist, depend on `core` only (project-wise), and `cli` depends on both | 1, 3 | `settings.gradle.kts`, module build files |
| AC4 | The Renovate parser reads every change of a monorepo-group PR body | 1 | Parser test on a captured body |
| AC5 | Release notes are extracted from the PR body | 1 | Parser test |
| AC6 | Digest, pin, and lock-file rows produce no `DependencyChange`; a PR with none is skipped with exit `0` | 1, 2 | Parser and pipeline tests |
| AC7 | Each trigger check failing (author, fork, branch prefix, head commit by our bot) skips with exit `0` and no comment | 2 | One pipeline test each |
| AC8 | The author check uses the PR author from the event, not `github.actor` | 2, Don't | Source: no use of `GITHUB_ACTOR` or `github.actor` in the trigger check |
| AC9 | A green verify stops the run: no recipe, no commit, no new comment, exit `0` | 2 | Pipeline test with fakes |
| AC10 | Grouped changes mapping to the same recipe run it once; different recipes run in PR table order, one commit each | 2 | Pipeline test with fakes |
| AC11 | With remaining diagnostics, the recipe commits and every accepted fix commit are pushed | 2 | Pipeline test with fakes |
| AC12 | The push never uses force; a rejected push ends the run without a comment update | 2, Don't | Unit test on the git command; pipeline test with a rejecting fake |
| AC13 | Exit code is `1` when diagnostics caused by the update remain after the fix loop | 2 | Pipeline test |
| AC14 | JDK selection: release 11 → 17, `java.version` 21 → 21, nothing declared → 21, release 26 → exit `2` | 2 | Unit tests |
| AC15 | A missing base commit in a shallow clone is fetched before verify | 2 | Unit test on the git commands, or end-to-end run |
| AC16 | The GitHub adapter uses the JDK `HttpClient`; no GitHub client library, JGit, or `gh` is added | 3, Don't | Version catalog, module build files, Dockerfile |
| AC17 | Commits use the bot identity from the action inputs | 3 | Pipeline test on a temp Git repo |
| AC18 | The sticky comment is created once and then updated, found by its marker | 4 | GitHub adapter test against a fake server |
| AC19 | The comment lists recipes, accepted fixes, remaining diagnostics, pre-existing/flaky/unattributed failures, and guardrail rejections per rule | 4 | Pipeline test on the rendered comment |
| AC20 | The `ai-assisted` label is added only when a commit was pushed | 4 | Pipeline tests |
| AC21 | No code path merges, approves, or requests reviews | 4, Don't | Source of `git`: no merge or review endpoints |
| AC22 | A tool Dockerfile exists: `eclipse-temurin` base, JDKs 17, 21, 25, Maven, `git`, the distribution; no model weights, no `rewrite-*` JARs | 5 | Dockerfile content |
| AC23 | An Ollama Dockerfile exists that takes the model as a build argument and bakes the weights in | 5 | Dockerfile content |
| AC24 | A release workflow builds and pushes the tool image and both Ollama variants to GHCR on a version tag | 5 | Workflow file under `.github/workflows/` |
| AC25 | The images are on GHCR with the release's version tag (7B variant at least; 30B or a recorded registry error) | 5 | Outside the repo: GHCR package page. Can't verify from the repo alone |
| AC26 | `action/action.yml` is a Docker container action pointing at the tool image | 6 | File content |
| AC27 | The action has inputs for token, bot identity, bot logins, Ollama URL, model, budgets, Maven settings, and none for a private key or a mode | 6, Don't | `action/action.yml` |
| AC28 | The tool reads no config file from the target repository | 6, Don't | Source: no config file lookup in the repo |
| AC29 | The setup guide's example workflow uses `pull_request`, an Ollama `services:` sidecar, a per-PR concurrency group with `cancel-in-progress`, and `actions/create-github-app-token` | 7 | Docs content |
| AC30 | The setup guide explains `gitIgnoredAuthors`, the App permissions, runner sizing for both variants, and the limitations | 7 | Docs content |
| AC31 | No file in the repo uses `pull_request_target` | Don't | `grep -r pull_request_target`: no matches |
| AC32 | `docs/licensing.md` exists and records the outcome of the MSAL check and the licenses of model weights and base images | 8 | File content |
| AC33 | Parser, trigger, pipeline, GitHub adapter, and JDK selection tests exist as listed in section 9 | 9 | Test sources |
| AC34 | A real Renovate PR in the test repository got a fix commit and a sticky comment with no manual steps | 9 | Outside the repo: link to the PR, recorded in `docs/features.md` when M5 is set to ✅ |
| AC35 | No Dependabot, GitLab, Gradle, or companion-PR code is added | Don't | Module list, source adapters, ports |
| AC36 | A build or test process started by the tool sees neither the token nor any `INPUT_*` variable | 3 | Unit test on the process runner: a child process started with those variables set prints its environment, and none of them appear |
| AC37 | A build or test run that exceeds the remaining time budget is stopped, the attempt is rejected and reset, the loop stops with `TIME_BUDGET`, and the report is still written | 2 | Fix-loop unit test with a slow fake adapter; pipeline test |

## Docs to update

- `docs/features.md`: set M5 to ✅ when done, with the link to the end-to-end PR. Remove "Legal check" from M5 once `docs/licensing.md` exists.
- `docs/concept.md` section 8.1: the tool runs on every bot PR and stops when verify is green (replaces "only for major/minor updates or failed builds").
- `docs/concept.md` section 8.3: grouped PRs get one commit per distinct recipe; partial results push the recipe commits **and** the accepted fix commits.
- `docs/concept.md` section 8.4: two images, two model variants, standard runners with the 7B variant.
- `docs/concept.md` section 3: the Renovate adapter reads every row of a grouped PR.
- `AGENTS.md` **Project layout**: `sources`, `git`, and `action` exist now; **Commands**: how to build the images locally.
- New: `docs/licensing.md` (section 8) and the user setup guide (section 7).
