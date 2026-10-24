# M10 – Simple setup · Brainstorming

Milestone: M10 in [features.md](../../../docs/features.md) (new) · Status: **Closed**

**How to answer:** the agent asks these questions in chat; this file has the full options and records the answers. The recommended option is preselected with `[x]`.

## Overview

Setting lector-ai up on GitHub takes five manual steps today ([setup guide](../../../docs/setup.md)): create a GitHub
App by hand, store two secrets twice (Actions and Dependabot), look up the bot's name and email with `curl`, copy an
80-line workflow with concurrency rules and trigger conditions, and add the bot's email to Renovate's
`gitIgnoredAuthors`. M10 cuts this down to: one click to create the App, a few `gh secret set` commands, and a workflow
of about ten lines that calls a **reusable workflow** shipped with lector-ai. The bot's name and email are derived from
the App token instead of being typed in. M5 (action, App token, trigger checks) and M8 (Dependabot, companion PRs,
cleanup) exist; M10 changes none of their behaviour, only how a repository gets them.

**Key concepts**

- **Reusable workflow** — a workflow with `on: workflow_call` that other repositories call with `jobs.<id>.uses: owner/repo/.github/workflows/file.yml@ref`. It runs in the caller's context (the caller's `pull_request` event).
- **The action** — `action/action.yml`, the Docker container action that runs `lector pr`. Stays as it is for users who need more control.
- **App slug** — the App's URL name. Its bot is `<slug>[bot]`, its commit email `<user id>+<slug>[bot]@users.noreply.github.com`. `actions/create-github-app-token` returns the slug next to the token.
- **Bot identity** — name and email of every commit. The tool skips a head commit whose author email is its own (concept 8.2), so the email must be exact.
- **App manifest / URL parameters** — GitHub can open the "New GitHub App" form pre-filled with name and permissions.

```
User's repository                                lector-ai repository
.github/workflows/lector-ai.yml (~10 lines)      .github/workflows/lector.yml (new, workflow_call)
  on: pull_request                                 ├─ concurrency (Q5)
  jobs.lector.uses: …/lector.yml@v0.1.0 ─────────▶ ├─ heal job: if (trigger), services: ollama (Q3)
    secrets (Q4)                                   │    fetch-metadata → app token → checkout
    with: optional inputs (Q3)                     │    → [action] (bot identity Q1, Renovate hint Q2)
                                                   └─ cleanup job: if closed → app token → [action]
[action/action.yml] ──▶ [tool image: lector pr] ──▶ [GitHubHost: comment, push, own-commit skip]
docs: README.md + docs/setup.md: App link + gh secret set (Q6), short and advanced workflow (Q3)
```

