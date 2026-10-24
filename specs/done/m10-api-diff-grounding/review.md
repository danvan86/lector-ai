# Review – M10 API diff as grounding

Scope: commits `c31fba9..3e99287` (milestone base `cf183a8`) against [`spec.md`](spec.md).
Method: read every changed source, test, and build file; ran `./gradlew clean build --no-build-cache`; ran the installed
start script on good and bad input; ran `:cli:integrationTest --tests '*ApiChangesIntegrationTest*'` with real Maven;
probed the gates, the ClassFile API's failure modes, the diff on real JavaParser releases, and the cost of reading real
JARs with temporary changes (all reverted, `git status` clean afterwards).

Outcome per finding: **Fixed** · **Not changed** (with reason) · **Needs decision** (with options).

Severity: **High** = breaks a requirement or the external contract · **Medium** = real gap, should fix before the next milestone · **Low** = cleanup / nit.

## Summary

- 36 of 37 acceptance criteria are met. AC28 can only be checked from `progress.md` (needs Ollama), AC30 waits for
  architecture-docs, which the workflow runs next.
- The main design issue: a static field "moves" to any other class with a field of that name, whatever its type, and is
  marked unambiguous (F1). On JavaParser 2.5.1 → 3.25.8, three of four moved members are false.
- The main efficiency issue: every changed artifact's JARs are parsed into full API models, and kept for the run, before
  the check whether the errors refer to them at all (F2).
- No test gap of note: every category, limit, failure path, and the fetch contract have a test; the real-Maven test passes.

## Acceptance criteria

| ID | Result | Evidence |
|---|---|---|
| AC1 | Met | `./gradlew clean build --no-build-cache`: `BUILD SUCCESSFUL`, 222 tasks executed, exit 0 |
| AC2 | Met | `git diff cf183a8..HEAD --stat -- core/src/main`: `EcosystemAdapter.kt`, `FixLoop.kt` only |
| AC2a | Met | `git diff cf183a8..HEAD -- …/LlmClient.kt llm/src/main`: only the heading in `Prompt.kt:73`; `FixRequest` (in `LlmClient.kt`) unchanged |
| AC2b | Met | `FixLoopApiChangesTest` T-D1, T-D2 green; `FixLoop.kt:222-229` |
| AC2c | Met | T-D3 green |
| AC3 | Met | T-A1 asserts no Maven call (`ApiChangesTest`) |
| AC4 | Met | T-B1 (dependency plugin, `-N`, `--settings`, 2 min); `ApiChangesIntegrationTest` with real Maven green on this machine (15.9 s), asserts `git status --porcelain` empty |
| AC5 | Met | `gradle/libs.versions.toml`: `maven-dependency-plugin = "3.11.0"` |
| AC6 | Met | T-C1: no Gradle run during `apiChanges` |
| AC7 | Met | T-C3 green |
| AC8 | Met | T-B1 (two fetches for three groundings of two changes), T-B2 (failed fetch not retried) |
| AC9 | Met | T-B5, T-B6 |
| AC10 | Met | T-B2, T-B3, T-B4, T-C2, `GroundingFailuresTest`; probe: 516 truncations of `String.class` and an unsupported class file version all throw `IllegalArgumentException`, which `ApiChangesGrounding.kt:37` catches |
| AC11 | Met | `ApiModelTest` (generic, varargs, fields, constructor, `$` names, `kotlin.time.DurationKt` from the real stdlib) |
| AC11a | Met | the TOML diff adds only the dependency plugin's version and library |
| AC12 | Met | T-A3, T-A8 |
| AC13 | Met | T-A1 … T-A7, one per category |
| AC14 | Met | T-A7; `ApiDiffTest` "found in two other classes" |
| AC14a | Met | T-A5 |
| AC14b | Met | T-A6 |
| AC15 | Met | T-A9 |
| AC16 | Met | T-A2; `ApiDiffTest` "classes of a package whose old package lives on" |
| AC17 | Met | `ApiDiffTest` "the typed diff holds every entry": 40 entries, text cut at 15 |
| AC18 | Met | T-A1; `JavapGroundingTest` not in the diff and green |
| AC19 | Met | T-A11 (16/16/11/11 → 15/15/10/10, `… and 1 more`) |
| AC20 | Met | T-A11 |
| AC21 | Met | T-A10 |
| AC22 | Met | T-E2 |
| AC23 | Met | T-E1 |
| AC24 | Met | `git diff cf183a8..HEAD --stat -- ecosystems/typescript/src/main`: empty |
| AC25 | Met | `git grep -n ast-grep -- ':!docs' ':!specs'`: no match |
| AC26 | Met | T-A4, T-A7; `ApiDiffTest` asserts no `{` |
| AC27 | Met | `CorpusTest` green; `git ls-remote` of `danvan86/lector-benchmark-corpus`: `case/pdfbox-3` = `ce90005…`, `case/snakeyaml-2` = `f67b97d…`, as in `corpus.json` |
| AC28 | Met (from the record) | `progress.md`: commands, both outcomes (exit 1 → 0), the `no recipe matches` reason, the blocks sent; not re-run (needs Ollama) |
| AC28a | Met (from the record) | `progress.md` quotes the grounding at `cf183a8` for both cases |
| AC29 | Met | `docs/features.md:161` M10, `:173` M11, `:180` Later |
| AC30 | Not yet | `docs/concept.md`, `docs/setup.md`, `docs/features.md`, the `MavenAdapter` KDoc are updated; `docs/architecture/` still says `## API of the new version` and "Nothing is downloaded" (`fix-loop.md:168`, `:179`). Left to architecture-docs, as `progress.md` says |

