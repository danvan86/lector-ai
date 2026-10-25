# M11 – Fix-loop yield · Tests

Spec: [spec.md](spec.md) · Decisions: [brainstorming.md](brainstorming.md) · Evidence: [evidence.md](evidence.md) · Status: **Test code red, awaiting approval**

## Test contract

```
1. Test design (this file)    ──▶ questions in chat, answers recorded here
2. Test code                  ──▶ every test runs and fails for the stated reason (red)
3. Approval by the user       ──▶ test files frozen in tests.lock
4. Implementation             ──▶ production code only; tests change only after asking
5. Done when                  ──▶ all tests green + all quality gates green + all AC of the spec met
```

**Tools and levels:** as in AGENTS.md. Fix-loop tests use the fake adapter and scripted model of `FixLoopFixture` (frozen by M10, unchanged) on a real Git repository; the base-commit tests add an adapter whose head and base verify differently (`BuildErrorFixture`, harness). Compiler and Maven messages are the real tools' wording from [evidence.md](evidence.md). The signing check runs once with real Maven (`:cli:integrationTest`).

## Coverage

| AC | Covered by | Note |
|---|---|---|
| AC1 | T-A1 | |
| AC2 | T-A2 | |
| AC3 | T-A3 | |
| AC4 | T-A4 | |
| (same-error rule) | T-A8 | the line doesn't identify an error |
| AC5 | T-A5 | |
| AC6 | T-A6 | |
| AC7 | T-A7 | |
| AC8 | T-B1 | |
| AC9 | T-B2, T-B3, T-B10 | |
| AC10 | T-B4, T-B5 | the `pr` wiring (first verify, after the recipe) by implementation tests |
| AC11 | T-B6 | |
| AC12 | T-B7, T-B8, T-B9, T-B11 | |
| AC13 | T-C1, T-C2, T-C3 | T-C2 needs `mvn` and network |
| AC14 | T-D1 | |
| AC15 | T-D2, T-D6 | |
| AC16 | T-D3, T-D4, T-D7 | |
| AC17 | T-D5 | |
| AC18 | T-E1, T-E5 | |
| AC19 | T-E2, T-E3 | |
| AC20 | T-E4 | |
| AC21 | T-F1 | |
| AC22 | T-G1, T-G4 | |
| AC23 | T-H1, T-H3, T-H4 | |
| AC24 | T-G2, T-G3, T-H2, T-H5 | |
| AC25 | by command: `grep -n -A4 "model:" .github/workflows/benchmark.yml` and `MODEL: ${{ inputs.model` | workflow configuration |
| AC26–AC30 | by command on the committed results (spec section 8) | CI runs and the user's review, after the implementation |
| AC31 | by command: `git diff --exit-code 2f543e1 -- ecosystems/typescript 'ecosystems/jvm/src/main/kotlin/**/Gradle*' 'ecosystems/jvm/src/main/resources/**/*.gradle'` | |
| AC32 | by command: `./gradlew build` | |

---

## Test catalog

### Group A · An attempt that uncovers hidden compile errors is kept

Level: unit, `FixLoopProgressTest`. Catches an implementation that still counts only (T-A1, T-A3, T-A7 red), and one that keeps too much: an error moved into the model's own lines, a build error swapped for a new one, more errors than before, a swapped test, errors going back and forth, an error that only moved down a line (T-A2, T-A4–T-A8 red against permissive stubs).

