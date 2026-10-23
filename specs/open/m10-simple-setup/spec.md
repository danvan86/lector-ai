# M10 – Simple setup · Spec

Milestone: M10 in [features.md](../../../docs/features.md) (new) · Decisions: [brainstorming.md](brainstorming.md) (including the spec critique) · Status: **Ready to implement**

## Goal

A repository on github.com gets lector-ai with three steps: create the App from a pre-filled link, store its two
secrets with `gh secret set`, and add a workflow of about ten lines that calls a reusable workflow shipped with
lector-ai.

**Why:** today's setup takes an 80-line workflow, a hand-built bot email, and secrets stored twice by clicking. Every
step is a place to get it wrong, and a wrong bot email makes the tool miss its own commits (concept 8.2). The pipeline's
behaviour on a PR is done (M5, M7, M8, M9); what's left between a user and that behaviour is the setup.

## What to build

### 1. The bot identity from the App's slug

- `lector pr` takes `--app-slug <slug>` as an alternative to `--bot-name` and `--bot-email`. With it, the tool asks
  GitHub's API for the user `<slug>[bot]` (`GET {api}/users/<slug>[bot]`, with the token it already has) and uses
  name `<slug>[bot]` and email `<id>+<slug>[bot]@users.noreply.github.com`.
- Exactly one way is accepted: `--app-slug`, or both `--bot-name` and `--bot-email`. An empty value counts as not given
  (the action passes empty inputs). Any other combination is a usage error: exit `2`, stderr names the two ways, stdout
  is empty, nothing runs and no request is sent.
- The lookup runs before anything is built or fetched. It fails with exit `2` and a message naming the slug when the
  user doesn't exist (404), the answer isn't a bot (`type` is not `Bot`), its `login` isn't `<slug>[bot]`, its `id` is
  missing or not a positive number, or the request fails (an error status, a timeout, no connection).
