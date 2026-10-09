# M1 – Verify and report (JVM/Maven, no AI) · Brainstorming

Milestone: [M1 in features.md](../../../docs/features.md#m1--verify-and-report-jvmmaven-no-ai-) · Status: **Closed** (all questions decided)

**How to answer:** the recommended option is preselected with `[x]`. Move the `x` to choose another one. Write anything else under **Notes**.

## Already fixed by the concept

- Data model `DependencyChange` and `Diagnostic` (`file, line, column, code, message, severity, tool`).
- `EcosystemAdapter` with `detect` and `verify`. The JVM adapter lives in `ecosystems:jvm`.
- "Green" means tests pass, not only compiles.
- CLI: `verify <repo>` prints a readable report.

```
verify <repo>
   │
   ▼
MavenAdapter.detect(repo) ──no──▶ "no supported ecosystem" (exit 2)
   │ yes
   ▼
run Maven ──▶ console output + surefire XML
   │
   ▼
parsers ──▶ Diagnostic[] ──▶ report (text / JSON)
```

---

## Round 1

### Q1 · How Maven is invoked

- [x] **A · `./mvnw` if the repo has a wrapper, otherwise `mvn` from the image** — *recommended.* Respects the Maven version the project chose. The wrapper may download Maven once (read-only artifact fetch, allowed by rule 7).
- [ ] **B · Always `mvn` from the image** — Fully controlled, no download. Can break projects that need a specific Maven version.
- [ ] **C · In-process via Maven Embedder / Invoker API** — No process boundary, but drags Maven internals into our classpath and breaks on Maven version differences.

**Notes:**

### Q2 · Which Maven goals `verify` runs

Most dependency-update failures are compile errors. Test failures only mean something once the code compiles.

- [x] **A · Two stages: `test-compile`, then `verify` only if compile is clean** — *recommended.* Both with `-B -fae` (batch mode, fail at end, so all modules report). Clean separation of compile and test diagnostics, and the fix loop (M3) can re-run only the cheap stage.
- [ ] **B · One run: `mvn -B -fae verify`** — Simpler, but compile errors in one module hide test results elsewhere, and every fix-loop iteration pays for the full run.
- [ ] **C · Configurable goal list** — Flexible, but every project then needs configuration before the tool is useful.

**Notes:** Tests can fail although the dependencies are correct. So failing tests should not be a criterion for an LLM to repair the code. → Handled in Round 2 (Q14–Q16).

### Q3 · Which compilers are parsed in M1

The JVM adapter covers Java and Kotlin projects. Their error formats differ.

- [ ] **A · javac only** — *recommended.* The Spring Boot fixture for M2 is Java. Kotlin follows when a fixture needs it.
- [x] **B · javac and kotlinc from the start** — Covers Kotlin Maven projects (`kotlin-maven-plugin`) right away. Two parsers, two sets of fixtures.

**Notes:** Use B.

### Q4 · How compile errors are captured

- [x] **A · Parse Maven's console output** — *recommended.* Lines like `[ERROR] /path/Foo.java:[12,5] cannot find symbol`. No changes to the build, works with any compiler plugin config. Tested with captured output files.
- [ ] **B · Make javac emit structured output via CLI properties** — e.g. `-Dmaven.compiler.showWarnings`/compiler args. Still text in the end, and fragile across plugin versions.
- [ ] **C · A Maven extension (EventSpy) that records structured events** — Most structured, but it's a JAR we inject into the build. More moving parts than M1 needs.

**Notes:** A

### Q5 · How test failures become `Diagnostic`s

- [x] **A · Read surefire/failsafe XML reports (`target/*-reports/TEST-*.xml`)** — *recommended.* Stable, structured format. File and line come from the first stack frame inside the test class. The same format is used by Gradle (M7).
- [ ] **B · Parse console output** — No file dependency, but the format varies with surefire version and settings.

**Notes:** A

### Q6 · Failures without a source location

Dependency resolution errors, plugin failures, or "artifact not found" have no file or line, but they are often the root cause.

- [x] **A · `Diagnostic` with nullable `file`, `line`, `column`** — *recommended.* One type for everything. The report groups them as "build errors".
- [ ] **B · Separate `BuildFailure` type** — Clearer typing, but every consumer (report, fix loop, PR comment) has to handle two types.

**Notes:**

### Q7 · Report output format

The report later becomes a PR comment (M5). Nobody reads it on a laptop.

- [x] **A · Text by default, `--format json` for machines** — *recommended.* Text for CI logs, JSON for the later pipeline steps and tests.
- [ ] **B · Text + JSON + SARIF** — SARIF enables GitHub code scanning annotations. Nice, but not needed until M5 at the earliest.
- [ ] **C · Text only** — Least work, but tests and later steps then parse human text.

**Notes:**

### Q8 · Exit codes

- [x] **A · `0` green, `1` diagnostics found, `2` tool error** — *recommended.* The pipeline can tell "the build is broken" from "our tool is broken".
- [ ] **B · `0` always, result only in the report** — Never fails the CI job, but then the job status says nothing.

**Notes:**

### Q9 · JSON library

Needed for `--format json` now, and for Ollama (M3) and the GitHub API (M5) later.

- [x] **A · kotlinx.serialization** — *recommended.* Kotlin-native, compile-time, works naturally with data classes.
- [ ] **B · Jackson** — Very common on the JVM, reflection-based, needs the Kotlin module. Fine, but heavier.

**Notes:** A

### Q10 · JDK used to build the target repo

Target repos need their own JDK version (8, 11, 17, 21, 25…).

- [x] **A · M1: whatever `JAVA_HOME` points to** — *recommended.* Keep M1 simple. Picking the right JDK per repo becomes a question for the container image in M5.
- [ ] **B · Detect the required version now** (`maven.compiler.release`, toolchains) and pick from installed JDKs. Right in the long run, premature without the image.

**Notes:** A

### Q11 · Maven repositories and settings

Builds need dependencies from Maven Central or an internal mirror.

- [x] **A · Use the runner's Maven settings as they are, plus an optional `--maven-settings <file>`** — *recommended.* Regulated environments usually have a mirror configured. We don't invent our own config.
- [ ] **B · Require an explicit mirror configuration** — Stricter, but adds setup for every user.

**Notes:**

### Q12 · Fixtures and tests

- [x] **A · Both: parser unit tests on captured output files, plus one tagged integration test that runs real Maven on a fixture repo** — *recommended.* Parser tests are fast and cover edge cases. The integration test proves the whole path works.
- [ ] **B · Captured output only** — Fast, but never proves Maven is invoked correctly.
- [ ] **C · Real Maven only** — Realistic, but slow and needs network for dependency downloads.

**Notes:** A

### Q13 · Multi-module Maven projects

- [x] **A · Supported from the start** — *recommended.* Most real projects are multi-module. With `-fae` and paths relative to the repo root, the cost is small.
- [ ] **B · Single-module only in M1** — Simpler fixtures, but we'd rework the parsers soon.

**Notes:** A

---

## Round 2

Triggered by the note on Q2: a failing test does not mean the dependency update broke it. It may be a pre-existing bug, a developer issue, or a flaky test. The tool may only treat a failure as its job if the update caused it.

```
head (PR) verify
   │
   ├── compile errors (main or test code) ─────────────▶ caused by update (always)
   │
   └── failing tests ──▶ rerun once ──passes──▶ flaky (reported, ignored)
                              │ fails again
                              ▼
                     same tests on base commit
                        ├── fail on base too ──▶ pre-existing (reported, ignored)
                        └── pass on base ──────▶ caused by update
```

### Q14 · Which test failures count as caused by the update

- [x] **A · Baseline comparison** — *recommended.* A test failure counts only if it fails on the PR head but passes on the base commit. Compile errors in main or test code always count. Accurate, costs a second test run.
- [ ] **B · Compile errors only** — Test failures are reported but never attributed to the update. Simplest, misses real runtime breakages (changed defaults, removed config properties).
- [ ] **C · Stack-trace heuristic** — Counts a failure if the stack trace contains packages of the updated dependency. No extra run, but unreliable.

**Notes:** A (answered in chat)

### Q15 · When the base commit is tested

- [x] **A · Only when the head run has failing tests, and only those tests** — *recommended.* No extra cost in the common case. The same baseline can later give M4 its test-count reference.
- [ ] **B · Always run the full verify on base** — Simplest logic, doubles build time for every PR.
- [ ] **C · Reuse base-branch CI test reports** — No extra build, depends on the user's CI publishing reports.

**Notes:** A (answered in chat)

### Q16 · Flaky tests

- [x] **A · Rerun failed tests once; a test that passes on rerun is reported as flaky and ignored** — *recommended.* Cheap, removes most noise.
- [ ] **B · No reruns** — Simpler, but flaky tests may be attributed to the update.

**Notes:** A (answered in chat)

### Q17 · How `verify` learns the base commit

In M1 there is no PR yet (source adapters come in M5).

- [x] **A · `--base <git-ref>` option. Without it, test failures are reported as "unattributed" and don't count** — *recommended.* Explicit, easy in tests and the benchmark. M5 passes the PR's base SHA automatically.
- [ ] **B · Default to the merge base with the default branch** — Works without the option, but guesses the default branch and fails on shallow CI clones.
- [ ] **C · Require `--base` whenever tests fail** — Strict, but `verify` can't give any answer without it.

**Notes:** A (answered in chat)

### Q18 · Where the base commit is built

- [x] **A · A separate Git worktree of the base commit in a temp directory** — *recommended.* The head working tree stays untouched (important later, when the fix loop has local commits). Clean up afterwards.
- [ ] **B · Check out base in place, then return to head** — No extra directory, but risky with uncommitted changes and build output from the other commit.

**Notes:** A (answered in chat)

### Q19 · How the classification is represented

- [x] **A · `verify` returns a result with separate lists: caused by update, pre-existing, flaky, unattributed** — *recommended.* `Diagnostic` stays a plain description of a problem. The classification is a result of the comparison, not a property of the diagnostic.
- [ ] **B · A `status` field on each `Diagnostic`** — One flat list, but every `Diagnostic` from a parser starts with a meaningless status.

**Notes:** A (answered in chat)

### Q20 · Exit code with pre-existing, flaky, or unattributed failures

Refines Q8.

- [x] **A · Exit `1` only for diagnostics caused by the update. Pre-existing, flaky, and unattributed failures are reported, exit `0`** — *recommended.* The job only fails for problems the tool is responsible for.
- [ ] **B · Exit `1` for any failure, the report explains the classification** — The job mirrors the build state, but a pre-existing failure looks like the update's fault.

**Notes:** A (answered in chat)

---

## Decisions

### Round 1

| # | Topic | Decision |
|---|---|---|
| Q1 | Maven invocation | `./mvnw` if present, otherwise `mvn` from the image |
| Q2 | Verify goals | Two stages: `test-compile`, then `verify` only if compile is clean; both `-B -fae`. Failing tests alone are not the LLM's job (see Q14–Q16) |
| Q3 | Compilers | javac and kotlinc from the start |
| Q4 | Compile errors | Parse Maven's console output |
| Q5 | Test failures | Read surefire/failsafe XML reports |
| Q6 | Failures without location | `Diagnostic` with nullable `file`, `line`, `column` |
| Q7 | Report format | Text by default, `--format json` |
| Q8 | Exit codes | `0` green, `1` diagnostics found, `2` tool error (refined by Q20) |
| Q9 | JSON library | kotlinx.serialization |
| Q10 | JDK for target repos | Whatever `JAVA_HOME` points to (per-repo JDK selection in M5) |
| Q11 | Maven settings | Runner's settings as they are, plus optional `--maven-settings <file>` |
| Q12 | Tests | Parser unit tests on captured output + one tagged integration test with real Maven |
| Q13 | Multi-module | Supported from the start |

### Round 2

| # | Topic | Decision |
|---|---|---|
| Q14 | Attribution | Baseline comparison: a test failure counts only if it passes on the base commit. Compile errors (main and test) always count |
| Q15 | Baseline run | Only when head has failing tests, and only those tests |
| Q16 | Flaky tests | Rerun failed tests once; passing on rerun = flaky, reported and ignored |
| Q17 | Base commit | `--base <git-ref>`; without it, test failures are "unattributed" and don't count |
| Q18 | Base build location | Separate Git worktree in a temp directory, cleaned up afterwards |
| Q19 | Representation | `verify` result with separate lists: caused by update, pre-existing, flaky, unattributed |
| Q20 | Exit code | `1` only for diagnostics caused by the update; everything else is reported with exit `0` |