| ID | Test | Scenario (given → when → then) | Red because | AC |
|---|---|---|---|---|
| T-A1 | `a fix that uncovers the next removed method in the same call chain is kept, with equal counts` | Flyway 9 removed four methods of one call chain; javac reports the first (flyway-9) → the 30B's correct replacement of that line → accepted and committed with counts 0/1/0 → 0/1/0; the uncovered error in the next line is left | rejected as "no progress" today | AC1 |
| T-A2 | `a fix whose new error lies in a line it changed is rejected` | Same chain → the 7B's replacement calls another removed method in the same line → rejected "no progress", file unchanged | green today; red against a stub that keeps every attempt whose sent errors are gone | AC2 |
| T-A3 | `an error that was there before and is still there in a changed line doesn't block a fix` | Two errors, only the first fits into the request; the edit spans the second one's line and leaves it; a third error was hidden behind the first → accepted, 0/2/0 → 0/2/0 | rejected today | AC3 |
| T-A4 | `a fix after which a new build error without a location takes the place of another is rejected` | An enforcer failure and the chain error; the enforcer's cluster uses its one attempt; the correct chain fix swaps the enforcer failure for GPG signing (dledger's message) → 1/1/0 → 1/1/0, rejected | green today; red against a stub that keeps every attempt whose sent errors are gone | AC4 |
| T-A5 | `a fix after which more errors are left than before is rejected` | The correct chain fix, but a new error appears in another file → 0/1/0 → 0/2/0, rejected | as T-A4 | AC5 |
| T-A6 | `a fix that makes its failing test pass and another test fail is rejected` | `adds` fails because of the update → the edit makes `adds` pass and `subtracts` fail (passes on the base) → 0/0/1 → 0/0/1, rejected | as T-A4 | AC6 |
| T-A7 | `an error that an earlier accepted fix removed and that comes back is not uncovered` | Attempt 1 removes x and uncovers y (accepted); attempt 2 removes y, and x is back in a line attempt 2 didn't change → rejected | attempt 1 rejected today; attempt 2 accepted by the permissive stub | AC7 |
| T-A8 | `an edit that only moves its error to another line is rejected` | A comment line inserted above the chain → the same error one line further down → rejected | green today; red against a stub that identifies errors by their line | same-error rule |

### Group B · A build error the base commit has too is not the update's

Level: unit, `BuildErrorAttributionTest` on `BuildErrorFixture`. Catches an implementation that never checks the base (T-B1, T-B4, T-B6, T-B8, T-B9, T-B11 red), and one that blames the base too readily: without tests run, for a head-only or different error, without a base, for a base that stopped earlier, or that verifies the base when nothing needs it (T-B2, T-B3, T-B5, T-B7, T-B10 red against permissive stubs). A run's base commit is a `RunBase` (decided by the agent, below).

| ID | Test | Scenario (given → when → then) | Red because | AC |
|---|---|---|---|---|
| T-B1 | `a build error the base commit has too is pre-existing when the head ran its tests, and the model never sees it` | Head: a compile error and dledger's GPG failure, 12 tests run; base: the GPG failure → GPG is pre-existing, the compile error caused by the update; the loop fixes it and ends green; no request carries GPG | GPG counted as caused today | AC8 |
| T-B2 | `a build error the base commit has too still counts when the head ran no tests, or can't tell` | Same GPG failure on both, tests run 0 and unknown → caused by the update, nothing pre-existing | green today; red against a stub that ignores the tests run | AC9 |
| T-B3 | `a build error only the head has is caused by the update, also when the base fails differently` | GPG failure on the head; the base has nothing, or a surefire failure → caused by the update, nothing pre-existing | as T-B2 | AC9 |
| T-B4 | `the base commit is verified once per run, however many attributions see a build error` | GPG on head and base in the first attribution and three attempts → one base verification | no base verification today | AC10 |
| T-B5 | `the base commit is not verified when no build error needs it` | Only compile errors → no base checkout, no base verification | green today; red against a stub that always verifies the base | AC10 |
| T-B6 | `two build errors that differ only in the checkout's path match, also when the base names its real path` | GPG failure naming the checkout directory; the base is checked out through a link and its message names the real path → pre-existing; with another plugin version on the base → caused | caused today | AC11 |
| T-B7 | `without a base commit a build error is caused by the update` | No base → caused by the update | green today; red against a stub that leaves it unattributed | AC12 |
| T-B8 | `a base that fails to verify leaves the build error caused by the update, and is not tried again in the run` | Base verification throws a tool error, or times out → caused by the update, twice, with one base verification | no base verification today | AC12 |
| T-B11 | `a base that can't be checked out leaves the build error caused by the update, and is not tried again in the run` | The base's checkout throws (`git worktree add failed`) → caused by the update, twice, one checkout | no checkout today | AC12 |
| T-B9 | `a base that times out inside an attempt doesn't end the run` | The fix makes the build reach GPG; the base times out → the attempt is rejected with GPG in its reason, the loop ends with a result | no base verification today | AC12 |
| T-B10 | `a base whose build stops earlier, at compile errors, leaves the head's later build error caused by the update` | Head: GPG failure; base: a compile error → caused by the update | green today; red against a stub that calls every build error pre-existing when a base exists | AC9 |

