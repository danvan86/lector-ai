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
| M2 – Recipe index and OpenRewrite run | [brainstorming](open/m2-recipe-index/brainstorming.md) · [spec](open/m2-recipe-index/spec.md) |
| M3 – Fix loop | [brainstorming](open/m3-fix-loop/brainstorming.md) · [spec](open/m3-fix-loop/spec.md) |
| M4 – Guardrails | [brainstorming](open/m4-guardrails/brainstorming.md) · [spec](open/m4-guardrails/spec.md) |
| M5 – Renovate in CI | [brainstorming](open/m5-renovate-ci/brainstorming.md) · [spec](open/m5-renovate-ci/spec.md) |
| M6 – Benchmark | [brainstorming](open/m6-benchmark/brainstorming.md) · [spec](open/m6-benchmark/spec.md) |
| M7 – Gradle | [brainstorming](open/m7-gradle/brainstorming.md) · [spec](open/m7-gradle/spec.md) |
| M8 – Dependabot and fallback | [brainstorming](open/m8-dependabot-fallback/brainstorming.md) · [spec](open/m8-dependabot-fallback/spec.md) |
| M9 – TypeScript | [brainstorming](open/m9-typescript/brainstorming.md) · [spec](open/m9-typescript/spec.md) |
