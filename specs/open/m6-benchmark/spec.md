# M6 – Benchmark · Spec

Milestone: [M6 in features.md](../../../docs/features.md#m6--benchmark-) · Decisions: [brainstorming.md](brainstorming.md) · Status: **Ready to implement**

## Goal

An on-demand benchmark runs the tool on 20–25 real and synthetic JVM/Maven dependency upgrades, in three modes, and produces the first numbers: green rates, where the fixes come from, guardrail rejections, runtime, and a verdict on the 90/10 hypothesis.

**Why:** "measure instead of guessing" (features.md). The concept calls the 90/10 split between recipe and AI a hypothesis (section 11). Before widening to Gradle, Dependabot, and TypeScript, we need to know whether the JVM/Maven path works, and which part does the work.

## What to build

### 1. Corpus

- A corpus file in the `benchmark` module, read with kotlinx.serialization (JSON). Each case has:
  - an ID and an origin: a link to a historical bot PR, or "synthetic";
  - the repository URL, the base commit, and the head commit with the bump;
  - the `DependencyChange`s (package, from, to).
- 20–25 cases. A mix of historical Renovate/Dependabot PRs that failed CI and synthetic bumps on known projects. Open-source and own repositories only.
- Each case must have at least one failure caused by the update at its head commit. The harness checks this before running a case. A case that doesn't is reported as invalid and excluded from the numbers.

**Why:** historical PRs are realistic. Synthetic bumps fill gaps for specific upgrades (Q1). 20–25 cases give first numbers without weeks of curation (Q5). A case that isn't broken by its update measures nothing (M1 attribution). JSON uses the library we already have, so no YAML dependency is needed.

### 2. Harness in a new module `benchmark`

- Uses `core` and the adapters directly. It isn't part of the CLI distribution or the container image.
- Three modes per case:
  - **recipe only:** recipe + verify;
  - **fix loop only:** verify + fix loop, with no recipe;
  - **recipe + fix loop:** the full `heal` flow.
- Three repetitions per case and mode for the two modes that use the LLM. Recipe only is deterministic and runs once.
- Every run uses a fresh checkout of the case's head commit, the product's default budgets, and records the tool commit and the model.
- Model: `qwen2.5-coder:7b` only.

**Why:** three modes are the only way to tell where the value comes from (Q3). Three repetitions separate signal from noise in LLM output (Q4). The harness shares the product's code and data model, but nothing benchmark-specific ships to users (Q6). On CPU-only runners, larger models would mostly measure the time budget running out (Q10, Q12).

### 3. Metrics per run

- **Green:** no failure caused by the update left, tests run.
- The stage tuple (build errors, compile errors, test failures caused by the update) before and after.
- Recipe found and applied, by name.
- Attempts, accepted fixes, guardrail rejections per rule, and why the loop stopped (green, budget, no progress).
- Runtime in total and per stage.
- Lines changed by recipe commits and by fix commits.
- Pre-existing and flaky failures, counted separately.
- Fix requests that left diagnostics out because they didn't fit (section 8).

**Why:** these are the concept's metrics (section 11), plus what the 90/10 verdict needs. Pre-existing and flaky failures are tracked but never counted as the tool's job (M1).

### 4. Manual review sample

- A reviewer checks a sample of at least 10 green runs from the fix-loop modes (or all of them, if there are fewer) and records for each: accepted unchanged, yes or no, with a one-line reason.
- The review is stored next to the results and is included in the report.

**Why:** a green build can still contain a wrong fix. A sample gives a first estimate of "accepted unchanged by reviewers" (Q2, concept section 11) without reviewing every run.

### 5. Report

- Each benchmark run produces a `results.json` with every run's metrics.
- A Gradle task generates `report.md` from `results.json` and the review file:
  - green rate per mode, as mean and spread over repetitions;
  - cases green after recipe only vs. after recipe + fix loop;
  - **90/10 verdict:** for recipe + fix-loop runs that end green, the recipe's share of changed lines (recipe commits vs. fix commits). The hypothesis **holds if the mean share is at least 90%**. The report states "holds" or "doesn't hold" with the number;
  - guardrail rejection rate per rule, attempts, runtime;
  - the manual review result;
  - tool commit, model, runner type, date.
- `results.json`, the review file, and `report.md` are committed under `benchmark/results/<date>/`.

**Why:** versioned and diffable, readable in the repo, no dashboard to build (Q9). The changed-line split measures how much of the work each part did, is language-neutral, and comes straight from the commits (Q11).

### 6. Workflow

- A `workflow_dispatch` workflow on standard GitHub-hosted runners (CPU only), with the 7B Ollama image (M5) as a service.
- A matrix with one job per case, mode, and repetition, so each job stays well within GitHub's job time limit.
- A final job merges the matrix results into one `results.json` and uploads it, together with the generated report, as an artifact. A person adds the review and commits the results.

**Why:** on demand, no new infrastructure (Q7, Q10). The product's 30-minute fix-loop budget fits in a single job. Committing stays a human step because the review is part of it.

### 7. Default model

- The product default becomes `qwen2.5-coder:7b` (CLI from M3, action input from M5). `qwen3-coder:30b` stays an image variant and a documented option for GPU runners, marked as unmeasured.

**Why:** the default should be what we have numbers for (Q13). The M3 and M5 specs are already updated.

### 8. Diagnostics that don't fit

- When a cluster's diagnostics and their code snippets don't fit into the context, the fix request carries only as many diagnostics (each with its snippets) as fit. The rest come back in the next round, because clusters are rebuilt after every accepted attempt.
- This replaces M3's rule that diagnostics and snippets are never trimmed (M3 spec section 4). Grounding, release notes, and the recipe diff are still trimmed first.

**Why:** a request that doesn't fit is cut by Ollama without notice, so the model sees part of the errors and code and nobody knows which part. Sending fewer diagnostics in full is honest, and the benchmark shows how often it happens ([M3 review](../../done/m3-fix-loop/review.md) D3).

### 9. Tests

- Unit tests with fakes: corpus parsing, the invalid-case check, aggregation (mean, spread), the changed-line split, and the 90/10 verdict at 89% and 90%.
- The report task runs on a small checked-in `results.json` fixture.
- No real Ollama and no benchmark run in `./gradlew build`.

**Why:** the numbers are only worth something if the arithmetic is right. Real-model runs stay opt-in (AGENTS.md).

## What not to do

| Don't | Why |
|---|---|
| Use GPU runners, or benchmark 14B or 30B models | Decided against (Q10, Q12). |
| Run on a schedule | Decided against (Q7). |
| Compare with the human fix commit | Decided against (Q2): different correct fixes would look wrong. |
| Run only the full pipeline | Decided against (Q3). |
| Run 1 or 5 repetitions of LLM modes | Decided against (Q4). |
| Add a `benchmark` CLI subcommand, or external scripts | Decided against (Q6). |
| Ship benchmark code in the distribution or image | Q6. |
| Build a dashboard | Decided against (Q9). |
| Use a different 90/10 definition in the report (green rate or diagnostic split) | Decided against (Q11). Green rates are reported, but not as the verdict. |
| Add Gradle, Dependabot-only, or TypeScript cases | M7, M8, M9 add their own cases. |
| Change budgets or prompts for the benchmark | It measures the product as shipped. |
| Add a YAML library for the corpus | JSON with the existing library is enough. |

## Acceptance criteria

The review agent checks every criterion and reports it as *met*, *not met*, or *can't verify*, with evidence.

| ID | Criterion | Covers | How to verify |
|---|---|---|---|
| AC1 | `./gradlew build` passes and runs no benchmark and no real Ollama | all | Run it from the repo root: exit code `0`; build configuration |
| AC2 | Module `benchmark` exists and isn't in the CLI distribution | 2 | `settings.gradle.kts`; `cli` doesn't depend on it; `./gradlew :cli:installDist` contains no benchmark classes |
| AC3 | The corpus file is JSON and has 20–25 cases, each with ID, origin, repository, base commit, head commit, and changes | 1 | Corpus file |
| AC4 | The corpus has both historical PR cases and synthetic cases | 1 | Corpus file: origins |
| AC5 | A case without a failure caused by the update at its head is reported as invalid and excluded | 1 | Unit test |
| AC6 | The three modes run with the product's default budgets | 2 | Harness source; unit test with fakes |
| AC7 | LLM modes run 3 times per case; recipe only runs once | 2 | Unit test with fakes |
| AC8 | Every run records tool commit and model | 2, 5 | `results.json` fixture or unit test |
| AC9 | Per-run metrics include green, stage tuple before/after, recipe, attempts, accepted fixes, guardrail rejections per rule, stop reason, runtime, changed lines per commit type, pre-existing and flaky counts | 3 | `results.json` format and a unit test |
| AC10 | The report shows the green rate per mode with mean and spread | 5 | Report task on the fixture |
| AC11 | The report states the 90/10 verdict from the changed-line split on green recipe + fix-loop runs; 89% → "doesn't hold", 90% → "holds" | 5 | Unit tests |
| AC12 | The report includes guardrail rejections per rule, attempts, runtime, the review result, tool commit, model, runner type, and date | 4, 5 | Report task on the fixture |
| AC13 | The review file format records per run: accepted unchanged yes/no plus a reason | 4 | File format and an example |
| AC14 | The workflow is `workflow_dispatch` only, on GitHub-hosted runners, with the 7B Ollama image as a service and a matrix over case, mode, and repetition | 6 | Workflow file |
| AC15 | The workflow merges results and uploads `results.json` and `report.md` as an artifact | 6 | Workflow file |
| AC16 | The product default model is `qwen2.5-coder:7b` in the CLI and the action | 7 | CLI `--help`; `action/action.yml` |
| AC17 | First results with at least 10 reviewed runs and the 90/10 verdict are committed under `benchmark/results/<date>/` | 4, 5 | Directory content |
| AC18 | No GPU runner, schedule trigger, dashboard, CLI subcommand, or YAML library is added | Don't | Workflow files, CLI commands, version catalog |
| AC19 | When diagnostics and snippets don't fit, the request carries the diagnostics that fit, each in full, and the run's metrics count these requests | 3, 8 | `Prompt` unit test with an oversized cluster; `results.json` format |

## Docs to update

- `docs/features.md`: set M6 to ✅ when done, with a link to the first report and the 90/10 verdict.
- `docs/concept.md` section 11: define the 90/10 split as the changed-line share of recipe commits on green runs (threshold 90%).
- `docs/concept.md` section 8.4: the default model is `qwen2.5-coder:7b` on standard runners; 30B is an unmeasured option for GPU runners.
- `AGENTS.md` **Commands**: how to generate the report from a `results.json`.