### Group C · lector's Maven builds don't sign

Level: unit with the adapter's fake process runner (`MavenSigningTest`), and once with real Maven (`SigningIntegrationTest`, fixture `signed-build`). Catches a flag on some commands only, and a test that passes only because the machine has no `gpg`.

| ID | Test | Scenario (given → when → then) | Red because | AC |
|---|---|---|---|---|
| T-C1 | `every Maven command skips signing` | verify (compile and verify), a test rerun, a recipe run, the effective POM → all five commands carry `-Dgpg.skip=true` | no flag today | AC13 |
| T-C3 | `fetching the old version's JAR for the API changes skips signing too` | The old JAR is fetched with `dependency:get` for the API changes → the fetch carries the flag | no flag today | AC13 |
| T-C2 | `a project that signs in verify verifies green, because lector's builds don't sign` | A project binds `maven-gpg-plugin` to `verify` with `gpg.executable` set to a program that doesn't exist → `lector verify` exits 0, nothing caused by the update | the GPG failure is a build error today (checked by hand: `mvn verify` fails, `-Dgpg.skip=true` passes) | AC13 |

### Group D · A repeated answer is asked again, not built

Level: unit, `FixLoopRepeatTest`. Catches building repeats (T-D1–T-D3 red), and over-eager detection: across an accepted attempt, or of invalid answers (T-D4, T-D5 red against a permissive stub).

| ID | Test | Scenario (given → when → then) | Red because | AC |
|---|---|---|---|---|
| T-D1 | `a repeated answer is not built, and the model is asked once more at a higher temperature with a note` | Attempt 1 rejected; attempt 2's first answer repeats it → asked again at 0.7 with a note naming attempt 1, its reason and its edits; the second answer fixes it and is what attempt 2 records; two builds in all | the repeat is built today | AC14 |
| T-D2 | `two repeats in one attempt reject it without a build, and it counts as an attempt without progress` | The re-ask repeats too → attempt 2 rejected, its reason names attempt 1; one build; the loop stops with no progress | as T-D1 | AC15 |
| T-D3 | `an answer is a repeat whatever the order of its edits and the whitespace at line ends, and of any earlier attempt` | Answers A, B, then A reordered with trailing spaces → the third is a repeat of attempt 1 (temperatures 0, 0.3, 0.3, 0.7) | as T-D1 | AC16 |
| T-D4 | `after an accepted attempt, an answer that was rejected before is built again` | Attempt 1 rejected, attempt 2 accepted, attempt 3 repeats attempt 1's edits → built, no re-ask, no note | green today; red against a stub that compares with every earlier attempt | AC16 |
| T-D5 | `an invalid answer is never a repeat` | Two invalid answers → two attempts, both rejected as invalid, no re-ask | green today; red against a stub that treats invalid answers as repeats | AC17 |
| T-D6 | `a different second answer that is rejected says that the first answer repeated an attempt` | The first answer repeats attempt 1, the second differs and makes no progress → its reason starts with "the first answer repeated attempt 1" and says why; the attempt records the second answer | the repeat is built today | AC15 |
| T-D7 | `an answer that differs only in indentation is not a repeat` | The second answer differs from the first only in leading whitespace → built, no re-ask | green today; red against a stub that ignores all whitespace | AC16 |

