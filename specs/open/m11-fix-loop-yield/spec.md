# M11 – Fix-loop yield · Spec

Milestone: M11 in [features.md](../../../docs/features.md) (new; generated rewrite rules move to M12) · Decisions: [brainstorming.md](brainstorming.md), including the additions from the spec critique and the user's answers to R1, R15, R16 and the size question · Evidence: [comparison.md](comparison.md), real tool output in [evidence.md](evidence.md) · Status: **Ready to implement**

Milestone base for every diff below: `2f543e1` (`origin/main` when the milestone started).

## Goal

The fix loop keeps correct partial fixes, doesn't count environment failures against the update, and stops spending builds on answers it already rejected. The benchmark shows the effect.

**Why:** a local comparison of 7B, 14B, and 30B models found 2, 3, and 2 of 8 cases green: the model's size barely mattered. A correct fix was rejected three times by the count-only progress check (dledger with every model, flyway-9 with the 30B), and 12 of 16 retries repeated the rejected attempt before them, each costing a build and ending the loop after two. Every later measurement, M12's generated rules and the choice of the default model included, depends on a loop that doesn't throw away work.

## Terms

- **Same error:** two diagnostics with the same tool, file, test, and message. The line may differ: an edit above an error moves it.
- **Run:** one invocation of `lector` (`verify`, `migrate`, `heal`, `pr`), or one benchmark run of one case, mode, and repetition.
- **Changed lines:** the lines of the edited file that an attempt's replace texts occupy after the edits. A replace text's final line break ends its last line; it doesn't make the next line a changed one. An edit whose replace text is empty changes the line where its search text began (R2 critique, fidelity check).
- **Counts:** the three numbers of failures caused by the update (build errors without a location, compile errors, test failures), compared stage by stage in that order as in concept 6.4.

## What to build

### 1. Unmasked compile errors count as progress

- An attempt is kept, as today, when the counts get lower (concept 6.4).
- It is also kept when every error it was sent is a compile error and all of these hold (Q2, R3):
  - every error it was sent is gone after it (same-error rule);
  - no error after it that wasn't there before lies in a changed line (R2);
  - no build error without a location is caused by the update after it that wasn't before;
  - none of the three counts is higher than before;
  - no error that an earlier accepted attempt of the run removed is back (R20).
- Test failures keep today's rule: only lower counts are progress (R3).
- Everything else about an accepted attempt is unchanged: one commit, the cluster rebuild, the no-progress count reset. The progress feedback of a rejected attempt is unchanged.

**Why:** javac reports one error per call chain, so a correct fix can uncover the next error with the count unchanged (flyway-9, 30B: the error moves from line 700, which it replaced, to line 701, which it didn't; see evidence.md). A wrong fix leaves the error in its own lines (flyway-9, 7B) and stays rejected. Counts that rise stay rejected, so an edit that breaks callers elsewhere isn't kept (Q2 B rejected). Masking explains equal counts for compile errors only; for tests, one fixed and another broken would be a regression (R3). An error that comes back isn't unmasked, and without that check two attempts could swap errors back and forth until the cluster budget (R20).

### 2. Build errors are checked on the base commit

- When a verification in a run has a build error without a location, and a base commit is known, the base commit is verified, in a separate worktree as today, at most once per run. Its result is reused by every later attribution of the run: the first verification, after the recipe, every attempt (Q9, R4). The run creates its base commit once and hands the same one to every attribution; that base commit remembers what its verification found, a failure included.
- A head build error without a location that the base also has is **pre-existing**, but only if the head's verification ran tests (the adapter's tests run is above 0). Then it is reported with the other pre-existing failures, is not caused by the update, and is never sent to the model. If the head ran no tests, or the adapter can't tell, the build error counts as caused by the update, as today (R1, user).
- Two build errors are the same if their tool is the same and their messages are the same after the checkout's root path is replaced in both, as given and as its real path (on macOS the temporary worktree also appears under `/private`) (Q8, R6). Messages that carry volatile text (the "unexplained failure" messages that end with the build output's tail: times, timestamps) don't match; their build errors count as caused by the update (R6).
- The base verification **fails to run** when its checkout or build throws a tool error or times out. Then, and without a base commit, build errors count as caused by the update, as today. The failure is reused for the rest of the run; it never ends the run, a timeout included (R5).
- A base whose build stops earlier than the head's (compile errors on the base) has none of the head's later build errors: those count as caused by the update (R5).
- Compile errors are never checked on the base: they always count (concept 5).
- This applies to every ecosystem and to `verify` and `migrate` as well; their exit codes follow the attribution as today (pre-existing failures exit `0`).

