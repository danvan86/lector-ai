# Review – M3 Fix loop

Scope: commits `da21345..6dddbcf` (10 commits on `danvan86/m3-fix-loop`) against [`spec.md`](spec.md).
Method: read every source, test, and build file in the diff against `origin/main`; ran `./gradlew clean build --no-build-cache`; ran the installed `lector heal` on bad input (unknown option, extra argument, missing directory, invalid integer, non-HTTP URL); probed detekt (`System.out.printf`) and Kover (untested branch) in the new `llm` module and checked the task graph with `build --dry-run` (all reverted, `git status` clean).

Outcome per finding: **Fixed** · **Not changed** (with reason) · **Needs decision** (with options).

Severity: **High** = breaks a requirement or the external contract · **Medium** = real gap, should fix before the next milestone · **Low** = cleanup / nit.

## Summary

- 39 of 40 acceptance criteria are met, 4 of them with caveats (AC18 → F4, AC26 → F1, AC27 → F2, AC28 → F3). After the fixes, only the caveats of AC18 (D3) and AC28 (D1) remain. AC36 can't be verified here: the reviewer's real-model run ran out of memory in Docker (F14).
- The main design gap: an attempt isn't fully contained. A tool error inside it leaves the model's edits in the tree (F2), the time budget can't stop it (F3), and an accepted one commits whatever the build left untracked (F1).
- No gate holes: the new module's gates are wired into `build` and fire, and `realModelTest` and `integrationTest` stay out of it.

## Acceptance criteria

| ID | Result | Evidence |
|---|---|---|
| AC1 | Met | `./gradlew clean build --no-build-cache` exits 0; 291 unit tests (`core` 90, `recipes` 33, `llm` 16, `ecosystems:jvm` 77, `cli` 75), 15 end-to-end |
| AC2 | Met | `core/.../Edit.kt:12` |
| AC3 | Met | `core/.../LlmClient.kt:8`: `propose(FixRequest): Proposal`, `Proposal.Edits(edits, rationale)` |
| AC4 | Met | `core/.../EcosystemAdapter.kt:37,47,50` |
| AC5 | Met | `core/build.gradle.kts` has no `project(`; `grep -rniE 'ollama\|maven\|javap' core/src/main`: no matches |
| AC6 | Met | `settings.gradle.kts:15`; `llm/build.gradle.kts:7` (`:core` only); `cli/build.gradle.kts:9` |
| AC7 | Met | `OllamaClientTest` "posts to api_chat … the schema as format" |
| AC8 | Met | same test, `num_ctx` 16384 |
| AC9 | Met | `FixLoopTest:88` (0.0), `:132` (0.3) |
| AC10 | Met | `OllamaClientTest` "code fences …", "doesn't match the schema is invalid"; `FixLoopTest:183` |
| AC11 | Met | `gradle/libs.versions.toml`: only Testcontainers (test-only) is new |
| AC12 | Met | `HealCommandTest:222,250`; `lector heal --help` shows both defaults |
| AC13 | Met | `FixLoopTest:109` |
| AC14, AC15 | Met | `ClusterTest:12,28` |
| AC16 | Met | `FixLoopTest:74` |
| AC17 | Met | `SnippetTest:27` |
| AC18 | Met, with caveats | `PromptTest:101`. When errors and code alone don't fit, the request goes out oversized and unannounced (F4) |
| AC19 | Met | `FixLoopTest:120,137` |
| AC20 | Met | `JavapGroundingTest:67` with the real `javap` |
| AC21 | Met | `ClusterKeysTest:14–36` |
| AC22, AC23 | Met | `EditTest:38–101`, `FixLoopTest:120` |
| AC24 | Met | `FixLoopTest:199,233`. Removing the package isn't covered (F7) |
| AC25 | Met | `ClusterTest:56`, `FixLoopTest:278,297` |
| AC26 | Met, with caveats | `FixLoopTest:49`. The commit also takes untracked build output (F1) |
| AC27 | Met, with caveats | `FixLoopTest:137`. Not on a tool error inside the attempt (F2) |
| AC28 | Met, with caveats | `heal --help` shows 3, 20, 30, 2; `FixLoopTest:336–391`. The time limit is only checked between attempts (F3) |
| AC29 | Met | `FixLoopTest:49` (one request), `:394` |
| AC30 | Met | `HealCommandTest:62` |
| AC31 | Met | `HealCommandTest:289` |
| AC32, AC33 | Met | `HealCommandTest:80,140`, `FixLoopReportTest`, `LectorEndToEndTest:185` |
| AC34 | Met | `HealCommandTest:104,302,315`; `LectorEndToEndTest:207` |
| AC35 | Met | `MigrateCommandTest` unchanged and green |
| AC36 | Can't verify | The implementer's run passed (progress.md). The reviewer's run after the fix round got Ollama's `llama-server process has terminated: signal: killed` (status 500): the 8 GB Docker VM was shared with other containers. Needs a run on a free machine or the `Real model` workflow (F14) |
| AC37 | Met | `RealModelTest.kt:31`, `cli/build.gradle.kts:101` |
| AC38 | Met | `.github/workflows/real-model.yml`: `workflow_dispatch`, `schedule` only |
| AC39 | Met | `cli/build.gradle.kts:48`; `build --dry-run` has no `realModelTest` or `integrationTest` |
| AC40 | Met | No guardrail, `editableScope`, push, PR, or GitHub API code |