### Group E · Precise feedback when an edit can't be applied

Level: unit, `FixLoopFeedbackTest`. Catches reasons that don't name the edit or show the real text, unbounded match lists, and a guessed path counted as a guardrail rejection.

| ID | Test | Scenario (given → when → then) | Red because | AC |
|---|---|---|---|---|
| T-E1 | `a search text that isn't in the file names the edit and shows the most similar lines with their numbers` | The 30B's pdfbox edit lost the `import ` of its third line → the reason names edit 1 and the file, and shows lines 3–5 (the real imports) with their numbers, not the package line or the class; the file is unchanged | the reason names neither today | AC18 |
| T-E5 | `a failing second edit is named, with the line numbers of the committed file, not of the half-edited one` | Edit 1 adds an import line, edit 2 is the broken pdfbox edit → "edit 2", the PDDocument import at line 5 (as committed), not 6 | the reason names neither today | AC18 |
| T-E2 | `a search text found twice names the lines of both matches` | The 14B's metrics edit matches lines 5 and 9 → the reason names edit 1 and `5, 9` | count only today | AC19 |
| T-E3 | `a search text found many times names the first five lines and counts the rest` | 8 matches → `1, 2, 3, 4, 5` and `and 3 more`, nothing after line 5 | as T-E2 | AC19 |
| T-E4 | `an edit of a file that doesn't exist names the tracked file of that name, and is not a guardrail rejection` | The 30B's kafka path without `src/test/java` → "no such file", names the tracked `src/test/java/…/DropwizardKafkaIT.java`; no violation, no rejection per guardrail | a scope violation today | AC20 |

### Group F · The model sees every failed attempt

Level: unit, `FixLoopFeedbackTest`. Catches a history that drops a kind of failure, its edits, or its reason.

| ID | Test | Scenario (given → when → then) | Red because | AC |
|---|---|---|---|---|
| T-F1 | `every request carries every earlier failed attempt on its cluster, of every kind, with its reason` | Five rejected attempts on one cluster (not applied, guardrail, no progress, invalid, two repeats), then a fix → the sixth attempt's request has all five, with their edits and reasons (incl. "edit 1" for the one not applied, the errors after the no-progress edit, and "repeated attempt 3") | the fifth is built and the sixth answer used up today | AC21 |

### Group G · The best rejected attempt as a suggestion

Level: unit, `FixLoopSuggestionTest`. Catches choosing a guardrail-rejected, unbuilt, or older-commit attempt, the wrong one of a tie, a suggestion on a green run, and a suggestion that ends up committed.

| ID | Test | Scenario (given → when → then) | Red because | AC |
|---|---|---|---|---|
| T-G1 | `a red run suggests the built, guardrail-clean attempt on the last commit with the lowest counts, the later one of a tie` | Attempt 1 (0/2/0) before the accepted attempt 2; after it: a guardrail rejection, two built at 0/3/0, then one at 0/4/0 → attempt 5 and its diff. Ignoring the commit picks 1, the last picks 6, the earlier of the tie picks 4, a missing count as zero picks 3 | no suggestion today | AC22 |
| T-G2 | `the suggestion is never committed, and the working tree is clean after the run` | Two rejected attempts with equal counts → attempt 2 suggested; no commit contains its edit, HEAD unchanged, clean tree | as T-G1 | AC24 |
| T-G3 | `a green run has no suggestion` | One rejected attempt, then a fix → no suggestion | green against the compile stub; red against a stub that always suggests | AC24 |
| T-G4 | `a red run whose attempts were never built has no suggestion` | One edit not applied, one guardrail rejection → no suggestion | as T-G3 | AC22 |

### Group H · The suggestion in the reports

