# M10 – API diff as grounding · Tests

Spec: [spec.md](spec.md) · Decisions: [brainstorming.md](brainstorming.md) · Status: **Approved, frozen (tests.lock)**

## Test contract

```
1. Test design (this file)    ──▶ questions in chat, answers recorded here
2. Test code                  ──▶ every test runs and fails for the stated reason (red)
3. Approval by the user       ──▶ test files frozen in tests.lock
4. Implementation             ──▶ production code only; tests change only after asking
5. Done when                  ──▶ all tests green + all quality gates green + all AC of the spec met
```

**Tools and levels:** as in AGENTS.md. Adapter tests run the real `javap` and JDK on JARs compiled in the test (`buildJar`), with the adapters' fake process runner for Maven and the fake Gradle (`FakeGradleTest`). Fix-loop tests use the fake adapter and scripted model of `FixLoopFixture`. Error messages are javac's and kotlinc's real wording.

## Coverage

| AC | Covered by | Note |
|---|---|---|
| AC1 | by command: `./gradlew build` | |
| AC2, AC2a | by command: `git diff cf183a8..HEAD --stat -- core/src/main`; `git diff cf183a8..HEAD -- core/src/main/kotlin/gmbh/vanella/lector/core/LlmClient.kt llm/src/main` shows only the heading | |
| AC2b | T-D1, T-D2 | |
| AC2c | T-D3 | |
| AC3 | T-A1 | no Maven call when the old JARs are local |
| AC4 | T-B1 (command line); real Maven: implementation test | the integration test with real Maven needs `mvn` and network |
| AC5, AC11a | by command: `git diff cf183a8..HEAD -- gradle/libs.versions.toml` | |
| AC6 | T-C1 | |
| AC7 | T-C3 | |
| AC8 | T-B1, T-B2 | |
| AC9 | T-B5, T-B6 | |
| AC10 | T-B2, T-B3, T-B4, T-C2 | |
| AC11 | T-A8 (Kotlin import, `$` members); details by implementation tests | constructors, fields, generic and varargs signatures are the model's construction |
| AC12 | T-A3, T-A8 | |
| AC13 | T-A1 (moved class), T-A2 (renamed package), T-A3 (removed class), T-A4 (changed signature), T-A5 (moved member), T-A7 (rename candidate), T-A6 (removed member) | |
| AC14 | T-A7 | |
| AC14a | T-A5 | |
| AC14b | T-A6 | |
| AC15 | T-A9 | |
| AC16 | T-A2 | |
| AC17 | by implementation tests | the typed value is internal to the adapter |
| AC18 | T-A1; the existing `JavapGroundingTest` unchanged | |
| AC19 | T-A11 | |
| AC20 | T-A11 | |
| AC21 | T-A10 | |
| AC22 | T-E2 | |
| AC23 | T-E1 | |
| AC24 | by command: `git diff cf183a8..HEAD -- ecosystems/typescript/src/main` | |
| AC25 | by command: `git grep -n ast-grep -- ':!docs' ':!specs'` | |
| AC26 | T-A4 (no overload pairs), T-A7 (no cross-class candidate); no JSON: by implementation tests | |
| AC27 | by command: `./gradlew :benchmark:test` (`CorpusTest`); `git fetch` of the heads | |
| AC28, AC28a | `progress.md` (manual real-model runs) | can't run without Ollama |
| AC29, AC30 | by command: file content | |

---

## Test catalog

### Group A · What the model learns about an update's API changes

Level: unit, on JARs compiled in the test. `ApiChangesTest`. Catches an implementation that diffs the wrong classes, guesses moves and renames, or prints noise.

