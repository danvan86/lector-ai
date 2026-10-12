# Progress – M6 Benchmark

Spec: [`spec.md`](spec.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | Fix requests carry only the diagnostics that fit, and the fix loop counts them (spec 8) | ✅ |
| 2 | Recipe step for a group of changes in `core`, shared by the PR pipeline and the benchmark; JDK variables in `ecosystems:jvm` | ⬜ |
| 3 | Module `benchmark`: corpus format, run plan (modes × repetitions), harness with the invalid-case check and per-run metrics | ⬜ |
| 4 | Results: merge, aggregation, 90/10 verdict, review file, `report.md`, Gradle tasks | ⬜ |
| 5 | Workflow `benchmark.yml`: Ollama image job, matrix, merge and report artifact | ⬜ |
| 6 | Corpus: 20–25 cases, historical and synthetic | ⬜ |
| 7 | Default model `qwen2.5-coder:7b` (spec 7) | ✅ already in place since M5 (CLI, action, concept 8.4) |
| 8 | Quality gates verified | ⬜ |
| 9 | Docs in sync | ⬜ |

## Open

- First results (AC17): need the workflow on `main` (`workflow_dispatch` only runs workflows on the default branch), one run, and a human review.