- `--app-slug` is refused (exit `2`, before any request) unless `--github-server-url` has the scheme `https` and the
  host `github.com` (a trailing slash or path doesn't matter): the noreply domain is only known for github.com. The
  message says to use `--bot-name` and `--bot-email` there. (R10)
- The action gets an input `app-slug` (default empty). `bot-name` and `bot-email` are no longer required (default
  empty). The action passes all three in the `--option=value` form, so an empty value stays one argument (R2); the
  CLI's rule above decides.

**Why:** Q1 and Q10. The own-commit check and the comment lookup depend on the exact identity, so it is derived in
tested Kotlin, not in shell; users of the action directly get it too. GitHub Enterprise Server and GHE.com keep the
explicit pair through the advanced setup.

### 2. The `gitIgnoredAuthors` value in the log

- On every run in which the Renovate source is selected for the PR, whatever the outcome after that (green, own-commit
  skip, no changes, fixed, push rejected), `lector pr` logs one line on stderr with the bot's email and the hint that
  Renovate's `gitIgnoredAuthors` must contain it. A Renovate PR that also carries the `lector` label is taken by
  Renovate and logs it. A PR another source takes doesn't. The report on stdout and the PR comment don't change.
- The line comes from outside `core` (the Renovate source in `sources`, or `cli`), which gets the email through the
  options it is built from: `core` names no bot (architecture rule 1). (R3)

**Why:** Q2. The user can copy the exact value from the first run, which is green and pushes nothing, before Renovate
sees a foreign commit; the comment stays for reviewers.

### 3. The reusable workflow

- `.github/workflows/lector.yml` in this repository, `on: workflow_call`. It does what `docs/setup.md`'s workflow does
  today, with the same behaviour on every event that workflow handles:
  - the `heal` job: only on a same-repository PR that isn't closed, by one of `bot-logins` or labelled with
    `fallback-label`, and on a `labeled` event only for that label; `dependabot/fetch-metadata` on Dependabot's PRs with
    `continue-on-error` (a refusal skips the lector-ai step, as today); the App token from
    `actions/create-github-app-token`; the checkout of the PR's head SHA with `persist-credentials: false`; the action
    with `app-slug` from the token step's `app-slug` output;
  - the Ollama service container named `ollama` from `inputs.ollama-image`, with a health check;
  - the `cleanup` job: only on a closed same-repository PR by one of `bot-logins` or labelled with `fallback-label`;
    the App token and the action with `app-slug` from the token step; no service, no checkout, a 10-minute timeout. (R1)
- Concurrency (R4, R9): both jobs carry the same job-level concurrency group, which names the repository and the PR
  number. A new push, a `fallback-label` label, or closing the PR cancels the job still working on that PR, unless the
  event's sender is a bot (`sender.type == 'Bot'`) that isn't in `bot-logins`. Events of another label use a group of
  their own, so they can neither cancel nor displace a pending run. The docs' caller workflow sets no concurrency, and
  the docs say why: a caller job sharing the group would be cancelled with it.
- Matching against `bot-logins` (in both `if`s and the concurrency rule) is exact per entry: the comma-separated list
  and the login are each wrapped in commas before `contains`, so `my-renovate[bot]` doesn't match `renovate[bot]`. The
  inputs table says the list has no spaces. (R5)
- Inputs: every input of `action/action.yml` except `token`, `app-slug`, `bot-name`, `bot-email`,
  `dependabot-metadata`, and `ollama-url` (R14), which the workflow fills itself, each `type: string`, not required,
  with the action's default. Plus `runs-on` (string, default `ubuntu-latest`), `ollama-image` (string, default
  `ghcr.io/danvan86/lector-ollama:<version>-<model>`, where `<model>` is the action's `model` default with `:` replaced
  by `-`), and `timeout-minutes` (number, default `120`, the heal job's timeout). (R6, R7)
- Secrets: `app-client-id` and `app-private-key`, both required.
- The jobs' `GITHUB_TOKEN` permissions are `contents: read`.
- The action is referenced as `danvan86/lector-ai/action@v<version>`, in both jobs.

**Why:** Q3, Q4, Q5, Q7. The workflow is the 80 lines users copy today; inside lector-ai it is versioned with the tool.
Explicit secrets work for every caller (`secrets: inherit` only within one organization or enterprise). The concurrency
rule needs no bot name. A reusable workflow can't receive the caller's `env`, so registry tokens stay with the action
used directly. The workflow owns the `ollama` service, so an Ollama URL could only point away from it.

### 4. A build check that keeps the workflow and the docs in line

- `./gradlew build` fails, with a message naming the file and the mismatch, when:
  - `lector.yml` references anything other than `danvan86/lector-ai/action@v<version>` (`gradle.properties`) for the
    action, in either job;
  - the `ollama-image` default isn't exactly `ghcr.io/danvan86/lector-ollama:<version>-<model>` as defined in 3 (R6);
  - an input of `action/action.yml` outside the exempt list is missing from `lector.yml`, or its default there differs
    (defaults are compared without quotes, so `"20"` and `20` are the same, `"20"` and `"30"` are not) (R7);
  - `README.md` or `docs/setup.md` pins `danvan86/lector-ai/…@v…` or `lector-ollama:…` to another version than
    `<version>` (R12).
- The existing check that the action runs the tool image of the same version stays.
- `.github/dependabot.yml` ignores `danvan86/lector-ai` for the `github-actions` ecosystem: the pin moves with the
  tool's version, not by Dependabot, and a bump would fail the check. (R8)

**Why:** Q8. Versions and inputs that must match get a check in the build (AGENTS.md); the version bump of a release
must reach the workflow and the docs together.

### 5. The short setup in the docs

- `README.md` and `docs/setup.md` lead with the short setup:
  0. Preconditions, stated before step 1 (R11): github.com only (GHES and GHE.com use the advanced setup, R10); a
     published release with public images; the lector-ai repository reachable (public, or for the owner's own private
     repositories, the Actions "Access" setting).
  1. Create the App from a link that opens GitHub's "New GitHub App" form pre-filled (homepage, webhook off, private,
     Contents and Pull requests read and write), one link for a personal account, one for an organization, with
     placeholders the user replaces. Generate a private key, install the App.
  2. Store the client ID and the key as `LECTOR_APP_CLIENT_ID` and `LECTOR_APP_PRIVATE_KEY` (the names of today's
     setup, R13) with `gh secret set`, as Actions and as Dependabot secrets, for a repository and for an organization.
  3. Add the caller workflow: `on: pull_request` with the five event types, `permissions: contents: read`, one job
     that `uses` the reusable workflow at `@v<version>` and maps `LECTOR_APP_CLIENT_ID` to `app-client-id` and
     `LECTOR_APP_PRIVATE_KEY` to `app-private-key`. No concurrency, with the reason.
  4. Renovate only: a `gh api` one-liner that prints the value for `gitIgnoredAuthors`, and the note that the first run
     logs it too.