**In scope:** reusable workflow, derived bot identity, the shorter setup in README and setup guide. · **Out of scope:**
GitLab, a hosted App, making the repository public (the user's decision, see Notes of Q4), any change to the pipeline's
behaviour on a PR.

## Already fixed by the project docs

- Trigger on `pull_request` only, never `pull_request_target`; check author or label and same repository, never `github.actor` (concept 8.1).
- Push with a GitHub App token, not `GITHUB_TOKEN` (concept 8.2). The App's key is stored as Actions and Dependabot secret (M8).
- The tool skips its own head commit by author email (concept 8.2), and finds its own comment by the bot's login (AGENTS.md: identity, not marker).
- Configuration through workflow inputs only, no configuration file in the user's repository (M5).
- The action's image tag matches the tool version, checked in `./gradlew build` (`checkActionImage`). Versions that must match get a build check, not a comment (AGENTS.md).
- Exit codes `0`/`1`/`2` and the comment are unchanged.
- No new dependency without a reason; this runs only in CI (architecture rules 6 and 7).

---

## Round 1

### Q1 · Where the bot identity is derived

The tool needs the bot's login (comment lookup) and email (commit author, own-commit skip). Today the user types both.

- [x] **A · In the CLI: `lector pr --app-slug <slug>`, the action gets an `app-slug` input** — *recommended.* The CLI looks up `GET /users/<slug>[bot]` with the token it already has and builds name and email. Tested in Kotlin like the rest of `GitHubHost`; users of the action directly benefit too. `bot-name`/`bot-email` stay as an alternative (e.g. GitHub Enterprise with another noreply domain). Costs a CLI option and one API call.
- [ ] **B · In the reusable workflow, as a shell step with `gh api`** — No CLI change. The logic the own-commit skip depends on lives in untested shell, and users of the action directly still look the email up by hand.
- [ ] **C · Keep `bot-name`/`bot-email` as required inputs of the reusable workflow** — No new logic, but the step the user found hardest stays.

**Notes:** A (accepted as recommended)

### Q2 · The Renovate `gitIgnoredAuthors` step

Renovate needs the bot's email in `gitIgnoredAuthors`, or it stops updating its branch after lector-ai's first commit. This can't be automated from lector-ai; the question is how the user learns the exact value.

- [x] **A · Docs give a one-line `gh api` command that prints the value; the run on a Renovate PR also prints it in the log** — *recommended.* Copy-paste instead of assembling an email by hand; no new channel.
- [ ] **B · Also say it in the PR comment on Renovate PRs** — Hard to miss, but the comment is for reviewers, and the hint would repeat on every Renovate PR forever (the tool can't see whether Renovate's config already has it: presets, org config).
- [ ] **C · Default Renovate PRs to a companion PR, like Dependabot** — The step goes away, but every Renovate update needs a second PR to merge. Changes M5's decided behaviour.

**Notes:** A (accepted as recommended)

### Q3 · What the reusable workflow lets the caller set

- [x] **A · Every input of the action, plus `runs-on`, `ollama-image`, and `timeout-minutes`; registry credentials only through the action directly** — *recommended.* One-to-one with the action, so docs and a build check stay simple. A reusable workflow can't receive the caller's `env`, so tokens for private registries (`NODE_AUTH_TOKEN`, `MAVEN_*`) need the full workflow; `docs/setup.md` keeps it as "Advanced setup".
- [ ] **B · A few inputs only (`model`, `runs-on`, `ollama-image`, `mode`)** — Smaller surface, but users with a Maven mirror or other budgets fall back to the 80-line workflow.
- [ ] **C · Like A, plus one optional secret `build-env` with `KEY=value` lines that the workflow exports for the build** — Private registries work with the short setup too. A generic secret channel into the build environment, which the allowlist (AGENTS.md) was built to keep narrow, and more to test.

**Notes:** A (accepted as recommended)

### Q4 · How the caller passes the App's secrets

`secrets: inherit` is the shortest form, but GitHub passes secrets with it only within the same organization, or across organizations of one enterprise (docs: "Reusing workflow configurations").

- [x] **A · The reusable workflow declares `app-client-id` and `app-private-key` as required secrets; docs show passing them explicitly** — *recommended.* Works for every caller, two extra lines, and the workflow says which secrets leave the caller.
- [ ] **B · `secrets: inherit` in the docs** — One line, but fails for callers outside the owner's organization, and hands every secret of the repository to the called workflow.

**Notes:** A (accepted as recommended). A reusable workflow in a private repository can only be called from repositories the owner allows (the repository's Actions "Access" setting); repositories of other owners need the repository to be public. That is outside this spec.

### Q5 · Concurrency without the bot's name

Today a new push cancels the run on the old head, unless the pusher is lector-ai's own bot (its run must still post the comment) or the event is another label. In the reusable workflow the bot's name is only known after the token step.

- [x] **A · Cancel unless the sender is an App bot (`sender.type == 'Bot'`) that isn't in `bot-logins`; the label rule stays** — *recommended.* No name needed. Pushes by other apps no longer cancel, which only costs a run.
- [ ] **B · An optional `bot-name` input, only for this rule** — Exact, but brings back the input M10 removes.
- [ ] **C · No `cancel-in-progress`** — Simplest; runs on old heads keep the runner busy for up to two hours.

**Notes:** A (decided by the agent, confirmed by the user)

### Q6 · Creating the App

- [x] **A · A link that opens GitHub's "New GitHub App" form pre-filled through URL parameters (personal account and organization), plus `gh secret set` commands for Actions and Dependabot** — *recommended.* One click and copy-paste, docs only.
- [ ] **B · The App manifest flow** — Also hands over the key, but needs an HTML form and a code exchange somewhere: a hosted page or a script.
- [ ] **C · Reword the manual steps** — No gain.

**Notes:** A (decided by the agent, confirmed by the user)

### Q7 · Default Ollama image of the reusable workflow

- [x] **A · Pinned to the release, `lector-ollama:<version>-qwen2.5-coder-7b`, checked in the build; `ollama-image` overrides it** — *recommended.* `@v0.1.0` then means the same tool and model image every time.
- [ ] **B · The model tag without version, as the setup guide uses today** — Moves with every release, under a pinned workflow.

**Notes:** A (decided by the agent, confirmed by the user)

### Q8 · Keeping the reusable workflow in line

- [x] **A · Extend the build check: the action pinned at `@v<version>`, the Ollama default carries `<version>`, every action input is a reusable-workflow input** — *recommended.* Versions that must match get a build check (AGENTS.md); Dependabot can't bump the pin.
- [ ] **B · Only the version pins** — Inputs can drift.
- [ ] **C · No check** — Against AGENTS.md.

**Notes:** A (decided by the agent, confirmed by the user)

### Q9 · Testing the reusable workflow

- [x] **A · The build check (Q8) plus a real run on a test repository, recorded as verified outside the repository** — *recommended.* The same way M5, M7, and M8 verify the workflow today.
- [ ] **B · actionlint in `./gradlew build`** — A new tool with its own runtime for a syntax check.
- [ ] **C · A workflow in this repository that calls it** — Needs dependency PRs, an App, and a model in this repository.

**Notes:** A (decided by the agent, confirmed by the user)

### Q10 · `--app-slug` and `--bot-name`/`--bot-email` together (if Q1 = A)

- [x] **A · Exactly one way: `--app-slug`, or both `--bot-name` and `--bot-email`; anything else is a usage error (exit 2); a failed lookup is a tool error (exit 2) before any build** — *recommended.* Validate option values completely when parsing (AGENTS.md).
- [ ] **B · `--app-slug` wins, missing pieces are filled in** — Hides a misconfiguration.

**Notes:** A (decided by the agent, confirmed by the user)

### Q11 · Place in the milestones

- [x] **A · New milestone M10 after M9, before "Later"** — *recommended.* One milestone, one branch and PR (AGENTS.md).
- [ ] **B · Part of M5** — M5's scope is closed apart from its real-PR run.

**Notes:** A (decided by the agent, confirmed by the user)

---

## Decided by the agent

Questions the triage found agent-decidable and an independent second agent answered the same way. Veto any of them by ID.

| # | Topic | Decision | Reason |
|---|---|---|---|
| Q5 | Concurrency | A · cancel unless the sender is an App bot not in `bot-logins`; the label rule reads `fallback-label` | No name needed; other apps' pushes only cost a run |
| Q6 | Creating the App | A · pre-filled "New GitHub App" link (account and organization) plus `gh secret set` for Actions and Dependabot | Simplest; the manifest flow needs a hosted page for a one-time step |
| Q7 | Ollama default | A · `lector-ollama:<version>-qwen2.5-coder-7b`, build-checked, `ollama-image` overrides | The release publishes it; matching versions get a build check |
| Q8 | Build check | A · action pinned at `@v<version>`, Ollama default carries the version, every action input is a workflow input, with an explicit list of inputs the workflow fills itself | Extends `checkActionImage`; catches the likeliest drift |
| Q9 | Testing | A · build check plus a real run on a test repository | No new tool; same as M5, M7, M8 |
| Q10 | Identity options | A · `--app-slug` or both `--bot-name` and `--bot-email`; otherwise usage error; failed lookup is a tool error before any build | Validate options completely; the email must be exact |
| Q11 | Milestone | A · new M10 after M9 | M5's scope is the first release |

---

## Decisions

### Round 1

| # | Topic | Decision |
|---|---|---|
| Q1 | Bot identity | A · derived in the CLI: `lector pr --app-slug`, action input `app-slug`; `bot-name`/`bot-email` stay as the alternative |
| Q2 | Renovate `gitIgnoredAuthors` | A · a `gh api` one-liner in the docs; runs on Renovate PRs print the value in the log |
| Q3 | Reusable workflow inputs | A · every action input plus `runs-on`, `ollama-image`, `timeout-minutes`; registry credentials only through the action directly ("Advanced setup") |
| Q4 | Secrets | A · declared secrets `app-client-id` and `app-private-key`, passed explicitly |
| Q5–Q11 | Agent decisions | All A, confirmed by the user (see "Decided by the agent") |

---

## Additions from the spec critique

| # | Finding | Correction in the spec |
|---|---|---|
| R1 | The cleanup job had no `app-slug` and no trigger condition: every close would exit 2 | Cleanup gets today's `if`, the token's `app-slug`, a 10-minute timeout (AC14b, AC17b) |
| R2 | Empty `--bot-name`/`--bot-email` as separate argv items get lost | The action passes `--option=value` (AC10, AC5d) |
| R3 | The Renovate log line had no place and no outcomes; `core` must not name Renovate | Logged outside `core` on every outcome after Renovate is selected, green and own-commit skip included (AC11, AC11b, AC12c) |
| R4 | Concurrency location and group were open; a caller group could clash | Job-level, same group on both jobs; no concurrency in the caller, with the reason; real-run cases for cancelling (AC15, AC22, AC27) |
| R5 | `contains` on a comma list matches substrings | Comma-wrapped exact matching; no spaces in `bot-logins` (AC14) |
| R6 | Ollama image and `model` default were two sources of one fact | The default is derived exactly from version and model, and checked (AC19) |
| R7 | The check's comparison rules were undefined | Mirrored inputs are `type: string`; defaults compared without quotes (AC19b) |
| R8 | Dependabot would bump the self-pin, and the stated reason was wrong | `ignore` for `danvan86/lector-ai` in `.github/dependabot.yml` (AC19c) |
| R9 | Another label could displace a pending run on the bot's own push | Other-label events use their own group (AC15, AC27 f) |
| R10 | Exact URL comparison refused `https://github.com/`; GHES/GHE.com users had no way in | Scheme and host compared; docs scope the short setup to github.com (AC7b, AC21) |
| R11 | Preconditions of the short setup were missing | Stated before step 1 (AC21) |
| R12 | Version pins in the docs were unchecked | The build check scans `README.md` and `docs/setup.md` (AC19) |
| R13 | Secret names were unspecified | Today's `LECTOR_APP_CLIENT_ID` and `LECTOR_APP_PRIVATE_KEY`, mapped explicitly (AC21b) |
| R15 | Bundled or unobservable criteria | AC5 split, AC12b and AC27 made concrete |

**Escalated:**

- R14 · `ollama-url` as a reusable-workflow input could only point away from the workflow's own `ollama` service. Narrows Q3 ("every input of the action"). **User: leave it out** (exempt; a different Ollama server uses the advanced setup).
