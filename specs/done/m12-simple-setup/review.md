# Review – M10 Simple setup

Scope: commits `b54f346..460e954` (the M10 commits on top of `cf183a8`) against [`spec.md`](spec.md).
Method: read every changed source, test, build, workflow, and doc file; ran `./gradlew clean build --no-build-cache`
(green, 881 unit and 24 end-to-end tests); ran the installed `lector pr` on every naming combination, a foreign server
URL, an unreachable API, a blank slug, and an extra argument; probed `checkReusableWorkflow` with temporary changes to
`lector.yml`, `README.md`, and `docs/setup.md` (all reverted, `git status` clean).

Outcome per finding: **Fixed** · **Not changed** (with reason) · **Needs decision** (with options).

Severity: **High** = breaks a requirement or the external contract · **Medium** = real gap, should fix before the next milestone · **Low** = cleanup / nit.

## Summary

- 38 of 40 acceptance criteria are met, AC6 with a caveat (F1). AC27 and AC28 can't be verified here: they need a
  published release and a real App on github.com.
- No High finding. The CLI contract holds on every path probed: exit `2`, stdout empty, the message on stderr.
- The main gap is in the new build check: it doesn't see whether an input reaches the action (F2), and it fails on a
  correct pin written in inline code (F3).
- One design question: the cleanup job ignores `runs-on` (F8).

## Acceptance criteria

| ID | Result | Evidence |
|---|---|---|
| AC1 | Met | `AppSlugFlowTest` "a broken Renovate PR healed with the App's slug…": `git log` on the origin shows `my-lector[bot] <123+my-lector[bot]@users.noreply.github.com>` |
| AC2 | Met | `AppSlugFlowTest` "the report comment the bot wrote earlier is updated": `PATCH …/comments/5`, no `POST` |
| AC3 | Met | `AppSlugFlowTest` "a head commit with the bot's derived email…": stdout `Skipped: the head commit is the tool's own`, exit 0, no `mvn.log` |
| AC4 | Met | `AppSlugFlowTest` "the bot is looked up with the token before anything else": first request `GET /users/my-lector[bot] Bearer ghs_token`; `PrCommand.kt:218` calls `bot(token)` before the repository check and the pipeline |
| AC5a | Met | `AppSlugFlowTest` Group B "neither"; installed script: exit 2, stdout empty, `Error: missing option --bot-name and --bot-email, or --app-slug` |
| AC5b | Met | Group B "slug and the pair/name/email"; installed script with `--app-slug x --bot-name a`: exit 2, `name the bot one way: …` |
| AC5c | Met | Group B "only the name", "only the email" |
| AC5d | Met | `AppSlugFlowTest` "the action's empty values count as not given…" |
| AC6 | Met, with caveats | `AppSlugFlowTest` per case (404, `User`, other login, no id, id 0, id `abc`, not JSON, 500, unreachable); `GitHubAppIdentityTest` adds negative and string ids. No timeout case (F1) |
| AC7 | Met | `AppSlugFlowTest` "a GitHub other than github_com…": exit 2, stdout empty, no request; installed script with `https://ghe.example.com`: names `--bot-name` and `--bot-email` |
| AC7b | Met | `AppSlugFlowTest` "a server URL with a trailing slash is still github_com" |
| AC8 | Met | `PrCommandTest` runs with the pair; the pair case of the empty-values test asserts no `/users/` request |
| AC9 | Met | `SimpleSetupEndToEndTest` runs the start script with `--app-slug=my-lector --bot-name= --bot-email=`; green in `:cli:endToEndTest` |
| AC10 | Met | `action/action.yml:11–19` inputs with `default: ""`, `:85–87` the attached `--option=value` form |
| AC11 | Met | `SimpleSetupEndToEndTest` green Renovate PR: a stderr line with `gitIgnoredAuthors` and the email |
| AC11b | Met | `SimpleSetupEndToEndTest` own head commit: same line |
| AC12 | Met | `SimpleSetupEndToEndTest` Dependabot and labelled PR: no `gitIgnoredAuthors` on stderr |
| AC12b | Met | `git diff cf183a8 -- cli/src/test/…/PrCommandTest.kt`: one stderr assertion follows the new metadata message, two usage cases added, no stdout assertion changed |
| AC12c | Met | `grep -rni "gitIgnoredAuthors\|renovate" core/src/main`: no match |
| AC13 | Met | `lector.yml:10` `workflow_call`, jobs `heal` and `cleanup` |
| AC14 | Met | `lector.yml:116–121`, comma-wrapped `contains`, only `inputs.*` |
| AC14b | Met | `lector.yml:183–187` |
| AC15 | Met | `lector.yml:125–131` and `:188–192`: same group, `sender.type != 'Bot' \|\| <in bot-logins>`, the `-other-label` suffix; no workflow-level `concurrency` |
| AC16 | Met | `lector.yml:145–178` |
| AC17 | Met | `lector.yml:132–141` |
| AC17b | Met | `lector.yml:193–206`: `app-slug` from the token step, 10 minutes, no service, no checkout |
| AC18 | Met | `lector.yml:100–109` |
| AC18b | Met | Read `lector.yml` inputs: none of the six |
| AC19 | Met | Probes: wrong image tag, `max-attempts` `"30"`, a renamed `npmrc` input, a README image at `0.0.9`, a quoted cleanup pin at `v9.9.9` each fail `checkReusableWorkflow` with a message naming the mismatch. See F2–F4 for what it misses |
| AC19b | Met | Probe: `default: 20` against the action's `"20"` passes |
| AC19c | Met | `.github/dependabot.yml:11–14` ignores `danvan86/lector-ai*` |
| AC20 | Met | `./gradlew clean build --no-build-cache`: BUILD SUCCESSFUL |
| AC21 | Met | `docs/setup.md` "0. Before you start"; README "Before you start" |
| AC21b | Met | Both files: two App links, four `gh secret set` commands, the org variant (`--org <org> --visibility all`), the caller workflow, the `gh api` one-liner |
| AC22 | Met | The caller workflows in both files: explicit mapping, no `secrets: inherit`, `concurrency`, `bot-name`, or `bot-email` |
| AC23 | Met | `docs/setup.md` "Advanced setup": the action directly, `app-slug`, the new concurrency rule, no typed bot email |
| AC24 | Met | Both input tables against `lector.yml`: every name and default matches |
| AC25 | Met | `lector.yml` has no `run:` step |
| AC26 | Met | `git diff cf183a8 -- gradle/libs.versions.toml buildSrc/build.gradle.kts '*/build.gradle.kts'`: empty; only a task in the root `build.gradle.kts` |
| AC27 | Can't verify | Needs a published release (blocked by the MSAL check) or a fork with its own images, and a test repository |
| AC28 | Can't verify | Needs a browser and a GitHub account; the docs tell the user to check both permissions by hand |

