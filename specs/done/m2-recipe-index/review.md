# Review – M2 Recipe index and OpenRewrite run

Scope: commits `724faeb..448bd7b` against [`spec.md`](spec.md).
Method: read every source, test, and build file in the diff; ran `./gradlew clean build --no-build-cache` and `./gradlew :cli:integrationTest --rerun-tasks`; ran the installed `lector` start script on bad input; inspected the generated index, report, and distribution; probed detekt and Kover with temporary violations (all reverted).

Outcome per finding: **Fixed** · **Not changed** (with reason) · **Needs decision** (with options).

Severity: **High** = breaks a requirement or the external contract · **Medium** = real gap, should fix before the next milestone · **Low** = cleanup / nit.

## Summary

- 44 of 44 acceptance criteria are met. AC12 is met with a caveat (F3).
- No High findings. The one Medium gap is that the rewrite plugin version is tied to the recipe BOM only by a comment (F1): a lone Dependabot bump can break every recipe run without any gate noticing.
- The rest is cleanup: Maven's effective-POM parser prints parse errors to stderr (F2), pattern-qualified X-ranges are indexed without their pattern (F3), and two ktlint-wrapped expressions read badly (F5).
- After the fixes: `./gradlew clean build --no-build-cache --rerun-tasks` passes with 196 unit tests (core 46, recipes 33, jvm 63, cli 54) and 12 end-to-end tests, 100 % Kover coverage; `:cli:integrationTest` passes (2 tests).

## Acceptance criteria

| ID | Result | Evidence |
|---|---|---|
| AC1 | Met | `./gradlew clean build --no-build-cache` exits 0: 194 unit tests (core 46, recipes 32, jvm 62, cli 54), 12 end-to-end |
| AC2 | Met | `core/.../DependencyChange.kt:10` |
| AC3 | Met | `grep -rniE 'maven\|groupid\|artifactid\|pom' core/src/main`: no match |
| AC4 | Met | `core/build.gradle.kts` has no `project(` |
| AC5 | Met | `core/.../Recipe.kt:34`, `RecipeSelection(chosen, alternatives)` |
| AC6 | Met | `core/.../EcosystemAdapter.kt:10-16` |
| AC7 | Met | `settings.gradle.kts`; `recipes/build.gradle.kts:22` (only `:core`); `cli/build.gradle.kts:8` |
| AC8 | Met | `git ls-files` lists only the hand-written `test-recipe-index.json`; after `clean build` `recipe-index.json` is in `recipes/build/generated/recipe-index/` and in the `recipes` JAR of the distribution |
| AC9 | Met | `gradle/libs.versions.toml`: `rewrite-recipe-bom`, `rewrite-maven-plugin` |
| AC10 | Met | `ls cli/build/install/lector/lib \| grep rewrite`: nothing |
| AC11 | Met | `GeneratedIndexTest`; the generated index has `UpgradeSpringBoot_3_4`, `3.4.x`, depth 1, `rewrite-spring:6.37.0` |
| AC12 | Met, with caveats | `IndexEntry`, `IndexFormatTest`; a leaf's `versionPattern` is dropped (F3) |
| AC13 | Met | `IndexFormatTest`: `ecosystem, package, selector, recipe, depth, artifact, license` |
| AC14 | Met | `IndexBuilder.VERSION_LEAVES`; `IndexBuilderTest` (`ChangeDependency` gives no entry) |
| AC15 | Met | `IndexBuilderTest`, report: `LatestRelease 27` |
| AC16 | Met | `recipe-index-report.md`: 314 recipes, 5325 entries, 92 skipped by reason, `rewrite-spring:6.37.0` as MSAL |
| AC17 | Met | `BundledRecipeIndexTest` "a recipe that goes further doesn't match" |
| AC18 | Met | `BundledRecipeIndexTest`: one test each for selector, depth, name |
| AC19 | Met | `RecipeReportsTest` (text and JSON) |
| AC20 | Met | `BundledRecipeIndexTest` "other ecosystems don't match", `RecipeStepTest` |
| AC21 | Met | `recipes/src/main` has no comparison of its own (only `versions.compareSelectors`), no `maven-artifact` |
| AC22–AC25 | Met | `RecipeStepTest` with a fake adapter, one test per rule plus three unreadable-versions tests |
| AC26 | Met | `MigrateIntegrationTest:72`, passed |
| AC27 | Met | `MavenVersionSchemeTest` |
| AC28 | Met | `EffectivePomTest` on captured `help:effective-pom` output |
| AC29, AC30 | Met | `MavenAdapterTest` |
| AC31, AC34, AC35 | Met | `MigrateIntegrationTest`, passed in 16 s |
| AC32 | Met | `MigrateCommandTest`, `LectorEndToEndTest` |
| AC33 | Met | `RecipeStepTest` `assertClean()` after failed and discarded runs |
| AC36, AC37 | Met | `MigrateIntegrationTest`, `MigrateCommandTest`, `LectorEndToEndTest` parse stdout as JSON |
| AC38 | Met | `MigrateCommandTest`: no match, failed run, outdated PR |
| AC39 | Met | Start script: missing options, extra argument, `--format xml`, missing directory, no `pom.xml`, missing settings file, no Git: all exit 2 with empty stdout and the error on stderr |
| AC40 | Met | `VerifyCommandTest`, `VerifyIntegrationTest` green |
| AC41 | Met | `./gradlew :cli:integrationTest --rerun-tasks` passes; `setConnectTimeout` → `connectTimeout` |
| AC42 | Met | `./gradlew build --dry-run` has no `integrationTest` |
| AC43 | Met | Ports, modules, catalog |
| AC44 | Met | `LectorEndToEndTest` "help lists exactly the verify and migrate commands" |

