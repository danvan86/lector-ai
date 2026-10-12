# M4 – Guardrails · Spec

Milestone: [M4 in features.md](../../../docs/features.md#m4--guardrails-) · Decisions: [brainstorming.md](brainstorming.md) · Status: **Ready to implement**

## Goal

Every fix attempt from the LLM passes a set of deterministic guardrails before it is verified. An attempt that deletes code, weakens or skips tests, stubs out logic, adds suppressions, edits generated code, or disables tests in the build is rejected.

**Why:** the easiest way for an LLM to make a build green is to delete code (concept section 7). Without guardrails, a green build says nothing. M4 is the condition for any AI output reaching a real PR (features.md strategy). It also closes rule 5 (never edit generated code), which M3 left open (M3 Q16).

## What to build

### 1. `LanguageSupport` and generic rules

- `LanguageSupport` is part of the `EcosystemAdapter` (concept section 5). For a file's content, it reports language-neutral facts: methods (with their containing type), test methods, assertions inside each test method, skip markers, suppressions, stubs, and parse errors, each with its line range.
- The JVM adapter implements it with **`tree-sitter-ng`** and query files for **Java and Kotlin**.
- The rules live in `core`, as a plain list of small `Guardrail` implementations. They only see facts, file contents, diffs, and diagnostics. They contain no tree-sitter calls and no Java or Kotlin specifics.
- The tree-sitter dependency lives in `ecosystems:jvm`, not in `core`.

**Why:** the rules are generic and the language knowledge comes from the adapter, so a new language needs a grammar and queries, not new rules (concept section 7, rule 1). `tree-sitter-ng` ships prebuilt grammars for Java and Kotlin on Linux and macOS, so there is no native build step (Q1). Its latest releases were in March 2026; the Kotlin grammar artifact is from November 2024. Kotlin is in from the start because Kotlin projects exist (Q8). Keeping tree-sitter out of `core` means `core` gets no native dependency, and the parser sits with its only user until a second ecosystem needs it (M9).

### 2. When guardrails run

- Per attempt, after the edits are applied to the working tree and before the downgrade check and verify (M3 section 6).
- "Before" is the file at the last commit. "After" is the file in the working tree.
- Only LLM edits are checked. The recipe's output is not.
- A violation rejects the attempt: the working tree is reset (M3), the attempt counts against the budgets, and the violation (rule, file, line, reason) goes into the retry request and the report.

**Why:** a bad edit never reaches the build, which saves verify time (Q2). Rejecting is rule 4. Telling the model what it did wrong isn't a repair, and it stops it from repeating the same cheat (Q9). Recipes are deterministic, reviewed upstream, and end up in their own commit (Q10).

### 3. The rules

| Rule | Rejects | Applies to |
|---|---|---|
| **Scope** | An edit to a file outside the adapter's `editableScope()`, a Git-ignored file, or a file outside the repository. JVM scope: everything except `target/`, `build/`, `**/generated*/**` | All files |
| **Diff size** | More than 50 changed lines (added + removed) in one attempt. Configurable | All files |
| **Symbol comparison** | A method that existed before and is gone after (per containing type and name, overloads counted), unless a current diagnostic caused by the update points into it **and** its logic survives: its body was empty, or a method the attempt added has the same body (whitespace ignored) | Java, Kotlin |
| **Test protection: deletion** | A deleted test method. No exception | Java, Kotlin |
| **Test protection: skips** | A new skip marker (e.g. `@Disabled`, `@Ignore`, an assumption that always skips) | Java, Kotlin |
| **Test protection: assertions** | Fewer assertions in a test method than before, or a changed existing assertion that no current compile diagnostic points into. New assertions are allowed | Java, Kotlin |
| **Test count** | Fewer test methods after the attempt than before (static count) | Java, Kotlin |
| **Stubbing** | A new `return null`, empty method body, or `TODO` placeholder (comment or Kotlin `TODO()`) in an added or changed line | Java, Kotlin |
| **Suppression** | A new suppression (`@SuppressWarnings`, `@Suppress`, `//noinspection`, …) in an added or changed line | Java, Kotlin |
| **Build-file blocklist** | Test-disabling configuration in an added or changed line of a build file. Maven: `skipTests`, `skipITs`, `maven.test.skip`, `testFailureIgnore`, new `<excludes>`/`<exclude>` in surefire or failsafe | Build files |
| **Parse errors** | A changed Java or Kotlin file with more parse errors after the attempt than before | Java, Kotlin |

- The runtime test count of the final verify is **reported**, not enforced.
- The blocklist, the scope globs, and the queries live in the adapter. The rules are generic.

**Why:**
- Scope closes rule 5. The Git check also catches generators the globs don't know about (Q11).
- 50 lines is enough for a real fix and small enough to stop rewrites (Q6).
- Deleting a method is sometimes required: an empty hook that overrode a removed interface method, or a method that is renamed or moved. A diagnostic pointing into it shows it has to change; it doesn't show its logic may go. Deleting `configure(HttpSecurity)` in a Spring Security 6 upgrade compiles and drops every access rule. So the logic must survive, checked by the body, the same way as an assertion (Q3, revised in the review, F5). A rename or move that also changes the body is rejected and left to a human for now; M6 measures how often. Tests are never deleted, because a test that no longer compiles is fixed, not removed.
- Changing an expected value is how a model hides a behaviour change. Compile fixes inside assertion lines (e.g. `getStatusCodeValue()` removed in Spring 6) stay possible (Q12).
- The static count works even when the build is red from the start and no test ran (Q4).
- Stubs and suppressions are only checked in lines the attempt touched. Existing ones in the codebase aren't our business (Q5).
- Disabling tests in the build file is the most obvious cheat once build-file edits are allowed (Q7).
- Facts from a file that doesn't parse can't be trusted. If the tool can't check an attempt, it doesn't accept it, the same as M2's downgrade rules.

### 4. Report

- The fix-loop section of the report (M3) shows every guardrail rejection with rule, file, line, and reason.
- A count of rejections per rule.
- The runtime test count from the final verify.

**Why:** reviewers see what the model tried. The rejection rate per rule is a benchmark metric (concept section 11, M6).

### 5. Removing the PR's own package

- M3's downgrade check also rejects an attempt after which the PR's own package no longer resolves. Removing any other dependency stays allowed (Q7).

**Why:** dropping the updated package is the same cheat as reverting the bump, and Q7's concern, legitimate removals of other dependencies, doesn't apply to it ([M3 review](../../done/m3-fix-loop/review.md) D2).

### 6. Tests and fixtures

- **One test per rule that rejects a matching bad edit**, in Java and, for the language-specific rules, in Kotlin.
- **One test per rule that lets a legitimate edit pass**, e.g. a compile fix inside an assertion that a diagnostic points into, renaming a method a diagnostic points into, a 50-line edit.
- **Query tests** for Java and Kotlin on small source fixtures under `src/test/resources/fixtures/`: methods, test methods, assertions, skips, suppressions, stubs.
- **Fix-loop tests** with a fake `LlmClient`: a violating attempt is reset, counted, and its reason appears in the next request.

**Why:** the M4 "done when" is a test per rule that rejects a bad edit. The passing cases make sure the rules don't block the fixes M3 needs.

## What not to do

| Don't | Why |
|---|---|
| Use `jtreesitter`, `ktreesitter`, or build grammars ourselves | Decided against (Q1). |
| Put tree-sitter or Java/Kotlin knowledge into `core` | Rule 1. The rules work on facts from `LanguageSupport`. |
| Check only the final diff | Decided against (Q2). |
| Enforce the runtime test count, or build the base branch for it | Decided against (Q4). Reported only. |
| Scan whole files for stubs or suppressions | Decided against (Q5): only added or changed lines. |
| Forbid removing dependencies or plugins | Decided against (Q7): blocks legitimate fixes. |
| Reject silently | Decided against (Q9). |
| Run guardrails on the recipe output | Decided against (Q10). |
| Allow changes to existing assertions without a compile diagnostic in them | Decided against (Q12). |
| Repair a violating edit | Rule 4. |
| Call an LLM in a guardrail | Rule 2. |
| Make guardrails a public extension point (ServiceLoader, config-defined rules) | Concept section 2.1: an internal list. Only the diff-size limit is configurable. |
| Add grammars for other languages (TypeScript, XML, YAML, …) | M9 and later. Build files get the blocklist, not a grammar. |
| Change the downgrade rule | It's M3's (Q15) and stays as it is. |

## Acceptance criteria

The review agent checks every criterion and reports it as *met*, *not met*, or *can't verify*, with evidence.

| ID | Criterion | Covers | How to verify |
|---|---|---|---|
| AC1 | `./gradlew build` passes | all | Run it from the repo root: exit code `0` |
| AC2 | `LanguageSupport` is part of the `EcosystemAdapter` port and reports methods, test methods, assertions, skip markers, suppressions, stubs, and parse errors with line ranges | 1 | Interface definition in `core` |
| AC3 | `core` has no tree-sitter dependency and no Java- or Kotlin-specific code | 1, Don't | `core/build.gradle.kts` and the version catalog; case-insensitive search of `core/src/main` for `treesitter`, `tree-sitter`, `kotlin.test`, `junit`, `@Test`: no matches |
| AC4 | `ecosystems:jvm` uses `tree-sitter-ng` with the Java and Kotlin grammars | 1 | Version catalog and module build file |
| AC5 | Query files for Java and Kotlin exist in `ecosystems:jvm` | 1 | Resources of the module |
| AC6 | Guardrails are a list of separate rule implementations in `core` | 1 | Source of `core` |
| AC7 | Guardrails run before verify; a violating attempt never triggers a verify | 2 | Fix-loop unit test: the fake adapter records no verify call |
| AC8 | A violating attempt is reset, counts against the budgets, and its rule, file, line, and reason are in the next request | 2 | Fix-loop unit test with a fake `LlmClient` |
| AC9 | Recipe output is not checked by guardrails | 2, Don't | Unit or integration test: a recipe result with a deleted method is kept |
| AC10 | Scope: edits to `target/…`, a `generated` directory, a Git-ignored file, and a path outside the repository are each rejected | 3 | One test each |
| AC11 | Diff size: 51 changed lines are rejected, 50 pass, and the limit is configurable | 3 | Tests and CLI option |
| AC12 | Symbol comparison: deleting a method is rejected, also when a current diagnostic points into it and its body is lost; deleting an empty method, or renaming a method with its body kept, passes if a current diagnostic points into it; a diagnostic in another file that names the method doesn't count | 3 | Tests in Java and Kotlin |
| AC13 | A deleted test method is rejected, even if a diagnostic points into it | 3 | Tests in Java and Kotlin |
| AC14 | A new `@Disabled` / `@Ignore` is rejected | 3 | Tests in Java and Kotlin |
| AC15 | Removing an assertion from a test method is rejected | 3 | Tests in Java and Kotlin |
| AC16 | Changing an expected value in an existing assertion without a compile diagnostic in it is rejected; the same change with a compile diagnostic pointing into it passes | 3 | Tests in Java and Kotlin |
| AC17 | Adding an assertion passes | 3 | Test |
| AC18 | A drop in the static test count is rejected | 3 | Test |
| AC19 | A new `return null`, an emptied method body, and a new `TODO` placeholder are each rejected | 3 | Tests in Java and Kotlin |
| AC20 | An existing `return null` in an unchanged line doesn't cause a rejection | 3 | Test |
| AC21 | A new `@SuppressWarnings` (Java) and `@Suppress` (Kotlin) are rejected | 3 | Tests |
| AC22 | Adding `skipTests`, `skipITs`, `maven.test.skip`, `testFailureIgnore`, or a surefire `<exclude>` to `pom.xml` is rejected | 3 | One test each |
| AC23 | The Maven blocklist lives in `ecosystems:jvm`, not in `core` | 3 | Search `core/src/main` for `skipTests`: no match |
| AC24 | An attempt that adds a parse error to a Java or Kotlin file is rejected | 3 | Test |
| AC25 | The report lists every guardrail rejection with rule, file, line, and reason, plus a count per rule | 4 | CLI test with a fake `LlmClient` |
| AC26 | The report shows the runtime test count of the final verify, and a lower count doesn't change the exit code | 3, 4 | CLI test |
| AC27 | Every rule has at least one test that rejects a matching bad edit | 6 | Test sources: one rejecting test per rule in the table |
| AC28 | Query tests cover methods, test methods, assertions, skips, suppressions, and stubs for Java and Kotlin | 6 | Test sources |
| AC29 | No guardrail calls the `LlmClient` | Don't | Guardrail sources: no `LlmClient` reference |
| AC30 | No grammar other than Java and Kotlin is added | Don't | Version catalog |
| AC31 | An attempt that removes the PR's own package is rejected as a downgrade; removing another dependency is not | 5 | Fix-loop unit tests |

## Docs to update

- `docs/features.md`: set M4 to ✅ when done. Mention Kotlin next to Java in the M4 bullets.
- `docs/concept.md` section 7: the test-count row uses the static count (runtime count reported only); add the method-deletion exception, the "tests are never deleted" rule, the assertion rule, the parse-error rule, the build-file blocklist, and the scope details (globs + Git-ignored + inside the repository).
- `docs/concept.md` section 7 diagram: the facts come from `LanguageSupport`; `tree-sitter` lives in the adapter.
