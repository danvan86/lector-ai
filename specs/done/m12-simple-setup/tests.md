# M12 – Simple setup · Tests

Spec: [spec.md](spec.md) · Decisions: [brainstorming.md](brainstorming.md) · Status: **Approved, frozen (tests.lock)**

## Test contract

```
1. Test design (this file)    ──▶ questions in chat, answers recorded here
2. Test code                  ──▶ every test runs and fails for the stated reason (red)
3. Approval by the user       ──▶ test files frozen in tests.lock
4. Implementation             ──▶ production code only; tests change only after asking
5. Done when                  ──▶ all tests green + all quality gates green + all AC of the spec met
```

**Tools and levels:** as in AGENTS.md. The flow tests run `lector pr` in-process through the real sources, GitHub host,
and Maven adapter, with GitHub's API and Ollama as local HTTP servers and a local bare remote (the `PrFlowTest`
pattern). The end-to-end tests run the installed start script, tagged `e2e`. Tests that assert exit `2` for a usage error
go through `LectorCommand`, which maps usage errors to `2` as the pipeline sees them.

## Coverage

| AC | Covered by | Note |
|---|---|---|
| AC1 | T-A1 | |
| AC2 | T-A3 | |
| AC3 | T-A4 | |
| AC4 | T-A2, T-A6 | order and token; one request only on failure |
| AC5a–c | T-B1 | |
| AC5d | T-B1 (all empty), T-B2 | |
| AC6 | T-A6, T-A7 | a timeout is an `IOException` like an unreachable host (`GitHubApi`); not waited for in a test |
| AC7 | T-A5 | |
| AC7b | T-A8 | |
| AC8 | existing `PrFlowTest`, `PrCommandTest` unmodified; T-B2 | `PrFlowTest` asserts the exact request list, which has no `/users/` |
| AC9 | T-C1 | |
| AC10 | by reading `action/action.yml` | configuration |
| AC11 | T-C1 | |
| AC11b | T-C2 | |
| AC12 | T-C3 | |
| AC12b | by command: `git diff origin/main -- cli/src/test` shows no changed `stdout` assertion | |
| AC12c | by command: `grep -ri 'gitIgnoredAuthors\|renovate' core/src/main` is empty | |
| AC13–AC18b, AC25 | by reading `.github/workflows/lector.yml` | YAML that runs only on GitHub (Q9) |
| AC19, AC19b | by command, in the review: each mismatch in a scratch copy, `./gradlew checkActionImage` fails naming it; implement-spec may add unit tests of the check | build configuration |
| AC19c | by reading `.github/dependabot.yml` | |
| AC20 | by command: `./gradlew build` | |
| AC21–AC24 | by reading `README.md` and `docs/setup.md` | docs |
| AC26 | by command: `git diff origin/main -- gradle/libs.versions.toml '**/*.gradle.kts'` | |
| AC27, AC28 | outside the repository: a real run, recorded in `docs/features.md` | |

---

## Test catalog

### Group A · The bot's identity from the App's slug

Flow tests (`AppSlugFlowTest`). They catch an identity built from the wrong parts (name without `[bot]`, email without the
id or with another domain), a lookup without the token or after the build, a run that goes on with an answer that isn't
the App's bot, and a refusal that comes too late or doesn't say what to do instead.

| ID | Test | Scenario (given → when → then) | Red because | AC |
|---|---|---|---|---|
| T-A1 | `a broken Renovate PR healed with the App's slug is pushed under the bot's name and noreply email` | Renovate PR the update broke; GitHub knows `my-lector[bot]` with id 123 → `pr --app-slug my-lector` → exit 0, the fix commit on the branch is by `my-lector[bot] <123+my-lector[bot]@users.noreply.github.com>` | `no such option --app-slug` (exit 1 instead of 0) | AC1 |
| T-A2 | `the bot is looked up with the token before anything else is asked of GitHub` | same → same → the first request to GitHub is `GET /users/my-lector[bot]` with the token, and there is one lookup | no request at all | AC4 |
| T-A3 | `the report comment the bot wrote earlier is updated, not written a second time` | green Renovate PR with an earlier report comment by `my-lector[bot]` → `pr --app-slug` → that comment is patched, no new comment | option missing | AC2 |

Changed after approval, with the user's consent (2026-10-24): T-A3 stripped the request log with `substringBeforeLast(" ")`, which left `Bearer` on every entry, so it could never pass. It now strips `" Bearer…"`; the scenario and the assertion are unchanged.
| T-A4 | `a head commit with the bot's derived email is the tool's own and is skipped without a build` | Renovate PR whose head commit is by `123+my-lector[bot]@users.noreply.github.com` → `pr --app-slug` → `Skipped: the head commit is the tool's own`, exit 0, Maven never ran | option missing, stdout empty | AC3 |
| T-A5 | `a GitHub other than github_com refuses the App's slug before any request, and names the options to use instead` | `--github-server-url https://ghe.example.com` → `pr --app-slug` → exit 2, stdout empty, stderr names github.com, `--bot-name`, `--bot-email`, no request | stderr has no reason naming github.com | AC7 |
| T-A6 | `an answer that doesn't name the App's bot is a tool error with exit 2 before any build` | GitHub answers 404 / a `User` / another login / no id / id 0 / a non-numeric id / not JSON / 500 → `pr --app-slug` → each: exit 2, stdout empty, stderr `Error:` naming `my-lector`, only the lookup request, Maven never ran | option missing (exit 1) | AC4, AC6 |
| T-A7 | `an unreachable GitHub during the lookup is a tool error with exit 2 that names the slug` | API URL on a closed port → `pr --app-slug` → exit 2, stdout empty, stderr names `my-lector`, Maven never ran | option missing (exit 1) | AC6 |
| T-A8 | `a server URL with a trailing slash is still github_com` | `--github-server-url https://github.com/` → `pr --app-slug` → exit 0, the lookup is the first request | option missing | AC7b |