## Findings

### Tests

**F1 · Low · The lookup's timeout case has no test (AC6).** `AppSlugFlowTest` covers 404, the wrong answers, 500, and an
unreachable API, but no timeout. A timeout is an `HttpTimeoutException`, an `IOException`, so it takes the same branch
as the unreachable case (`GitHubApi.kt` `request`), which is tested. A dedicated test would need the fixed one-minute
`REQUEST_TIMEOUT` to become a parameter.

Outcome: **Not changed.** A timeout parameter would exist only for this test, and the branch it would cover is already
covered. Revisit if `GitHubApi` gets a configurable timeout for another reason.

### Quality gates

**F2 · Medium · `checkReusableWorkflow` doesn't check that the workflow passes its inputs to the action.** Probe:
deleting `yarnrc: ${{ inputs.yarnrc }}` from the heal job's `with:` passes the check. The check compares the declared
inputs and defaults only (`ReusableWorkflow.kt:31–37`). Consequence: the next action input gets declared (the check
demands that) but not forwarded, and a caller's value is silently ignored. Expected: each non-exempt action input must
appear as `<name>: ${{ inputs.<name> }}` in `lector.yml`.

Outcome: **Fixed.** The check now fails with "lector.yml doesn't pass its input <name> to the action"; probed with the
`yarnrc` line removed and with a typo in `max-attempts`' expression (`f44bc16`).