## Findings

### Correctness and tests

**F1 · Medium · An accepted fix commits every untracked file the build left behind.** `GitWorkingTree.commit` runs `git add --all` (`GitWorkingTree.kt:50`), and the loop commits right after a full Maven run (`FixLoop.kt:198`). In a repository that doesn't ignore `target/`, or whose tests write files into the tree, the "fix" commit carries build output. The tests don't see it because every fixture gets a `.gitignore` with `target/` (`FixtureRepository.kt:30`). From M5 on these commits go into a PR. Expected: a fix commit contains only the files its edits changed. Edits can only change existing files (`repoFile`), so staging exactly those is enough. The recipe commit keeps `--all`: a recipe can add files.

Outcome: **Fixed.** `GitWorkingTree.commit` takes the files to stage; a fix commits the edited files that Git tracks, the recipe commit its `changedFiles`. Tests: build output stays out of the fix commit, an edit to an ignored file doesn't break the commit, `commit` stages only the given files.

**F2 · Medium · A tool error inside an attempt leaves the model's edits in the working tree.** `FixLoop.attempt` resets only after `judge` returns (`FixLoop.kt:122–129`). If verification throws a `ToolError` after the edits are applied (Maven's one-hour timeout, `MavenAdapter.kt:20`, a failing `git`), `heal` exits 2 with a dirty tree. A hanging test that the model's edit caused is the typical case. Expected: the tree is reset to the last commit on every way out of an attempt. Whether such an error should rather reject the attempt is part of F3.

Outcome: **Fixed.** `FixLoop.attempt` resets the tree on a `ToolError` and rethrows it. Test: a verification that times out after the edits leaves `git status` empty.

**F3 · Medium · The time budget is only checked between attempts.** `stopReason()` reads the clock before each attempt (`FixLoop.kt:115`). One attempt can take up to 15 minutes for Ollama (`OllamaClient.kt:36`) plus up to an hour per Maven run, and `attribute` can run Maven three times (verify, rerun, base). So "30 minutes wall-clock" (spec 7, concept 6.4) is not a limit on the run. A CI job timeout bounds it today, but would then kill the run without a report.

Outcome: **Needs decision** (D1).

**F4 · Medium · A request that doesn't fit is sent anyway, and nobody learns that Ollama cut it.** When errors, code, and earlier attempts alone exceed `MAX_CHARS`, `Prompt.user` sends them in full (`Prompt.kt:62`, tested in `PromptTest:121`). Ollama then shortens the prompt to `num_ctx` on its own, so the model sees only part of the errors or code, and neither the log nor the report says so. A large cluster (a removed class used in 30 files) gets there quickly: ±40 lines per file. Expected: at least a warning in the log with the estimated size. Capping the diagnostics per request is a spec change (decision D3).

Outcome: **Fixed** for the gap; the cap is **Needs decision** (D3). `OllamaClient` logs a warning when `Prompt.fits` says the request is too large. Tests: `Prompt.fits` on both sizes, an oversized request is still sent. The log line itself isn't asserted; the project has no log capture.

**F5 · Low · An `--ollama-url` without `http://` passes the option check and fails after the recipe and a full build.** `URI("localhost:11434")` parses, with `localhost` as the scheme. `HttpRequest.newBuilder` then throws an `IllegalArgumentException`, which `send` doesn't catch (`OllamaClient.kt:69–81`): exit 2 as an unexpected failure, minutes in. Expected: the converter rejects anything but `http`/`https` with a host, as a usage error before anything runs.

Outcome: **Fixed.** The converter accepts only `http`/`https` with a host. Test: four bad URLs are usage errors with exit 2, and the recipe doesn't run.

**F6 · Low · Edits that change nothing are verified with a full build.** A small model sometimes returns `search == replace`. `applyEdits` writes the same content back, and the loop runs Maven to find out that nothing improved. Expected: reject the attempt before verifying ("the edits change nothing").

Outcome: **Fixed.** `applyEdits` returns "the edits change nothing" when every touched file ends up as it was, before anything is written. Test: `search == replace`, and two edits that undo each other.

**F7 · Low · Removing the PR's own package isn't a downgrade.** `downgrades` skips changes where `after` is null (`RecipeStep.kt:148`). For a plugin update, deleting the plugin can make the build green, which is the same cheat Q15 rules out by version. M4 decided against forbidding removals in general (M4 Q7). Needs a decision (D4).