### Group B · One way to name the bot

Flow tests (`AppSlugFlowTest`). They catch a CLI that silently prefers one way, fills half a pair from the lookup, or
treats the action's empty values as given, and a usage error that only names the old options.

| ID | Test | Scenario (given → when → then) | Red because | AC |
|---|---|---|---|---|
| T-B1 | `naming the bot in no way, in both ways, or with half the pair is a usage error with exit 2 that names both ways` | neither; slug and pair; slug and name; slug and email; only name; only email; all three empty → `pr` → each: exit 2, stdout empty, stderr names `--app-slug`, `--bot-name`, `--bot-email`, no request | stderr names only `--bot-name`/`--bot-email` | AC5a–d |
| T-B2 | `the action's empty values count as not given, so the slug alone or the pair alone is used` | `--app-slug=my-lector --bot-name= --bot-email=` → lookup and exit 0; `--app-slug= --bot-name=… --bot-email=…` → exit 0 and no lookup | option missing | AC5d, AC8 |

### Group C · As the action runs it, and the Renovate hint

End-to-end tests (`SimpleSetupEndToEndTest`, tag `e2e`). They catch empty arguments lost between the action and the
CLI, a hint that only appears when lector-ai pushes (too late: Renovate has already seen a foreign commit), and a hint
on every source.

| ID | Test | Scenario (given → when → then) | Red because | AC |
|---|---|---|---|---|
| T-C1 | `pr with the action's arguments looks the bot up and logs the value for Renovate's gitIgnoredAuthors on a green PR` | green Renovate PR; GitHub knows `my-lector[bot]`, id 123 → installed `lector pr --app-slug=my-lector --bot-name= --bot-email=` → exit 0, green, the lookup is the first request, one stderr line names `gitIgnoredAuthors` and `123+my-lector[bot]@users.noreply.github.com` | `no such option --app-slug`, exit 2 | AC9, AC11 |
| T-C2 | `a run on a Renovate PR whose head is the tool's own commit still logs the value for gitIgnoredAuthors` | Renovate PR whose head is the tool's own → `pr` with the pair → skipped, exit 0, one stderr line names `gitIgnoredAuthors` and the email | no such line | AC11b |
| T-C3 | `runs on a Dependabot PR and on a labelled PR don't mention gitIgnoredAuthors` | a Dependabot PR and a labelled PR, each with the tool's own head → `pr` → skipped, stderr has no `gitIgnoredAuthors` | **green before the implementation**: a guard against logging for every source. It runs the same skip path as T-C2, which is red, so an implementation that logs for every source fails it | AC12 |

### Edge cases considered and left out

| Dimension | Decision |
|---|---|
| Quantity | One lookup per run (T-A2); `bot-logins` lists are unchanged CLI behaviour (existing tests) |
| Validity | Every malformed lookup answer in T-A6; malformed server URL is existing validation |
| Repetition | A second run finds its own comment by the derived login (T-A3) and its own commit by the derived email (T-A4) |
| Failure | 404, 500, not JSON, unreachable (T-A6, T-A7); a timeout is the same `IOException` path, not waited for |
| Isolation | Another bot's comment isn't the report: existing `GitHubHostTest` |
| Lifecycle | Closed PRs with `--app-slug` run the same lookup first; the cleanup path itself is unchanged (existing tests) |
| Time, Order, Conflict, Invariants | Not applicable: one request before an unchanged pipeline |

### Checked by command or by implementation tests

The reusable workflow, the action's inputs, the docs, and Dependabot's ignore are configuration and text, checked by
reading them (AC10, AC13–AC18b, AC19c, AC21–AC25). The build check (AC19, AC19b) is checked in the review by breaking a
scratch copy; implement-spec writes its own tests if it moves the check's logic into code. AC27 and AC28 need GitHub.

---

## Test code

| Group | Files | Red before the implementation, because |
|---|---|---|
| A, B | `cli/src/test/kotlin/gmbh/vanella/lector/cli/AppSlugFlowTest.kt` | compiles (only existing API); 10 of 10 fail on their assertions: `--app-slug` is unknown, so exits are 1 or 2 with the wrong message, no lookup request, no commit |
| C | `cli/src/test/kotlin/gmbh/vanella/lector/cli/SimpleSetupEndToEndTest.kt` | 2 of 3 fail on their assertions (`no such option --app-slug`; no `gitIgnoredAuthors` line); T-C3 is a guard and passes, see above |

Checked before approval: `ktlintCheck` and `detektTest` pass on the test code. No stubs were needed: the tests use only
existing types and fail on assertions that name the expectation.

---

## Decided by the agent

No open behaviour questions: every outcome follows from the spec (exit codes, messages, identity format, which sources log).

---

## Decisions

_None: no question rounds._

## Approval

Approved by the user on 2026-10-23, as designed. 2 test files in `tests.lock`.
