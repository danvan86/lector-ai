# Progress – M2 Recipe index and OpenRewrite run

Spec: [`spec.md`](spec.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | `core`: `DependencyChange`, `VersionScheme`, `Recipe`, `RecipeIndex` port, optional adapter capabilities | ✅ |
| 2 | `ecosystems:jvm`: Maven version scheme (`ComparableVersion`, X-ranges, `updateType`) | ✅ |
| 3 | `recipes`: index format, reader, recipe selection on a hand-written index | ✅ |
| 4 | `recipes`: index generator at build time from `rewrite-recipe-bom`, index report, generated-index test | ✅ |
| 5 | `core`: migration flow with downgrade rules, Git working tree (clean check, capture, reset) | ✅ |
| 6 | `ecosystems:jvm`: resolved versions and recipe runner (`runNoFork`) | ✅ |
| 7 | `cli`: `migrate` command, recipe section in text and JSON report, end-to-end tests | ✅ |
| 8 | Tagged integration test: Spring Boot 3.3 → 3.4 fixture | ✅ |
| 9 | Quality gates verified | ✅ |
| 10 | Docs in sync (`features.md`, concept 2.3, 4, 5, 9, `AGENTS.md`) | ✅ |
| 11 | Review and fixes: [`review.md`](review.md) | ✅ |

## Quality gates

Final check from a clean state, each on its own: `ktlintCheck`, `detektMain detektTest :recipes:detektGenerator`, `test`, `:cli:endToEndTest`, `koverVerify`, `:cli:integrationTest`, and `clean build --rerun-tasks --no-build-cache` all exit with 0. 196 unit tests (`core` 46, `recipes` 33, `ecosystems:jvm` 63, `cli` 54) after the review, 12 end-to-end tests, 2 integration tests (about 25 s with a warm Maven repository). Kover: 100 % lines and branches in every module, the `generator` source set of `recipes` included (`MainKt` excluded, as before). `build --warning-mode all` shows no deprecation from our build files.

Each gate was checked by adding a violation to the new code, running `./gradlew build --continue`, and reverting:

| Gate | Probe | Result |
|---|---|---|
| ktlint | extra space in `recipes` main, generator, test, and `recipes/build.gradle.kts` | fails in `ktlintMainSourceSetCheck`, `ktlintGeneratorSourceSetCheck`, `ktlintTestSourceSetCheck`, `ktlintKotlinScriptCheck` of `:recipes` |
| detekt | `println` in the generator and in `MigrateCommand`, `System.out.println` in `recipes` main | fails in `:recipes:detektGenerator`, `:recipes:detektMain`, `:cli:detektMain` with `ForbiddenMethodCall` |
| Kover | untested function with a branch in `recipes` main, and in the generator | fails in `:recipes:koverVerify` |
| Tests | inverted assertion in `BundledRecipeIndexTest` | fails in `:recipes:test` |
| End-to-end | the same `println` in `MigrateCommand` | also fails `:cli:endToEndTest`: stdout must stay clean |
| Integration test | `migrate` with `--from 3.4.1 --to 3.4.1` | fails: the recipe is not applied |

`./gradlew build --dry-run` contains `:recipes:generateRecipeIndex` and `:recipes:detektGenerator` once each, and no `:cli:integrationTest`.

## Acceptance criteria

| ID | Result | Evidence |
|---|---|---|
| AC1 | Met | `./gradlew clean build --rerun-tasks --no-build-cache` exits with 0 |
| AC2 | Met | `DependencyChange.kt` |
| AC3 | Met | `grep -rniE 'maven\|groupid\|artifactid\|pom' core/src/main` finds nothing |
| AC4 | Met | `core/build.gradle.kts` has no `project(` |
| AC5 | Met | `Recipe.kt`: `RecipeIndex.find` returns `RecipeSelection(chosen, alternatives)` |
| AC6 | Met | `EcosystemAdapter.kt`: `versionScheme`, `recipeRunner?`, `resolvedVersions?` |
| AC7 | Met | `settings.gradle.kts`; `recipes` depends on `:core` only; `cli` on `:recipes` |
| AC8 | Met | `git ls-files` has no generated index (only the hand-written test fixture); after `clean build` it is in `recipes/build/generated/recipe-index/` and in `recipes-0.1.0.jar` |
| AC9 | Met | `gradle/libs.versions.toml`: `rewrite-recipe-bom`, `rewrite-maven-plugin` (and `maven-help-plugin`) |
| AC10 | Met | `cli/build/install/lector/lib/` has no `rewrite-*` JAR |
| AC11 | Met | `GeneratedIndexTest` |
| AC12 | Met | `IndexEntry`, `IndexFormatTest`, `GeneratedIndexTest` |
| AC13 | Met | `IndexFormatTest`: fields `ecosystem, package, selector, recipe, depth, artifact, license` |
| AC14 | Met | `IndexBuilder` (`VERSION_LEAVES`); `IndexBuilderTest`: `ChangeDependency` and Gradle's `UpgradePluginVersion` produce no entry |
| AC15 | Met | `IndexBuilderTest` (skipped and counted), `GeneratedIndexTest` (`LatestRelease` in the report) |
| AC16 | Met | `IndexReportTest`, `GeneratedIndexTest`; the report lists `rewrite-spring` as MSAL |
| AC17 | Met | `BundledRecipeIndexTest`: a recipe that goes further doesn't match |
| AC18 | Met | `BundledRecipeIndexTest`: one test each for tightest selector, shallowest leaf, name |
| AC19 | Met | `RecipeReportsTest` (text and JSON) |
| AC20 | Met | `BundledRecipeIndexTest` (other ecosystems), `RecipeStepTest` (no match is an outcome, not an error) |
| AC21 | Met | `recipes` has no version comparison and no `maven-artifact`; selection uses `VersionScheme` |
| AC22–AC25 | Met | `RecipeStepTest` with a fake adapter |
| AC26 | Met | `MigrateIntegrationTest`: one worktree after the run |
| AC27 | Met | `MavenVersionSchemeTest` |
| AC28 | Met | `EffectivePomTest` on captured `help:effective-pom` output |
| AC29, AC30 | Met | `MavenAdapterTest` |
| AC31 | Met | `MigrateIntegrationTest` |
| AC32 | Met | `MigrateCommandTest`, `LectorEndToEndTest` |
| AC33 | Met | `RecipeStepTest`, `MigrateCommandTest` |
| AC34, AC35 | Met | `MigrateIntegrationTest` |
| AC36 | Met | `MigrateIntegrationTest`, `MigrateCommandTest` (recipe section before the verification), `LectorEndToEndTest` |
| AC37 | Met | `MigrateCommandTest`, `LectorEndToEndTest` parse stdout as JSON |
| AC38 | Met | `MigrateCommandTest`: exit follows verify with no match, a failed run, and an outdated PR |
| AC39 | Met | `MigrateCommandTest`, `LectorEndToEndTest` |
| AC40 | Met | `VerifyCommandTest`, `VerifyIntegrationTest` unchanged and green |
| AC41 | Met | `./gradlew :cli:integrationTest` passes; the type-based rename `setConnectTimeout` → `connectTimeout` happens on the PR head, so Q8 stays closed |
| AC42 | Met | `cli/build.gradle.kts`; `build --dry-run` |
| AC43 | Met | Ports, modules, and catalog have none of these |
| AC44 | Met | `LectorEndToEndTest`: `--help` lists exactly `verify` and `migrate` |

## Deviations and judgment calls

- **BOM 3.37.0, not 3.38.0.** Maven Central lists 3.38.0 as the latest, but the artifacts it names (e.g. `rewrite-spring` 6.38.0, `rewrite-maven-plugin` 6.47.0) aren't downloadable yet. 3.37.0 and plugin 6.46.1 are the newest consistent pair; Dependabot will offer the bump.
- **Selector classification in the generator** uses OpenRewrite's own `Semver` (build time only), so `recipes` has no selector logic of its own. At run time every version question goes to the adapter's `VersionScheme`.
- **One OpenRewrite environment per JAR** to know which artifact a recipe comes from: `RecipeDescriptor.source` is deprecated without a replacement. Coordinates come from Gradle, not the manifest (`rewrite-java`'s manifest says `8.89.0-SNAPSHOT`); the license comes from the manifest's `License-Name`.
- **The pure index logic is in `recipes` main** (`IndexBuilder`, `IndexReport`), so it is unit-tested and covered. The `generator` source set is a thin adapter over OpenRewrite. Its tests run with OpenRewrite on the test classpath, and Kover measures it too.
- **Resolved versions from `help:effective-pom`** (maven-help-plugin, pinned in the catalog): parents, dependencies with managed versions, and build plugins of every module, without compiling. `dependencyManagement` and `pluginManagement` are left out (versions nothing may use), and so are the build's own modules. Transitive dependencies are not included.
- **Order of the flow:** rule 1, rule 2 (base versions), find the recipe, versions before, run, rule 3, as the spec lists it. Rule 2 therefore reads the base versions even when no recipe matches.
- **`ReturnCount`** in detekt now ignores guard clauses (one-line reason in `config/detekt/detekt.yml`). The recipe step is a chain of checks that each may stop it.
- **`RepositoryCommand`** holds what `verify` and `migrate` share (options, detection, exit codes); `JsonReport.render` became `JsonReport.json`. `verify`'s output is unchanged.
- **`ToolError.message`** is non-null now, which removed `orEmpty()` calls.

## Open

- CI on GitHub hasn't run yet for this branch. `generateRecipeIndex` needs network access to Maven Central during the build (it resolves the BOM's recipe modules), like any other dependency.
- `docs/architecture/` is written after the review (architecture-docs), as the workflow says.