Level: unit, `PrCommentSuggestionTest` (core) and `FixLoopReportSuggestionTest` (cli). Catches a missing marker, a fence a Markdown diff can close, a missing JSON field, and a section without a suggestion.

| ID | Test | Scenario (given → when → then) | Red because | AC |
|---|---|---|---|---|
| T-H1 | `a red run's comment shows the suggestion collapsed, marked not applied, in a fence longer than any in the diff` | A suggestion whose diff has fences of three and four backticks → inside one `<details>`: "not applied, needs review", attempt 2, the diff in a fence of five or more backticks | no section today | AC23 |
| T-H2 | `a comment without a suggestion has no suggestion section` | No suggestion → no marker | green against the compile stub; red against a stub that always prints it | AC24 |
| T-H3 | `the text report shows the suggestion, marked not applied, with its diff` | `heal`'s text report → marker, attempt 6, every diff line | no section today | AC23 |
| T-H4 | `the JSON report has the suggestion's attempt and diff, or null` | → `"suggestion": {"attempt": 6, "diff": …}`, or `null` | no field today | AC23 |
| T-H5 | `without a suggestion the text report has no suggestion section` | No suggestion → no marker | as T-H2 | AC24 |

### Edge cases considered and left out

| Dimension | Decision |
|---|---|
| Quantity | Two and eight matches (T-E2, T-E3); one to five failed attempts (T-F1); one and three attributions with a build error (T-B4) |
| Repetition | Repeat of the last and of an earlier attempt (T-D1, T-D3), two repeats (T-D2), across an accepted attempt (T-D4) |
| Order | Edit order and trailing whitespace in repeats (T-D3); tie-break of suggestions (T-G1) |
| Time | Base timeout outside and inside an attempt (T-B8, T-B9); the time budget during a re-ask is the existing budget check, by implementation tests |
| Failure | Base tool error and timeout (T-B8), no base (T-B7), invalid answers (T-D5) |
| Isolation | Not applicable: no tenant state |
| Lifecycle | Suggestion on red, green, and never-built runs (T-G1–T-G4) |
| Extension | Section 2 is in `core` and works for any adapter; the base-check tests use a neutral fake adapter, not Maven |
| Volatile messages (R6) | A message with an output tail never matches: by implementation tests (the adapters' "unexplained failure" texts) |
| Empty replace text (changed lines) | By implementation tests: how the changed lines are mapped is the implementation's construction |

### Checked by command or by implementation tests

- AC25: workflow configuration, by `grep`.
- AC26–AC30: the CI benchmark runs and the user's review, after the implementation; checked on the committed `benchmark/results/` (spec section 8).
- AC31, AC32: by command.
- The `pr` and `heal` wiring of the per-run base cache, the re-ask under the time budget, the changed-line mapping of deleting edits, and existing Maven command-line tests that change by the new flag: implementation tests.

---

## Test code

| Group | Files | Red before the implementation, because |
|---|---|---|
| A | `core/src/test/kotlin/gmbh/vanella/lector/core/FixLoopProgressTest.kt` | 3 of 8 fail on their assertion; the other 5 fail against permissive stubs (keep every attempt whose sent errors are gone; identify errors by line) |
| B | `core/src/test/kotlin/gmbh/vanella/lector/core/BuildErrorAttributionTest.kt` | 6 of 11 fail on their assertion; the other 5 fail against permissive stubs (always verify the base, every build error pre-existing when a base exists, unattributed without a base) |
| C | `ecosystems/jvm/src/test/kotlin/gmbh/vanella/lector/ecosystems/jvm/MavenSigningTest.kt`, `cli/src/test/kotlin/gmbh/vanella/lector/cli/SigningIntegrationTest.kt`, fixture `cli/src/test/resources/fixtures/signed-build/` | T-C1 and T-C3 fail on their assertion (`[mvn, -B, -fae, clean, test-compile]` and the `dependency:get` call have no flag); T-C2: the fixture fails with `mvn verify` and passes with `-Dgpg.skip=true` (real Maven, by hand) |
| D | `core/src/test/kotlin/gmbh/vanella/lector/core/FixLoopRepeatTest.kt` | 4 of 7 fail on their assertion; the other 3 fail against permissive stubs (compare with every earlier attempt, invalid answers as repeats, all whitespace ignored) |
| E, F | `core/src/test/kotlin/gmbh/vanella/lector/core/FixLoopFeedbackTest.kt` | 6 of 6 fail on their assertion |
| G | `core/src/test/kotlin/gmbh/vanella/lector/core/FixLoopSuggestionTest.kt` | compile: `FixLoopResult.suggestion` missing; against a compile stub (always null) 2 of 4 fail; the other 2 fail against a stub that always suggests |
| H | `core/src/test/kotlin/gmbh/vanella/lector/core/PrCommentSuggestionTest.kt`, `cli/src/test/kotlin/gmbh/vanella/lector/cli/FixLoopReportSuggestionTest.kt` | compile: `Suggestion` missing; against the compile stub 3 of 5 fail on their assertion; the other 2 fail against renderers that always print the section |

Checked before approval: `ktlintFormat`, `ktlintCheck`, `detektTest` on the test code; stubs deleted (the build is red on purpose until the implementation: `Suggestion`, `FixLoopResult.suggestion`, and `RunBase` don't exist).

Fidelity check (fresh agent, step 4b): 15 findings, all resolved. Five let a wrong implementation pass (T-G1 tested neither the lowest counts nor the last commit; T-G2 checked the wrong edit; Group B never compared messages; a per-run cache was forced into global state; no test for the line-independent same-error rule): T-G1 and T-G2 redesigned, T-B3 and T-B6 got differing messages, T-B10 and T-B11 added, `RunBase` made explicit, T-A8 added. The rest: T-A4 now swaps build errors at equal counts, T-E5 (second edit, committed line numbers), T-H1 (fence longer than a four-backtick run, one `<details>` block), T-D1 and T-D6 (the note's reason, the recorded answer, the reason prefix), T-C3 (the fetch), T-A3's indentation, small missing checks; two spec ambiguities settled in the spec (window lines compared by position; a replace text's final line break).

### Harness (not frozen)

Frozen in commit `(set on approval)`. Repairs and extensions allowed; a change that makes a test easier to pass needs the user's consent (review-spec checks the diff).

| File | Role |
|---|---|
| `core/src/test/kotlin/gmbh/vanella/lector/core/BuildErrorFixture.kt` | the two-checkout adapter, the counting base, the GPG messages, the loop for that adapter |
| `core/src/test/kotlin/gmbh/vanella/lector/core/FixLoopFixture.kt` | frozen by M10's `tests.lock`, unchanged |

---

## Round 1

No open behaviour questions: the spec and its critique settle every outcome the tests assert.

---

## Decided by the agent

| # | Situation | Outcome | Reason |
|---|---|---|---|
| — | The note on a repeat | Carried as an extra entry in the earlier attempts of the re-ask request (edits of the repeated attempt, a reason naming it) | Spec 4: a part of the request that is never trimmed and that the fit check counts; the earlier attempts are exactly that, so no new field on the model's request |
| — | Format of match lines | `5, 9`; more than five: the first five and `and N more` | The AGENTS rule for lists; the wording of the existing "… and N more" lines |
| — | Per-run base commit | A run wraps its base commit once in a `RunBase` and hands it to every attribution; it remembers the base's verification, a failure included | Spec 2: "at most once per run"; without an explicit per-run object, only global state could remember it (fidelity check) |
| — | Same error, line moved | An edit that only pushes its error down a line hasn't removed it | Spec terms: the line doesn't identify an error |
| — | The suggestion in JSON | `"suggestion": {"attempt": N, "diff": "…"}` or `null` | Like the report's other optional fields (`testsRun` is `null` when unknown) |

---

## Decisions

_No question rounds: nothing open._

## Approval

_Not yet approved._