- The optional inputs of the reusable workflow, in one table.
- An "Advanced setup" section keeps the full workflow with the action used directly, for registry credentials, a GPU
  runner's service options, GHES and GHE.com, or a changed checkout. It uses `app-slug` on github.com and the new
  concurrency rule.
- Every input name and default in the docs matches the files.

**Why:** Q6. One click and copy-paste instead of five manual steps; the full workflow stays for the cases the reusable
workflow can't cover. Existing users keep their secrets.

## What not to do

| Don't | Why |
|---|---|
| Derive the identity in a shell step of the workflow | Q1: the own-commit check depends on it; it belongs in tested code |
| Guess the noreply domain of GHES or GHE.com | A wrong email makes the tool work on its own commits; they keep `--bot-name`/`--bot-email` |
| Log the `gitIgnoredAuthors` hint from `core`, or say it in the PR comment | Architecture rule 1; Q2: the comment is for reviewers, and the tool can't see Renovate's config |
| Default Renovate PRs to a companion PR | Q2: changes M5's behaviour, two PRs per update |
| `secrets: inherit` in the docs | Q4: fails across organizations and hands over every secret |
| New secret names | R13: existing users would have to store their secrets again |
| A generic secret or input that passes variables into the build | Q3: widens the allowlisted build environment; registry tokens use the advanced setup |
| A `bot-name` or `ollama-url` input on the reusable workflow | Q5, R14: the concurrency rule doesn't need the name; the workflow owns the Ollama service |
| Concurrency in the docs' caller workflow | R4: a caller job sharing the group would be cancelled with it |
| The App manifest flow, a hosted page, or a setup script | Q6: a pre-filled link does it without anything to host |
| actionlint, a YAML library, or another new dependency for the check | Q9: a new tool for a syntax check; the check reads lines like `checkActionImage` |
| A workflow in this repository that calls the reusable workflow | Q9: needs an App, secrets, and dependency PRs here |
| Any change to what `lector pr` does on a PR, the comment, or the exit codes | Out of scope: M10 is about setup |
| GitLab | Later (features.md) |
| Making the repository or the images public | The owner's decision, outside the code |

## Acceptance criteria

The review agent checks every criterion and reports it as *met*, *not met*, or *can't verify*, with evidence.

