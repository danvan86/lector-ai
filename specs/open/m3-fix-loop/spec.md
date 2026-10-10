# M3 – Fix loop · Spec

Milestone: [M3 in features.md](../../../docs/features.md#m3--fix-loop-cli-only-no-ci-yet-) · Decisions: [brainstorming.md](brainstorming.md) · Status: **Ready to implement**

## Goal

`lector heal <repo> --package … --from … --to …` runs the recipe and verify from M2, then lets a local LLM (Ollama) fix the remaining failures caused by the update, in small grounded search/replace edits, with one local commit per accepted fix.

**Why:** the recipe covers the known migration patterns. The rest is project-specific and is what a developer fixes by hand today (concept section 1). M3 proves that a local model can do it when it gets the real API of the new version and is only allowed small, verifiable steps. It runs from the CLI only. The pipeline (M5) and the guardrails (M4) come later, so no M3 output reaches a PR.

## What to build

### 1. Data model and ports in `core`

- `Edit` with `file`, `search`, `replace`.
- `LlmClient` port: turns a grounded fix request into `Edit`s plus a one-line rationale.
- `EcosystemAdapter` grows by:
  - `ground(change, diagnostics)`: the API surface of the new version relevant to the diagnostics.
  - A **cluster key** per `Diagnostic` (e.g. missing symbol, missing package, missing artifact).
  - Whether a file is a **build file**.

**Why:** the concept defines `Edit`, `LlmClient`, and `ground` (sections 2.1, 2.3, 5). Error messages and build files are language-specific, so the knowledge about them lives in the adapter, and the core only groups by key (rule 1, Q5). `core` still knows no language and no LLM vendor.

### 2. Ollama client in a new module `llm`

- Native `/api/chat` with `format` set to the JSON schema of the response (edits + rationale).
- `java.net.http.HttpClient` and kotlinx.serialization. No new HTTP library.
- `num_ctx` fixed at 16k tokens.
- Temperature `0` on the first attempt for a cluster, about `0.3` on retries.
- Code fences around the JSON are stripped. A response that doesn't match the schema is a failed attempt.
- Ollama URL (default `http://localhost:11434`) and model (default `qwen2.5-coder:7b`) are CLI options. The default was `qwen3-coder:30b` (Q1) and was changed by M6 Q13: the default is the model the benchmark measures.
- Ollama is the only outbound call the fix loop makes.

**Why:** structured output stops the model from returning prose or whole files (rule 3, Q2). The JDK client is enough for one endpoint (Q3). A fixed context size keeps memory use on the runner predictable (Q12). The same prompt at temperature 0 gives the same answer, so retries raise it a little (Q10). Source code only goes to Ollama (rule 7).

### 3. Clustering and order

- Only diagnostics **caused by the update** (M1 attribution) are in scope. Pre-existing, flaky, and unattributed failures are never sent to the model.
- Diagnostics are grouped by the adapter's cluster key. Without a key, they are grouped by file.
- Order: clusters with build-file errors (including build errors without a location) first, then the largest cluster.
- Clusters are rebuilt from the new diagnostics after every accepted attempt.

**Why:** one root cause, like a missing artifact, can produce hundreds of follow-on errors. Fixing it first makes most of them disappear (concept section 6.2). Pre-existing and flaky failures are not the update's fault (M1 Q14–Q16).

### 4. The fix request

Every request contains:

- The diagnostics of the cluster.
- The code around each error: at most the enclosing method or ±40 lines, never a whole file longer than that.
- Grounding from the adapter.
- The release notes from `--release-notes <file>`, if given.
- The recipe's diff, if a recipe ran.
- On a retry: the previous edits and why they failed.

If the request would not fit into the context, grounding, release notes, and recipe diff are trimmed. The diagnostics and the code snippets are never trimmed.

**Why:** a local model often doesn't know the new API, because it was released after its training cutoff. Without grounding it guesses (concept section 6.1, rule 3). Only the relevant code fits into a small context and keeps the model focused. Failure feedback on retries lets the model learn from its mistake (Q10).

### 5. JVM grounding in `ecosystems:jvm`

- `javap -public` signatures of the classes involved, read from the new version's JARs that the build already resolved into the local Maven repository.
- The classes come from the erroring file's imports and the symbol named in the error message.
- Cluster keys for javac and kotlinc errors: missing symbol, missing package, missing artifact.

**Why:** the JARs are already downloaded by the build, so grounding needs no extra network (Q6). Public signatures are the real API surface of the new version.

### 6. Applying edits and judging an attempt

1. **Apply:** each `search` must match exactly once. If there's no exact match, a whitespace-tolerant match is tried, and it must also be unique. Every edit must name a file that exists in the repository. If any edit of an attempt doesn't apply, the whole attempt is rejected.
2. **No downgrades:** if the attempt touched a build file, the resolved versions are read (M2 capability). If any package resolves lower than before the fix loop, including the PR's own package, the attempt is rejected.
3. **Verify:** M1's verify with attribution. It stops after the compile stage while there are compile errors and runs the tests once compile is clean. Test failures caused by the update are fixed in the same loop once they appear.
4. **Progress:** count the diagnostics caused by the update per stage as the tuple (build errors without a location, compile errors, test failures). The attempt is accepted if the tuple is lower than before, compared stage by stage in that order. Otherwise it's rejected.
5. **Accepted:** one local commit with the tool identity `lector`. The subject is the model's one-line rationale, and the body lists the diagnostics addressed.
6. **Rejected:** the working tree is reset to the last commit. The attempt counts against the budgets, and its edits and failure reason go into the next retry for that cluster.

**Why:** whitespace mistakes are the most common error of small models, and a unique match never guesses the location (Q4). Rejecting instead of repairing is rule 4. Reverting the PR's bump is the easiest way to make a build green, so downgrades are rejected (Q15, consistent with M2). The stage tuple accepts a correct root-cause fix that unmasks new compile errors, and never counts a new compile error as progress while fixing tests (Q18). Git commits make rollback reliable and give M5 its "one commit per AI fix" with no extra work (Q9, Q14).

### 7. Budgets and stopping

- Defaults: 3 attempts per cluster, 20 attempts in total, 30 minutes wall-clock, stop after 2 attempts in a row without progress. All configurable through CLI options.
- The loop stops when nothing caused by the update is left, or when a budget is used up.

**Why:** fits a normal CI job (Q11), and the concept requires all four limits (section 6.4).

### 8. CLI command `heal`

- `lector heal <repo> --package <package> --from <version> --to <version> [--base <git-ref>] [--release-notes <file>] [--format text|json] [--maven-settings <file>] [--ollama-url <url>] [--model <name>]` plus the budget options.
- Flow: the M2 `migrate` flow (clean working tree, downgrade rules, recipe, verify). If the recipe changed anything, its result becomes one local commit with the identity `lector` and the recipe name in the subject. Then the fix loop.
- The report adds a **fix-loop section** after the recipe section: every attempt with its cluster, outcome, and reason. Every accepted fix with its commit and rationale. Then the final verification with the remaining diagnostics.
- `--format json` contains the same, with nothing else on stdout.
- Exit codes as in M1, taken from the final verification: `0` nothing caused by the update left, `1` something left, `2` tool error (including Ollama not reachable).
- `verify` and `migrate` are unchanged. `migrate` still leaves the recipe result uncommitted.

**Why:** each command maps to one pipeline stage, and `heal` builds on `migrate` (M2 Q13). Committing the recipe separately keeps the first fix commit free of recipe changes (Q14). The report shows reviewers what the model tried, not only what worked.

### 9. Tests and fixtures

- **Fix loop unit tests** with a fake `LlmClient` and a fake adapter: accept on progress, reject on no progress, reject on a non-applying edit, reject on a downgrade, cluster order, budgets, stop after 2 attempts without progress, temperature on first attempt and retries, retry request contains the previous edits and failure.
- **Edit application unit tests:** exact match, whitespace-tolerant match, ambiguous match, missing match, file not in the repository.
- **Ollama client tests** against a local fake HTTP server: request has `format` with the schema, `num_ctx` 16k, the temperature; response parsing; code fences stripped; invalid response.
- **JVM tests:** cluster keys on captured javac and kotlinc output. `javap` grounding on a small JAR fixture.
- **One tagged real-model test** with Testcontainers' Ollama module: a simple Maven fixture that is red because of an update, with no matching recipe, goes green through the fix loop alone. The model is a parameter, default `qwen2.5-coder:7b`. A CI workflow runs it manually and nightly on a standard GitHub-hosted runner.

**Why:** unit tests never use a real LLM (AGENTS.md). The real-model test is the M3 "done when". It uses a small model because standard runners are CPU-only with about 16 GB RAM. The default model's quality is measured in the benchmark (M6, Q17). Testcontainers gives the same setup locally and in CI (Q13). Testcontainers is a new test-only dependency; its reason goes into the PR description.

## What not to do

| Don't | Why |
|---|---|
| Let the model return whole files or free text | Rule 3. Small models drop lines and truncate. |
| Use the OpenAI-compatible API, Ktor, or OkHttp | Decided against (Q2, Q3). |
| Use fuzzy (edit-distance) matching, or apply an ambiguous match | Decided against (Q4). It can change the wrong place. |
| Repair an edit that doesn't apply | Rule 4: rejected, counted as a failed attempt. |
| Send pre-existing, flaky, or unattributed failures to the model | Not the update's fault (M1). |
| Read `-sources.jar` or download anything for grounding | Decided against (Q6). |
| Fetch release notes from GitHub or a registry | Decided against (Q7). Source adapters come in M5, the fallback in M8. |
| Apply edits for several clusters before verifying | Decided against (Q8): every attempt is verified on its own. |
| Roll back with in-memory snapshots | Decided against (Q9): Git commits and resets. |
| Request several candidates per attempt | Decided against (Q10). |
| Use a context larger than 16k | Decided against (Q12). |
| Accept an attempt only because the total count dropped, or only because its cluster disappeared | Decided against (Q18): the stage tuple decides. |
| Add guardrails: symbol comparison, test protection, stub or suppression checks, diff size limit, `editableScope` | M4. Also no check for generated or Git-ignored files (Q16, B). |
| Push, open PRs, comment, or use a GitHub token | M5. Commits stay local. |
| Commit in `migrate` | M2 decision stays (Q14 only changes `heal`). |
| Call an LLM during recipe selection or verify | Rule 2. |
| Use a real Ollama in unit tests or in `./gradlew build` | AGENTS.md: real-model tests are tagged and opt-in. |
| Bake model weights or the container image | M5. |

## Acceptance criteria

The review agent checks every criterion and reports it as *met*, *not met*, or *can't verify*, with evidence.

| ID | Criterion | Covers | How to verify |
|---|---|---|---|
| AC1 | `./gradlew build` passes | all | Run it from the repo root: exit code `0` |
| AC2 | `Edit` in `core` has `file`, `search`, `replace` | 1 | Type definition |
| AC3 | `LlmClient` is a port in `core` and returns `Edit`s plus a rationale | 1 | Interface definition |
| AC4 | `EcosystemAdapter` has `ground`, a cluster key per `Diagnostic`, and a build-file check | 1 | Interface definition |
| AC5 | `core` has no project dependencies and no Ollama- or Maven-specific code | 1 | `core/build.gradle.kts` has no `project(`; case-insensitive search of `core/src/main` for `ollama`, `maven`, `javap`: no matches |
| AC6 | Module `llm` exists, depends on `core` only (project-wise), and `cli` depends on it | 2 | `settings.gradle.kts`, module build files |
| AC7 | The client calls `/api/chat` with `format` set to a JSON schema | 2 | Ollama client test against a fake server |
| AC8 | Requests use `num_ctx` 16384 | 2 | Ollama client test |
| AC9 | Temperature is `0` on a cluster's first attempt and higher on retries | 2 | Fix loop unit test with a recording fake `LlmClient` |
| AC10 | Code fences around the JSON are stripped; a response not matching the schema is a failed attempt | 2 | Ollama client tests |
| AC11 | No HTTP library other than the JDK client is added | 2, Don't | Version catalog and module build files: no Ktor, OkHttp, or similar |
| AC12 | Ollama URL and model are CLI options with defaults `http://localhost:11434` and `qwen2.5-coder:7b` | 2, 8 | CLI test or `lector heal --help` |
| AC13 | Only diagnostics caused by the update reach the `LlmClient` | 3 | Fix loop unit test: pre-existing and flaky diagnostics never appear in a request |
| AC14 | Diagnostics are grouped by the adapter's cluster key, falling back to file | 3 | Unit test |
| AC15 | Build-file clusters come first, then the largest cluster | 3 | Unit test |
| AC16 | A request contains the cluster's diagnostics, code snippets, grounding, release notes (if given), and recipe diff (if any) | 4 | Fix loop unit test inspects the recorded request |
| AC17 | A snippet for an error in a 500-line file has at most 81 lines | 4 | Unit test |
| AC18 | When trimming is needed, diagnostics and snippets are kept in full | 4 | Unit test with oversized grounding |
| AC19 | A retry request contains the previous edits and their failure reason | 4, 6 | Fix loop unit test |
| AC20 | JVM grounding returns `javap -public` signatures of a class from a JAR | 5 | Unit test on a small JAR fixture |
| AC21 | javac and kotlinc diagnostics get cluster keys for missing symbol, missing package, missing artifact | 5 | Unit tests on captured output |
| AC22 | An exact unique match applies; a whitespace-only difference applies through the tolerant match | 6 | Edit application unit tests |
| AC23 | An ambiguous match, a missing match, or a file not in the repository rejects the whole attempt | 6 | Edit application and fix loop unit tests |
| AC24 | An attempt that makes any package resolve lower than before the loop, including the PR's own package, is rejected | 6 | Fix loop unit test with a fake adapter |
| AC25 | An attempt is accepted only if the stage tuple (build errors, compile errors, test failures) gets lower | 6 | Unit tests: 1 build error → 0 build errors + 50 compile errors is accepted; 0/0/3 → 0/1/0 is rejected |
| AC26 | An accepted attempt becomes one local commit by `lector`, with the rationale as subject and the diagnostics in the body | 6 | Integration or unit test on a temp Git repo |
| AC27 | A rejected attempt leaves the working tree equal to the last commit | 6 | Unit test on a temp Git repo: `git status --porcelain` is empty |
| AC28 | Defaults are 3 per cluster, 20 in total, 30 minutes, 2 in a row without progress, and each is a CLI option | 7 | CLI test or `--help`, plus fix loop unit tests for each limit |
| AC29 | The loop stops as soon as nothing caused by the update is left | 7 | Fix loop unit test: no further `LlmClient` call |
| AC30 | `heal` commits a changed recipe result as its own commit before the first fix | 8 | Integration test with a fake `LlmClient`: commit order |
| AC31 | `heal` requires a clean working tree (exit `2` otherwise) | 8 | CLI test |
| AC32 | The text report has a fix-loop section with every attempt (cluster, outcome, reason), accepted fixes with commit and rationale, and the remaining diagnostics | 8 | CLI test with a fake `LlmClient` |
| AC33 | `--format json` contains the fix-loop section and the final verification, with nothing else on stdout | 8 | CLI test parses stdout as JSON |
| AC34 | Exit code follows the final verification (`0` / `1`); an unreachable Ollama is `2` | 8 | CLI tests |
| AC35 | `migrate` still leaves the recipe result uncommitted | 8, Don't | M2 tests still pass (part of AC1) |
| AC36 | The tagged real-model test turns a red fixture with no matching recipe green through the fix loop alone | 9 | Run the tagged test task (needs Docker): passes |
| AC37 | The real-model test's model is a parameter with default `qwen2.5-coder:7b` | 9 | Test source and build configuration |
| AC38 | A CI workflow runs the real-model test on `workflow_dispatch` and on a schedule, and not on push or pull requests | 9 | Workflow file under `.github/workflows/` |
| AC39 | Neither the real-model test nor any real Ollama call is part of `./gradlew build` | 9, Don't | Build configuration excludes the tag; unit tests use the fake `LlmClient` |
| AC40 | No guardrail, `editableScope`, push, PR, or GitHub API code is added | Don't | Ports in `core`, module list, version catalog |

## Docs to update

- `docs/features.md`: set M3 to ✅ when done. M4: add "close rule 5: reject edits to generated code (`editableScope`); not enforced in M3".
- `docs/concept.md` section 6.4: define progress as the stage-by-stage tuple (build errors without location, compile errors, test failures), and add the "no downgrades by the LLM" rule.
- `docs/concept.md` section 5 interface outline: add the cluster key and the build-file check.
- `docs/concept.md` section 8.3: commits are created locally by `heal` from M3 on, with the identity `lector`; M5 pushes them and swaps the identity.
- `AGENTS.md` **Commands**: add the command for the tagged real-model test.
