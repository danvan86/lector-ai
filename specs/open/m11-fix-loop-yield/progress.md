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
| 8 | Quality gates verified | ⬜ |
| 9 | Docs in sync | ⬜ |

## Open

- AC26–AC30: the three benchmark runs on GitHub-hosted runners (`7b-before` at `2f543e1`, `7b-after` and `14b-after` at
  the same tool commit) and the user's `review.json` for both 7B runs.
- The 14B's disk fallback (spec 8): the workflow builds `lector-ollama:benchmark-qwen2.5-coder-14b` from the `model`
  input like the 7B. Whether its service image fits the runner's free disk is known only after the first 14B run; only
  then does the workflow get the pull-after-freeing-disk step, for that model only.
