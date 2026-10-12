# M4 – Guardrails · Brainstorming

Milestone: [M4 in features.md](../../../docs/features.md#m4--guardrails-) · Status: **Closed** (all questions decided)

**How to answer:** the recommended option is preselected with `[x]`. Move the `x` to choose another one. Write anything else under **Notes**.

## Already fixed by the project docs and earlier milestones

- Guardrails are deterministic, built on tree-sitter, and never call the LLM.
- Rules are generic and live in `core`. Language knowledge comes from `LanguageSupport` (grammar + query files) in the adapter.
- Guardrails reject, they don't repair. A violation counts as a failed attempt.
- Rules: symbol comparison, test protection, test count, stubbing, suppression, diff size, scope.
- From M3: attempts are applied in the working tree and reset with Git on rejection; a rejected attempt's reason goes into the retry; downgrades by the LLM are already rejected (M3 Q15); rule 5 (generated code) is not enforced yet and must be closed here (M3 Q16).
- From M1: javac and kotlinc diagnostics are parsed; `verify` never skips or filters tests.

```
Edit[] from LLM
   │
   ▼
apply to a copy ──▶ before/after of each changed file
   │
   ▼
for each Guardrail rule ──▶ Violation? ──yes──▶ reject attempt
   │ none                                         (reason goes into the retry prompt?)
   ▼
apply for real ──▶ verify
```

---

## Round 1

### Q1 · tree-sitter Java binding

- [x] **A · `tree-sitter-ng` (io.github.bonede)** — *recommended.* JNI-based, ships prebuilt grammars (Java, Kotlin, TypeScript, …) for Linux and macOS on Maven Central. No native build step. Check its release activity before committing.
- [ ] **B · `jtreesitter` (official, by the tree-sitter org)** — Best long-term support, uses the FFM API (JDK 22+). Grammars must be compiled into native libraries per platform, which adds a build step for the image and for tests.
- [ ] **C · Kotlin bindings (`ktreesitter`)** — Kotlin-first, also official, same grammar build question as B, smaller user base.

**Notes:** A. Checked on 2026-10-09: `tree-sitter-ng` core 0.26.6 and `tree-sitter-java` 0.23.5 released March 2026; `tree-sitter-kotlin` 0.3.8.1 is from November 2024 (older, but available).

### Q2 · When guardrails run

- [x] **A · Per attempt, before verify (static rules); test-count rule after verify** — *recommended.* A bad edit never reaches the build, which saves verify time. The runtime test count needs a test run, so it is checked afterwards.
- [ ] **B · Only once, on the final diff** — Simpler, but a bad edit can poison later attempts before it is caught.
- [ ] **C · Both per attempt and on the final diff** — Extra safety net against rules that only show up in combination.

**Notes:** A. Refined by Q4: the static test count is checked per attempt too; only the runtime count is after verify (report only).

### Q3 · When deleting a method is allowed

Major upgrades sometimes require it: e.g. a method that overrode a removed interface method.

- [x] **A · Allowed only if the method's name appears in a current `Diagnostic` (e.g. "method does not override or implement")** — *recommended.* Covers the common legitimate case, stays deterministic.
- [ ] **B · Never in M4** — Strictest. Measure the rejection rate in M6, relax later.
- [ ] **C · Allowed if the grounding shows the API was removed** — More precise, but needs reliable "removed API" data that we don't have yet.

**Notes:** A

### Q4 · Test-count baseline

On a broken PR, tests don't run at all, so there is no runtime count to compare against.

- [x] **A · Static count: the number of test methods found via tree-sitter must not drop. The runtime count from the final verify is reported, not enforced** — *recommended.* Works even when the build is red from the start.
- [ ] **B · Runtime count from a build of the base branch** — Accurate, but costs a full extra build per run.
- [ ] **C · Runtime count from the base branch's last CI run (test report artifact)** — No extra build, but depends on the user's CI publishing test reports.

**Notes:** A

### Q5 · Stub and suppression detection scope

- [x] **A · Only in lines the attempt added or changed** — *recommended.* Existing `return null` or `@SuppressWarnings` in the codebase are not our business.
- [ ] **B · Whole changed file, compared before/after counts** — Catches moved code too, but more false positives.

**Notes:** A

### Q6 · Diff size limit

- [x] **A · 50 changed lines per attempt, configurable** — *recommended.* Enough for a real fix in a cluster, small enough to stop rewrites.
- [ ] **B · 20 lines** — Very safe, rejects legitimate multi-site fixes.
- [ ] **C · 100 lines** — Permissive, makes the rule nearly ineffective.

**Notes:** A

### Q7 · Build-file edits

Build files are editable (concept 6.2). The easiest way to make a build green is to disable tests there.

- [x] **A · Scope and size rules, plus a blocklist of test-disabling config** — *recommended.* For Maven: `skipTests`, `maven.test.skip`, `testFailureIgnore`, new `<excludes>` in surefire/failsafe. The blocklist lives in the adapter.
- [ ] **B · Scope and size only** — Simpler, leaves the most obvious cheat open.
- [ ] **C · Also forbid removing any dependency or plugin** — Strictest, but blocks legitimate fixes like dropping a dependency that was merged into another artifact.

**Notes:** A

### Q8 · Languages covered in M4

- [ ] **A · Java only** — *recommended.* Matches the M1–M3 fixtures. Kotlin queries are added when a Kotlin fixture exists.
- [x] **B · Java and Kotlin** — The JVM adapter is complete right away. Two sets of queries and tests.

**Notes:** B. I already have Kotlin projects.

### Q9 · Does the LLM learn why an edit was rejected?

- [x] **A · Yes: the violation reason goes into the retry prompt** — *recommended.* Rule 4 is about not repairing edits ourselves. Telling the model what it did wrong is not a repair, and it raises the hit rate.
- [ ] **B · No: silent rejection** — Strict separation, but the model will often repeat the same cheat.

**Notes:** A

### Q10 · Do guardrails apply to the recipe output?

- [x] **A · No, only to LLM edits** — *recommended.* Recipes are deterministic and reviewed upstream. Their output is a separate commit the reviewer sees anyway.
- [ ] **B · Yes, but report-only (warnings, no rejection)** — Extra transparency, useful to spot unusual recipe behaviour.

**Notes:** A

---

## Round 2

Two points the Round 1 questions don't cover: rule 5, which M3 deferred to M4, and what "weakened assertion" means in a checkable way.

### Q11 · What the scope rule (`editableScope`) covers

M3 Q16 deferred rule 5 (never edit generated code) to M4.

- [x] **A · The adapter's `editableScope()` globs (JVM: everything except `target/`, `build/`, `**/generated*/**`), plus: no Git-ignored files, nothing outside the repository** — *recommended.* Covers generated code wherever it lands. The Git check also catches generators the globs don't know about. The globs live in the adapter (rule 1).
- [ ] **B · Only the adapter's globs** — Explicit, but misses generated files in unusual places.
- [ ] **C · Only Git-ignored files** — Language-neutral, but generated code that is committed (e.g. OpenAPI clients checked into `src/`) passes.

**Notes:** A (answered in chat)

### Q12 · When an assertion may be changed

The concept forbids "removed or weakened assertions". Changing an expected value is how a model hides a behaviour change. But compile fixes often touch assertion lines, e.g. `assertEquals(200, response.getStatusCodeValue())` → `response.getStatusCode().value()` in Spring 6.

- [x] **A · The number of assertions per test method must not drop. An existing assertion may only be changed if a current compile diagnostic points into it. New assertions may be added** — *recommended.* Compile fixes in assertions stay possible. A failing test can't be made green by changing what it expects. It has to be fixed in main code, configuration, or test setup. Same pattern as Q3.
- [ ] **B · Count only: the number of assertions per test method must not drop** — Simpler, but `assertEquals(5, x)` → `assertEquals(6, x)` passes.
- [ ] **C · Existing assertions may never change** — Strictest, blocks the common compile fix above.

**Notes:** A (answered in chat)

---

## Decisions

### Round 1

| # | Topic | Decision |
|---|---|---|
| Q1 | tree-sitter binding | `tree-sitter-ng` (prebuilt grammars for Java and Kotlin) |
| Q2 | When guardrails run | Per attempt, before verify. Static test count per attempt (Q4); runtime count after the final verify, report only |
| Q3 | Deleting a method | ~~Allowed only if the method's name appears in a current `Diagnostic`~~ Revised in the review (F5): allowed only if a current `Diagnostic` points into the method and its logic survives (empty body, or the same body in a method the attempt added). A name in any message let unrelated methods go, javac's "does not override" names no method, and a diagnostic shows a method must change, not that its logic may go |
| Q4 | Test-count baseline | Static count of test methods via tree-sitter must not drop; runtime count reported, not enforced |
| Q5 | Stub/suppression scope | Only lines the attempt added or changed |
| Q6 | Diff size | 50 changed lines per attempt, configurable |
| Q7 | Build-file edits | Scope and size rules plus an adapter blocklist of test-disabling config |
| Q8 | Languages | Java and Kotlin (Kotlin projects exist) |
| Q9 | Rejection feedback | The violation reason goes into the retry prompt |
| Q10 | Recipe output | Guardrails apply only to LLM edits |

### Round 2

| # | Topic | Decision |
|---|---|---|
| Q11 | Scope rule | Adapter's `editableScope()` globs (JVM: not `target/`, `build/`, `**/generated*/**`), plus no Git-ignored files and nothing outside the repository |
| Q12 | Assertions | Assertion count per test method must not drop; an existing assertion may only change if a current compile diagnostic points into it; new assertions allowed |
