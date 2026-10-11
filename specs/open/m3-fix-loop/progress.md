# Progress – M3 Fix loop

Spec: [`spec.md`](spec.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | `core`: `Edit` and edit application, code snippets | ✅ |
| 2 | Adapter grows (`ground`, cluster key, build file); `ecosystems:jvm`: cluster keys for javac, kotlinc, and Maven, `javap` grounding | ✅ |
| 3 | `core`: `LlmClient` port, clusters and order, progress tuple, Git commits, fix loop with budgets and downgrade check | ✅ |
| 4 | `llm`: Ollama client (`/api/chat`, JSON schema, `num_ctx`, temperature) and prompt with trimming | ✅ |
| 5 | `cli`: `heal` command, recipe commit, fix-loop report (text and JSON), end-to-end tests | ✅ |
| 6 | Real-model test with Testcontainers' Ollama module, fixture, CI workflow | ✅ |
| 7 | Quality gates verified | ✅ |
| 8 | Docs in sync (`features.md`, concept 5, 6.4, 8.3, `AGENTS.md`) | ✅ |
| 9 | Review and fixes: [`review.md`](review.md) | ✅ |

## Quality gates

Final check from a clean state, each on its own: `ktlintCheck`, `detektMain detektTest :recipes:detektGenerator`, `test`, `:cli:endToEndTest`, `koverVerify`, `:cli:integrationTest`, and `clean build --rerun-tasks --no-build-cache` all exit with 0. After the review: 299 unit tests (`core` 96, `recipes` 33, `llm` 17, `ecosystems:jvm` 77, `cli` 76), 15 end-to-end tests, 2 integration tests, 1 real-model test (`:cli:realModelTest`, 8 min with Docker on a Mac, CPU only, model download included). Kover: 100 % lines and branches in every module (`MainKt` excluded, as before). `build --warning-mode all` shows no deprecation from our build files.

Each gate was checked by adding a violation, running `./gradlew build --continue`, and reverting:

| Gate | Probe | Result |
|---|---|---|
| ktlint | extra space in `llm` main, test, and `llm/build.gradle.kts` | fails in `:llm:ktlintMainSourceSetCheck`, `ktlintTestSourceSetCheck`, `ktlintKotlinScriptCheck` |
| detekt | `println` in `llm` main, `System.out.println` in `HealCommand` | fails in `:llm:detektMain`, `:cli:detektMain` with `ForbiddenMethodCall` |
| Kover | untested function with a branch in `core` main | fails in `:core:koverVerify` |
| Tests | wrong expected `Progress` in `ClusterTest` | fails in `:core:test` |
| End-to-end | the same `println` in `HealCommand` | also fails `:cli:endToEndTest`: stdout must stay clean |

`./gradlew build --dry-run` contains the `:llm` gates and `:cli:endToEndTest`, and neither `:cli:integrationTest` nor `:cli:realModelTest`.

## Acceptance criteria

| ID | Result | Evidence |
|---|---|---|
| AC1 | Met | `./gradlew clean build --rerun-tasks --no-build-cache` exits with 0 |
| AC2 | Met | `core/.../Edit.kt` |
| AC3 | Met | `core/.../LlmClient.kt`: `propose(FixRequest)` → `Proposal.Edits(edits, rationale)` or `Proposal.Invalid` |
| AC4 | Met | `EcosystemAdapter.kt`: `ground`, `clusterKey`, `isBuildFile` |
| AC5 | Met | `core/build.gradle.kts` has no `project(`; `grep -rniE 'ollama\|maven\|javap' core/src/main` finds nothing |
| AC6 | Met | `settings.gradle.kts`; `llm` depends on `:core` only; `cli` on `:llm` |
| AC7, AC8 | Met | `OllamaClientTest` against a JDK `HttpServer`: `/api/chat`, `format` = schema, `num_ctx` 16384 |
| AC9 | Met | `FixLoopTest`: 0.0 on the first request, 0.3 on the retry |
| AC10 | Met | `OllamaClientTest`: fences stripped; every schema mismatch is `Proposal.Invalid`; `FixLoopTest`: invalid is a rejected attempt |
| AC11 | Met | Catalog and build files: only Testcontainers (test-only) is new |
| AC12 | Met | `HealCommandTest`: defaults reach the client factory; `heal --help` shows them |
| AC13 | Met | `FixLoopTest`: pre-existing and flaky never reach a request |
| AC14, AC15 | Met | `ClusterTest` |
| AC16 | Met | `FixLoopTest` inspects the recorded request |
| AC17 | Met | `SnippetTest`: 81 lines for an error in a 500-line file |
| AC18 | Met | `PromptTest`: oversized grounding, release notes, and diff are trimmed; errors and code stay whole |
| AC19 | Met | `FixLoopTest` |
| AC20 | Met | `JavapGroundingTest` with the real `javap` on JARs compiled in the test |
| AC21 | Met | `ClusterKeysTest` on captured javac, kotlinc 2 and 1.9, and Maven output |
| AC22, AC23 | Met | `EditTest`, `FixLoopTest` |
| AC24 | Met | `FixLoopTest`: the PR's own package and another package |
| AC25 | Met | `ClusterTest` (1/0/0 → 0/50/0 accepted, 0/0/3 → 0/1/0 rejected) and `FixLoopTest` |
| AC26, AC27 | Met | `FixLoopTest` and `GitWorkingTreeTest` on temp Git repos |
| AC28 | Met | `heal --help` shows 3, 20, 30, 2; `FixLoopTest` covers each limit; `HealCommandTest` sets them |
| AC29 | Met | `FixLoopTest`: one request, then `NOTHING_LEFT` |
| AC30 | Met | `HealCommandTest`: recipe commit below the fix commit, its diff in the request |
| AC31 | Met | `HealCommandTest`, exit 2 |
| AC32, AC33 | Met | `HealCommandTest`, `FixLoopReportTest`, `LectorEndToEndTest` parses stdout as JSON |
| AC34 | Met | `HealCommandTest` (0, 1, unreachable Ollama 2), `LectorEndToEndTest` |
| AC35 | Met | `MigrateCommandTest`, `MigrateIntegrationTest` unchanged and green |
| AC36 | Met | `./gradlew :cli:realModelTest` passes: one accepted attempt adds `import …MoreObjects` and calls `MoreObjects.firstNonNull` |
| AC37 | Met | `RealModelTest` uses `-Plector.model`, else the CLI's `DEFAULT_MODEL` (`qwen2.5-coder:7b`) |
| AC38 | Met | `.github/workflows/real-model.yml`: `workflow_dispatch` and `schedule` only |
| AC39 | Met | `tasks.test` excludes `real-model`; `build --dry-run` has no `realModelTest` |
| AC40 | Met | No guardrail, `editableScope`, push, PR, or GitHub API code |

## Deviations and judgment calls

- **`ground` takes the repository:** `ground(repo, change, diagnostics)`. It reads the erroring files' imports. Concept section 5 says so now.
- **Grounding JARs:** every artifact of the package's group at the new version (`<group>/*/<version>/*.jar`), so a release train like Spring Boot is covered. The local repository is `~/.m2/repository`; a `localRepository` in the Maven settings is not followed.
- **Classes shown by `javap`:** the files' imports and qualified names in the messages, plus the classes in those packages that declare the missing symbol, and classes named like it anywhere in the JARs. That is how `MoreObjects.firstNonNull` reaches the model for a removed `Objects.firstNonNull`. Nested classes are left out.
- **Snippets:** ±40 lines, merged per file. The enclosing method is not detected: that needs a parser (tree-sitter, M4). Build errors without a location get the build files around the line with the new version.
- **Whitespace-tolerant match** is line by line (indentation, trailing blanks, runs of spaces); the replacement is inserted as given.
- **`analysis` field in the answer schema**, first, ignored by the loop. Without it, `qwen2.5-coder:7b` replaced the class of the moved method but never added its import, at any temperature; with it, 5 of 5 answers had both edits. The port still returns edits and a rationale.
- **Feedback on a rejection without progress** includes the errors after the edits (up to ten), not only the counts. With only the counts the model repeated its edit.
- **Locale:** cluster keys and symbol extraction read the English compiler messages. The real-model task sets `MAVEN_OPTS=-Duser.language=en`; the container image (M5) must run Maven in English too.
- **Ollama image** `0.40.3` lives in the version catalog, but Dependabot doesn't update it from there.
- **Answer length** is capped with `num_predict` 3072; the prompt is cut to fit the rest of the 16k context (3 characters per token, conservatively).
- **`help` shows default values** for every command now (`MordantHelpFormatter(showDefaultValues = true)`).
- **`UpdateCommand`** holds `--package/--from/--to` for `migrate` and `heal`.
- **New test fixture** `ecosystems/jvm/.../fixtures/missing-package` with captured output for javac's "package does not exist".

## Open

- CI on GitHub hasn't run for this branch, and the `Real model` workflow hasn't run on a GitHub-hosted runner yet (CPU only, 16 GB). Locally it passed in Docker with 8 GB.
- `docs/architecture/` is written after the review (architecture-docs).
