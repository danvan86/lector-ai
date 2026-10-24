# M10 – API diff as grounding · Brainstorming

Milestone: M10 in [features.md](../../../docs/features.md) (added with this milestone) · Status: **Closed**

**How to answer:** the agent asks these questions in chat; this file has the full options and records the answers. The recommended option is preselected with `[x]`.

## Overview

Today the model gets the public API of the **new** version of the classes the errors refer to (`javap -public`, [concept 6.1](../../../docs/concept.md#61-grounding-is-the-key-factor)). It doesn't get what **changed**. The first real run on a private repository (tassilo-rest, kotlin-logging 3 → 8) showed what that costs: the 7B model guessed an import twice until the grounding named the class in its new package. M10 adds a deterministic diff between the old and the new version's API ("class moved", "member removed", "signature changed", "rename candidate") as its own prompt section, before the full API. No LLM computes it (rule 2). M11 then turns the unambiguous parts of the same diff into generated ast-grep rules that fix mechanical errors without the model, so the diff model must be reusable.

**Key concepts**

- **Grounding** — the API facts the model gets with each fix request; for the JVM, `javap -public` of the new version's JARs (`JavapGrounding`).
- **Old JARs** — the JARs of the change's group at `fromVersion`. Usually not on the runner: lector builds the base commit only to rerun failing tests, and the version check reads POMs, not JARs.
- **API model** — classes (fully qualified name) and their public members (name, parameter types, return type), parsed from `javap -public`.
- **API diff** — old model vs. new model, for the classes the errors refer to: moved class, renamed package, removed member, changed signature, rename candidate.
- **Rename candidate** — a member that is gone, with exactly one new member in the same class and an identical signature: probably renamed, shown as a candidate, not as a fact.

```
 lector pr / heal
   │
   ▼
 [FixLoop] ── request ──▶ [EcosystemAdapter.ground(repo, change, diagnostics)]   core unchanged (port returns a string)
                                   │
                                   ▼
                      [JavapGrounding]  (ecosystems:jvm)
                       │                     │
        [new JARs: local repo / Gradle model]   (old JARs)  ◀── Q2 Maven, Q5 Gradle, Q6 once per run
                       │                     │
                       ▼                     ▼
                  [javap -public]       (javap -public)
                       │                     │
                       ▼                     ▼
                    (API model) ◀── Q9 ── (API model)          (new, reusable by M11)
                            \             /
                             ▼           ▼
                            (API diff)  ◀── Q3, Q7, Q8           (new, reusable by M11)
                                 │
                                 ▼
                [Prompt]  "API changes X → Y" (new, first)  +  "API of the new version" [existing]  ◀── Q10
                                 │
                                 ▼
                             [LlmClient]
 Scope: Q1 (JVM only?)   Proof: Q4   Module: Q11   Roadmap: Q12   Benchmark cases: Q13
```

**In scope:** the JVM adapters (Maven, Gradle), the diff in the prompt, the proof on real cases. · **Out of scope:** generated rules and ast-grep (M11); a package replaced by another one (a group ID change, like kotlin-logging 3 → 5: one change removes a package, another adds one), whose moved classes the grounding already finds by name since #21.

## Already fixed by the project docs

- No LLM computes the diff; it is deterministic (rule 2).
- `core` stays unchanged: `EcosystemAdapter.ground` returns a string, the adapter composes it (rule 1). *Superseded by the user's answer to R13: the port gets a second grounding capability with a default, and `FixLoop` puts all diffs first.*
- Fetching old JARs is a read-only metadata/artifact lookup through the project's own repositories, allowed by rule 7; nothing is downloaded by the tool itself outside the build tool.
- A failure while getting old JARs or running `javap` in grounding is never a `ToolError`: it means no diff (AGENTS: "a timeout in grounding into no grounding").
- Every list for the model names the first few and counts the rest (AGENTS).
- Tool output in tests (javap output, compiler messages) is copied from real runs (AGENTS).

**Decided by the user in chat (before Round 1):**

| Topic | Decision |
|---|---|
| Old JARs can't be fetched | No diff; the existing grounding and fix loop continue unchanged, no tool error |
| Rename candidates | Shown, marked as candidates |
| Diff and full API | Both: the diff first, the full API after it; when space is short, the full API is trimmed before the diff |
| Limits | About 50 diff lines in total: moved classes / renamed packages 15 (a renamed package is one line), removed members 15, changed signatures 10, rename candidates 10, then "… and N more". Only classes the errors refer to |

---

## Round 1

### Q1 · Ecosystems in M10

The diff idea also fits TypeScript (old vs. new `.d.ts`), but its grounding (`DeclarationGrounding`) works differently.

- [x] **A · JVM only (Maven and Gradle)** — *recommended.* The JVM has the failing real case, javap is already there, and M11 starts with Java and Kotlin too. TypeScript gets the same idea as a later milestone. Costs: TypeScript stays at today's grounding for now.
- [ ] **B · JVM and TypeScript** — Both ecosystems profit at once. Costs: a second old-version source (the old package's `.d.ts` from the registry or lockfile), roughly doubles the milestone.

**Notes:**

### Q2 · Old JARs for Maven

The old version's JARs must come from somewhere. Every option uses the project's own Maven settings and mirror.

- [x] **A · Local repository first, else `dependency:copy` of the group's artifacts at `fromVersion` (no transitive dependencies)** — *recommended.* Fast (one small download per run), touches nothing in the project, and the artifacts are known: the same artifact IDs as the new version's JARs. Costs: an artifact only reachable through a repository declared in the project's `pom.xml` (not in the settings) may not be found — I'm not sure whether `dependency:copy` run in the project directory uses those; the implementation must check. If not found: no diff (decided).
- [ ] **B · Resolve the base commit's dependencies in its worktree (`dependency:resolve`)** — Uses exactly the project's repositories, including ones declared in the POM. Costs: resolves every dependency of the base, which can take minutes on a cold cache, for every red PR.
- [ ] **C · Local repository only, no download** — No extra network and no extra time. Costs: on a fresh CI runner the old JARs are almost never there, so the diff would rarely exist.

**Notes:**

### Q3 · A member that moved to another class

The approved categories don't cover a method that moved to another class, e.g. Guava's `Objects.toStringHelper(Object)` → `MoreObjects.toStringHelper(Object)`.

- [x] **A · Add "moved member"**: a removed member with the same name and signature in exactly one other class of the new JARs — *recommended.* Real upgrades do this (Guava, Mockito's `Matchers` → `ArgumentMatchers`), and M11 can turn it into a rule. Counts toward the removed members' 15 lines, so the total stays at about 50. Costs: a search over all new classes per removed member (cheap: the class index is already built).
- [ ] **B · No extra category** — It shows up as "removed member"; the full API may contain the new class if the grounding finds it by the missing symbol. Costs: the model has to connect the two itself, which the 7B model did badly in the real case.

**Notes:**

### Q4 · What proves M10 is done

The milestone's value is a higher success rate of the fix loop, which needs a real model.

- [x] **A · Local real-model runs plus the new cases in the benchmark corpus; a benchmark run on GitHub is optional** — *recommended.* `heal` with qwen2.5-coder:7b on two cases the current grounding doesn't solve (moved member, changed signature), red before and green after, documented in the progress file. The cases go into the benchmark corpus so the next benchmark run measures them. Costs: no measured effect on the whole corpus yet.
- [ ] **B · Also a benchmark run before and after on GitHub** — The effect on all cases, in numbers. Costs: two full runs, about 2,000+ runner minutes each on a private repository, and the benchmark has never run yet (it may need its own fixes first).
- [ ] **C · Unit tests only** — Cheapest. Costs: no evidence that the model fixes more; the reason M10 exists stays unproven.

**Notes:**

### Q5 · Old JARs for Gradle

- [x] **A · The compile classpath of the base commit's model**, read when the base's versions are read (once per run, already done for the recipe rules) — *recommended.* Gradle resolves the files through the project's repositories and credentials; no new resolution mechanism. Costs: the base model must keep the classpath, not only the versions.
- [ ] **B · A detached configuration with `<group>:<artifact>:<fromVersion>` in the init script** — Independent of the base model. Costs: a second Gradle invocation and new init-script code.

**Notes:**

### Q6 · How often the old JARs are fetched

- [x] **A · Once per run per change, kept for every attempt** — *recommended.* Grounding is built per attempt; fetching per attempt repeats downloads. Costs: a small cache per run.
- [ ] **B · Per attempt** — Simplest. Costs: repeated Maven/Gradle calls per attempt.

**Notes:**

### Q7 · What counts as a rename candidate

- [x] **A · Same class; exactly one removed and exactly one added member of the same kind with identical parameter types, return type, and static-ness** — *recommended.* Unambiguous, cheap, and safe for M11. Costs: misses renames that also changed the signature.
- [ ] **B · Also across classes** — More hits. Costs: overlaps with "moved member", and more false candidates.
- [ ] **C · Also by name similarity** (edit distance) — More hits. Costs: heuristics and false positives.

**Notes:**

### Q8 · Changed signatures and overloads

- [x] **A · One line per class and member name: the old overloads → the new overloads**, if the name exists in both versions and its set of signatures changed — *recommended.* No pairing guesses between overloads. Costs: a long line for heavily overloaded names.
- [ ] **B · Pair overloads by parameter count** — Shorter lines. Costs: wrong pairs when counts collide.

**Notes:**

### Q9 · Kotlin and compiler-generated members

`javap -public` also prints members the compiler generated (Kotlin `…$default`, `access$…`, `componentN`, `copy`).

- [x] **A · Leave out members whose name contains `$`; keep everything else** — *recommended.* Removes synthetic noise with one rule. Costs: `componentN`/`copy` of data classes stay (they are real API).
- [ ] **B · No filtering** — Simplest. Costs: noise lines eat the limits.

**Notes:**

### Q10 · Format of the diff section

- [x] **A · Plain text under the title "API changes from {from} to {to}", one block per category, one fully qualified entry per line** (`moved: a.B → c.B`, `removed: a.B.m(int)`, `changed: a.B.m(int) → a.B.m(int, java.lang.String)`, `rename candidate: a.B.m(String) → a.B.n(String)`) — *recommended.* Compact, readable for small models, the same style as the javap section. Costs: none notable.
- [ ] **B · JSON** — Structured. Costs: more tokens, harder for a 7B model to read.

**Notes:**

### Q11 · Where the API model and the diff live

- [x] **A · In `ecosystems:jvm`, as internal types next to `JavapGrounding`; M11's JVM rule generation uses them there** — *recommended.* Nothing else needs them; `core` stays free of JVM terms. Costs: a TypeScript diff later gets its own model.
- [ ] **B · A generic `ApiDiff` in `core`** — Shared shape for all ecosystems. Costs: an abstraction before its second use (AGENTS: extract on the second real use).

**Notes:**

### Q12 · Roadmap entries

- [x] **A · `features.md` gets M10 (API diff as grounding) and M11 (generated rules with ast-grep) before "Later"; M10 replaces part of "smarter grounding" there** — *recommended.* Keeps the milestone list the single roadmap. Costs: none.
- [ ] **B · Only M10 now, M11 when it starts** — Less up front. Costs: the dependency of M11 on M10's diff model isn't visible.

**Notes:** Blind check: picked A, confidence low ("build what the current milestone needs" argues for a short M11 entry only). Settled by the user: they asked to implement both M10 and M11 as the next steps, so both go on the roadmap.

### Q13 · Benchmark cases for the proof

- [x] **A · Two synthetic bumps on small fixture projects, same group ID: Guava 18 → 21 (`Objects.toStringHelper` → `MoreObjects.toStringHelper`, moved member) and Mockito 1 → 2 (`InvocationOnMock.getArgumentAt(int, Class)` → `getArgument(int)`, changed signature)** — *recommended.* Real API changes of real releases, small and fast to build, and the corpus already has six synthetic bumps (M6). The versions are checked against the real JARs before the cases are fixed. OpenRewrite has a Mockito recipe, so the proof uses the fix-loop-only mode. Costs: synthetic, not historical PRs.
- [ ] **B · Historical PRs from open-source repositories** — More realistic. Costs: finding PRs that failed exactly this way takes time.

**Notes:** Blind check: picked A, confidence low. It doubted that Mockito 1 → 2 breaks on `org.mockito.runners`/`Matchers` (deprecated in 2.x, not removed), so option A now uses `getArgumentAt` → `getArgument` instead.

---

## Decided by the agent

Questions the triage found agent-decidable and an independent second agent answered the same way. Veto any of them by ID.

| # | Topic | Decision | Reason |
|---|---|---|---|
| Q5 | Old JARs for Gradle | A · The compile classpath of the base commit's model, kept when the base's versions are read | Already resolved once per run through the project's repositories; `GradleAdapter.resolvedVersions` must keep the model instead of dropping it |
| Q6 | How often | A · Once per run per change | AGENTS: read what is shared once per run; grounding is rebuilt every attempt |
| Q7 | Rename candidate | A · Same class, exactly one removed and one added member, identical signature | Unambiguous, no heuristics, safe for M11 |
| Q8 | Overloads | A · One line per class and name: old overloads → new overloads | No pairing guesses; deterministic |
| Q9 | Synthetic members | A · Leave out names with `$` | One rule; matches `classNames()`, which already skips `$` |
| Q10 | Format | A · Plain text per category, one fully qualified entry per line | Matches the javap section; fewer tokens for a 7B model |
| Q11 | Module | A · Internal types in `ecosystems:jvm` | Rule 1; extract on the second real use |

---

## Decisions

### Round 1

| # | Topic | Decision |
|---|---|---|
| Q1 | Ecosystems | A · JVM only (Maven and Gradle); TypeScript later |
| Q2 | Old JARs for Maven | A · Local repository first, else `dependency:copy` of the group's artifacts at `fromVersion`, without transitive dependencies; not found → no diff |
| Q3 | Moved member | A · New category "moved member", within the removed members' 15 lines |
| Q4 | Proof | A · Local real-model runs (red before, green after) plus the cases in the benchmark corpus; a GitHub benchmark run is optional |
| Q5–Q11 | Agent decisions | As listed under "Decided by the agent"; not vetoed |
| Q12 | Roadmap | A · M10 and M11 in `features.md` before "Later" (the user asked to implement both next) |
| Q13 | Benchmark cases | A · Guava 18 → 21 (moved member) and Mockito 1 → 2 (`getArgumentAt` → `getArgument`, changed signature), versions checked against the real JARs |

---

## Additions from the spec critique

A fresh agent critiqued `spec.md` against the docs and the code (proof cases checked against the real Guava and Mockito JARs).

| # | Finding | Correction in the spec |
|---|---|---|
| R2 | A class that is gone was never selected (the selection resolved only against the new JARs), so "moved class" and "renamed package" could never fire | Diffed classes are resolved against the old and the new class index; not the up-to-300 package candidates |
| R5 | Gradle's base model was underspecified; Maven doesn't need a base | Gradle: the group's JARs at `fromVersion` from any model read in the run, kept apart from `isBuildFile`'s models; the no-diff conditions listed. Maven: independent of the base |
| R6 | Fetching per change would start processes for hundreds of transitive changes | Lookup only for changes whose grounding selected classes; cached by (group, `fromVersion`), failures included |
| R7 | `dependency:copy` writes into the user's tree, runs per module, takes one artifact, has no `transitive` parameter, no timeout | One call per artifact, `-N`, into the local repository or a temporary directory, 2-minute timeout |
| R8 | Partial inputs give false entries; a javap timeout escapes `ground` as exit 2 | Only artifacts present in both versions; any failure means no diff, never an exception |
| R10 | "Member", "kind", "signature" undefined; nested classes and inheritance unstated | Defined in section 2 (erased parameters + static-ness; return type for display only; nested classes and inherited members not followed) |
| R11 | "Affected class of a package" undefined; no "removed class"; no precedence | Renamed package needs at least two classes and an empty old package; "removed class" added; categories decided in a fixed order; "changed signature" needs a gone signature |
| R12 | Empty or equal versions, empty diff | No lookup; no block; grounding byte-equal to today's |
| R14 | Line order and shared limits undefined | Sorted by fully qualified name; shared limits fill in category order |
| R15 | M11 needs every entry, not the rendered lines | The diff is a typed value with all entries; limits apply only to the rendering; unambiguous categories marked; `features.md` notes M11 needs the diff before the loop |
| R16 | Several criteria not binary or not verifiable | Milestone base `cf183a8` named; criteria split and made countable; the proof relies on no recipe matching |
| R17 | Docs to update incomplete; `CorpusTest` caps the case count | Docs list completed; AC27 covers `CorpusTest` |
| R18 | The diff sat under "API of the new version" | The prompt heading becomes `API` |

**Escalated to the user, with their answers:**

| # | Finding | Decision |
|---|---|---|
| R1 | Both proof cases are likely green before M10 (Guava's replacement is already in today's prompt; `getArgumentAt` doesn't exist in Mockito 1.9.5) | A · Choose the cases during implementation: red at `cf183a8` with 7B, no recipe, replacement not in today's prompt; checked before committing; versions and JDK pinned |
| R3 + R4 | A moved member needs every class of the group, and Guava's move changes the return type; instance moves are unsafe | A · Static members only, matched by name and erased parameters, not the return type; class files read in-process with the JDK's ClassFile API (no new dependency); `javap` stays for the prompt's API |
| R9 | The port's KDoc says grounding never downloads | A · KDoc change in `core` allowed |
| R13 | With several changes, the diff-first priority held only per change; same-group diffs repeated | B · `core` changes: the port gets a second grounding capability (the API changes) with a default of none; `FixLoop` puts all distinct diffs before all distinct full APIs. `FixRequest`, `LlmClient`, the TypeScript adapter unchanged |

## Decisions during implementation

| # | Finding | Decision |
|---|---|---|
| R19 | A changed signature's new form is always in today's prompt: Maven's javac names the class fully qualified, and the erroring file's imports are grounded, so their full API is sent. AC28a could only hold for a changed signature if trimming cut that class | The user: keep the SnakeYAML case (red at `cf183a8`, green with M10); for a changed signature, AC28a asks that today's prompt doesn't state the change |
| R20 | The test design's T-B5 (an error about `java.util` in a file that imports a class of the group starts no fetch) conflicts with diffing every import of the erroring files | The agent: the old version is looked up only when the errors themselves refer to the group (the classes the messages name, the imports tied to a missing symbol or package, the classes named like a missing symbol); once it is, every import is diffed, as section 3 says |
| R21 | A major release adds artifacts that have no old version (PDFBox 3 added `pdfbox-io`) | The agent: a fetch Maven can't resolve skips only that artifact; a timeout ends the lookup; the change's own artifact is fetched first |
| R22 | Review F1: a static field moved by name alone, whatever its type (JavaParser 2.5.1 → 3.25.8: `Position.UNKNOWN → LineSeparator.UNKNOWN`, 3 of 4 moves false) | The user: a field moves only to a static field of the same type; a method's return type is still not compared |

