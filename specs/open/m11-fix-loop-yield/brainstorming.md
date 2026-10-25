# M11 – Fix-loop yield · Brainstorming

Milestone: [M11 in features.md](../../../docs/features.md) (new, Q1) · Status: **Closed**

**How to answer:** the agent asks these questions in chat; this file has the full options and records the answers. The recommended option is preselected with `[x]`.

## Overview

A local comparison of three models (qwen2.5-coder 7B, 14B, qwen3-coder 30B; 8 Maven cases, fix loop only, one run each) found
that a bigger model barely helps: 2, 3, and 2 of 8 green. The logs show why: the tool throws away work. A correct fix
was rejected three times because the progress check only counts errors (dledger: the fix let Maven reach GPG signing,
which isn't installed; flyway-9: the fix uncovered the next removed method in the same call chain). 12 of 16 retries
were identical to the rejected attempt before them, and each cost a build and ended the loop after two. This milestone
makes the fix loop keep what works and stop wasting attempts. Evidence: [comparison.md](comparison.md). What exists: the fix loop (M3, [concept 6](../../../docs/concept.md#6-the-fix-loop)), attribution
([concept 5](../../../docs/concept.md#attribution-what-did-the-update-break)), the benchmark (M6). What comes after:
generated rewrite rules (today's M11), and the choice of the default model, both on the benchmark's results.

**Key concepts**

- **Progress** — when an attempt is kept: today, only if the counts of (build errors without a location, compile errors, test failures) caused by the update get lower, compared in that order ([concept 6.4](../../../docs/concept.md#64-budgets)).
- **Unmasked error** — an error that was there all along but hidden behind another one: javac reports one error per call chain, Maven skips modules that depend on a failed one, and later build steps (signing, packaging) only run once compilation passes.
- **Build error without a location** — a failure of the build tool itself, not of a source line: a missing artifact, a plugin that fails (GPG signing, a linter bound to `verify`).
- **Attribution** — deciding which failures the update caused. Today a failing test is checked on the base commit; compile and build errors always count as the update's.
- **Retry** — the next attempt on the same error group after a rejected one. It gets the rejected edits and the reason.
- **Apply failure** — an edit that can't be applied: its search text isn't in the file, matches more than once, or names a file Git doesn't track.

```
 verify head ──▶ attribution [existing] ──────────────────────────▶ errors caused by the update
                   └─ (new) build errors checked on the base commit too   Q3
                                                                    │
                                                                    ▼
 ┌──────────────────────────── fix loop [existing] ─────────────────────────────┐
 │ ask the model ──▶ (new) same answer as a rejected one? re-ask, don't build  Q6 │
 │       │                                                                       │
 │       ▼                                                                       │
 │ apply edits ──fails──▶ reason back to the model (new: precise feedback)   Q4  │
 │       │                                                                       │
 │       ▼                                                                       │
 │ guardrails ──▶ verify + attribution ──▶ progress? (new: unmasked errors)   Q2 │
 │                                           ├─ yes ─▶ commit                    │
 │                                           └─ no ──▶ reset, retry              │
 └───────────────────────────────────────────────────────────────────────────────┘
                                                                    │
                                                                    ▼
 report ──▶ (new, optional) the best rejected attempt as a suggestion  Q4
 benchmark rerun: 7B before/after, 14B after                          Q5
```

**In scope:** the fix loop, attribution of build errors, the Maven verify call, the report, the benchmark workflow's model input. · **Out of scope:** a bigger or different default model (decided on this milestone's benchmark), TypeScript- or Gradle-specific changes, generated rewrite rules.

## Already fixed by the project docs

- Guardrails reject, they don't repair (rule 4): an edit that can't be applied stays a failed attempt; the tool never patches the model's search text.
- No LLM in deterministic steps (rule 2): progress, attribution, duplicate detection, and feedback are computed without the model.
- Never auto-merge (rule 5): a suggestion in the report is never committed or pushed as code.
- Only failures caused by the update reach the fix loop (concept 5); pre-existing failures are reported, never fixed.
- Budgets stay configurable on the CLI (`--max-attempts-without-progress` and the others, M3 Q11).
- Partial results are pushed: accepted fixes are committed even if the run ends red (concept 8.3).
- The benchmark measures each case in three modes, model modes three times (concept 11, M6); a case counts only if its head has a failure caused by the update.
- The Gradle adapter already runs only `Test` tasks, not linters (M7 Q17); this milestone doesn't change Gradle.

---

## Round 1

### Q1 · Placement in the milestones

`docs/features.md` plans M11 as generated rewrite rules and says to prioritize after M11 by benchmark results. This work comes from benchmark evidence and makes every later measurement more meaningful.

- [x] **A · Insert as M11 "Fix-loop yield"; generated rewrite rules become M12** — *recommended.* The fix loop's waste distorts every benchmark, including the one that will judge generated rules.
- [ ] **B · Append as M12 after the generated rewrite rules** — Keeps the plan's order; the rules would be measured on a loop that throws away work.

**Notes:**

### Q2 · When an attempt counts as progress

Today an attempt is kept only if the error counts drop. A correct fix that uncovers a hidden error elsewhere keeps the counts equal, or raises them, and is thrown away (flyway-9, 30B). Concept 6.4 changes with this.

- [x] **A · Also keep an attempt when every error it was sent is gone, no new error lies in a line it changed, no new build error without a location appears, and the counts don't rise** — *recommended.* Catches flyway-9 (one error in, one other error out, in a line the model didn't touch). A wrong fix that only moves the error into its own lines (flyway-9, 7B: a different removed method in the edited line) is still rejected. Doesn't catch a fix that uncovers more errors than it removes.
- [ ] **B · Like A, but the counts may rise** — Also catches unmasking across modules (Maven skips modules that depend on a failed one; fixing it can reveal ten errors). Risk: an edit that breaks callers elsewhere (a changed signature) is kept, and the loop has to fix its own damage.
- [ ] **C · Keep today's rule** — Safe and simple; correct partial fixes keep being thrown away.

**Notes:**

### Q3 · Build errors from later build steps

dledger: the correct fix let `mvn verify` get past compilation to GPG signing, which fails because `gpg` isn't installed. A build error without a location always counts as the update's and weighs more than any compile error, so the fix is rejected and the case can never turn green. Linters bound to `verify` have the same effect (open point in `features.md`, "Later"). Concept 5 changes with A and C.

- [x] **A · Check build errors on the base commit too, and skip signing in Maven's verify (`-Dgpg.skip`)** — *recommended.* A build error that the base commit has too is pre-existing, like a failing test today: reported, not fixed. Works for any ecosystem and any plugin. Costs one extra build of the base commit, only in runs whose head has a build error without a location, once per run. Skipping signing avoids that build in the most common case; signing is never part of what an update can break.
- [ ] **B · Only skip signing (`-Dgpg.skip`)** — Fixes dledger, nothing else. Linters, enforcer rules and other environment failures still block.
- [ ] **C · Only check build errors on the base commit** — General, but pays the extra base build every time a project signs in `verify`.
- [ ] **D · Stop Maven's verify after the tests, like Gradle (M7 Q17)** — No packaging, signing, or linting. But integration tests bound to Maven's lifecycle (pre-integration-test setup, packaged JARs) would stop running.

**Notes:**

### Q4 · What else joins this milestone

The logs show more waste than Q2, Q3, and the duplicate retries. Pick what belongs in M11; the rest goes to "Later".

- [x] **A · Precise feedback on apply failures** — *recommended.* 6 rejected attempts in the logs failed to apply, and the model couldn't correct itself: "the search text is not in the file" names no edit and shows no real lines; "matches 2 times" names no lines; a guessed path gets "Git doesn't track the file". The feedback names the edit and shows what's really there.
- [x] **B · The best rejected attempt as a suggestion in the report** — *recommended.* When a run ends red, the report shows the attempt that got furthest as a diff marked "not applied, needs review". The reviewer would have had the correct dledger fix. Never committed.
- [ ] **C · The full class name of a missing class as a hint** — flyway-7, 7B: right fix, missing import, identical retry. The new version's JAR knows the class's package. One case in the logs.
- [ ] **D · The test's source file for a failing test without a location** — kafka: the model never saw the test's code and guessed its path. Needs a test-class-to-file mapping per adapter.
- [ ] **E · Raise the default of attempts without progress from 2** — Runs ended after about 1 minute of a 30-minute budget. On CI's CPU runners each attempt takes minutes, so the cost is real; with duplicate retries gone, the 2 attempts are real attempts.

**Notes:** multi-select. Answered in chat: A and B. In addition: "Please make sure that the model knows about the previous failed attempts so that it doesn't make the same errors again." Today every retry already carries the rejected attempts on its error group with their edits and reason, and the model still repeated itself (12 of 16). So the spec makes it a requirement that every kind of failed attempt is in that history with its precise reason (apply failures with Q10's detail, guardrail rejections, invalid answers, re-asked repeats from Q6), never trimmed, and tests it. C, D, E go to "Later".

### Q5 · When the milestone is done

There is no benchmark result on record yet (`benchmark/results/` doesn't exist); the repository is now public, so CI runners are free and have 4 CPUs and 16 GB, where the 14B fits.

- [x] **A · Before/after on CI: full benchmark with the 7B on `main` before and on the branch after, plus the 14B after. Done when the 7B's green runs in the fix-loop modes rise, and no case green before turns red in all three repetitions** — *recommended.* Shows the effect of the milestone apart from the model, and gives the data for the default-model choice. Two full 7B runs plus one 14B run.
- [ ] **B · Only after: 7B and 14B on the branch, no threshold** — Cheaper by one run; nothing to compare the effect with.
- [ ] **C · Local runs as in the comparison** — Fast, but not the benchmark's runner, and one repetition.

**Notes:**

### Q6 · An answer identical to a rejected one

12 of 16 retries were identical, even at temperature 0.3 and with "Don't repeat them" in the prompt.

- [x] **A · Detect it before applying; ask again once in the same attempt, at a higher temperature (0.7) and with a note that this exact answer was rejected; if the second answer is a repeat too, reject the attempt without building** — *recommended.* Costs one more model call, saves a build, and gives the model a second chance with a different sample.
- [ ] **B · Reject without building, no re-ask** — Saves the build, but the attempt is still lost.
- [ ] **C · Only raise the retry temperature** — Simplest, but the edits may still repeat, and a build is spent on each.

**Notes:**

### Q7 · What counts as identical

- [x] **A · The same edits (file, search, replace), ignoring their order and whitespace at line ends, as any earlier rejected attempt on the same error group** — *recommended.* Catches repeats of any earlier attempt, not only the last; trailing whitespace differences aren't a new idea.
- [ ] **B · Byte-identical to the previous attempt only** — Simplest; misses A→B→A.

**Notes:**

### Q8 · Matching a build error to the base commit (if Q3 A or C)

The base commit is built in a separate worktree, so absolute paths in messages differ.

- [x] **A · Same tool and same message, with the checkout's root path replaced in both** — *recommended.* Paths are the only systematic difference between the two builds.
- [ ] **B · Same message, byte for byte** — Simplest; fails on every message with a path.

**Notes:**

### Q9 · When the base commit's build runs (if Q3 A or C)

- [x] **A · Only when the head has a build error without a location, at most once per run, its result reused for every later attempt; without a base commit, build errors count as today** — *recommended.* The base commit doesn't change during a run; most runs pay nothing.
- [ ] **B · Always at the start of the run** — Simple, but doubles the build time of every run.

**Notes:**

### Q10 · Content of the apply-failure feedback (if Q4 A)

- [x] **A · Search text not found: name the edit and show the lines of the file most similar to its search text, with line numbers. Found several times: the line numbers of every match. A file that doesn't exist: say so, and name the tracked file with the same name if there is exactly one** — *recommended.* Covers the three failures in the logs with what the model needs to correct its edit.
- [ ] **B · Name the failing edit only** — Less to build, but the model still can't see the real text.

**Notes:**

### Q11 · Which rejected attempt the report suggests (if Q4 B)

- [x] **A · Only if the run ends with failures caused by the update left: of the attempts that passed the guardrails and were built, the one with the lowest counts after it (ties: the later one); shown collapsed in the PR comment and in the CLI's text and JSON reports** — *recommended.* An attempt that broke a guardrail is never shown: it broke a rule. One suggestion keeps the comment short.
- [ ] **B · Every rejected attempt that was built** — More material, a much longer comment.

**Notes:**

### Q12 · The 14B on the benchmark runner (if Q5 A or B)

- [x] **A · A `model` input on the Benchmark workflow (default the 7B); the 14B comes as an Ollama image with the weights baked in, like the 7B; if its pull doesn't fit the runner's free disk, the run pulls the model in a step after freeing disk space** — *recommended.* Same path as the 7B; I'm not sure the 9 GB image fits next to the runner's free disk during the pull, so the fallback is named up front.
- [ ] **B · Always pull the model in a step** — Works regardless of disk, but measures a setup the product doesn't ship.

**Notes:** the blind second agent also picked A, with low confidence: the service image is pulled before any step can free disk, so the fallback is likely needed, and 16 GB is tight next to a Maven build. Answered in chat: A.

---

## Decided by the agent

Questions the triage found agent-decidable and an independent second agent answered the same way. Veto any of them by ID.

| # | Topic | Decision | Reason |
|---|---|---|---|
| Q6 | Identical answer | A: detect before applying, re-ask once at 0.7 with a note; a second repeat is rejected without a build | 12 of 16 retries repeated; one model call is cheaper than a build, and re-asking isn't repairing (rule 4) |
| Q7 | What counts as identical | A: same edits, any order, trailing whitespace ignored, against every earlier rejected attempt on the group | Catches A→B→A too; deterministic and cheap |
| Q8 | Matching base build errors | A: same tool and message, checkout root replaced in both | Paths are the only systematic difference between the two worktrees |
| Q9 | When the base build runs | A: only when the head has a build error without a location, once per run, reused; without a base, as today | Concept 5: the base is built only when needed |
| Q10 | Apply-failure feedback | A: name the edit; most similar lines with numbers; line numbers of every match; "no such file" plus the one tracked file with that name | Each part answers a failure in the logs; the tool reports facts, never applies a near match |
| Q11 | Suggested attempt | A: only when the run ends red; the built, guardrail-clean rejected attempt with the lowest counts (ties: later); collapsed in PR comment, text and JSON report | One suggestion is useful, all of them are noise; never one that broke a rule |

---

## Decisions

### Round 1

All answered in chat (the file wasn't saved from the editor).

| # | Topic | Decision |
|---|---|---|
| Q1 | Placement | New M11 "Fix-loop yield"; generated rewrite rules become M12 |
| Q2 | Progress | Also keep an attempt when every error it was sent is gone, no new error lies in a line it changed, no new build error without a location appears, and the counts don't rise. Concept 6.4 changes |
| Q3 | Build errors | Build errors without a location are checked on the base commit too (pre-existing if there as well); Maven's verify skips signing (`-Dgpg.skip`). Concept 5 changes |
| Q4 | Extra scope | Precise apply-failure feedback; the best rejected attempt as a suggestion in the report. Plus (user): the model knows every earlier failed attempt on its errors, so it doesn't repeat them. Missing-class hint, test-source mapping, and the budget default go to "Later" |
| Q5 | Done when | Before/after on CI: full benchmark with the 7B on `main` and on the branch, plus the 14B on the branch; done when the 7B's green runs in the fix-loop modes rise and no case green before is red in all three repetitions |
| Q6–Q11 | (agent) | See "Decided by the agent" |
| Q12 | 14B on the runner | `model` input on the Benchmark workflow; the 14B as a baked-in Ollama image; if its pull doesn't fit, pull the model in a step after freeing disk |

---

## Additions from the spec critique

A fresh agent critiqued the spec against the docs and the code (20 findings). Fixed directly in the spec:

| # | Finding | Correction in the spec |
|---|---|---|
| R2 | "No error in a changed line" dropped "new": an unsent error that was there before would block a correct fix | Only errors that weren't there before count (section 1, AC3) |
| R3 | One failing test swapped for another with equal counts would be committed | The unmasking rule applies only when every sent error is a compile error; tests keep counts-only (section 1, AC6). Narrows Q2 to what its reason covers |
| R4 | Attribution runs in several places (first verify, after the recipe, the loop, `verify`, `migrate`, the benchmark); "once per run" was undefined | "Run" defined; the base result is shared by every attribution of a run (Terms, section 2, AC10) |
| R5 | A base build that times out inside an attempt would end the run with exit 2 | "Fails to run" defined; the failure is reused and never ends the run; a base that stops earlier counts the head's later build errors (section 2, AC12) |
| R6 | Real paths (`/private/var/…` on macOS) and volatile output tails would never match | Both path forms replaced; volatile messages named as a limit (section 2, AC11) |
| R7 | Repeat detection against rejections made on an older commit would block an edit that now works | Compared only since the last accepted attempt; multiset of edits (section 4, AC16) |
| R8 | Suggestion candidates on older commits; counts across commits not comparable | Only attempts on the last commit, ordered stage by stage, diff taken before the reset (section 7, AC22) |
| R9 | A missing file is a scope violation today, not a failed edit | Checked before the scope guardrail as a failed edit; untracked existing files stay a scope violation (section 5, AC20) |
| R10 | "Most similar lines" undecidable by a test; "every match" broke the AGENTS list rule | Window rule with ties and a cap; first five matches and a count; the committed file's line numbers (section 5, AC18, AC19) |
| R11 | `pr` has no JSON report; a diff with backticks breaks the comment | `heal`'s text and JSON plus the PR comment; fence longer than any backtick run (section 7, AC23) |
| R12 | The re-ask path left open what the attempt records and where the note goes | The judged answer is recorded with "the first answer repeated attempt N"; the note is never trimmed and counted by the fit check (section 4) |
| R13 | AC15 (history) contradicted itself and couldn't run under the default budgets | Raised budgets, five kinds, the sixth attempt's request (AC21); the note moved to AC14 |
| R14 | Before/after not decidable: `main` moves, valid cases differ, "green before" undefined, after runs might not measure the final code | Pinned `2f543e1`, cases valid in both, "green in at least one repetition", tool commit with every production change (section 8, AC26, AC28, AC29) |
| R17 | The GPG integration test would pass on a runner that has `gpg` | Fixture points `gpg.executable` at a missing program; the flag sits in the shared Maven command line (section 3, AC13) |
| R18 | AC22's diff missed the Gradle init scripts and depended on the shell | Quoted paths including `*.gradle` resources (AC31) |
| R19 | The real tool wording the tests need wasn't in the repository | [evidence.md](evidence.md) with the verbatim excerpts, flyway-9's initial error reproduced (all unit ACs) |
| R20 | Equal-count acceptance could swap errors back and forth until the cluster budget | An error an earlier accepted attempt removed must not come back (section 1, AC7) |
| — | Concept 8.3, requirements without a criterion, AC16 with four facts | 8.3 in "Docs to update"; AC12, AC17, AC24 added; AC16 split into AC22–AC24 |

Escalated and answered by the user in chat:

| # | Question | Decision |
|---|---|---|
| R1 | A build error on both commits that stops the build before the tests would end the run green | Pre-existing only if the head's verification ran tests; otherwise caused by the update, as today (section 2, AC9) |
| R15 | More permissive progress could raise green runs with worse fixes | The user reviews both 7B runs (`review.json`); the share accepted unchanged must not fall (AC27, AC30) |
| R16 | The 14B may not produce data on CPU runners | Informational; below half valid is recorded as "doesn't fit CPU runners"; no rerun (section 8) |
| Size | 32 criteria against a target of about 15; only the suggestion could ship alone | Keep it in M11 |