| ID | Test | Scenario (given → when → then) | Red because | AC |
|---|---|---|---|---|
| T-A1 | `a class that moved to another package is named with its new place` | 1.0 has `org.example.util.Names`, 2.0 has it in `org.example.text`; the code imports the old place → javac can't find `Names` → the API changes start with `API changes of org.example:lib-core from 1.0 to 2.0` and list `- org.example.util.Names → org.example.text.Names` under `Moved classes:`; no Maven call | `apiChanges` missing; against a stub: empty text | AC3, AC13, AC18 |
| T-A2 | `a package whose classes all moved together is one line, a single moved class is not a renamed package` | Three classes move from `org.example.old` to `org.example.lib` → `- org.example.old → org.example.lib` under `Renamed packages:`, no `Moved classes:` | as T-A1 | AC13, AC16 |
| T-A3 | `a class that is gone without a successor is a removed class` | `Legacy` exists only in 1.0 → `- org.example.lib.Legacy` under `Removed classes:` | as T-A1 | AC12, AC13 |
| T-A4 | `a method whose parameters changed shows its old and new overloads, an added overload alone is no change` | `add(int, int)` became `add(int, int, int)`; `sub(int)` only gained `sub(int, int)` → `- org.example.lib.Calc.add: (int, int) → (int, int, int)`; `sub` not mentioned | as T-A1 | AC13, AC26 |
| T-A5 | `a static method that moved to a class the code doesn't import is a moved member, even with another return type` | `Strings.describe(Object)` (returns `String`) is gone; `org.example.fmt.Describer.describe(Object)` (returns `Object`) is new, in a package the code doesn't import → `- org.example.lib.Strings.describe(java.lang.Object) → org.example.fmt.Describer.describe(java.lang.Object)` under `Moved members:` | as T-A1 | AC13, AC14a |
| T-A6 | `an instance method that exists in another class is removed, not moved` | `Pipe.close()` is gone, another class has an instance `close()` → `- org.example.lib.Pipe.close()` under `Removed members:`, no `Moved members:` | as T-A1 | AC13, AC14b |
| T-A7 | `a method replaced by exactly one new method of the same signature is a rename candidate, two of them are not` | `greetPolitely(String)` → `greet(String)` in `Greeter`; in `Twins`, `a(int)` and `b(int)` became `c(int)` and `d(int)` → one candidate for `Greeter`; `Twins.a(int)` listed as removed, not as a candidate | as T-A1 | AC13, AC14, AC26 |
| T-A8 | `a Kotlin import of a package kotlinc can't resolve is diffed, compiler-generated members are left out` | kotlinc: `Unresolved reference 'mu'.` for `import mu.KotlinLogging`; 2.0 has `io.github.oshai.kotlinlogging.KotlinLogging`; both have `access$000()` → `- mu.KotlinLogging → io.github.oshai.kotlinlogging.KotlinLogging`; no `access$000` | as T-A1 | AC11, AC12 |
| T-A9 | `a changed class the errors don't refer to is left out` | `Unrelated` changed too, but no error names it → only `Greeter` appears | as T-A1 | AC15 |
| T-A10 | `nothing changed in the classes the errors refer to adds nothing` | Both versions of `Greeter` are equal → empty text | against a stub that reports changes: its text | AC21 |
| T-A11 | `each limit lists the first entries in order and counts the rest` | 16 moved classes (each its own package), 16 removed members, 11 changed signatures, 11 rename candidates → 15, 15, 10, 10 lines, each block ending `- … and 1 more`; the first moved class is `org.example.p01.C01` | as T-A1 | AC19, AC20 |

### Group B · Getting the old version's JARs on a CI runner (Maven)

Level: unit, with the adapter's fake process runner that places the JAR like a real fetch. `OldJarsTest`. Catches fetching per change or per attempt, retrying a failure, writing into the user's repository, and failures that end the run.