**Why:** a plugin that fails for reasons of the environment (GPG signing without `gpg`, a linter that already fails on the base) blocked every attempt in dledger, because a build error weighs more than any compile error. The rule is the one failing tests already follow (Q3). Requiring tests to have run keeps a build error that stops everything before the tests from turning a broken update green (R1).

### 3. Maven doesn't sign

- Every Maven command lector runs passes `-Dgpg.skip=true` (the shared Maven command line: verify, test reruns, the base commit's builds, recipe runs, fetches) (R17).

**Why:** signing is never part of what an update can break, and skipping it saves the base build of section 2 in the most common case (Q3).

### 4. A repeated answer is asked again, not built

- Before an answer is applied, it is compared with the rejected attempts on the same cluster since the last accepted attempt of the run (R7). It is a repeat if it has the same edits (file, search, replace) as one of them, as a multiset in any order, with whitespace at line ends ignored (Q7).
- A repeat is not applied. The model is asked once more in the same attempt, at temperature 0.7, with a note that this exact answer repeated attempt N and why that was rejected. The note is a part of the request that is never trimmed, and the fit check counts it (Q6, R12).
- The attempt records the answer that was judged, and its reason starts with "the first answer repeated attempt N". If the second answer is a repeat too, the attempt is rejected without a build, with a reason that says so. Either way it counts as an attempt and, if rejected, toward the attempts without progress (Q6, R12).
- Invalid answers (no edits, not JSON) are never repeats; they are rejected as today.

**Why:** 12 of 16 retries repeated the rejected attempt at temperature 0.3, with "Don't repeat them" in the prompt. A model call is far cheaper than a build, and asking again doesn't repair the edit (rule 4). A rejection is a fact about one commit: after an accepted attempt, the same edit may succeed (R7).

### 5. Precise feedback when an edit can't be applied

- The file is the committed one the request showed, so line numbers match the code the model saw (R10).
- **Search text not in the file:** the reason names the edit (its number in the answer and its file) and shows the most similar lines with their line numbers: the window of consecutive lines, as long as the search text but at most 20 lines, in which the most of the search text's lines equal the window's line at the same position, ignoring indentation and whitespace at line ends. Ties: the earliest window. None if no line equals (Q10, R10).
- **Search text found several times:** the reason gives the line numbers of the matches: the first five, and the count of the rest (R10, AGENTS lists).
- **A file that doesn't exist:** a failed edit, checked before the scope guardrail. The reason says there is no such file, and names the tracked file with the same name if there is exactly one. A file that exists but Git doesn't track stays a scope violation (R9).
- The tool never applies a near match: the attempt stays rejected (rule 4).

**Why:** six attempts in the logs failed to apply, and the retries couldn't correct them: the old reasons named no edit and showed no real text (pdfbox, easy-rules, kafka, metrics; evidence.md). A guessed path was reported as "Git doesn't track the file", a guardrail rejection, which also counted in the rejections per guardrail (R9).

### 6. The model sees every failed attempt on its errors

- Every request on a cluster carries every earlier failed attempt on that cluster, of every kind: an edit that couldn't be applied, a guardrail rejection, an attempt without progress, an invalid answer, an attempt rejected after two repeats. Each comes with its edits (if any) and its reason, including the detail of section 5 and the errors after the edits.
- The earlier attempts are never trimmed to fit the context (as today).

**Why:** the user asked that the model know its earlier failures so it doesn't repeat them (Q4). Today's history already holds most of this; the requirement makes it complete and tested.

### 7. The best rejected attempt as a suggestion

- Only when the run ends with failures caused by the update left. The candidates are the rejected attempts made on the run's last commit (after its last accepted attempt) that passed the guardrails and were built. Of those, the one with the lowest counts after it, compared stage by stage, is suggested; ties: the later one (Q11, R8).
- Its diff is taken from the working tree before the reset, and shown marked "not applied, needs review" (R8).
- It is in `heal`'s text and JSON reports and in the PR comment, collapsed (the PR comment is also `pr`'s output and the companion PR's description). The diff's code fence is longer than the longest run of backticks inside it (R11).
- It is never committed or pushed. An attempt that broke a guardrail, couldn't be applied, or wasn't built is never suggested. A run that ends green has no suggestion.

**Why:** the reviewer would have had the correct dledger fix; partial work that didn't meet the bar for a commit still helps a person (user, Q4). An attempt on an older commit may not apply to the pushed head, and counts on different commits aren't comparable (R8). `pr` has no JSON report, and a new output format isn't needed for this (R11).

### 8. The benchmark measures the effect

- The Benchmark workflow gets a `model` input, default `qwen2.5-coder:7b`, used for the Ollama image and the runs (Q12).
- `qwen2.5-coder:14b` comes as an Ollama image with the weights baked in, like the 7B. If its service image doesn't fit the runner's free disk during the pull, the workflow pulls the model in a step after freeing disk space, for that model only.
- Three full benchmark runs on GitHub-hosted runners, each committed under `benchmark/results/<date>-<label>/` with its `results.json` and `report.md` (Q5, R14):
  - `7b-before`: the 7B at tool commit `2f543e1`;
  - `7b-after`: the 7B at a tool commit with every production change of the milestone;
  - `14b-after`: the 14B at the same tool commit as `7b-after`.
- Both 7B runs get a `review.json` by the user, following M6's procedure (R15, user).
- The 14B run is informational: whatever share of its runs is valid is its result for the default-model choice. Below half valid, `report.md` or the milestone's docs record that the 14B doesn't fit CPU runners. It is not rerun (R16, user).

**Why:** no benchmark result is on record yet; only a before/after with the same model shows the milestone's effect apart from the model. The new progress rule keeps more attempts, so green runs alone can't tell better from more permissive: the review can (R15). The repository is public, so the runners are free and have 16 GB, where the 14B should fit, but 4 CPUs may be too slow for it (R16).

## What not to do

| Don't | Why |
|---|---|
| Keep attempts whose counts rise | An edit that breaks callers elsewhere would be kept (Q2 B rejected) |
| Apply the unmasking rule to test failures | One test fixed and another broken would be committed as progress (R3) |
| Check compile errors on the base commit | Code that compiled before and doesn't after is the update's doing (concept 5) |
| Treat a build error as pre-existing when the head ran no tests | A build that stops before the tests would let a broken update end green (R1, user) |
| Stop Maven's verify after the tests | Integration tests bound to Maven's lifecycle would stop running (Q3 D rejected) |
| Skip other Maven plugins (linters, enforcer, javadoc) by flag | A list that is never complete; section 2 covers pre-existing failures |
| Apply a near match of a search text, or patch the model's edits | Guardrails and edit application reject, they don't repair (rule 4) |
| Suggest more than one attempt, or one that broke a guardrail | Noise, and a rule-breaking edit must not reach a person as a proposal (Q11) |
| Commit or push the suggestion | Never auto-merge; it didn't meet the bar for a commit (rule 5) |
| A JSON report for `pr` | A new output contract nothing needs; the PR comment carries the suggestion (R11) |
| The full class name of a missing class as a hint | One case in the logs; "Later" (Q4 C) |
| Map a failing test without a location to its source file | Needs a mapping per adapter; "Later" (Q4 D) |
| Change budget defaults (attempts without progress stays 2) | Each attempt costs minutes on CPU runners; measured again after this milestone (Q4 E) |
| Change the default model or the released Ollama images | Decided on this milestone's benchmark, not in it |
| Change the Gradle or TypeScript adapters | Gradle already runs no linters (M7 Q17); nothing in the evidence is specific to them. Section 2 lives in `core` and reaches them without adapter changes |
| Rerun the 14B until it produces data | Informational run (R16, user) |

## Acceptance criteria

The review agent checks every criterion and reports it as *met*, *not met*, or *can't verify*, with evidence. Unit tests use the real tool wording in [evidence.md](evidence.md) (AGENTS, R19).

| ID | Criterion | Covers | How to verify |
|---|---|---|---|
| AC1 | An attempt whose sent compile error is gone, with one new compile error in a line it didn't change and equal counts, is accepted and committed | 1 | A `core` unit test with flyway-9 30B's messages and edit passes; the commit exists |
| AC2 | An attempt whose new compile error lies in a changed line is rejected with "no progress" | 1 | A `core` unit test with flyway-9 7B's messages and edit passes |
| AC3 | An unsent error that was there before and is still there in a changed line doesn't block the attempt | 1 | A `core` unit test: 2 errors, 1 sent, the edit spans the unsent one's line; accepted |
| AC4 | An attempt that removes its sent compile errors but adds a build error without a location caused by the update is rejected | 1 | A `core` unit test passes |
| AC5 | An attempt that removes its sent compile errors but raises a count is rejected | 1, Don't | A `core` unit test (1 error out, 2 elsewhere in) passes |
| AC6 | An attempt sent a failing test, after which that test passes and another test fails, with equal counts, is rejected | 1, Don't | A `core` unit test passes |
| AC7 | An attempt after which an error removed by an earlier accepted attempt is back is not kept by the unmasking rule | 1 | A `core` unit test with two attempts that swap errors: the second is rejected |
| AC8 | A head build error without a location that the base also has is pre-existing and in no request, when the head ran tests | 2 | A `core` unit test with dledger's GPG message: it is in the pre-existing failures, not in those caused by the update, and no request contains it |
| AC9 | The same build error counts as caused by the update when the head ran no tests | 2, Don't | A `core` unit test with the adapter's tests run at 0 and at unknown |
| AC10 | The base is verified at most once per run, across the first verification, the recipe, and every attempt, and not at all without a build error without a location | 2 | Unit tests count base verifications: 0 in a `heal` run without build errors; 1 in a `pr` run whose first verification and three attempts each have one |
| AC11 | Two GPG build errors whose messages differ only in the checkout root, given once as `/var/…` and once as `/private/var/…`, match | 2 | A `core` unit test with dledger's message |
| AC12 | Build errors count as caused by the update without a base commit, when the base verification times out, and when it throws a tool error; the run doesn't end | 2 | A `core` unit test per case; the timeout case leaves budget and finishes with a report, and the base isn't tried again |
| AC13 | Every Maven command lector runs passes `-Dgpg.skip=true` | 3 | A unit test on the shared command line; `:cli:integrationTest` has a fixture whose `verify` binds `maven-gpg-plugin` with `gpg.executable` set to a program that doesn't exist, and it verifies green |
| AC14 | A repeated answer is not applied: the model is asked once more at temperature 0.7 with the note naming attempt N, and no build runs for the repeated answer | 4 | A `core` unit test with a fake `LlmClient` that repeats once: two requests in the attempt, the second at 0.7 with the note; no verify for the repeat |
| AC15 | Two repeats in one attempt reject it without a build, and it counts toward the attempts without progress | 4 | A `core` unit test: the reason says it repeated a rejected answer, no verify ran in the attempt, the loop stops with `noProgress` after the budget of 2 |
| AC16 | A repeat is detected regardless of edit order and whitespace at line ends, against any rejected attempt on the cluster since the last accepted one, and not against an attempt before it | 4 | A `core` unit test with answers A, B, A′ (A reordered, trailing spaces) is a repeat; after an accepted attempt, A again is applied |
| AC17 | An invalid answer is never treated as a repeat | 4 | A `core` unit test with two invalid answers in a row: both rejected as invalid, no re-ask |
| AC18 | A search text not in the file is rejected with the edit's number, its file, and the most similar lines with their line numbers | 5 | A `core` unit test with pdfbox 30B's import edit: the reason shows the window with the file's real `import java.io.File;` and `import java.io.IOException;` lines and their line numbers |
| AC19 | A search text found several times is rejected with the line numbers of the first five matches and the count of the rest | 5 | `core` unit tests with 2 matches (metrics 14B's edit) and with 8 matches |
| AC20 | An edit of a missing file is rejected as a failed edit "no such file", naming the one tracked file with that name, and isn't counted as a guardrail rejection | 5 | A `core` unit test with kafka 30B's path: the reason names `src/test/java/io/dropwizard/kafka/integration/DropwizardKafkaIT.java`; `rejectionsPerRule` is empty |
| AC21 | A request carries every earlier failed attempt on its cluster, one of each kind, each with its reason | 6 | A `core` unit test with raised budgets and five rejected attempts on one cluster (not applied, guardrail, no progress, invalid, rejected after two repeats): the sixth attempt's first request has all five with their reasons |
| AC22 | A run that ends red picks the right suggestion: the built, guardrail-clean rejected attempt on the last commit with the lowest counts, stage by stage, ties the later one | 7 | A `core` unit test with four candidates: a guardrail-rejected one with lower counts, one on an older commit, two tied |
| AC23 | The suggestion is in `heal`'s text report and JSON report and in the PR comment, marked "not applied, needs review", in a fence longer than any backtick run in the diff | 7 | Unit tests on the three renderers with a diff that contains a ```` ``` ```` line |
| AC24 | The suggestion is never committed, and a green run has no suggestion | 7, Don't | A `core` unit test: after a red run, no commit contains the suggested edits; a green run's three reports have no suggestion |
| AC25 | The Benchmark workflow has a `model` input defaulting to `qwen2.5-coder:7b`, used for the image and the runs | 8 | `.github/workflows/benchmark.yml` contains the input, and `MODEL` comes from it |
| AC26 | `benchmark/results/` has `*-7b-before`, `*-7b-after`, `*-14b-after`, each with `results.json` and `report.md` from GitHub-hosted runners; `7b-before`'s tool commit is `2f543e1`; the two after runs share a tool commit that has every production change | 8 | The directories exist; `toolCommit` and `runner` in each `results.json`; `git diff --exit-code <after toolCommit> HEAD -- '*/src/main/**'` |
| AC27 | Both 7B runs have a `review.json` by the user that `:benchmark:report` accepts | 8 | The files exist; `./gradlew :benchmark:report -Presults=<dir>` succeeds for both |
| AC28 | On the cases valid in both 7B runs, the green runs in the fix-loop modes (fix-loop-only and recipe-and-fix-loop) are more after than before | 8 | Count `green: true` per mode over cases valid in both `results.json` |
| AC29 | No case and fix-loop mode that was green in at least one repetition before is red in all three repetitions after | 8 | Compare the two 7B `results.json` per case and mode |
| AC30 | The share of reviewed runs accepted unchanged is not lower after than before | 8 | Compare `acceptedUnchanged` in the two `review.json` |
| AC31 | The Gradle and TypeScript adapters, the budget defaults, and the default model are unchanged | Don't | `git diff --exit-code 2f543e1 -- ecosystems/typescript 'ecosystems/jvm/src/main/kotlin/**/Gradle*' 'ecosystems/jvm/src/main/resources/**/*.gradle'`; `Budgets` and `DEFAULT_MODEL` unchanged |
| AC32 | `./gradlew build` passes | all | Run it |

## Docs to update

- `docs/concept.md`: 5 (build errors without a location checked on the base commit, only when the head ran tests), 6.4 (unmasked compile errors as progress, the returning-error check; repeated answers), 8.3 (an accepted fix may now keep the counts equal; the suggestion in the report).
- `docs/features.md`: new M11 "Fix-loop yield" with its result and the 14B's outcome; generated rewrite rules become M12; "Later" gets the missing-class hint, the test-source mapping for tests without a location, and the budget default; the "Linters bound to Maven verify" item is reworded to what is left (a linter that fails only on the model's code).
- `docs/architecture/fix-loop.md` (progress, repeats, feedback, history, suggestion), `verify-pipeline.md` (base check for build errors, the tests-ran condition, `-Dgpg.skip`, exit codes of `verify` and `migrate`), `pr-pipeline.md` (the suggestion in the comment, the per-run base cache), `benchmark.md` (model input, the 14B, the result directories).
- `docs/setup.md`: the report's suggestion section, if the setup guide describes the report.
