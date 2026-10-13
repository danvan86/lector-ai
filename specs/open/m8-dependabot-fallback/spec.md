# M8 – Dependabot and fallback · Spec

Milestone: [M8 in features.md](../../../docs/features.md#m8--dependabot-and-fallback-) · Decisions: [brainstorming.md](brainstorming.md) · Status: **Ready to implement**

## Goal

Dependabot PRs and labelled manual dependency PRs go through the same pipeline as Renovate PRs. By default, the result arrives as a stacked companion PR `heal/<branch>` instead of commits on the source branch.

**Why:** the tool should not care who opened the PR (concept section 3). Dependabot is GitHub's built-in updater and the most common alternative to Renovate. The fallback covers everything else: manual bumps, Snyk, future bots. Dependabot doesn't tolerate foreign commits on its branches, so it needs a second branch policy, the companion PR (concept 3.1).

## What to build

### 1. Dependabot source adapter in `sources`

- Recognises a PR by the author `dependabot[bot]` and the branch prefix `dependabot/`.
- Reads every change from Dependabot's `updated-dependencies-json`. The user's workflow produces it with `dependabot/fetch-metadata` and passes it to our action, which hands it to the CLI (`--dependabot-metadata <file>`). The metadata missing on a Dependabot PR is a tool error (exit `2`) with a message pointing to the setup guide.
- `package-ecosystem` maps to an ecosystem with a lookup table: `maven` → `maven`, `gradle` → `maven`. Rows from other ecosystems produce no `DependencyChange`. A PR with no changes is skipped (M5 rule).
- Gradle plugin entries map to marker coordinates `<id>:<id>.gradle.plugin` (M7). Check how Dependabot names plugins on captured metadata.
- `updateType` comes from the adapter's `versionScheme` (M2), not from Dependabot.
- Release notes come from the PR body (Dependabot's release notes and changelog sections), read through `GitHost`.
- A grouped update is one run for the whole group, as in M5: each distinct recipe once, then one fix loop.
- Default branch policy: companion PR.

**Why:** `fetch-metadata` is official and structured, and it covers grouped updates (Q1). Parsing the PR body for release notes is the same approach as the Renovate adapter, with no extra network calls (Q12). Grouped dependencies usually belong together, so their errors are related (Q2, M5 Q15). Dependabot stops rebasing after foreign commits, so the companion PR is its default (concept 3.1).

### 2. Fallback source in `sources`

- Runs only on same-repo PRs that carry the opt-in label (default `lector`, configurable).
- Determines the changes by comparing the adapter's **resolved versions** (M2 for Maven, M7 for Gradle) on the base commit and on the head. The base is read in a separate worktree, as in M2.
- **Every package whose resolved version differs** becomes a `DependencyChange`, transitive ones included. The report shows the count and the first few, with the full list in the JSON output.
- If the resolved versions can't be read on either side, nothing runs. The reason goes into the sticky comment, and the exit code is `2`.
- No release notes.
- Default branch policy: companion PR.

**Why:** resolved versions catch changes made through properties, BOM imports, parents, catalogs, and plugins. The capability already exists for both build tools, so no new `manifestDiff` capability is needed (Q5). Listing every difference needs no "declared vs. transitive" data. Recipe lookup already runs each distinct recipe once, and changes that aren't upgrades are skipped by M2 rule 1 (Q10). The label keeps the tool off feature PRs nobody asked it to touch, and only people with write access can add labels (Q11). Unreadable versions mean the tool can't know what changed, so it doesn't act, the same as M2's downgrade rules. Release notes for arbitrary packages are left for later (Q6).

### 3. Source selection

- Automatic, in this order: Renovate (author and `renovate/` prefix), Dependabot (author and `dependabot/` prefix), fallback (opt-in label). Otherwise skip with exit `0` and no comment.
- An optional `source` input (`renovate`, `dependabot`, `fallback`) overrides the automatic choice. The chosen source's own trigger checks still apply.
- For every source: the head repository is the base repository, and the head commit is not authored by our bot identity (M5).
- The allowed bot logins default to `renovate[bot]` and `dependabot[bot]`.

**Why:** works without configuration, with an override for unusual setups (Q7). The same-repo and own-commit checks are the security and loop-prevention rules from concept 8.1 and 8.2, and they hold for every source.

### 4. Branch policy and companion PRs

- Action input `mode`: `in-place` or `companion`. If it is empty, the source's default applies: Renovate in-place, Dependabot and fallback companion.
- **In-place** is M5's behaviour, unchanged.
- **Companion:**
  - The tool creates or updates the branch `heal/<source-branch>` from the source PR's head, with the recipe commits and the accepted fix commits on top.
  - It opens one PR from `heal/<source-branch>` into the source branch (stacked), or updates the existing one.
  - When the source PR gets a new head, the companion branch is rebuilt from that head and force-pushed. The companion PR stays the same.
  - The companion PR is only created when there is at least one commit. A run without commits only updates the sticky comment.
- **Force-push** only ever targets `heal/*`. Source branches are never force-pushed.
- **Cleanup:** the tool closes the companion PR and deletes `heal/<source-branch>` when the source PR is closed or merged, and when a new run finds the source PR green.
- `GitHost` grows by what this needs: create or update a PR, close a PR, delete a branch.

**Why:** a stacked companion PR leaves Dependabot's PR in Dependabot's hands and keeps our changes separate (Q3). Force-pushing the same companion PR keeps review discussion in one place, instead of closing and reopening it as the concept wording said (Q4). A superseding Dependabot PR has a new branch name, so it gets its own companion, and the old one is closed with the old source PR (Q14). The input fits M5's "configuration through inputs only" (Q9).

### 5. Report

- The sticky comment stays on the **source PR**, as in M5. In companion mode it links the companion PR.
- The companion PR's description has the same summary and links back to the source PR.
- The `ai-assisted` label goes on the PR that carries the AI commits: the companion PR in companion mode, the source PR in place.
- When the tool closes a companion PR, the sticky comment says why (source PR closed, merged, or green).

**Why:** reviewers find the result where the update is. The label sits where the AI commits are, so it stays filterable (Q13).

### 6. Action and workflow

- New action inputs: `dependabot-metadata`, `mode`, `source`, `fallback-label`. The `bot-logins` default changes to `renovate[bot]` and `dependabot[bot]`.
- **Setup guide:**
  - The example workflow listens to `pull_request` with `opened`, `synchronize`, `reopened`, `labeled`, and `closed`.
  - It runs `dependabot/fetch-metadata` only for Dependabot PRs and passes `updated-dependencies-json` to our action.
  - The GitHub App's ID and key are stored as Actions secrets **and** Dependabot secrets.
  - It explains companion PRs: merge the companion PR first, then the source PR.
  - It explains in-place mode for Dependabot: Dependabot stops rebasing after our commits.
  - It explains the opt-in label for manual PRs.
  - The limitations drop "Renovate only".

**Why:** workflows triggered by Dependabot only see Dependabot secrets and a read-only `GITHUB_TOKEN` (concept 3.1). One `pull_request` workflow keeps the M5 security model (Q8). `closed` and `labeled` are needed for cleanup (Q14) and for the opt-in (Q11).

### 7. Tests and end-to-end run

- **Dependabot parser tests** on captured `updated-dependencies-json`: single update, grouped update, Gradle plugin entry, unsupported ecosystem. Release notes extraction on a captured PR body.
- **Fallback tests** with a fake adapter: changed, added, and removed packages, transitive changes included, unreadable versions.
- **Source selection tests:** each source, the label, the override, fork and own-commit skips.
- **Pipeline tests** with fakes for companion mode: first run creates the branch and the PR; a new source head force-pushes `heal/*` and keeps the PR; a green run closes the companion PR and deletes the branch; source closed closes the companion PR; a run without commits creates no companion PR; the label goes on the companion PR.
- **GitHub adapter tests** against a local fake HTTP server: create, update, and close a PR, delete a branch.
- **End-to-end** on a GitHub test repository with the documented workflow:
  - a real Dependabot PR that breaks the build gets a companion PR with a fix commit, and a sticky comment on the Dependabot PR;
  - a manual dependency PR with the opt-in label gets the same.
- **Benchmark:** the M6 harness passes no release notes to the fix loop, because Dependabot PRs had none in the product ([M6 review](../m6-benchmark/review.md) F4). With the Dependabot source, store each historical case's notes with the corpus and pass them in, and drop the report's "no release notes" line.

**Why:** fakes cover the lifecycle branches quickly. The end-to-end runs are the M8 "done when": Dependabot and manual PRs are fixed the same way as Renovate PRs.

## What not to do

| Don't | Why |
|---|---|
| Parse the Dependabot PR title or body for the changes | Decided against (Q1): `fetch-metadata` is the source. The body is only for release notes. |
| Run one pass per dependency of a group | Decided against (Q2). |
| Let the companion PR target the default branch, or close the Dependabot PR | Decided against (Q3). |
| Close and reopen the companion PR when the source changes | Decided against (Q4): force-push and keep it. |
| Force-push any branch other than `heal/*` | Never overwrite a bot's or a person's work (M5). |
| Add a `manifestDiff` capability, or diff manifest text | Decided against (Q5): resolved versions from M2/M7. |
| Filter fallback changes to declared dependencies, or keep only the largest one | Decided against (Q10). |
| Fetch release notes from registries or GitHub releases | Decided against (Q6). |
| Run the fallback on every PR, or on unlabelled PRs | Decided against (Q11). |
| Use `pull_request_target` or a `workflow_run` split | Concept 8.1, Q8. |
| Read a config file from the target repository | Decided against (Q9, M5 Q12). |
| Post the report only on the companion PR | Decided against (Q13). |
| Leave companion PRs open after their source PR is gone or green | Decided against (Q14). |
| Use Dependabot's `update-type` | `updateType` comes from the adapter's version scheme (M2). |
| Auto-merge or approve the companion PR | Rule 5. |
| Support GitLab or other hosts | Concept "Later". |
| Add an ecosystem beyond the JVM | M9. |

## Acceptance criteria

The review agent checks every criterion and reports it as *met*, *not met*, or *can't verify*, with evidence.

| ID | Criterion | Covers | How to verify |
|---|---|---|---|
| AC1 | `./gradlew build` passes | all | Run it from the repo root: exit code `0` |
| AC2 | `core` has no project dependencies and no Dependabot-specific code | 1 | `core/build.gradle.kts` has no `project(`; case-insensitive search of `core/src/main` for `dependabot`: no matches |
| AC3 | The Dependabot and fallback sources live in `sources` and implement `UpdateSource` | 1, 2 | Source files in `sources` |
| AC4 | Every entry of a grouped `updated-dependencies-json` becomes a `DependencyChange` | 1 | Parser test on captured metadata |
| AC5 | `package-ecosystem` `maven` and `gradle` map to `maven`; other ecosystems produce no `DependencyChange` | 1 | Parser test |
| AC6 | A Gradle plugin entry maps to `<id>:<id>.gradle.plugin` | 1 | Parser test on captured metadata |
| AC7 | `updateType` is computed by the adapter's version scheme, not read from Dependabot | 1, Don't | Parser test with a mismatching Dependabot `update-type`; no use of the field in `sources` |
| AC8 | Release notes are extracted from a captured Dependabot PR body | 1 | Parser test |
| AC9 | A Dependabot PR without metadata → exit `2` with a message naming the setup step | 1 | Pipeline or CLI test |
| AC10 | The fallback turns every resolved-version difference between base and head, transitive ones included, into a `DependencyChange` | 2 | Fallback test with a fake adapter |
| AC11 | The fallback reads base versions in a separate worktree that is removed afterwards | 2 | Integration test: `git worktree list` shows only the main worktree after the run |
| AC12 | Unreadable resolved versions → no recipe, no fix loop, reason in the comment, exit `2` | 2 | Pipeline test |
| AC13 | No `manifestDiff` capability exists on `EcosystemAdapter` | 2, Don't | Interface definition in `core` |
| AC14 | Source selection picks Renovate, Dependabot, or fallback as in section 3, and skips other PRs with exit `0` and no comment | 3 | Source selection tests |
| AC15 | A same-repo PR without the opt-in label and without a bot author is skipped | 3, Don't | Source selection test |
| AC16 | The `source` override selects the adapter, and the same-repo check still rejects a fork | 3 | Source selection test |
| AC17 | A PR whose head commit is by our bot identity is skipped for every source | 3 | Source selection tests |
| AC18 | With `mode` empty, Renovate runs in place and Dependabot and fallback run in companion mode; a set `mode` overrides the default | 4 | Pipeline tests |
| AC19 | Companion mode creates `heal/<source-branch>` from the source head with the recipe and fix commits, and a PR into the source branch | 4 | Pipeline test with fakes |
| AC20 | A new source head rebuilds and force-pushes `heal/<source-branch>` and keeps the same companion PR | 4 | Pipeline test with fakes |
| AC21 | No git push to a branch outside `heal/*` uses force | 4, Don't | Unit test on the git commands |
| AC22 | A run without commits creates no companion PR | 4 | Pipeline test |
| AC23 | Source PR closed or merged → companion PR closed and `heal/<source-branch>` deleted | 4 | Pipeline test with fakes |
| AC24 | A green run closes an existing companion PR and deletes its branch | 4 | Pipeline test with fakes |
| AC25 | The sticky comment is on the source PR and links the companion PR | 5 | Pipeline test on the rendered comment |
| AC26 | The companion PR description contains the summary and links the source PR | 5 | Pipeline test |
| AC27 | `ai-assisted` goes on the companion PR in companion mode and on the source PR in place | 5 | Pipeline tests |
| AC28 | The GitHub adapter can create, update, and close a PR and delete a branch | 4 | Adapter tests against a fake server |
| AC29 | `action/action.yml` has the inputs `dependabot-metadata`, `mode`, `source`, `fallback-label`, and `bot-logins` defaults to both bots | 6 | File content |
| AC30 | The setup guide's workflow listens to `opened`, `synchronize`, `reopened`, `labeled`, `closed` and runs `fetch-metadata` only for Dependabot PRs | 6 | Docs content |
| AC31 | The setup guide covers Dependabot secrets, companion merge order, in-place mode for Dependabot, and the opt-in label | 6 | Docs content |
| AC32 | No file in the repo uses `pull_request_target` or `workflow_run` for the tool | 6, Don't | `grep -r` for both: no matches in workflows and docs examples |
| AC33 | No code path merges or approves a PR | Don't | Source of `git`: no merge or review endpoints |
| AC34 | A real Dependabot PR got a companion PR with a fix commit and a sticky comment, with no manual steps | 7 | Outside the repo: link recorded in `docs/features.md` when M8 is set to ✅. Can't verify from the repo alone |
| AC35 | A real labelled manual dependency PR got the same | 7 | Outside the repo: link recorded in `docs/features.md`. Can't verify from the repo alone |

## Docs to update

- `docs/features.md`: set M8 to ✅ when done, with links to both end-to-end PRs.
- `docs/concept.md` section 3: the fallback runs on labelled PRs, detects changes from resolved versions, and has no release notes. Dependabot's companion PR is force-pushed and kept, not closed and regenerated.
- `docs/concept.md` section 5: remove `manifestDiff` from the interface outline. The fallback uses resolved versions.
- `docs/concept.md` section 8.1: add the opt-in label as a trigger next to the bot author check.
- Setup guide: as in section 6.
