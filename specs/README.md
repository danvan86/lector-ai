# Specs

One folder per milestone from [`docs/features.md`](../docs/features.md).

```
specs/
  open/{feature}/brainstorming.md   questions and options, answered in rounds
  open/{feature}/spec.md            what to build and why, written once the brainstorming is closed
  done/{feature}/                   shipped features, moved here by the PR that ships them
```

## How a brainstorming file works

- Each round asks the questions that block implementation.
- Every question has options. The **recommended option is preselected** with `[x]`.
- The agent asks each round's questions in chat and writes the answers into the file, free-text answers under **Notes**.
- When a round is answered, the choices go into **Decisions** at the bottom of the file, and the next round is added if anything is still open.
- No code is written until the brainstorming for a feature is closed.

## Features

| Feature | File |
|---|---|
| M0 – Project skeleton | [brainstorming](done/m0-project-skeleton/brainstorming.md) · [spec](done/m0-project-skeleton/spec.md) |
| M1 – Verify and report | [brainstorming](done/m1-verify-and-report/brainstorming.md) · [spec](done/m1-verify-and-report/spec.md) |
| M2 – Recipe index and OpenRewrite run | [brainstorming](done/m2-recipe-index/brainstorming.md) · [spec](done/m2-recipe-index/spec.md) |
| M3 – Fix loop | [brainstorming](done/m3-fix-loop/brainstorming.md) · [spec](done/m3-fix-loop/spec.md) |
| M4 – Guardrails | [brainstorming](done/m4-guardrails/brainstorming.md) · [spec](done/m4-guardrails/spec.md) |
| M5 – Renovate in CI | [brainstorming](done/m5-renovate-ci/brainstorming.md) · [spec](done/m5-renovate-ci/spec.md) |
| M6 – Benchmark | [brainstorming](done/m6-benchmark/brainstorming.md) · [spec](done/m6-benchmark/spec.md) |
| M7 – Gradle | [brainstorming](done/m7-gradle/brainstorming.md) · [spec](done/m7-gradle/spec.md) |
| M8 – Dependabot and fallback | [brainstorming](done/m8-dependabot-fallback/brainstorming.md) · [spec](done/m8-dependabot-fallback/spec.md) |
| M9 – TypeScript | [brainstorming](done/m9-typescript/brainstorming.md) · [spec](done/m9-typescript/spec.md) |
| M10 – API diff as grounding | [brainstorming](done/m10-api-diff-grounding/brainstorming.md) · [spec](done/m10-api-diff-grounding/spec.md) · [tests](done/m10-api-diff-grounding/tests.md) |
| M11 – Fix-loop yield | [brainstorming](open/m11-fix-loop-yield/brainstorming.md) · [spec](open/m11-fix-loop-yield/spec.md) |
| M12 – Simple setup | [brainstorming](done/m12-simple-setup/brainstorming.md) · [spec](done/m12-simple-setup/spec.md) · [tests](done/m12-simple-setup/tests.md) |