| ID | Criterion | Covers | How to verify |
|---|---|---|---|
| AC1 | `lector pr --app-slug my-lector` against a fake API that returns `{"login":"my-lector[bot]","id":123,"type":"Bot"}` commits as `my-lector[bot]` with `123+my-lector[bot]@users.noreply.github.com` | 1 | Test in `cli` with the fake GitHub API: author name and email of the pushed commit |
| AC2 | With `--app-slug`, an existing report comment by `my-lector[bot]` is updated, not duplicated | 1 | Test |
| AC3 | With `--app-slug`, a head commit authored by the derived email is skipped as the tool's own: exit 0, stdout says skipped, no build ran | 1 | Test |
| AC4 | The lookup requests `/users/my-lector[bot]` (encoded or not) with the token, before any build or fetch | 1 | Test: the fake API's request log, and no build in the fake adapter |
| AC5a | Neither `--app-slug` nor the pair: exit `2`, stderr names both ways, stdout empty, no request | 1 | Test |
| AC5b | `--app-slug` together with `--bot-name` or `--bot-email`: exit `2`, stderr names both ways, stdout empty, no request | 1 | Test |
| AC5c | Only one of `--bot-name`/`--bot-email` without `--app-slug`: exit `2`, stderr names both ways, stdout empty, no request | 1 | Test |
| AC5d | `--app-slug=` (empty) with the pair works like the pair; `--bot-name= --bot-email=` (empty) with `--app-slug` works like `--app-slug` | 1 | Test |
| AC6 | A 404, a non-`Bot` type, a wrong `login`, a missing or non-positive `id`, a 5xx, and a timeout each exit `2` with a message naming the slug, before any build | 1 | Test per case, stderr and exit code |
| AC7 | `--app-slug` with `--github-server-url https://ghe.example.com` exits `2` before any request, and stderr names `--bot-name` and `--bot-email` | 1 | Test |
| AC7b | `--app-slug` with `--github-server-url https://github.com/` (trailing slash) is accepted | 1 | Test |
| AC8 | `--bot-name` and `--bot-email` without `--app-slug` work as before and send no user lookup | 1 | Existing `pr` tests stay green unmodified; the fake API's log has no `/users/` request |
| AC9 | The end-to-end test runs the installed `lector pr` with `--app-slug=my-lector --bot-name= --bot-email=` against a fake API and exits as the scenario expects | 1 | `./gradlew :cli:endToEndTest` |
| AC10 | `action/action.yml` has an `app-slug` input with default `""`; `bot-name` and `bot-email` are not required and default to `""`; all three are passed as `--app-slug=…`, `--bot-name=…`, `--bot-email=…` | 1 | Read the file |
| AC11 | On a green Renovate PR, stderr has one line with the bot's email and `gitIgnoredAuthors` | 2 | Test |
| AC11b | On a Renovate PR whose head is the tool's own commit, stderr has that line | 2 | Test |
| AC12 | On a Dependabot PR and on a PR the fallback source takes, stderr has no `gitIgnoredAuthors` line | 2 | Tests |
| AC12b | The existing stdout assertions of the `pr` tests pass unmodified | 2 | `git diff origin/main` of the existing assertions |
| AC12c | `core` contains no `gitIgnoredAuthors` and no `Renovate` | 2, Don't | `grep -ri` in `core/src/main` |
| AC13 | `.github/workflows/lector.yml` exists with `on: workflow_call` and the jobs `heal` and `cleanup` | 3 | Read the file |
| AC14 | The `heal` job's `if` covers: not closed, same repository, author in `bot-logins` or labelled `fallback-label`, a `labeled` event only for that label; from inputs, with comma-wrapped exact matching, no literal bot login or label | 3 | Read the file |
| AC14b | The `cleanup` job's `if` covers: closed, same repository, author in `bot-logins` or labelled `fallback-label`, from inputs with the same matching | 3 | Read the file |
| AC15 | Both jobs have job-level concurrency with the same group (repository and PR number); cancel-in-progress is false when the sender is a `Bot` not in `bot-logins`; events of another label get a separate group; no workflow-level concurrency | 3 | Read the file |
| AC16 | The `heal` job runs `fetch-metadata` only on Dependabot's PRs with `continue-on-error`, skips the action when it failed, checks out the head SHA with `persist-credentials: false`, and passes the token step's `app-slug` output to the action | 3 | Read the file |
| AC17 | The `heal` job has the `ollama` service from `inputs.ollama-image` with a health check, `runs-on` and `timeout-minutes` from inputs | 3 | Read the file |
| AC17b | The `cleanup` job passes the token step's `app-slug` output to the action, has a 10-minute timeout, no service, and no checkout | 3 | Read the file |
| AC18 | `lector.yml` declares the required secrets `app-client-id` and `app-private-key` and `contents: read` permissions | 3 | Read the file |
| AC18b | `lector.yml` has no `ollama-url`, `bot-name`, `bot-email`, `token`, `app-slug`, or `dependabot-metadata` input | 3, Don't | Read the file |
| AC19 | `./gradlew build` fails with a message naming the mismatch for each of: the action reference not `@v<version>`; the `ollama-image` default not exactly as defined; a non-exempt action input missing; its default different; a docs pin to another version | 4 | A test of the check, or each change in a scratch copy with the check's task, then restore |
| AC19b | A default of `20` against the action's `"20"` passes the check | 4 | Same as AC19 |
| AC19c | `.github/dependabot.yml` ignores `danvan86/lector-ai` for `github-actions` | 4 | Read the file |
| AC20 | `./gradlew build` passes on the finished branch | 4 | Run it |
| AC21 | `README.md` and `docs/setup.md` state the preconditions (github.com, published release and images, repository reachable) before the short setup | 5 | Read the files |
| AC21b | They show the two pre-filled App links, the `gh secret set` commands for Actions and Dependabot (repository and organization) with `LECTOR_APP_CLIENT_ID` and `LECTOR_APP_PRIVATE_KEY`, the caller workflow, and the `gitIgnoredAuthors` one-liner | 5 | Read the files |
| AC22 | The caller workflow in the docs maps the two secrets explicitly and contains no `secrets: inherit`, no `concurrency`, no `bot-name`, no `bot-email` | 5, Don't | `grep` the docs |
| AC23 | `docs/setup.md` has an "Advanced setup" with the full workflow using the action directly, `app-slug`, and the new concurrency rule; no bot email is typed in it | 5 | Read the file |
| AC24 | Every input named in the docs' table exists in `lector.yml` with the stated default | 5 | Compare the table with the file |
| AC25 | No shell step in `lector.yml` derives or prints the bot's identity | Don't | Read the file |
| AC26 | No `actionlint`, no new Gradle plugin, and no new library dependency is added | Don't | `git diff origin/main -- gradle/libs.versions.toml` and the build files |
| AC27 | Outside the repository, in a test repository on github.com set up only with the short setup: (a) a broken Renovate PR gets fix commits and the comment; (b) a broken Dependabot PR gets a companion PR; (c) a labelled PR heals; (d) closing a Dependabot PR closes its companion PR; (e) a push by Renovate during a run cancels it; (f) the run on lector-ai's own push isn't cancelled by its `ai-assisted` label and ends green | 1–5 | A real run, recorded in `docs/features.md` |
| AC28 | The pre-filled App link creates an App with Contents and Pull requests read and write, or the docs name the permission to set by hand | 5 | Outside the repository: open the link |

## Docs to update

- `docs/features.md`: M10 – Simple setup, with its "done when" (AC27) and what is still open.
- `docs/setup.md` and `README.md`: preconditions, the short setup first, the advanced setup after it.
- `docs/architecture/pr-pipeline.md`: `--app-slug`, the user lookup, the identity rule, the Renovate log line, the
  reusable workflow, its concurrency, and the build check.
- `docs/architecture/fix-loop.md`: the `pr` identity row names `--app-slug` as the alternative.
- `docs/concept.md` 8.2 and 8.4: the identity is derived from the App's slug on github.com; lector-ai ships a reusable
  workflow next to the action.
- `AGENTS.md`: the reusable workflow in the layout block and the setup sentence under "Commands".
- `specs/README.md`: M10 in the index (done).
