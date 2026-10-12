# M5 – Renovate in CI (first end-to-end release) · Brainstorming

Milestone: [M5 in features.md](../../../docs/features.md#m5--renovate-in-ci-first-end-to-end-release-) · Status: **Closed** (all questions decided)

**How to answer:** the recommended option is preselected with `[x]`. Move the `x` to choose another one. Write anything else under **Notes**.

## Already fixed by the project docs and earlier milestones

- Trigger on `pull_request`, never `pull_request_target`. Check PR author **and** same-repo head, not `github.actor`.
- GitHub App token (pushes with `GITHUB_TOKEN` don't trigger checks). Prevent the bot from re-triggering itself.
- Separate commits: bot bump, recipe output, one per AI fix. `ai-assisted` label, never auto-merged.
- Partial result: the recipe commit is still pushed, remaining diagnostics go into a comment.
- Renovate: in-place commits, documented `gitIgnoredAuthors` setup.
- Ollama as a network-isolated sidecar, model weights baked into the image. Everything the run needs is in the container image.
- From M2: one `DependencyChange` per run so far; downgrade rules; recipe JARs are never shipped; **MSAL legal check is a blocker before this release**.
- From M3: `heal` creates the recipe commit and one commit per accepted fix locally (identity `lector`, swapped for the GitHub App here). Default model `qwen3-coder:30b`; the real-model test uses `qwen2.5-coder:7b` on a standard GitHub-hosted runner.
- From M4: guardrails run on every attempt, so AI output may now reach a PR.

```
Renovate PR (renovate/*)
   │ pull_request
   ▼
GitHub Action (self-hosted runner)
   ├── trigger checks (author, same repo, update type, not our own commit)
   ├── RenovateSource.read(pr) ─▶ DependencyChange[] + release notes
   ├── migrate + heal (M2–M4) ─▶ local commits
   └── GitHost: push, sticky comment, label
```

---

## Round 1

### Q1 · Container layout

- [x] **A · Two images: our tool image + an Ollama image with weights baked in, as a job `services:` sidecar** — *recommended* (concept). Model and tool are updated independently, and the 20 GB model layer isn't re-pulled for every tool release.
- [ ] **B · One image with tool, toolchains, Ollama, and weights; Ollama started by the entrypoint** — Simplest for users (one image), but huge, and every tool release ships the model again.

**Notes:** A

### Q2 · GitHub Action type

- [x] **A · Docker container action (`runs.using: docker`) pointing at our image** — *recommended.* The whole run happens inside our image, as rule 6 requires. The user's workflow adds the Ollama service.
- [ ] **B · Composite action that calls `docker run`** — More control over mounts and GPU flags, more YAML to maintain.
- [ ] **C · Reusable workflow (`workflow_call`)** — Users call one workflow that defines runner, services, and permissions for them. Very convenient, less flexible for runner labels.

**Notes:** A

### Q3 · Toolchains for target repos

Target repos need their JDK and Maven version.

- [x] **A · Image contains several JDKs (17, 21, 25) and Maven. The adapter picks the JDK from `maven.compiler.release` / `<java.version>`, default 21** — *recommended.* No downloads, covers the vast majority of projects.
- [ ] **B · Only one JDK (21)** — Smaller image, breaks projects that require 17-only or 25 features.
- [ ] **C · Download JDKs on demand** — Smallest image, but network calls and slower runs.

**Notes:** A. No JDK 8 or 11 needed.

### Q4 · Reading the Renovate PR

- [x] **A · Parse the PR body's update table plus the `renovate/` branch name** — *recommended.* Zero configuration for users. Release notes come from the same body. Parser tested against captured PR bodies.
- [ ] **B · Renovate `postUpgradeTasks` writes a JSON file into the branch** — Structured, but needs Renovate config changes and a self-hosted Renovate.
- [ ] **C · Manifest diff (fallback adapter)** — Independent of Renovate's format, but that's M8, and it loses release notes.

**Notes:** A

### Q5 · When the tool runs

- [ ] **A · Only for major/minor updates** — *recommended* (concept). Patch updates are skipped. Simple condition based on the PR data.
- [x] **B · Always, exit early if verify is green** — Catches broken patch updates too, but costs one full build per PR.
- [ ] **C · Major/minor always, patch only when the PR's CI failed (`workflow_run`)** — Most precise, but a second workflow and more trigger logic.

**Notes:** B

### Q6 · Git operations

- [x] **A · `git` CLI in the image** — *recommended.* What every CI already does. Handles auth via the token in the remote URL.
- [ ] **B · JGit** — Pure Java, but behaves differently from `git` in edge cases (hooks, LFS, config).

**Notes:** A

### Q7 · Commit signing

Regulated environments often require signed commits on protected branches.

- [x] **A · Push with `git` (unsigned), document the limitation** — *recommended for M5.* Simplest. Renovate branches are usually not protected.
- [ ] **B · Create commits via the GitHub API with the App token** — GitHub signs them, so they show as "Verified". More API code.
- [ ] **C · GPG/SSH signing key provided as a secret** — Works with `git` push, but users must manage another key.

**Notes:** A

### Q8 · GitHub API client

- [x] **A · Plain `HttpClient` + kotlinx.serialization against the REST endpoints we need** — *recommended.* Only a handful of calls (comment, label, PR info). No new dependency.
- [ ] **B · `org.kohsuke:github-api`** — Full API coverage, but a big dependency for five calls.
- [ ] **C · `gh` CLI in the image** — Easy to script, but output parsing and another binary.

**Notes:** A

### Q9 · GitHub App token

- [x] **A · The user's workflow creates it with `actions/create-github-app-token` and passes it in** — *recommended.* Standard pattern, our tool never sees the private key.
- [ ] **B · Our action takes `app-id` and `private-key` and mints the token itself** — One less step for users, but we handle a private key.

**Notes:** A

### Q10 · Reruns and loops

Renovate rebases its branch, and our own push triggers `pull_request` again.

- [x] **A · Skip if the head commit is authored by our bot; one concurrency group per PR that cancels older runs** — *recommended.* Prevents loops and wasted runs. A Renovate rebase (new head by Renovate) runs the tool again from scratch.
- [ ] **B · Store the processed head SHA in a PR comment and skip known SHAs** — More precise, more state to manage.

**Notes:** A

### Q11 · Report location

- [x] **A · One sticky PR comment, updated on every run** — *recommended.* The PR stays readable. Each AI commit message also carries the error and a one-line rationale.
- [ ] **B · A new comment per run** — Full history, noisy.
- [ ] **C · Check run with summary** — Nice UI, but less visible than a comment and more API work.

**Notes:** A

### Q12 · Configuration

- [x] **A · Action inputs only (model, budgets, mode); no config file in the target repo** — *recommended.* One place, versioned in the workflow file.
- [ ] **B · Optional config file in the target repo (e.g. `.lector.yml`)** — Per-repo overrides, but a second place to look and a format to maintain.

**Notes:** A

### Q13 · Image registry and base image

- [x] **A · GitHub Container Registry, `eclipse-temurin` base** — *recommended.* Next to the code, free for public images. Temurin is the common OpenJDK build.
- [ ] **B · Your own internal registry** — Needed for air-gapped customers. Can be added as a mirror later.

**Notes:** A

---

## Round 2

Q5 = B and the earlier milestones leave four points open.

### Q14 · Recipe when the build is already green

Q5 = B: verify runs first and the tool stops if it's green. A green major update (e.g. Spring Boot 3.3 → 3.4) would then never get its recipe, which also migrates deprecated APIs and properties.

- [x] **A · Green means stop, for every update type. No recipe** — *recommended.* Matches Q5 as answered. The PR stays exactly what the bot proposed, and the tool only acts when something is broken.
- [ ] **B · Major/minor: run the recipe even when green; patch: stop when green** — Keeps the recipe's modernisation, but adds commits to PRs that didn't need fixing.

**Notes:** A (answered in chat)

### Q15 · Grouped Renovate PRs

Renovate groups monorepo packages by default (e.g. `spring-boot-starter-parent` and `spring-boot-maven-plugin` in one PR). M2 handles one change per run.

- [x] **A · Read every change from the PR. Apply the downgrade rules per change, look up a recipe per change, run each distinct recipe once (in the PR table's order), one commit per recipe. One fix loop over everything** — *recommended.* Monorepo groups usually map to the same recipe, so this is mostly one recipe. No PR is skipped because it's grouped.
- [ ] **B · Only the change with the biggest update type gets a recipe; the fix loop handles the rest** — Simpler, misses recipes for other groups in the same PR.
- [ ] **C · Skip grouped PRs** — Simplest, but skips Renovate's default for monorepos.

**Notes:** A (answered in chat)

### Q16 · What is pushed when the build is not fully green

Concept 8.3: on failure, the recipe commit is still pushed and the remaining errors are commented.

- [x] **A · Push the recipe commit and every accepted fix commit, and list the remaining diagnostics in the comment** — *recommended.* Accepted fixes passed the guardrails and made measurable progress, so the reviewer gets the closest-to-green state.
- [ ] **B · Push only the recipe commit unless the build is fully green** — Concept's wording, more conservative. Throws away guarded progress.

**Notes:** A (answered in chat)

### Q17 · Where the M5 end-to-end run happens

"Done when" needs a real Renovate PR in a test repo, fixed with no manual steps. Standard GitHub-hosted runners have about 16 GB RAM and 14 GB disk, CPU only, so an Ollama image with `qwen3-coder:30b` (about 20 GB) doesn't fit.

- [x] **A · Test repo on a standard GitHub-hosted runner with an Ollama image variant carrying `qwen2.5-coder:7b`. Both image variants (7B and 30B) are built and published** — *recommended.* No new infrastructure, same as M3's real-model test. The 30B variant is validated in M6 on proper hardware.
- [ ] **B · A self-hosted runner with a GPU and the 30B image** — Tests what production runs. Only possible with your own hardware.

**Notes:** A (answered in chat)

---

## Decisions

### Round 1

| # | Topic | Decision |
|---|---|---|
| Q1 | Container layout | Two images: tool image + Ollama image with weights baked in, as a job `services:` sidecar |
| Q2 | Action type | Docker container action (`runs.using: docker`) |
| Q3 | Toolchains | JDKs 17, 21, 25 and Maven in the image; JDK picked from `maven.compiler.release` / `<java.version>`, default 21. No JDK 8 or 11 |
| Q4 | Reading the PR | Parse the PR body's update table plus the `renovate/` branch name; release notes from the body |
| Q5 | When the tool runs | Always; exit early if verify is green (changes concept 8.1) |
| Q6 | Git | `git` CLI in the image |
| Q7 | Commit signing | Unsigned push, limitation documented |
| Q8 | GitHub API client | Plain `HttpClient` + kotlinx.serialization |
| Q9 | App token | Created by the user's workflow (`actions/create-github-app-token`) and passed in |
| Q10 | Reruns and loops | Skip if the head commit is authored by our bot; one concurrency group per PR, cancel older runs |
| Q11 | Report location | One sticky PR comment, updated on every run |
| Q12 | Configuration | Action inputs only |
| Q13 | Registry and base image | GHCR, `eclipse-temurin` base |

### Round 2

| # | Topic | Decision |
|---|---|---|
| Q14 | Green build | Green means stop for every update type; no recipe |
| Q15 | Grouped PRs | Every change is read; downgrade rules and recipe lookup per change; each distinct recipe runs once (PR table order), one commit each; one fix loop over everything |
| Q16 | Partial result | Push the recipe commit(s) and every accepted fix commit; remaining diagnostics in the comment |
| Q17 | End-to-end run | Test repo on a standard GitHub-hosted runner with the `qwen2.5-coder:7b` Ollama image; both variants (7B, 30B) are built and published |