| ID | Test | Scenario (given → when → then) | Red because | AC |
|---|---|---|---|---|
| T-B1 | `old artifacts are fetched once per group and version, one call each, with the run's settings, never into the repository` | Only 2.0 of `lib-core` and `lib-extra` is local; three groundings for two changes of the group → exactly two fetches (one per artifact at 1.0), each with the dependency plugin, `-N`, the run's settings, 2 minutes timeout; the API changes name the rename; no file added in the repository | as T-A1 | AC4, AC8 |
| T-B2 | `a failed fetch gives no API changes and is not retried on the next attempt` | The fetch fails → empty; a second grounding starts no new fetch | as T-A1 | AC8, AC10 |
| T-B3 | `a fetch that runs into its timeout gives no API changes and doesn't fail the run` | The fetch times out → empty, no exception | against a stub that reports changes: its text | AC10 |
| T-B4 | `an old JAR that can't be read gives no API changes` | A broken old JAR in the local repository → empty, no exception | as T-B3 | AC10 |
| T-B5 | `nothing is fetched for errors the new version's classes have nothing to do with` | The error is about `java.util` → empty, no process | as T-B3 | AC9 |
| T-B6 | `nothing is fetched for a package that was added, removed, or not changed` | `fromVersion` empty, `toVersion` empty, or equal → empty, no process | as T-B3 | AC9 |

### Group C · Gradle: the old version from a model the run already read

Level: unit, with the fake Gradle. `GradleApiChangesTest`. Catches a second Gradle run for grounding and the base's build leaking into the head's build files.

| ID | Test | Scenario (given → when → then) | Red because | AC |
|---|---|---|---|---|
| T-C1 | `the old version's JARs come from the base commit's model, without another Gradle run` | The base's versions were read (1.0 on its classpath), then the head was verified (2.0) → the API changes name the rename; no Gradle run during grounding | as T-A1 | AC6 |
| T-C2 | `without a model that has the old version, there are no API changes` | Only the head was read → empty | as T-B3 | AC10 |
| T-C3 | `the base commit's included builds don't become the head's build files` | The base includes `old-logic`, the head doesn't → a file under `old-logic/` is no build file | regression guard: green before; red against a wrong implementation that keeps the base model with the head's (checked) | AC7 |

### Group D · What the model reads first, across all changes of a PR

Level: unit, `FixLoopApiChangesTest` on `FixLoopFixture`. Catches a diff placed after the full APIs, repeated texts, and stray separators.

| ID | Test | Scenario (given → when → then) | Red because | AC |
|---|---|---|---|---|
| T-D1 | `the API changes of all changes come before their full APIs` | Two changes, each with API changes and a full API → request grounding: both API changes, then both full APIs | `apiChanges` missing; against a stub: the fix loop ignores API changes | AC2b |
| T-D2 | `changes of one group that ground the same are told once` | Two changes return identical texts → each text once | as T-D1 | AC2b |
| T-D3 | `an adapter without API changes grounds the request exactly as before` | The adapter has no API changes → grounding as today | regression guard: green before; red against a fix loop that joins empty API changes (checked) | AC2c |

### Group E · The prompt

Level: unit, `PromptTest`.

| ID | Test | Scenario (given → when → then) | Red because | AC |
|---|---|---|---|---|
| T-E1 | `the request shows the change, the errors, the code, the API, the release notes, the recipe diff, and earlier attempts` (existing test, heading assertion changed) | → the section is titled `## API` | heading is still `API of the new version` | AC23 |
| T-E2 | `an oversized API is cut after the API changes that come first, never through them` | A grounding that starts with API changes and has a huge API → the API changes are intact under `## API`, the rest is trimmed | as T-E1 | AC22 |

### Edge cases considered and left out

