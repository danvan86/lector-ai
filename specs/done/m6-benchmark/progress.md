# Progress – M6 Benchmark

Spec: [`spec.md`](spec.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | Fix requests carry only the diagnostics that fit, and the fix loop counts them (spec 8) | ✅ |
| 2 | Recipe step for a group of changes in `core`, shared by the PR pipeline and the benchmark; JDK variables in `ecosystems:jvm` | ✅ |
| 3 | Module `benchmark`: corpus format, run plan (modes × repetitions), harness with the invalid-case check and per-run metrics | ✅ |
| 4 | Results: merge, aggregation, 90/10 verdict, review file, `report.md`, Gradle tasks | ✅ |
| 5 | Workflow `benchmark.yml`: Ollama image job, matrix, merge and report artifact | ✅ |
| 6 | Corpus: 22 cases, 16 historical PRs and 6 synthetic bumps, each reproduced locally with Maven; synthetic heads and the heads of 4 still-open PRs on [`danvan86/lector-benchmark-corpus`](https://github.com/danvan86/lector-benchmark-corpus) | ✅ |
| 7 | Default model `qwen2.5-coder:7b` (spec 7) | ✅ already in place since M5 (CLI, action, concept 8.4) |
| 8 | Quality gates verified | ✅ |
| 9 | Docs in sync | ✅ |
| 10 | Review and fixes: [review.md](review.md) | ✅ |

## Acceptance criteria

| ID | Result | Evidence |
|---|---|---|
| AC1 | met | `./gradlew build` green; `:benchmark:run` and `:benchmark:report` aren't in its task graph (`build --dry-run`) |
| AC2 | met | `settings.gradle.kts`; `cli` doesn't depend on `benchmark`; no `lector/benchmark` class in `cli/build/install/lector/lib` |
| AC3 | met | `benchmark/src/main/resources/.../corpus.json`: 22 cases; `CorpusTest` checks every field and the size |
| AC4 | met | 16 PR origins, 6 `synthetic`; `CorpusTest` |
| AC5 | met | `HarnessTest`: invalid in every mode, no metrics; `StatsTest`/`ReportTest`: excluded from the numbers |
| AC6 | met | `Harness` passes no budgets to `FixLoop`; `HarnessTest`: stops after the default 2 attempts without progress |
| AC7 | met | `PlanTest` |
| AC8 | met | `HarnessTest`; `results.json` fixture |
| AC9 | met | `Metrics`; `HarnessTest` |
| AC10 | met | `ReportTest` on the fixture; `StatsTest` |
| AC11 | met | `StatsTest`: 89% doesn't hold, 90% holds |
| AC12 | met | `fixtures/results/report.md` |
| AC13 | met | `Review`/`ReviewedRun`; `fixtures/results/review.json` |
| AC14 | met | `.github/workflows/benchmark.yml` |
| AC15 | met | `report` job of the workflow |
| AC16 | met | `DEFAULT_MODEL` in `FixLoopOptions`, `action/action.yml` (since M5) |
| AC17 | can't verify yet | see Open |
| AC18 | met | no `schedule`, no GPU label, no dashboard, no `lector` subcommand, no YAML library |
| AC19 | met | `FixLoopTest`: an oversized cluster sends the diagnostics that fit; `OllamaClientTest`; `requestsWithDiagnosticsLeftOut` in `results.json` |

## Quality gates

Final check from a clean state, after the review: ktlint, detekt, 547 unit tests, 19 end-to-end tests, Kover 100% per module, and
`./gradlew clean build --rerun-tasks --no-build-cache` all green.

Probed on the new module, each reverted after: ktlint (main, test, `benchmark/build.gradle.kts`), detekt (main incl.
`System.out.println`, test), Kover (an untested function), tests (a wrong expectation). Each failed the build on its own
task. `build --dry-run` runs every gate task of `benchmark` once.

Smoke runs on this machine (real checkout from GitHub, Maven, recipe index, local Ollama) of `dledger-snakeyaml-2`:
recipe only (valid, no recipe matches, 1 compile error left) and fix loop only (valid, 2 attempts, no progress).

## Open

- First results (AC17): need the workflow on `main` (`workflow_dispatch` only runs workflows on the default branch), one
  run, and a human review of at least 10 green fix-loop runs. Then M6 becomes ✅ in `docs/features.md`, with the verdict.
- Cost: 22 cases × 7 runs = 154 jobs of up to 2 hours each on a private repository's Actions minutes.
