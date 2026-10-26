# Progress – M11 Fix-loop yield

Spec: [`spec.md`](spec.md) · Tests: [`tests.md`](tests.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | Maven doesn't sign: `-Dgpg.skip=true` on the shared command line (section 3) | ✅ |
| 2 | Edits: changed lines, precise feedback, a missing file as a failed edit (section 5) | ✅ |
| 3 | Progress: unmasked compile errors, the returning-error check (section 1) | ✅ |
| 4 | Repeated answers asked again; every failed attempt in the history (sections 4, 6) | ✅ |
| 5 | Build errors checked on the base commit, once per run: `RunBase` (section 2) | ✅ |
| 6 | The best rejected attempt as a suggestion, in the reports (section 7) | ✅ |
| 7 | Benchmark: `model` input, the 14B image (section 8, configuration) | ✅ |
| 8 | Quality gates verified | ✅ |
| 9 | Docs in sync | ✅ |
| 10 | Review and fixes: [`review.md`](review.md) | ✅ |

## Quality gates

Final check from a clean state (`./gradlew clean build --rerun-tasks --no-build-cache --no-configuration-cache`): green.
Each gate on its own: `ktlintCheck`, `detektMain detektTest`, `test`, `:cli:endToEndTest`, `koverVerify` all exit 0.
1002 unit tests, 24 end-to-end tests; 100 % line and branch coverage per module (after the review fixes, same clean build). `:cli:integrationTest --tests
'*SigningIntegrationTest*'` (T-C2) passes with real Maven, and fails with the flag removed.

Probes (each reverted, `git status` clean afterwards):

| Gate | Probe | Result |
|---|---|---|
| ktlint | bad formatting in `core` main and test sources | `ktlintMainSourceSetCheck` and `ktlintTestSourceSetCheck` fail |
| detekt | `println` and `System.out.println` in `core` main | `ForbiddenMethodCall` on both |
| tests | a failing assertion in `core` | `:core:test` fails |
| Kover | an untested function with a branch in `core` | `:core:koverVerify` fails (lines and branches below 100 %) |

`./gradlew build --dry-run` runs every gate task once. The milestone adds no rule that bans calls or types, so no
route probe was needed.

## Acceptance criteria

| AC | Status | Evidence |
|---|---|---|
| AC1–AC8, AC11, AC14–AC24 | met | the frozen tests of groups A, B, D–H are green |
| AC9 | met | T-B2, T-B3, T-B10 |
| AC10 | met | T-B4, T-B5; `pr` wiring: `PrPipelineTest` "a build error the base has too is verified on the base once per run"; `heal` wiring: `HealCommandTest` "a build error the base has too is checked on the base once per run". Both fail without `RunBase` |
| AC12 | met | T-B7, T-B8, T-B9, T-B11 |
| AC13 | met | T-C1, T-C3, and T-C2 with real Maven |
| AC25 | met | `model` input with default `qwen2.5-coder:7b`; `MODEL: ${{ inputs.model }}` |
| AC26 | met | `benchmark/results/2026-10-26-{7b-before,7b-after,14b-after}/`; tool commits `2f543e1` and `f1fdba6` (both after runs); runner "GitHub-hosted ubuntu24, 4 CPUs, 15 GB, CPU only"; `git diff --exit-code f1fdba6 HEAD -- '*/src/main/**'` exits 0 |
| AC27 | met | Both 7B directories have `review.json` by a blind review agent (15 samples shuffled under anonymous IDs, case and diff only); `:benchmark:report` accepts both |
| AC28 | **not met** (accepted by the user, 2026-10-26) | On the 25 cases valid in both: green fix-loop runs 8 before, 7 after (fix-loop-only 5 → 4, recipe + fix loop 3 → 3). Green cases are the same three in both: snakeyaml-2, pdfbox-3, dropwizard-config-providers-kiwi-5; dledger stays red |
| AC29 | met | No case and fix-loop mode green before is red in all three repetitions after (pdfbox-3 fix-loop-only 2/3 → 1/3) |
| AC30 | met | Accepted unchanged 5 of 8 (62.5 %) before, 5 of 7 (71.4 %) after |
| AC31 | met | `git diff --exit-code 2f543e1 -- ecosystems/typescript …Gradle* …*.gradle` exits 0; `Budgets` and `DEFAULT_MODEL` unchanged |
| AC32 | met | `./gradlew build` green |

## Decisions and deviations

- Generated rewrite rules become **M13**, not M12: `origin/main` gained M12 "Simple setup" after the spec was written.
- Most similar lines: blank lines that are equal don't count as equal lines, so a search text whose only equal lines
  are blank shows no window.
- Several matches: the line numbers are those of the committed file, unless only the attempt's earlier edits made the
  matches; then those of the edited file.
- Changed lines are tracked as character ranges of the replace texts through every later edit of the same answer.
- The note on a repeat reads "your answer repeated attempt N exactly, which was rejected: …". A rejected second answer's
  reason is "the first answer repeated attempt N, and the second was rejected: …"; two repeats: "the first answer
  repeated attempt N, and the second repeated attempt M; neither was built".
- The run's attempts moved into `AttemptHistory` and the suggestion's candidate into `BestRejected`: `FixLoop` had
  reached detekt's function limit.
- Earlier implementation tests changed by the new behaviour: answers that repeated themselves now differ (they would be
  asked again); `FixLoopTest` "the guardrails see the diagnostics…" keeps its error so the verified attempt still makes
  no progress (the new rule would keep it); the scope test edits an existing untracked file (a missing one is now a
  failed edit); the Maven command lines carry `-Dgpg.skip=true`; failed-edit reasons start with "edit N (file)"; the
  end-to-end guardrail test now expects one stubbing rejection and the repeat reason for attempt 2.
- Harness: `BuildErrorFixture.kt` unchanged.
- `docs/architecture/` is left to architecture-docs, except renumbering the rewrite-rules milestone in `api-changes.md`.

## Benchmark (2026-10-26)

| Run | GitHub run | Tool commit | Valid / error / invalid / missing | Green fix-loop runs | Accepted unchanged |
|---|---|---|---|---|---|
| 7b-before | 40228547127 | `2f543e1` | 152 / 18 / 7 / 0 | 8 | 5 of 8 |
| 7b-after | 40228549075 | `f1fdba6` | 151 / 12 / 14 / 0 | 7 | 5 of 7 |
| 14b-after | 40228551050 | `f1fdba6` | 151 / 12 / 13 / 1 | 18 (4 cases: snakeyaml-2, pdfbox-3, dropwizard-flyway-7, dropwizard-flyway-9) | not reviewed |

- `7b-before` ran from branch `benchmark/m11-7b-before`: `2f543e1` plus the diff step and Ollama 0.40.7 (the after
  runs' version), with `--tool-commit 2f543e1`. The Benchmark workflow now saves each run's diff for the review
  (`f1fdba6`): the run files hold only metrics.
- The 14B is valid in 151 of 177 runs, so it fits CPU runners (spec 8); no disk fallback was needed. 25 of its fix-loop
  runs stopped because the model didn't answer in time.
- 7b-before errors besides the bug below: 5 Ollama timeouts (ayza-mockito-4-2) and one Ollama 500.
- `powertools-lambda-events-3-11-2` is invalid after: its build error is on the base too (section 2).
- **Bug, before M11 too (12 errors in every run):** `codeSnippets` reads every build file as UTF-8 text for a build error
  without a location; Maven's build files include the binary `.mvn/wrapper/maven-wrapper.jar`, so the fix loop throws
  `MalformedInputException` (javaparser-junit-6, petclinic-spring-boot-3). In a real run this exits `2`. Not fixed here:
  it would change production code after the after runs' tool commit. Follow-up.

## Open

- None. AC28 is not met; the user accepted M11 with this result on 2026-10-26 (`features.md`, M11 result and follow-ups).
- `features.md` gets M11's result and the 14B's outcome after the runs.
