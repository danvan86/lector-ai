# M10 – API diff as grounding · Spec

Milestone: M10 in [features.md](../../../docs/features.md) · Decisions: [brainstorming.md](brainstorming.md), including the additions from the spec critique and the user's answers to R1, R3/R4, R9, R13 · Status: **Ready to implement**

Milestone base for every diff below: `cf183a8` (`origin/main` when the milestone started).

## Goal

For a JVM dependency update, the model gets a deterministic list of what changed between the old and the new version's public API, before the full API of the new version.

**Why:** the model often doesn't know a new version's API ([concept 6.1](../../../docs/concept.md#61-grounding-is-the-key-factor)), and the full API shows what exists, not what moved or disappeared. On the first real run (tassilo-rest, kotlin-logging), qwen2.5-coder:7b failed until the grounding named the class's new place. M11 turns the unambiguous entries of the same diff into generated rewrite rules, so the diff is built as a typed value first and rendered second.

## What to build

### 1. Old JARs, only when needed, once per run

- Old JARs are looked up only for a change whose new-version grounding selected at least one class, and only if both `fromVersion` and `toVersion` are set and differ (R6, R12). An unrelated change still starts no process.
- The lookup is cached for the run by (group, `fromVersion`), failures included: changes of one group share it, and a failed lookup is not retried on the next attempt (Q6, R6).
- **Maven** (independent of a base commit, R5): the artifacts are the ones the grounding finds for `toVersion`, at `fromVersion`. Each one missing from the local repository is fetched with one `maven-dependency-plugin` call per artifact, non-recursive (`-N`), into the local repository (`dependency:get` without transitive dependencies) or into a temporary directory outside the repository, never into the user's tree (R7). The call uses the run's settings file and `MAVEN_*` variables, and has a timeout of 2 minutes. The plugin version lives in `gradle/libs.versions.toml` (Q2).
- **Gradle** (R5): the old JARs are the group's JARs at `fromVersion` on the compile classpath of any model read in the run, the base commit's included. That classpath is kept in a store of its own, never added to the models `isBuildFile` reads, so the base's included builds don't leak into the head's build files. Gradle gets no diff when no model in the run has those JARs: `heal` without `--base`, a run without an upgrade, the benchmark's fix-loop-only mode (Q5).
- No old JARs for any reason (no base for Gradle, a failed or timed-out fetch, an unreadable model or JAR): no diff (`apiChanges` is empty, `ground` as today). Never a `ToolError` (user decision, AGENTS).

**Why:** the old JARs are usually not on a CI runner. Fetching through the project's own build tool keeps rule 7 and the user's mirror; fetching only what the grounding needs keeps a PR with hundreds of transitive changes cheap (AGENTS: per-change work).

### 2. API model

- The public API of the old and the new JARs is read from their class files with the JDK's ClassFile API (`java.lang.classfile`, part of the JDK lector runs on; no new dependency, R3), into classes (fully qualified name) with their public members. `javap` stays only for the prompt's full API. Defined (R10):
  - **Member kind:** method, constructor, or field.
  - **Signature:** the erased parameter types in order (varargs as an array), plus static or not. Generics, modifiers other than `static`, `default`, and `throws` are not part of it. The return type is kept for display, not for matching.
  - Members whose name contains `$` are left out (Q9). Nested classes (`$` in the class name) are left out, as in today's grounding; their changes are not shown.
  - Only declared members count. A member pulled up into a supertype shows as removed; the diff doesn't follow inheritance.
- Kotlin top-level functions (`…Kt` classes) are ordinary classes.
- Tests use class files of real releases or built in the test, not invented output (AGENTS).

**Why:** a diff needs exact, comparable structure. Erased signatures make "the same signature" decidable, and reading class files in-process lets the diff look at every class of the group, which "moved member" needs (R3), without a javap run over thousands of classes.

### 3. API diff

Computed without an LLM (rule 2), on the artifacts present in both versions only (R8). The **diffed classes** are the classes the grounding selects, resolved against both the old and the new class index, so a class that is gone is selected too (R2): imports and qualified names in the messages, plus classes named like the missing symbol, but not the up-to-300 candidates of a whole package.

Categories, decided in this order; the first that applies wins (R11):

| Category | When |
|---|---|
| **Renamed package** | At least two diffed classes of one package are gone, all of them moved to the same other package, and the old package has no class left in the new JARs: one line for the package |
| **Moved class** | A class is gone, and exactly one class with the same simple name is new in another package |
| **Removed class** | A class is gone and neither of the above applies |
| **Changed signature** (Q8) | A member name of a class exists in both versions, and at least one of its old signatures is gone: one line, old overloads → new overloads |
| **Moved member** (Q3, R3, R4) | A static member is gone from its class, and exactly one other class of the group's new JARs (all of them, not only the diffed classes) has a static member with the same name and erased parameter types. The return type is not compared (Guava's `toStringHelper` returns a different nested type in `MoreObjects`) |
| **Rename candidate** (Q7) | In the same class, exactly one member is gone and exactly one is new, of the same kind and signature |
| **Removed member** | A member is gone and none of the above applies |

Anything ambiguous (two candidates for a move or a rename) is reported as removed, never guessed.

The result is a typed value: per entry the category, the old and new fully qualified names, the member, its erased parameters, and static-ness. It holds every entry; limits apply only when it is rendered (R15). Moved class, renamed package, and moved member are marked unambiguous; rename candidates are not.

**Why:** these are the mechanical changes a model otherwise has to infer. The strict rules and the typed value keep the diff trustworthy for the model and usable by M11.

### 4. Diff in the prompt

- A non-empty diff is rendered per change as a block titled `API changes of {package} from {fromVersion} to {toVersion}` (the package names which update a block belongs to when a PR has several), one sub-block per category under its own heading (`Moved classes:`, …), one `- ` line per fully qualified entry (Q10), sorted by fully qualified name, overloads by their rendered signature (R14). The exact text is fixed by the approved tests ([tests.md](tests.md)).
- The fix loop puts the diffs of all changes first, then the full APIs (today's `javap -public` output), each distinct text only once (user decision: keep both; R13: diffs first across all changes, duplicates of one group once).
- Limits (user decision): renamed packages, moved classes, and removed classes together 15 lines; moved and removed members together 15; changed signatures 10; rename candidates 10. A shared limit fills in category order; beyond it, `… and N more`.
- An empty diff renders nothing (R12).
- The prompt's section heading `API of the new version` becomes `API` (R18).
- Prompt trimming cuts the grounding from its end, so every full API is cut before any diff.

**Why:** the diff is small and precise; the full API stays because the model needs the replacement's signatures. Putting all diffs first gives them the trimming priority, also when a PR has several changes (R13).

### 5. Placement

- The API model, the diff, and the old-JAR lookup live in `ecosystems:jvm`, next to `JavapGrounding` (Q11).
- `EcosystemAdapter` gets a second grounding capability, `apiChanges(repo, change, diagnostics)`, that returns the rendered API changes of one change, with a default of none (empty); `ground` keeps its signature and returns the full API as today. An adapter without a diff (TypeScript) doesn't change (R13).
- `FixLoop` assembles the request's grounding from both: all distinct API changes, then all distinct full APIs. `FixRequest`, `LlmClient`, and `Prompt`'s trimming stay as they are (R13).
- The port's KDoc says that grounding may fetch the old version's artifacts through the project's build tool (R9).
- A failure inside grounding (a javap run that fails or times out, a JAR that can't be read) gives grounding without the diff, never an exception out of `ground` (R8).
- The TypeScript adapter is unchanged (Q1).

**Why:** rule 1 holds: `core` learns only that grounding has two parts, nothing about the JVM. A default keeps the other adapters untouched. The diff types stay in the adapter (extract on the second real use); how M11 gets the typed diff out before the loop is M11's question.

### 6. Proof on real cases

- At least two cases, chosen during implementation by this criterion (R1): `heal` with `qwen2.5-coder:7b` is red at `cf183a8`, no recipe in the bundled index matches, and the fix is not visible in today's prompt (the replacement's class or signature isn't in the `javap -public` output sent). One case is a moved class or member, one a changed signature. The criterion is checked before a case is committed. Versions and the JDK are pinned in the case.
- The cases go into the benchmark corpus as synthetic bumps; their heads live in `danvan86/lector-benchmark-corpus`.
- The proof runs `lector heal` with `qwen2.5-coder:7b`: red at `cf183a8`, green with M10, recorded in `progress.md` (Q4). `heal` has no switch to skip the recipe, so each case must have no matching recipe in the bundled index; `progress.md` shows the `No recipe for …` line (R16). A benchmark run on GitHub is optional.

**Why:** the milestone exists to make the fix loop succeed more often; unit tests can't show that.

### 7. Roadmap

- `docs/features.md` gets M10 (this milestone) and M11 (generated rules with ast-grep, building on M10's typed diff, and needing a way to get the diff before the fix loop) before "Later"; the "smarter grounding" bullet there no longer claims what M10 delivers (Q12, R15).

**Why:** the user asked to implement both next; M11's dependency on M10 must be visible.

## What not to do

| Don't | Why |
|---|---|
| Change `FixRequest`, `LlmClient`, `Prompt`'s trimming, or the TypeScript adapter | R13: the port gets one capability with a default, the fix loop assembles; nothing else moves |
| Parse `javap` text for the diff, or run `javap` over a whole group | R3: the ClassFile API reads class files in-process |
| Treat an instance member, or a member with other parameter types, as a moved member | R4: unsafe for M11 (`build()`, `close()`) |
| Fail the run, exit `2`, or throw out of `ground`, when old JARs or the diff can't be built | User decision: no diff, the run continues as today |
| Download transitive dependencies, resolve the whole base, or write into the user's repository for Maven | Q2, R7 |
| Look up old JARs for a change whose grounding selected nothing | R6: a PR can carry hundreds of changes |
| Add a detached configuration or a second Gradle invocation for old JARs | Q5: the models already resolve them |
| Fetch old JARs per attempt, or retry a failed lookup | Q6, R6 |
| Guess renames across classes or by name similarity | Q7 |
| Pair overloads | Q8 |
| Use an LLM for any part of the diff | Rule 2 |
| Drop the full API from the prompt | User decision: keep both |
| Diff classes the errors don't refer to, or a whole package's candidates | User decision; R2. Only the moved-member search looks at every class of the group (R3) |
| Change TypeScript grounding | Q1: a later milestone |
| Generate or run rewrite rules, or add ast-grep | M11 |
| Diff a package against a different package (a group ID change) | Out of scope; the grounding finds moved classes by name since #21 |
| JSON or another structured format in the prompt | Q10 |

## Acceptance criteria

The review agent checks every criterion and reports it as *met*, *not met*, or *can't verify*, with evidence.

| ID | Criterion | Covers | How to verify |
|---|---|---|---|
| AC1 | `./gradlew build` passes | all | Exit code `0` |
| AC2 | In `core`, only `EcosystemAdapter` (the new capability with a default, and its KDoc) and `FixLoop` (assembling the grounding) changed | 5, Don't | `git diff cf183a8..HEAD --stat -- core/src/main` lists only `EcosystemAdapter.kt` and `FixLoop.kt` |
| AC2a | `FixRequest`, `LlmClient`, and `Prompt`'s trimming are unchanged | 5, Don't | `git diff cf183a8..HEAD` shows no change to `FixRequest`, `LlmClient.kt`, or `Prompt.trimmed` |
| AC2b | The request's grounding has all distinct API changes before all distinct full APIs | 4, 5 | `FixLoopTest` with a fake adapter and two changes, two of them returning identical texts: the order, and each identical text once |
| AC2c | An adapter that doesn't implement the new capability gets the same grounding as today | 5 | `FixLoopTest` with a fake adapter without it |
| AC3 | Maven: old JARs present in the local repository are used without a Maven call | 1 | Adapter test with a fake process runner: no process started |
| AC4 | Maven: a missing old artifact is fetched with one `maven-dependency-plugin` call per artifact, with `-N`, the run's settings file, and a 2-minute timeout, and writes nothing inside the repository | 1, Don't | Command-line test with a fake process runner; after an integration test with real Maven (`:cli:integrationTest`) on a fixture, `git status` of the fixture is clean |
| AC5 | The `maven-dependency-plugin` version is declared in `gradle/libs.versions.toml` | 1 | File content |
| AC6 | Gradle: the old JARs come from a model the run already read; `ground` starts no Gradle process | 1, Don't | Adapter test: zero process runner calls from `ground` |
| AC7 | Gradle: the base model's included builds don't change `isBuildFile` for the head | 1 | Adapter test with an included build only in the base |
| AC8 | Old JARs are looked up at most once per (group, `fromVersion`) and run, a failed lookup included | 1 | Test with two attempts and two changes of one group: one lookup; the same with a failing lookup |
| AC9 | A change whose grounding selected no class, or with an empty or equal `fromVersion`/`toVersion`, starts no lookup | 1, Don't | Tests with a fake process runner: no process |
| AC10 | No old JARs, a failed fetch, a timeout, and an unreadable JAR each give empty API changes and no exception | 1, 5, Don't | One test per case |
| AC11 | The API model, read with the ClassFile API, yields methods, constructors, and fields with erased parameter types and static-ness, including a Kotlin `…Kt` class and a generic and a varargs signature, and drops names with `$` | 2 | Model test on class files of a real release or built in the test |
| AC11a | No new library dependency is added for reading class files | 2, Don't | `git diff cf183a8..HEAD -- gradle/libs.versions.toml` adds only `maven-dependency-plugin` |
| AC12 | A class that is gone is among the diffed classes | 3 | Test: an import of a class that only the old JARs have |
| AC13 | Each of the seven categories is detected, in the stated order, on JARs built in the test | 3 | One test per category |
| AC14 | Two move or rename candidates for the same item give "removed", not a guess | 3, Don't | Test with an ambiguous pair |
| AC14a | A static member moved to a class the grounding didn't select, with a different return type, is a moved member | 3 | Test with JARs built in the test |
| AC14b | An instance member that appears in another class is not a moved member | 3, Don't | Test |
| AC15 | A changed class the errors don't refer to is absent from the diff | 3, Don't | Test |
| AC16 | A renamed package is one line; a single moved class is not a renamed package | 3, 4 | Two tests |
| AC17 | Every entry is in the typed diff, also beyond the rendering limits | 3 | Test with more entries than the limit: the value has all of them |
| AC18 | `apiChanges` of a change with a non-empty diff starts with `API changes of {package} from {from} to {to}`; `ground` returns the full API as before | 4, 5 | Adapter test; the existing `JavapGroundingTest` stays green unchanged |
| AC19 | Each of the four limits cuts with `… and N more`, at a count its default can't produce | 4 | One test per limit, with entries from distinct packages so nothing collapses into a renamed package |
| AC20 | Lines are sorted by fully qualified name | 4 | Test with unsorted input JARs |
| AC21 | An empty diff gives empty API changes | 4 | Test |
| AC22 | When the prompt is too long, the full API is cut before the diff | 4 | `PromptTest` |
| AC23 | The prompt section is titled `API` | 4 | `PromptTest` |
| AC24 | The TypeScript adapter is unchanged | 5, Don't | `git diff cf183a8..HEAD -- ecosystems/typescript/src/main` is empty |
| AC25 | No ast-grep binary, rule generation, or rule runner exists | Don't | Search for `ast-grep` outside `docs/` and `specs/`: no matches |
| AC26 | The prompt has no JSON for the diff, no overload pairs, and no cross-class rename candidates | Don't | Rendering tests on an overloaded and a cross-class case |
| AC27 | The proof cases are in `corpus.json`, `CorpusTest` accepts the new count, and their head commits exist in `danvan86/lector-benchmark-corpus` | 6 | `CorpusTest`; `git fetch` of the head commits from that repository (needs network) |
| AC28 | `heal` with `qwen2.5-coder:7b` is red at `cf183a8` and green with M10 for each proof case, with no recipe matching | 6 | `progress.md` records the commands, the diff block sent, the `No recipe for …` line, and both outcomes. Can't be re-run without Ollama |
| AC28a | For each proof case, the replacement's class or signature is absent from the prompt sent at `cf183a8` | 6 | `progress.md` quotes the grounding sent at `cf183a8` and names the missing replacement |
| AC29 | `docs/features.md` lists M10 and M11 before "Later" | 7 | File content |
| AC30 | Every doc under "Docs to update" is updated | Docs | Each file names the old-JAR lookup or the diff as stated below |

## Docs to update

- `docs/concept.md` 6.1: the grounding lists the API diff between the old and the new version; the adapter interface in section 5 (`ground(...)`) and the principle "Grounded, not guessing" mention the API changes.
- `docs/architecture/fix-loop.md`: the ports summary (the new grounding capability), the request assembly (diffs first, distinct), the `ground` row (old JARs, diff, limits, order; no longer "Nothing is downloaded"), the files table, and the tests table.
- `docs/architecture/gradle-adapter.md`: the compile classpaths of every model read in a run are kept for grounding, apart from the models `isBuildFile` reads.
- `docs/architecture/recipe-migration.md`: the pinned Maven plugins include `maven-dependency-plugin`.
- `docs/architecture/benchmark.md`: the case count and the new cases.
- `docs/architecture/README.md`: the fix-loop row mentions the API diff.
- `docs/features.md`: M10 done, M11 next.
- `MavenAdapter` KDoc: old JARs may be fetched for grounding.