**F3 · Medium · The docs pin check fails on a correct pin in inline code.** Probe: appending
``Use `danvan86/lector-ai/action@v0.1.0`.`` to `docs/setup.md` fails the build with
``setup.md pins lector-ai @v0.1.0`., not @v0.1.0``. `DOCS_PIN` captures `@v(\S+)`, trailing backtick and period
included. Consequence: the next doc edit that writes a pin in prose breaks the build with a confusing message. Expected:
capture a version that ends in a word character, e.g. `@v([\w.-]*\w)`.

Outcome: **Fixed.** `DOCS_PIN` captures `@v([\w.-]*\w)`; a correct pin in inline code passes, a wrong one in inline code
still fails (`f44bc16`).

**F4 · Low · A docs pin of the repository root (`danvan86/lector-ai@v…`) isn't checked.** Probe: appending
`uses: danvan86/lector-ai@v0.0.9` to `docs/setup.md` passes. `DOCS_PIN` requires a `/` after `lector-ai`. No doc has
such a pin today, but R12 is about every `danvan86/lector-ai/…@v…`, and a reference to the repository is the natural
next one. Expected: make the path optional.

Outcome: **Fixed.** The path is optional in `DOCS_PIN`; the probe above now fails with "setup.md pins lector-ai @v0.0.9"
(`f44bc16`).

**F5 · Low · The logic of `checkReusableWorkflow` has no automated test.** `buildSrc/src/main/kotlin/ReusableWorkflow.kt`
is 75 lines of parsing; buildSrc has no test task and detekt doesn't see it (ktlint does, `build.gradle.kts:11`). A
regression in a regex turns the check off silently. The implementation probed it by hand, and so did this review.

Outcome: **Not changed.** A test source set in buildSrc, wired into `build`, is more machinery than the check; AC19
allows the probes. Revisit when buildSrc gets a second piece of logic.

### Contract

**F6 · Low · The bot-naming usage errors print the root command's usage line.** Installed script:
`lector pr … --app-slug x --bot-name a` prints `Usage: lector [<options>] <command> [<args>]...`, while every other
`pr` usage error prints `Usage: lector pr [<options>] [<repo>]`. The `UsageError` thrown in `PrCommand.run()`
(`PrCommand.kt:194`, `:206`) has no context, so Clikt falls back to the root. Exit code and message are right. Expected:
pass `currentContext` to the error.

Outcome: **Fixed.** `PrCommand.usageError` sets the context; a new `PrCommandTest` case asserts `Usage: lector pr` for
all three errors, red before the fix (`96d20ab`).

### Leftovers

**F7 · Low · Two stale sentences.** `lector.yml:12` says the inputs are "checked by ./gradlew checkActionImage"; the
check is `checkReusableWorkflow`. `docs/setup.md` section 3 says "The inputs are strings, numbers included", but
`timeout-minutes` is a number.

Outcome: **Fixed.** Both sentences corrected (`dc16ac8`).

### Design

**F8 · Medium · The cleanup job always runs on `ubuntu-latest`, whatever `runs-on` says.** `lector.yml:193`. A repository
that sets `runs-on` because it has no GitHub-hosted runners (an organization that allows only self-hosted runners) gets
a cleanup job that waits for a runner that never comes, and its companion PRs are never closed. The spec names
`runs-on` without saying which jobs it covers; the docs say "the heal job's runner".

Outcome: **Fixed, after a decision.** Asked with three options (both jobs use `runs-on`; a new `cleanup-runs-on`
input; keep `ubuntu-latest` and document it). The user chose `runs-on` for both jobs: the cleanup job now uses the same
expression as the heal job, the input's description and both docs tables say "both jobs", and the spec's section 3
names it (`dc16ac8`). actionlint (Docker, by hand) has no finding on it.

## Found while fixing

**F9 · Low · The first fix commit was made with a ktlint finding in buildSrc.** The commit command ran after
`ktlintCheck` regardless of its result; `ktlintFormat` added the blank line and the commit was amended before anything
else. No effect on the branch; noted because `ktlintCheck` is the only gate that sees buildSrc.

Outcome: **Fixed.** Amended (`f44bc16`).

**F10 · Medium · The token couldn't read a Dependabot PR's commits in a private repository.** Not found by this
review: a parallel session committed `a604658` on this branch while the review ran, from the first real runs on
lector-sample-app. `fetch-metadata` failed with 403 under `contents: read` only; the M8 setup had the same gap. The
reusable workflow and every documented caller now grant `pull-requests: read`. Checked here: the final build below
includes it, and `checkReusableWorkflow` still passes.

Outcome: **Fixed** (`a604658`). The spec's section 3, its caller workflow, and AC18 now name `pull-requests: read`.

## After the fixes

`./gradlew clean build --rerun-tasks --no-build-cache --no-configuration-cache`: BUILD SUCCESSFUL. 882 unit tests and
24 end-to-end tests, 0 failures; coverage 100 % lines and branches per module.

## What's good

- The identity is derived in tested Kotlin (`gitHubAppIdentity`), validated strictly (type, login, positive numeric
  id), and looked up before anything is built; failures name the slug. Keep the shape: a function in `git`, injected
  into `PrCommand` like the host.
- The one-way rule in `PrCommand.bot()` reads as the spec's table, and empty values count as not given, so the action
  can pass all three inputs in the attached form.
- `UpdateSource.setupHint` keeps `core` free of Renovate: the source owns the text, the pipeline only logs it, and it is
  logged before the own-commit check, so the first green run shows it.
- The concurrency rule needs no bot name and is the same in the reusable workflow and the advanced setup; the
  comma-wrapped matching avoids `my-renovate[bot]` matching `renovate[bot]`.
- The check reads lines like `checkActionImage` instead of adding a YAML library (Q9).