Gate probes: `System.out.printf` in `recipes` main fails `:recipes:detektMain` (`ForbiddenMethodCall`); an untested branch in the `generator` source set fails `:recipes:koverVerify`. `build --dry-run` runs `generateRecipeIndex`, `detektGenerator`, and `endToEndTest`.

## Findings

### KISS / SOLID / DRY

**F1 · Medium · The rewrite plugin's version is tied to the recipe BOM only by a comment.** `gradle/libs.versions.toml:13` says "The plugin version must be the one the BOM names", but nothing checks it. Today they match: `rewrite-maven-plugin` 6.46.1 builds on `rewrite.version` 8.89.0, the BOM's `rewrite-core` is 8.89.0. Dependabot bumps the two in separate PRs. After one of them, the plugin runs recipes compiled against another OpenRewrite core; at best every run fails and is reported as "run failed", at worst it fails in ways the index can't explain. The only test that would notice is the opt-in integration test. Expected: a check in `./gradlew build` that compares the plugin POM's `rewrite.version` with the `rewrite-core` version the BOM resolves to, and fails with a message that names both.

Outcome: **Fixed.** `:recipes:checkRewritePluginVersion`, part of `check`, compares the plugin POM's `rewrite.version` with the BOM's `rewrite-core` and names both on a mismatch. Probed: plugin 6.40.0 fails with "built on OpenRewrite 8.83.0, but rewrite-recipe-bom names 8.89.0"; 6.46.1 passes.

**F2 · Low · `EffectivePom` prints parse errors to stderr, and builds its XML parser apart from `TestReports` (DRY).** `EffectivePom.kt:68-77` sets no error handler, so JAXP writes `[Fatal Error] :1:1: …` to stderr before the `ToolError` (checked with the same factory settings in a scratch program). `TestReports.kt:110-116` already solves this with a `DefaultHandler`, and both build a DOCTYPE-rejecting factory on their own. Expected: one hardened builder in the `jvm` module, used by both.

Outcome: **Fixed.** New `xmlDocumentBuilder()` (DOCTYPE rejected, secure processing, silent error handler) used by `EffectivePom` and `TestReports`; a new `EffectivePomTest` asserts stderr stays empty on malformed output (it failed before with `[Fatal Error] …`).

### Index

**F3 · Low · A pattern-qualified X-range is indexed without its pattern.** `skipReason` skips `ExactVersionWithPattern`, but an X-range with a `versionPattern` passes as plain `XRange`, and the entry drops the pattern. In BOM 3.37.0 this is one leaf: `org.apache.wicket:*` → `10.x` with `versionPattern: -SNAPSHOT`. It matches a change to `10.1.0`, though the leaf would only move to snapshots. Expected: any leaf with a `versionPattern` is skipped and counted, like the exact-version case.

Outcome: **Fixed.** `skipReason` skips an X-range with a `versionPattern` as `XRange with versionPattern`, tested first. The real count was **8** leaves, not 1 (the grep in the review covered only six recipe JARs); the report now lists them.

**F4 · Low · Resolved versions keep the highest version per package across modules, so a downgrade in one module can hide.** `EffectivePom.kt:31`, tested on purpose (`EffectivePomTest` "resolves to the highest"). If module A has `x` 2.0 and module B 1.0, and the recipe moves B to 0.9, rule 3 sees 2.0 → 2.0. The port is `package → version` by spec (section 1), so per-module versions would change `core`. Recipes that set versions back are rare, and a module set below another module's version is rarer.

Outcome: **Not changed.** Within the spec's `package → version` port; fixing it means per-module versions in `core`. Revisit with M7 (Gradle) or when a multi-module fixture shows a hidden downgrade.

### Robustness

**F6 · Low · A runner that fails with anything other than a `ToolError` leaves the recipe's changes in the tree.** `RecipeStep.kt:103-108` catches only `ToolError`. An `IOException` (e.g. from `toRealPath`) or a failing `git status` after the run ends with exit 2 and a dirty tree. The exit code is correct, and in CI the checkout is thrown away.

Outcome: **Not changed.** Exit 2 already says the tool failed, and CI discards the checkout. Revisit in M5, when the run commits the recipe result and a dirty tree would end up in a commit.

### Leftovers

**F5 · Low · Two expressions are wrapped by ktlint into hard-to-read shapes.** `IndexBuilder.kt:95-111` (`if (reason !=\n null)` after a separate `if (reason != null)`), `RecipeReport.kt:24-28` (`${updateType(\n change,\n)}` inside a string). Expected: restructure so the formatter has nothing to wrap: one guard clause in `IndexBuilder`, a local value in `RecipeReport`.

Outcome: **Fixed.** One guard clause in `IndexBuilder.entry`, a local `versions` value in `RecipeReport.text`.

## Found while fixing

Nothing new. F3 affected more leaves than the review measured (8, not 1); recorded under F3.

## What's good

- `core` knows no Maven: the index format, `VersionScheme`, and the optional capabilities fit npm or PyPI without a change to `core`.
- The generator is a thin adapter over OpenRewrite; the logic (`IndexBuilder`, `IndexReport`) lives in `main` and is fully unit-tested. OpenRewrite never reaches the distribution.
- The recipe module list is read from the BOM itself, so a new recipe module is indexed without a build change.
- `applyRecipe` is a flat chain of guard clauses with one outcome type; every non-applied outcome resets the tree, and tests assert `git status` is empty.
- `help:effective-pom` reads parents, managed versions, and plugins without compiling, as the spec requires.
- The CLI contract is tested at all three levels, and the start script behaves as the tests say.