Outcome: **Needs decision** (D2).

### KISS / SOLID / DRY

**F8 · Low · The short-commit length is defined twice (DRY).** `SHORT_COMMIT = 7` in `HealCommand.kt:123` and `FixLoopReport.kt:12`. Better: one internal constant.

Outcome: **Fixed.** One internal `SHORT_COMMIT` in `FixLoopReport.kt`.

### Leftovers

**F9 · Low · Two places read like formatter output, not code.** `ClusterKeys.kt:33–39` (`if (missingPackage !=\n null\n)`) and `FixLoopReport.kt:19–24` (lambda parameters on their own lines). Rewrite them so ktlint keeps them readable (`?.let … ?:`, a named function).

Outcome: **Fixed.** `ClusterKeys` uses a plain `if` on the package match, `FixLoopReport` a named `line` function.

**F10 · Low · A weak assertion.** `assertEquals(false, "symbol: x10" in reason)` (`FixLoopTest.kt:179`). Better: `assertFalse(…, reason)`, which shows the reason on failure.

Outcome: **Fixed.** `assertFalse(…, reason)`.

**F11 · Low · `user.home` is used as non-null.** `System.getProperty("user.home")` (`MavenAdapter.kt:38`) is a platform type; AGENTS.md asks for a clear message instead of an NPE.

Outcome: **Not changed.** `checkNotNull` in the default argument adds a branch that no test can take without clearing `user.home` for the whole test JVM, and Kover fails on it. The JVM always sets `user.home`. Revisit if the adapter ever reads it elsewhere.

## Found while fixing

**F12 · Low · Three fixes first failed Kover on branches no test can take.** `?.let { … } ?: …` on a non-null `let` result (`ClusterKeys`), a second safe call on a non-null value (`?.groupValues?.get(1)`), and the exceptional copy of a `finally` block (the first F2 fix) all compile to branches Kover counts. `checkNotNull` in a default argument (F11) is the same case.

Outcome: **Fixed.** Rewritten as a single safe call with a plain `if`, and as catch-and-rethrow. Lesson harvested into `AGENTS.md`.

**F13 · Low · Making a fix commit stage only the edited files would break on an edit to a Git-ignored file.** `git add` of an ignored path fails, which would end `heal` with exit 2 (generated code under `target/` is the likely case until M4's `editableScope`).

Outcome: **Fixed.** Only tracked files are staged (normalized, so `./App.java` counts); the test edits an ignored file and proves the filter: without it, the test fails.

**F14 · Low · A failing real-model run hid its cause.** `RealModelTest` parsed stdout as JSON before it looked at the exit code, so `heal`'s exit 2 showed up as `JsonDecodingException: unexpected end of the input`, and the error stayed in the captured stderr.

Outcome: **Fixed.** The test first asserts a non-empty stdout, with the exit code and stderr as the message. The rerun showed the real cause: Ollama's model server was killed for lack of memory in the shared 8 GB Docker VM. The `:cli:integrationTest` run passed (2 tests).

### Decisions

- **D1 (F3) · How hard is the time budget?** (a) Keep it as "no attempt starts after 30 minutes" and say so in the spec and concept; CI's job timeout is the hard stop. (b) Give the adapter's `verify` and `runTests` a deadline from the remaining budget, and let a timeout reject the attempt and stop with `TIME_BUDGET`, with the report intact. *Recommended: (b) in M5,* where the job timeout and a lost report start to matter; (a) for now, with the wording fixed.
- **D2 (F7) · Is removing the PR's own package a downgrade?** (a) Yes: reject an attempt after which the PR's package no longer resolves. (b) No, leave removals to review, as M4 Q7 decided for dependencies in general. *Recommended: (a),* only for the PR's own package. It is the same cheat as reverting the bump, and M4 Q7's concern (legitimate removals of other dependencies) doesn't apply to it.
- **D3 (F4) · What happens when errors and code don't fit?** (a) Keep sending them in full, with the warning. (b) Send only as many diagnostics of the cluster (and their snippets) as fit; the rest come back in the next round, because clusters are rebuilt after every accepted attempt. *Recommended: (b),* measured in the benchmark (M6). It changes spec 4 ("never trimmed").

## What's good

- The attempt's lifecycle is easy to follow: apply → downgrade check → verify → commit or reset, each step returning a reason string that ends up in both the report and the retry request.
- The stage tuple as a `Comparable` makes AC25 a one-line comparison, and the feedback of "errors after the edits" is a measured improvement (progress.md), not a guess.
- `ground` stays in the adapter; `core` still knows no language, LLM vendor, or Maven (AC5 holds by grep).
- `FixLoopTest` runs on a real temporary Git repository with a fake adapter, so commits and resets are tested for real, without Maven.
- The `analysis` field and the English locale are documented where they matter (schema KDoc, build file, progress.md), with the reason.
