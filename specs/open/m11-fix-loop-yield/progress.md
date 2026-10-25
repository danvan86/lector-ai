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

## Quality gates

Final check from a clean state (`./gradlew clean build --rerun-tasks --no-build-cache --no-configuration-cache`): green.
Each gate on its own: `ktlintCheck`, `detektMain detektTest`, `test`, `:cli:endToEndTest`, `koverVerify` all exit 0.
1000 unit tests, 24 end-to-end tests; 100 % line and branch coverage per module. `:cli:integrationTest --tests
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
| AC26–AC30 | can't verify yet | need the CI benchmark runs and the user's review (Open) |
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

## Open

- AC26–AC30: the three benchmark runs on GitHub-hosted runners (`7b-before` at `2f543e1`, `7b-after` and `14b-after` at
  the same tool commit) and the user's `review.json` for both 7B runs.
- The 14B's disk fallback (spec 8): the workflow builds `lector-ollama:benchmark-qwen2.5-coder-14b` from the `model`
  input like the 7B. Whether its service image fits the runner's free disk is known only after the first 14B run; only
  then does the workflow get the pull-after-freeing-disk step, for that model only.
- `features.md` gets M11's result and the 14B's outcome after the runs.