## Findings

### Design

**F1 · Medium · A static field moves to any class with a field of the same name, whatever its type, and is marked
unambiguous.** `ApiDiff.movedTo` (`ApiDiff.kt:155`) matches kind, name, parameters, and static-ness; a field has no
parameters, so its name alone decides. Probe on the real `javaparser-core` 2.5.1 → 3.25.8 with every class in both
versions diffed: 4 moved members, 3 of them fields whose type differs, all false: `Position.UNKNOWN →
LineSeparator.UNKNOWN`, `Range.UNKNOWN → LineSeparator.UNKNOWN`, `AccessSpecifier.DEFAULT →
GeneratedJavaParserConstants.DEFAULT` (an enum constant to an `int`). The one true move, `Difference.
STANDARD_INDENTATION_SIZE → IndentationConstants.STANDARD_INDENTATION_SIZE`, keeps its type (`int`). `progress.md`
found the same in Gson. Consequence: the model is told a wrong replacement as a fact, and M11 would generate a rewrite
rule from it (`unambiguous = true`). The spec says "the return type is not compared" (R4), for Guava's method
`toStringHelper`; for a field, the type is all its signature has. Expected: a field moves only to a static field of the
same type; methods unchanged.

Decision (user, asked in chat): **same type.** A field moves only to a static field with the same name and type; a
method's return type is still not compared.

Outcome: **Fixed.** `ApiDiff.movedTo` also compares a field's type; `ApiDiffTest` "a static field moves only to a static
field of the same type" (the JavaParser case) was red before (`53a6963`).

### Efficiency

**F2 · Medium · Every changed artifact's JARs are parsed into full API models, and kept for the run, before the check
whether the errors refer to them.** `ApiChangesGrounding.diff` (`ApiChangesGrounding.kt:50`) builds
`model(newJars.values.flatten())` only to take its class names for `refersTo`. The fix loop calls `apiChanges` for every
change on every attempt, and `models` caches each parsed JAR for the run. A PR with hundreds of transitive changes
parses all their JARs on the first attempt, though for most of them nothing is looked up. Probe on 60 real JARs (384 MB)
from the local repository: `ApiModel.read` 2.3 s and about 240 MB of heap kept (66,629 classes, 873,913 members); listing
the class entries of the same JARs takes 22 ms. A first probe on the 200 largest JARs did not finish within the test
JVM's 536 MB heap. AGENTS: per-change work must stay cheap when a PR carries hundreds of changes. Expected: decide
`refersTo` on the class names in the JARs' entries (as `JavapGrounding` indexes them), and parse models only for a change
that passes.

Outcome: **Fixed.** `refersTo` now gets the class names from the JARs' entries (`classNames`, moved out of
`JavapGrounding` and shared); models are parsed only after it. The same probe, one unrelated change over the 60 JARs:
3.0 s and 240 MB kept before, 0.18 s and 1 MB after (`191e51d`). No new test: the result is the same text, only the cost
changes; the gate's behavior stays covered by T-B5 and the selection tests.

### Leftovers

**F3 · Low · ktlint split two conditions over three lines each.** `ApiDiff.kt:105-107` (`if (successor ==\n null\n)`)
and `ApiDiff.kt:198-200` (`if (more >\n 0\n)`): ktlint wrapped over-long expressions at the operator. Hard to read.
Expected: shorter expressions (`successors[name]?.let { … } ?: …`, the `more` line built on its own).

Outcome: **Fixed.** One `?.let … ?:` line and a `count` value (`50ccf03`).

**F4 · Low · `MavenAdapter` has a property and a function both named `oldJars`.** `MavenAdapter.kt:66` passes
`::oldJars` (the function), `:70` declares the map. It compiles, but the reader has to work out which is meant.
Expected: name the map after what it is, the run's cache.

Outcome: **Fixed.** The map is `oldJarsCache` (`50ccf03`).

## Found while fixing

**F5 · Low · Gradle reused a stale `ApiDiff.class` once after a probe was reverted with `git checkout`.** The F1 test
stayed red with the fix in place; the class file was older than the edit, and `compileKotlin` ran only after `touch`.
An environment quirk of file watching, not of the build scripts. Consequence: a green or red run right after a
`git checkout` of sources can be stale.

Outcome: **Not changed.** Nothing in the repository causes it; the final build ran with `--rerun-tasks --no-build-cache`.

## What's good

- The typed diff (`ApiChange`, `ApiChangeKind.unambiguous`) is separate from its rendering; M11 can use it as is.
- The port change is minimal: one capability with a default, `FixLoop` joins and de-duplicates; the TypeScript adapter is
  untouched (`jvmDefault = NO_COMPATIBILITY` avoids an uncallable `DefaultImpls` that coverage would count).
- The fetch is careful: the change's own artifact first, an artifact the old version lacks skips only itself, a timeout
  ends the lookup, failures are cached, and `-Dmaven.repo.local` puts the JAR where grounding reads it.
- Gradle keeps the classpaths of every model apart from `models`, so the base's included builds can't become the head's
  build files (T-C3).
- `ground` no longer throws on a broken JAR or a javap timeout (`JavapGrounding.kt:41-55`); the ClassFile API's failures
  are all `IllegalArgumentException` (probe), which is caught.
- The proof cases were chosen by measuring 44 real release pairs and are pinned in the corpus with their heads pushed.