| Dimension | Decision |
|---|---|
| Quantity | none (T-A10, T-B5), one (T-A1 …), many and one beyond the limit (T-A11) |
| Boundaries | exactly at a limit is implied by "15 lines, then the rest" (T-A11); not tested separately |
| Validity | empty or equal versions (T-B6), unreadable JAR (T-B4) |
| Repetition | repeated attempts and changes of one group (T-B1, T-B2, T-D2) |
| Order | sorted output (T-A11); input order of classes is the JAR's, not controlled further |
| Time | the fetch timeout (T-B3); the 2-minute value (T-B1) |
| Conflict | two candidates for a rename (T-A7) |
| Failure | failed fetch, timeout, broken JAR, missing model (T-B2–T-B4, T-C2) |
| Isolation | the base build vs. the head build (T-C3); tenants: not applicable |
| Lifecycle | not applicable: the diff exists only during a run |
| Invariants | no property-based test: the inputs are class files, and the categories are exact rules |
| Extension | a new ecosystem keeps the port's default (T-D3) |

### Checked by command or by implementation tests

By command: AC1, AC2, AC2a, AC5, AC11a, AC24, AC25, AC27, AC29, AC30 (see the coverage table). By implementation tests (written test-first while building, not frozen): the API model's details (constructors, fields, generics, varargs, `…Kt` classes), the typed diff holding every entry (AC17), no JSON in the rendering, and the real-Maven integration test of AC4. AC28 and AC28a are recorded in `progress.md`.

---

## Test code

| Group | Files | Red before the implementation, because |
|---|---|---|
| A | `ecosystems/jvm/src/test/kotlin/gmbh/vanella/lector/ecosystems/jvm/ApiChangesTest.kt` | compile: `apiChanges` missing. Against a stub returning empty text: 10 of 11 fail on their assertion; T-A10 fails against a stub that reports changes |
| B | `ecosystems/jvm/src/test/kotlin/gmbh/vanella/lector/ecosystems/jvm/OldJarsTest.kt` | compile: `apiChanges` missing. Against the empty stub: T-B1, T-B2 fail; against the reporting stub: T-B3–T-B6 fail |
| C | `ecosystems/jvm/src/test/kotlin/gmbh/vanella/lector/ecosystems/jvm/GradleApiChangesTest.kt` | compile: `apiChanges` missing. T-C1 fails against both stubs, T-C2 against the reporting stub; T-C3 is a guard, red against a base model kept with the head's |
| D | `core/src/test/kotlin/gmbh/vanella/lector/core/FixLoopApiChangesTest.kt`, `core/src/test/kotlin/gmbh/vanella/lector/core/FixLoopFixture.kt` (the fake adapter tells API changes) | compile: `apiChanges` missing. T-D1, T-D2 fail against the stub; T-D3 is a guard, red against a fix loop that joins empty API changes |
| E | `llm/src/test/kotlin/gmbh/vanella/lector/llm/PromptTest.kt` | T-E1 and T-E2 fail on the heading |

Checked before approval: ktlint and detekt (`detektTest`) on the test code, green; every stub removed, `git diff cf183a8 -- core/src/main ecosystems/jvm/src/main llm/src/main` empty.

---

## Approval

Approved by the user as designed, with the agent decisions TQ1–TQ5 not vetoed. The files under "Test code", with the
helpers they use (`TestJars.kt`, `FakeGradleTest.kt`), are frozen in [tests.lock](tests.lock).

## Decided by the agent

Technical choices the tests fix, following the spec (Q10) and AGENTS.md. Veto any of them by ID.

| # | Topic | Decision |
|---|---|---|
| TQ1 | Port name | `EcosystemAdapter.apiChanges(repo, change, diagnostics): String`, default empty |
| TQ2 | Title | `API changes of {package} from {from} to {to}`: with several changes, the package says which update a block belongs to |
| TQ3 | Layout | One heading per category (`Renamed packages:`, `Moved classes:`, `Removed classes:`, `Changed signatures:`, `Moved members:`, `Rename candidates:`, `Removed members:`), one `- ` line per entry, `- … and N more` at the end of a cut block |
| TQ4 | Entries | `old → new` with fully qualified names; a member as `Class.name(param types)`; a changed signature as `Class.name: (old) → (new)` |
| TQ5 | Prompt heading | `## API`, since it now holds the API changes and the full API |
