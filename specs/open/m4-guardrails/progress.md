# Progress – M4 Guardrails

Spec: [`spec.md`](spec.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | `LanguageSupport` port in `core`; tree-sitter facts for Java and Kotlin in `ecosystems:jvm`, with query files and query tests | ✅ |
| 2 | Guardrail framework in `core` (line diff, `Guardrail`, `Violation`) and the file rules: scope, diff size, build-file blocklist (JVM scope and Maven blocklist in the adapter) | ✅ |
| 3 | Fact rules: symbol comparison, test deletion, skips, assertions, test count, stubbing, suppression, parse errors; Java and Kotlin tests | ✅ |
| 4 | Fix loop runs the guardrails per attempt, before verify; `--max-changed-lines` | ✅ |
| 5 | Report: every rejection, a count per rule, the runtime test count of the final verify | ✅ |
| 6 | Quality gates verified | ✅ |
| 7 | Docs in sync | ✅ |
| 8 | Review and fixes: [`review.md`](review.md) | ✅ |

## Quality gates

After the review: `clean build --rerun-tasks --no-build-cache` exits `0` with 378 unit tests (core 137, jvm 110, cli 81,
llm 17, recipes 33), 16 end-to-end tests, and 100 % lines and branches per module.

Before the review: each on its own from a clean state: `ktlintCheck`, `detektMain detektTest`, `test` (368 unit tests),
`:cli:endToEndTest` (16), `koverVerify` (100 % lines and branches per module), and
`clean build --rerun-tasks --no-build-cache`: all exit `0`. One `detektMain detektTest` run exited `1` with its log
overwritten; four reruns, one in the same sequence, and the clean build passed.

Probes, each failed the build on its own gate and was reverted:

| Gate | Probe | Failed with |
|---|---|---|
| ktlint | bad formatting in `core` main and `ecosystems:jvm` test sources | `ktlintMainSourceSetCheck`, `ktlintTestSourceSetCheck` |
| detekt | `println` in `core`, `System.out.println` in `ecosystems:jvm` | `ForbiddenMethodCall` in `detektMain` |
| Kover | an untested function in `core` | `:core:koverVerify` |
| Tests | a wrong assertion; a broken Kotlin query file | `:ecosystems:jvm:test` (the query broke two test classes) |

`./gradlew build --dry-run` lists every gate task once.

## Acceptance criteria

| ID | Status | Evidence |
|---|---|---|
| AC1 | met | `./gradlew build` exit `0` |
| AC2 | met | `EcosystemAdapter.languageSupport`, `LanguageSupport`/`SourceFacts` in `core` |
| AC3 | met | no matches for `treesitter`, `tree-sitter`, `kotlin.test`, `junit`, `@Test` in `core/src/main` or `core/build.gradle.kts` |
| AC4, AC5, AC30 | met | `tree-sitter`, `tree-sitter-java`, `tree-sitter-kotlin` in the catalog and `ecosystems/jvm/build.gradle.kts`; `queries/java/*.scm`, `queries/kotlin/*.scm` |
| AC6 | met | `FileRules.kt`, `SourceRules.kt`, the list in `Guardrails.kt` |
| AC7, AC8 | met | `FixLoopTest`: violating attempts are reset, never verified, counted, and their violations are in the retry |
| AC9 | met | `RecipeStepTest`: a recipe that deletes a method is kept |
| AC10, AC11 | met | `FileRulesTest` (target, generated, untracked, outside), `FixLoopTest` (a real Git-ignored file), 50/51 lines, `--max-changed-lines` |
| AC12–AC21, AC24 | met | `JvmGuardrailsTest`, Java and Kotlin; `SourceRulesTest` on hand-made facts |
| AC22, AC23 | met | `MavenGuardrailsTest`: each property, surefire `<exclude>`, failsafe `<skip>`; no `skipTests` in `core/src/main` |
| AC25, AC26 | met | `HealCommandTest`, `FixLoopReportTest`, and the end-to-end test through the start script |
| AC27, AC28 | met | a rejecting test per rule; `JvmLanguageSupportTest` on `fixtures/language-facts/` |
| AC29 | met | no `LlmClient` in the guardrail sources |

## Judgment calls

- Scope rejects every file Git doesn't track, not only ignored ones: an untracked file at attempt time is build output.
- Any new assumption call (`assume…`, `abort`) is a skip marker, not only one that always skips: "always" isn't decidable.
- The diff-size limit sits in `Budgets` (`changedLinesPerAttempt`), with the fix loop's other limits.
- detekt `TooManyFunctions` ignores overridden functions: an adapter's size is its port's.

## Open

- CI on GitHub hasn't run this branch yet. tree-sitter's native libraries were only loaded on macOS (arm64) here; Linux
  runs first in CI.
