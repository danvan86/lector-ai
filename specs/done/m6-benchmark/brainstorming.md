# M6 – Benchmark · Brainstorming

Milestone: [M6 in features.md](../../../docs/features.md#m6--benchmark-) · Status: **Closed** (all questions decided)

**How to answer:** the recommended option is preselected with `[x]`. Move the `x` to choose another one. Write anything else under **Notes**.

## Already fixed by the project docs and earlier milestones

- Real repositories with known upgrades, reported per ecosystem.
- Metrics: green rate after recipe vs. after fix loop, test-pass rate, guardrail rejections, runtime and attempts.
- Runs on demand. Goal: first JVM/Maven numbers and a verdict on the 90/10 hypothesis.
- From M3/M5: the default model `qwen3-coder:30b` was to be measured here "on proper hardware"; standard runners only fit the 7B variant. Real-model runs never happen in `./gradlew build`.
- From M1: only failures caused by the update count; pre-existing and flaky ones are tracked separately.

```
corpus.yaml (repo, commit, change)
   │
   ▼
for each case × mode × repetition
   ├── checkout pinned commit
   ├── run tool (pinned image + model)
   └── record metrics ─▶ results.json
                              │
                              ▼
                         report.md
```

---

## Round 1

### Q1 · Where the cases come from

- [x] **A · Mix: real historical Renovate/Dependabot PRs that failed CI, plus synthetic bumps on known projects** — *recommended.* Historical PRs are realistic. Synthetic bumps (e.g. Spring Boot 2.7 → 3.x on a sample app) fill gaps for specific upgrades.
- [ ] **B · Historical PRs only** — Most honest, but slow to collect and hard to balance.
- [ ] **C · Synthetic only** — Easy to produce, risks testing what we built for.

**Notes:** A (accepted in chat). Internal repos: not answered; the corpus uses open-source and own repositories.

### Q2 · What counts as success

- [x] **A · Green build + tests pass + no guardrail violation, plus a manual review of a sample** — *recommended.* Automatic metrics for every case. A reviewer checks a sample to estimate "accepted unchanged".
- [ ] **B · Also compare with the human fix commit from history** — Gives a similarity score, but different correct fixes look "wrong".
- [ ] **C · Automatic metrics only** — Cheapest, misses fixes that are green but wrong.

**Notes:** A (accepted in chat)

### Q3 · Run modes per case

- [x] **A · Three modes: recipe only, fix loop only, recipe + fix loop** — *recommended.* The only way to actually test the 90/10 split.
- [ ] **B · Full pipeline only** — One number per case, tells us nothing about where the value comes from.

**Notes:** A (accepted in chat)

### Q4 · Repetitions

LLM output varies even at low temperature (retries use a higher one).

- [x] **A · 3 runs per case and mode, report mean and spread** — *recommended.* Enough to tell signal from noise without tripling the bill again.
- [ ] **B · 1 run** — Cheapest, single results can mislead.
- [ ] **C · 5 runs** — More confidence, much more GPU time.

**Notes:** A (accepted in chat)

### Q5 · First corpus size

- [x] **A · 20–25 cases** — *recommended.* Enough for first numbers, small enough to curate well.
- [ ] **B · 10 cases** — Quick, but percentages jump by 10 points per case.
- [ ] **C · 50+ cases** — Better statistics, weeks of curation.

**Notes:** A (accepted in chat)

### Q6 · Where the harness lives

- [x] **A · Separate Gradle module `benchmark/`, not part of the product image** — *recommended.* Uses the same code and data model, but nothing benchmark-specific ships to users.
- [ ] **B · A `benchmark` subcommand in the CLI** — Easy access, but adds test tooling to the product.
- [ ] **C · External scripts (bash/Python) around the container image** — Tests exactly what users run, but a second language and duplicated parsing.

**Notes:** A (accepted in chat)

### Q7 · Where it runs

- [x] **A · `workflow_dispatch` workflow on a self-hosted GPU runner** — *recommended.* Same environment as production, triggered on demand.
- [ ] **B · Scheduled weekly** — Tracks regressions over time, uses GPU time even when nothing changed.

**Notes:** A accepted in chat, then changed by Q10: `workflow_dispatch` on standard GitHub-hosted runners, CPU only.

### Q8 · Models

- [x] **A · 2–3 models (e.g. the M3 default plus a smaller and a larger one)** — *recommended.* Answers the hardware question from M3 with data.
- [ ] **B · Default model only** — Cheaper, leaves the model choice untested.

**Notes:** A accepted in chat, then superseded by Q12: 7B only.

### Q9 · Results and report

- [x] **A · `results.json` per run plus a generated Markdown report committed under `benchmarks/results/`** — *recommended.* Versioned, diffable, readable in the repo.
- [ ] **B · Dashboard (e.g. a simple static HTML page)** — Nicer for trends, more to build.

**Notes:** A (accepted in chat)

---

## Round 2

Two points Round 1 doesn't settle: there is no GPU runner yet (M3 Q17, M5 Q17 deferred the 30B model to M6), and "a verdict on 90/10" needs a measurable definition.

### Q10 · Hardware

- [ ] **A · Ephemeral cloud GPU VM, registered as a self-hosted runner for the run** — *recommended.* Pay per run, your own infrastructure (rule 7).
- [ ] **B · Own GPU machine as a self-hosted runner** — No per-run cost, only if you have one.
- [ ] **C · GitHub-hosted GPU runner** — Paid larger runner, Team/Enterprise plan.
- [x] **D · CPU only, 7B on standard GitHub-hosted runners** — No new infrastructure. The 30B model stays unmeasured.

**Notes:** D (answered in chat)

### Q11 · Definition of the 90/10 hypothesis

- [x] **A · Changed-line split on green cases: in recipe + fix-loop mode, lines changed by recipe commits vs. by fix commits, for runs that end green. Holds if the recipe's mean share is at least 90%** — *recommended.* Measures "how much of the work", language-neutral, read from the commits. Green rates are reported anyway.
- [ ] **B · Green-rate split** — Says which cases, not how much work.
- [ ] **C · Diagnostic split** — Distorted by masked errors (M3 Q18).

**Notes:** A (answered in chat)

---

## Round 3

Follow-ups of Q10 = D.

### Q12 · Models on CPU runners

- [x] **A · `qwen2.5-coder:7b` only** — *recommended.* On CPU a 16k prompt already takes minutes; 14B would mostly measure the 30-minute budget running out.
- [ ] **B · 7B and `qwen2.5-coder:14b`** — Size comparison, about twice the runtime.

**Notes:** A (answered in chat)

### Q13 · Product default model

The default `qwen3-coder:30b` (M3 Q1) would never be measured.

- [x] **A · The default becomes the measured `qwen2.5-coder:7b`. 30B stays an image variant and a documented, unmeasured option for GPU runners** — *recommended.* The default is what we have numbers for.
- [ ] **B · Keep 30B as default** — Likely better on proper hardware, but unmeasured and doesn't fit standard runners.

**Notes:** A (answered in chat). Changes M3 Q1; the M3 and M5 specs are updated.

---

## Decisions

### Round 1

| # | Topic | Decision |
|---|---|---|
| Q1 | Cases | Mix of historical failing bot PRs and synthetic bumps; open-source and own repositories |
| Q2 | Success | Green build + tests pass, plus a manual review of a sample |
| Q3 | Modes | Recipe only, fix loop only, recipe + fix loop |
| Q4 | Repetitions | 3 per case and mode, mean and spread |
| Q5 | Corpus size | 20–25 cases |
| Q6 | Harness | Separate Gradle module `benchmark/`, not in the product image |
| Q7 | Where it runs | `workflow_dispatch` (runner changed by Q10) |
| Q8 | Models | Superseded by Q12 |
| Q9 | Results | `results.json` per run plus a generated Markdown report, committed to the repo |

### Round 2

| # | Topic | Decision |
|---|---|---|
| Q10 | Hardware | CPU only, standard GitHub-hosted runners, 7B model |
| Q11 | 90/10 | Changed-line split (recipe commits vs. fix commits) on green recipe + fix-loop runs; holds at a mean recipe share of at least 90% |

### Round 3

| # | Topic | Decision |
|---|---|---|
| Q12 | Models | `qwen2.5-coder:7b` only |
| Q13 | Default model | Product default becomes `qwen2.5-coder:7b`; 30B is an unmeasured option for GPU runners |
