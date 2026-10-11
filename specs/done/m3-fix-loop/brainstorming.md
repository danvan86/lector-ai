# M3 – Fix loop · Brainstorming

Milestone: [M3 in features.md](../../../docs/features.md#m3--fix-loop-cli-only-no-ci-yet-) · Status: **Closed** (all questions decided)

**How to answer:** the recommended option is preselected with `[x]`. Move the `x` to choose another one. Write anything else under **Notes**.

## Already fixed by the project docs and earlier milestones

- Ollama with structured output (JSON schema). The LLM returns search/replace edits, never whole files.
- Grounding: API signatures of the new version, release notes, the recipe diff, and only the code around the error.
- Errors are clustered by root cause, root causes first. Build-file edits are allowed. Generated code is never edited.
- Budgets: attempts per cluster, a global time limit, stop when there's no progress.
- Unit tests use a fake `LlmClient`. Real Ollama tests are tagged and opt-in.
- From M1: two-stage verify, attribution (only failures caused by the update are in scope), exit codes `0`/`1`/`2`, `--base`, `--format json`.
- From M2: `heal` builds on `migrate` (recipe + verify), same options. The recipe result stays uncommitted in `migrate`. Resolved versions and `versionScheme` exist as adapter capabilities. No downgrades.

```
Diagnostic[] ─▶ cluster ─▶ pick next cluster (root cause first)
                               │
                               ▼
                 adapter.ground() + code snippet + release notes
                               │
                               ▼
                    LlmClient ─▶ Edit[] ─▶ apply ─▶ verify
                               ▲                    │
                               └── retry (budget) ◀─┘ errors left?
```

---

## Round 1

### Q1 · Default model

Configurable in any case. The question is which model we test and tune prompts against.

- [x] **A · `qwen3-coder:30b` (MoE, about 3B parameters active per token)** — *recommended.* Strong at code, and fast for its size because only a few experts run per token. Needs about 20 GB of RAM or VRAM in its quantized form.
- [ ] **B · `qwen2.5-coder:14b` or `:7b`** — Fits small GPUs and works on CPU-only runners. Noticeably weaker at multi-file reasoning.
- [ ] **C · `devstral` (Mistral, 24B)** — Built for agentic coding tasks. Dense model, so slower than A on the same hardware.

**Notes:** A (all recommended options accepted in chat)

### Q2 · Ollama API

- [x] **A · Native `/api/chat` with `format` = JSON schema** — *recommended* (concept). Full access to Ollama options (`num_ctx`, `seed`, `keep_alive`).
- [ ] **B · OpenAI-compatible `/v1/chat/completions` with `response_format`** — Same client would work against vLLM or llama.cpp later. Loses some Ollama-specific options.

**Notes:** A (all recommended options accepted in chat)

### Q3 · HTTP client

- [x] **A · `java.net.http.HttpClient` + kotlinx.serialization** — *recommended.* Part of the JDK, no new dependency. Enough for a single endpoint.
- [ ] **B · Ktor client** — Idiomatic Kotlin, coroutines, but several new dependencies for one endpoint.
- [ ] **C · OkHttp** — Popular and solid, one more dependency with no real advantage here.

**Notes:** A (all recommended options accepted in chat)

### Q4 · How strictly `search` must match

Small models often get indentation or trailing whitespace wrong.

- [x] **A · Exact match first, then a whitespace-tolerant match; the match must be unique either way** — *recommended.* Catches the common whitespace mistakes without guessing. Ambiguous or missing matches are rejected (rule 4).
- [ ] **B · Exact match only** — Most predictable, but many otherwise-correct edits get rejected.
- [ ] **C · Fuzzy match (edit distance)** — Accepts more edits, but can apply a change to the wrong place.

**Notes:** A (all recommended options accepted in chat)

### Q5 · How errors are clustered

Error messages are language-specific, so rule 1 puts the knowledge in the adapter.

- [x] **A · The adapter provides a cluster key per `Diagnostic` (missing symbol, missing package, missing artifact), with "same file" as the fallback** — *recommended.* The core just groups by key. Order: build-file errors first, then the largest cluster.
- [ ] **B · Group by file only** — Language-neutral and trivial, but one removed class spread over 40 files becomes 40 clusters.
- [ ] **C · Group by diagnostic `code`** — Too coarse: every "cannot find symbol" lands in one cluster.

**Notes:** A (all recommended options accepted in chat)

### Q6 · Grounding for the JVM

- [x] **A · `javap -public` signatures of the classes involved, taken from the new version's JAR in the local Maven repository** — *recommended.* The JAR is already downloaded by the build, so there's no extra network. Classes come from the erroring file's imports and the symbol in the error message.
- [ ] **B · Snippets from the `-sources.jar`** — Includes Javadoc (often with "use X instead"), but needs an extra download and is much larger.
- [ ] **C · Both, sources only when javap isn't enough** — Best context, two code paths.

**Notes:** A (all recommended options accepted in chat)

### Q7 · Release notes in M3

Source adapters (which read release notes from the PR) arrive in M5.

- [x] **A · Optional `--release-notes <file>` option** — *recommended.* Tests and the benchmark can provide them. M5 fills the same field from the PR body.
- [ ] **B · Fetch from GitHub releases now** — Real data, but a network call and mapping logic that belongs to the fallback source (M8).
- [ ] **C · No release notes until M5** — Simplest, but M3's quality would be measured without a key grounding input.

**Notes:** A (all recommended options accepted in chat)

### Q8 · What is re-run after an edit

A full Maven `verify` can take minutes. The fix loop may need dozens of iterations.

- [x] **A · Compile stage after every attempt, full `verify` only once compile is clean** — *recommended.* Uses the two-stage verify from M1. Test failures caused by the update (see M1 attribution) are fixed in a second phase of the same loop. Pre-existing and flaky failures are never in scope.
- [ ] **B · Full `verify` after every attempt** — Most accurate, often too slow for the time budget.
- [ ] **C · Apply edits for all clusters, then one verify** — Fastest, but you can't tell which edit helped or broke something.

**Notes:** A (all recommended options accepted in chat)

### Q9 · Working-tree state and rollback

- [x] **A · Git: each accepted attempt is a local commit, a failed attempt is reset** — *recommended.* Reliable rollback, and the commits become the "one commit per AI fix" in M5 with no extra work.
- [ ] **B · In-memory snapshots of changed files** — No Git dependency in the loop, but we'd reimplement what Git does.

**Notes:** A (all recommended options accepted in chat)

### Q10 · Retries and temperature

The same prompt at temperature 0 gives the same answer, so a plain retry is useless.

- [x] **A · Temperature 0 on the first attempt. Retries include the previous edit and why it failed, at a slightly higher temperature (e.g. 0.3)** — *recommended.* The model learns from the failure, and the higher temperature avoids repeating itself.
- [ ] **B · Always temperature 0, retries only add the failure feedback** — Fully reproducible, but the model often repeats itself.
- [ ] **C · Several candidates per attempt (n samples), keep the first that verifies** — Better hit rate, but multiplies LLM time and verify runs.

**Notes:** A (all recommended options accepted in chat)

### Q11 · Default budgets

- [x] **A · 3 attempts per cluster, 20 attempts in total, 30 minutes wall-clock, stop after 2 attempts in a row without progress** — *recommended.* Fits in a normal CI job. All configurable.
- [ ] **B · Generous: 5 per cluster, 50 total, 90 minutes** — Higher success rate, long-running jobs and runner contention.
- [ ] **C · Tight: 2 per cluster, 10 total, 15 minutes** — Cheap, may give up on fixable cases.

**Notes:** A (all recommended options accepted in chat)

### Q12 · Context size per request

- [x] **A · Fixed `num_ctx` of 16k tokens. The snippet is the enclosing method or ±40 lines, and grounding is trimmed to fit** — *recommended.* Predictable memory use on the runner, enough for one cluster.
- [ ] **B · 32k context** — More grounding fits, but needs much more memory and is slower.
- [ ] **C · 8k context** — Runs on smaller hardware, often too little room for grounding.

**Notes:** A (all recommended options accepted in chat)

### Q13 · How the M3 "done when" is checked against a real model

The product never runs on a developer machine, but we still need a real end-to-end check.

- [x] **A · Tagged integration test using Testcontainers' Ollama module, run by a manual/nightly CI job** — *recommended.* Reproducible, same setup locally and in CI, and not part of `./gradlew build`.
- [ ] **B · Docker Compose file with Ollama, run by hand** — Simple, but not repeatable in CI.
- [ ] **C · Shared Ollama server on our infrastructure** — Fast (model already loaded), but tests depend on an external service.

**Notes:** A (all recommended options accepted in chat)

---

## Round 2

Round 1 was accepted as recommended. Checking it against M1 and M2 shows four points that are still open.

### Q14 · Local commits vs. M2's uncommitted recipe result

Q9 makes every accepted attempt a local commit. M2 (Q10) leaves the recipe result uncommitted and calls commits "M5's job". If `heal` commits fixes on top of an uncommitted recipe result, the first fix commit would also contain the recipe's changes.

- [x] **A · `heal` commits locally: first the recipe result as its own commit, then one commit per accepted fix. Fixed tool identity (`lector`) and a fixed message format (error addressed + one-line rationale). `migrate` stays as in M2** — *recommended.* Reliable rollback with Git, and the commit structure from concept section 8.3 exists from M3 on. M5 then only pushes and swaps the identity for the GitHub App.
- [ ] **B · No commits: roll back by restoring the files an attempt touched. Accepted fixes are recorded in the report (edits, error, rationale) so M5 can turn them into commits** — Keeps M2's "commits are M5's job", but M5 has to rebuild the commits from a mixed working tree.

**Notes:** A (answered in chat)

### Q15 · Downgrades by the LLM

Build-file edits are allowed. The easiest way for the model to make the build green is to revert the PR's own bump, or to pin some other dependency lower. M2's rule is "no downgrades", and resolved versions exist since M2.

- [x] **A · After every attempt that touched a build file, read the resolved versions. If any package is lower than before the fix loop (including the PR's own package), reject the attempt** — *recommended.* Closes the most obvious cheat, using a capability we already have. Rejected like any failed edit (rule 4).
- [ ] **B · Only protect the PR's own package** — Cheaper rule, but the model can still downgrade a transitive dependency.
- [ ] **C · Leave it to the guardrails in M4** — M3 output doesn't reach a PR yet, but the M3 "done when" could then be met by reverting the update.

**Notes:** A (answered in chat)

### Q16 · Generated code before M4

Rule 5: never edit generated code. The adapter's `editableScope` only arrives with the guardrails in M4.

- [ ] **A · Reject edits to files outside the repository or ignored by Git (e.g. `target/generated-sources`)** — *recommended.* Language-neutral, one Git check, and covers the usual generated output. `editableScope` refines it in M4.
- [x] **B · Wait for M4** — No work now, but rule 5 isn't enforced in M3.

**Notes:** B (answered in chat). Rule 5 is not enforced in M3. Acceptable only because M3 output never reaches a PR; M4 must close it

### Q17 · Hardware for the real-model test (Q13)

The repo's CI runs on GitHub-hosted runners (M0 Q7). Standard runners are CPU only with about 16 GB RAM. `qwen3-coder:30b` (Q1) needs about 20 GB and would be very slow on CPU, and the model has to be downloaded on every run.

- [x] **A · The tagged test takes the model as a parameter, default `qwen2.5-coder:7b`, on a standard GitHub-hosted runner. Its fixture is deliberately simple. The default model is measured in the benchmark (M6) on proper hardware** — *recommended.* Runs without new infrastructure and proves the loop works end to end. Says little about the quality of the default model.
- [ ] **B · A larger GitHub-hosted runner (paid, 32–64 GB RAM) with the default model** — Tests what we ship, but CPU-only and slow, and costs per run.
- [ ] **C · A self-hosted runner with a GPU, with the default model** — Realistic and fast. Only possible if you have one.

**Notes:** A (answered in chat)

---

## Round 3

Triggered while writing the loop: the concept measures progress as "the number of failures caused by the update drops". Fixing a root cause can unmask many errors, e.g. a fixed missing artifact turns 1 build error into 50 visible compile errors.

### Q18 · When an attempt counts as progress

- [x] **A · Compare stage by stage: count the diagnostics caused by the update as (build errors without a location, compile errors, test failures). Accept the attempt if this tuple gets lower, comparing the stages in that order** — *recommended.* A fixed missing artifact counts even if compile errors appear. A new compile error never counts as progress while fixing tests.
- [ ] **B · Total count drops** — The concept's wording and simplest. Rejects correct root-cause fixes that unmask hidden errors.
- [ ] **C · The targeted cluster is gone** — Accepts fixes that break other code.

**Notes:** A (answered in chat)

---

## Decisions

### Round 1

| # | Topic | Decision |
|---|---|---|
| Q1 | Default model | `qwen3-coder:30b`, configurable. **Changed by M6 Q13:** default `qwen2.5-coder:7b` |
| Q2 | Ollama API | Native `/api/chat` with `format` = JSON schema |
| Q3 | HTTP client | `java.net.http.HttpClient` + kotlinx.serialization |
| Q4 | `search` matching | Exact first, then whitespace-tolerant; must be unique |
| Q5 | Clustering | Cluster key per `Diagnostic` from the adapter, "same file" as fallback; build-file errors first, then largest cluster |
| Q6 | JVM grounding | `javap -public` signatures from the new version's JAR in the local Maven repository |
| Q7 | Release notes | Optional `--release-notes <file>` |
| Q8 | Re-run after an edit | Compile stage after every attempt, full `verify` only once compile is clean; test failures caused by the update in a second phase |
| Q9 | Rollback | Git: accepted attempt = local commit, failed attempt = reset (see Q14) |
| Q10 | Retries | Temperature 0 first; retries get the previous edit and failure, temperature about 0.3 |
| Q11 | Budgets | 3 per cluster, 20 total, 30 minutes, stop after 2 attempts without progress; all configurable |
| Q12 | Context | `num_ctx` 16k; snippet = enclosing method or ±40 lines; grounding trimmed to fit |
| Q13 | Real-model check | Tagged Testcontainers Ollama test, manual/nightly CI job (see Q17) |

### Round 2

| # | Topic | Decision |
|---|---|---|
| Q14 | Commits | `heal` commits locally: the recipe result first, then one commit per accepted fix. Fixed identity `lector`, message = error addressed + one-line rationale. `migrate` stays uncommitted (M2) |
| Q15 | LLM downgrades | After every attempt that touched a build file, read resolved versions. Any package lower than before the loop, including the PR's own package → attempt rejected |
| Q16 | Generated code | No scope check in M3; `editableScope` in M4. Rule 5 is not enforced until then, which is acceptable only because M3 output never reaches a PR |
| Q17 | Real-model test hardware | Model is a parameter of the tagged test, default `qwen2.5-coder:7b`, standard GitHub-hosted runner, simple fixture. The default model is measured in M6 |

### Round 3

| # | Topic | Decision |
|---|---|---|
| Q18 | Progress | Stage-by-stage comparison of (build errors without location, compile errors, test failures) caused by the update; accepted if the tuple gets lower |
