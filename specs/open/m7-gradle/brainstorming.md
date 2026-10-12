# M7 – Gradle · Brainstorming

Milestone: [M7 in features.md](../../../docs/features.md#m7--gradle-) · Status: **Closed**

**How to answer:** the recommended option is preselected with `[x]`. Move the `x` to choose another one. Write anything else under **Notes**.

## Already fixed by the project docs

- Recipes run through a Gradle init script. No file of the target repository is edited to run them (concept section 5).
- Fixture repos for Groovy DSL and Kotlin DSL. Done when the M5 flow works the same for a Gradle repo.
- From M1: two verify stages (compile first, then tests), fail-at-end, paths relative to the repo root, JUnit XML for test failures, build errors without a location become `Diagnostic`s, base-commit attribution in a separate worktree, exit codes `0` / `1` / `2`.
- From M2: the ecosystem is the package namespace `maven` for every JVM build. The adapter provides `versionScheme`, `recipeRunner`, and resolved versions. Resolved versions must work when the code doesn't compile. Recipe artifacts are never shipped. The recipe runs on the PR head as it is.
- From M3: grounding is `javap -public` on JARs the build already resolved, no extra downloads. Cluster keys for javac and kotlinc. The adapter says which files are build files.
- From M4: `LanguageSupport` for Java and Kotlin via `tree-sitter-ng`. Scope, blocklist, and globs live in the adapter. A file that can't be checked is not accepted.
- From M5: the tool image has JDKs 17, 21, 25 and no JDK 8 or 11. Renovate manager names map to ecosystems with a lookup table. Verify first, green means stop.
- Gradle output is read with `--console=plain`. Our own init-script tasks run with `--no-configuration-cache`. (No real alternative, so not asked.)

```
lector pr / heal / migrate / verify
          │
          ▼
ecosystems:jvm
  ├── MavenAdapter ──┐
  │                  ├── shared: javac/kotlinc parsers, JUnit XML parser,
  └── GradleAdapter ─┘           javap grounding, cluster keys, LanguageSupport
          │
          ▼
./gradlew  -I <lector init script>
          ├── query task    → JSON: resolved versions, classpath JARs, applied plugins
          ├── rewriteRun    → recipe (OpenRewrite Gradle plugin)
          ├── stage 1       → compile every source set   --continue
          └── stage 2       → check                      --continue
```

---

## Round 1

### Q1 · One adapter or two

- [x] **A · Two adapters (`MavenAdapter`, `GradleAdapter`) in `ecosystems:jvm`, sharing parsers, grounding, cluster keys, and `LanguageSupport`** — *recommended.* Detection, invocation, resolved versions, and the recipe runner are what differ. Each adapter stays small, and the shared code is already there from M1–M4.
- [ ] **B · One `JvmAdapter` with a build-tool strategy inside** — One adapter per language, but a second abstraction inside the adapter that only the JVM has.
- [ ] **C · Separate module `ecosystems:gradle`** — Clean split, but the shared JVM code then needs a third module.

**Notes:** A (all recommended options accepted in chat)

### Q2 · Repos with both `pom.xml` and Gradle files at the root

Some repos are mid-migration and have both. The `cli` asks the adapters in list order, so the first match would win silently.

- [x] **A · Tool error (exit `2`) with a clear message** — *recommended.* No guessing, same as M1 refusing to guess the base commit. Rare enough that a clear error is fine.
- [ ] **B · Maven wins (list order), documented** — Simplest code, but may verify the build the PR didn't touch.
- [ ] **C · Pick the build tool from the Renovate manager name (`maven` vs `gradle`)** — Usually right, but the M2 namespace decision drops the build tool from `DependencyChange`, so this needs a new field or a side channel.

**Notes:** A (all recommended options accepted in chat)

### Q3 · Supported Gradle versions

- [x] **A · Gradle 8.x and 9.x** — *recommended.* Covers current projects and keeps the init script on one API level. Older versions are reported as unsupported (exit `2`).
- [ ] **B · Also Gradle 7.x** — More legacy projects, more init-script compatibility work, and 7.x can't run on JDK 21 or newer, as far as I know.

**Notes:** A (all recommended options accepted in chat)

### Q4 · Which JDK runs Gradle, and toolchains

M5 picks the JDK for Maven from `maven.compiler.release`. Gradle build scripts are code, so the target can't be read from a property. Gradle separates the JDK that runs Gradle (the daemon) from the JDK that compiles (the toolchain). To my knowledge: Gradle 8.0–8.4 runs on JDK ≤ 20, 8.5–9.0 on JDK 21, 9.1+ on JDK 25. Please treat these numbers as unchecked.

- [x] **A · Respect `gradle/gradle-daemon-jvm.properties` if present, otherwise run Gradle on the highest image JDK that the wrapper's Gradle version supports. Toolchains resolve from the image JDKs (`org.gradle.java.installations.paths`), auto-download off. A toolchain request for a JDK not in the image (8, 11, …) is a tool error (exit `2`)** — *recommended.* No parsing of build scripts. Projects with a toolchain (Gradle's recommended setup) compile with exactly the JDK they ask for. Cost: a project without a toolchain compiles with the daemon's JDK, and an old plugin (e.g. an old Lombok or Kotlin plugin) can fail on a newer JDK.
- [ ] **B · Like M5: read the Java target (toolchain or `targetCompatibility`) with the query task, then restart Gradle on the smallest image JDK ≥ target** — Closest to M5's rule, but two Gradle starts per run, and the query itself needs a JDK to start on.
- [ ] **C · Read the target from build scripts with regexes** — One start, but fragile: build scripts are code, and versions often come from the catalog or `buildSrc`.

**Notes:** A (all recommended options accepted in chat)

### Q5 · Wrapper and Gradle distribution

The image has no Gradle (M5). `./gradlew` downloads the distribution named in `gradle/wrapper/gradle-wrapper.properties`. M5 says that nothing is downloaded per job except Maven dependencies, so any option except B changes that statement.

- [x] **A · Always `./gradlew`. The wrapper downloads the distribution from the repo's own `distributionUrl`. Repos without a wrapper are unsupported (exit `2`)** — *recommended.* Respects the project's Gradle version. Regulated environments already point `distributionUrl` at an internal mirror, the same way M1 respects the Maven settings. The download is a read-only artifact fetch (rule 7). The Gradle user home lives where a workflow cache can keep it.
- [ ] **B · Pre-bake common Gradle distributions in the image** — No download for those versions, but about 150 MB per version, and it still misses some.
- [ ] **C · Image `gradle` when there's no wrapper** — Covers wrapper-less repos, but the build may break on the wrong Gradle version.

**Notes:** A (all recommended options accepted in chat)

### Q6 · Pass-through configuration for regulated environments

M1 has `--maven-settings <file>` for mirrors and credentials. Gradle's equivalent is an init script or `gradle.properties` in the Gradle user home.

- [x] **A · `--gradle-init <file>`, passed to every Gradle call with `-I`, plus a matching action input** — *recommended.* Same shape as `--maven-settings`. An init script can set repository mirrors and credentials for all projects.
- [ ] **B · Nothing: the Gradle user home of the environment as it is** — Simplest, but inside our container image there is nothing for users to configure.
- [ ] **C · `--gradle-properties <file>`** — Covers credentials, but properties can't redirect repositories.

**Notes:** A (all recommended options accepted in chat)

### Q7 · Where the OpenRewrite Gradle plugin comes from

The init script has to put the OpenRewrite Gradle plugin on its own classpath. In Maven the user's Maven fetches `rewrite-maven-plugin` through its mirror. An init script's own classpath doesn't go through the project's repositories or a user's mirror config, so a mirror can't easily redirect it.

- [x] **A · Ship the plugin JAR (pinned in our version catalog) in the tool image as a small local repository. Recipe artifacts and the OpenRewrite libraries still resolve through the project's repositories** — *recommended.* No extra fetch and no mirror problem for the plugin. I believe the plugin is Apache 2.0 and loads the OpenRewrite libraries at run time from the project's repositories, so it stays small. Both points must be checked, and the license goes into `docs/licensing.md`.
- [ ] **B · Fetch the plugin from the Gradle Plugin Portal at run time** — Same as Maven in spirit, but blocked in regulated environments without a way to redirect it.

**Notes:** A (all recommended options accepted in chat)

### Q8 · How the adapter reads Gradle's model

Three things need data that only Gradle's resolution knows: resolved versions (M2 downgrade rules), JAR paths for `javap` grounding (M3), and the applied plugins (Q13). M3 reads JARs from the local Maven repository. Gradle has its own cache.

- [x] **A · One query task in our init script that writes JSON: the highest resolved version per package across all resolvable configurations and the buildscript classpath, the JAR paths of the compile classpaths, and the applied plugin IDs** — *recommended.* Gradle's own resolution, so platforms, catalogs, and version conflicts are handled. Works when the code doesn't compile, because it doesn't compile anything. Fails only when the build scripts don't configure, and then M2 already says "versions unreadable, no recipe".
- [ ] **B · Gradle Tooling API models** — Structured and official, but a new dependency, and custom models need the init script anyway.
- [ ] **C · Parse `dependencies` output and search `~/.gradle/caches/modules-2`** — No custom task, but depends on an internal cache layout and a human-readable format.

**Notes:** A (all recommended options accepted in chat)

### Q9 · Diagnostics from Gradle

The javac format in Gradle's output matches Maven's. kotlinc errors look different (`e: file:///…/File.kt:10:5 …`). Build failures appear in a `* What went wrong:` block.

- [x] **A · Parse the plain console output, like M1. Shared javac parser, a Gradle variant for kotlinc and for build failures, the shared JUnit XML parser on `build/test-results/**`** — *recommended.* Same approach as Maven (M1 decided against the Maven Embedder), most parsing code is shared.
- [ ] **B · Gradle Tooling API with build events and the Problems API** — Structured, but a new dependency, and as far as I know the Problems API only covers javac from Gradle 8.8 on.

**Notes:** A (all recommended options accepted in chat)

### Q10 · Verify stages and test reruns

Maven's stage 1 is `test-compile`. Gradle's `testClasses` only compiles the `test` source set, not `integrationTest` or `testFixtures`.

- [x] **A · Stage 1: an init-script task that depends on the `classes` task of every source set in every project, `--continue`. Stage 2: `check --continue`. `runTests` runs only the given tests in the `Test` task they came from (known from the report path)** — *recommended.* Same compile-first behaviour as Maven, covers custom test suites, and `check` matches Maven's `verify` (it also runs integration test suites and linters bound to `check`).
- [ ] **B · Stage 1 `testClasses`, stage 2 `test`** — Built-in tasks only, but misses other source sets and integration test suites.
- [ ] **C · One stage, `build --continue`** — One run, but no cheap compile stage for the fix loop, and it assembles artifacts nobody needs.

**Notes:** A (all recommended options accepted in chat)

### Q11 · Gradle daemon

The fix loop runs many builds in a row. A cold Gradle start costs several seconds plus configuration time.

- [x] **A · Daemon on during a run, stopped (`--stop`) at the end** — *recommended.* Much faster fix-loop iterations. In CI the container is thrown away anyway. Tests stop it so no daemons leak.
- [ ] **B · `--no-daemon` always** — Isolated and predictable, but every iteration pays the startup cost.

**Notes:** A (all recommended options accepted in chat)

### Q12 · Gradle plugins in the recipe index

In Gradle, Spring Boot is usually bumped as a plugin (`id("org.springframework.boot") version "3.4.0"`), not as a parent. Spring Boot recipes contain `org.openrewrite.gradle.plugins.UpgradePluginVersion` leaves with a plugin ID. M2 only indexes Maven leaves. Without this, the M7 "done when" (the M5 flow on a Spring Boot Gradle repo) finds no recipe.

- [x] **A · Index the Gradle `UpgradePluginVersion` leaves under the plugin marker coordinates (`<id>:<id>.gradle.plugin`) in the `maven` namespace. The Renovate adapter maps plugin rows to the same coordinates, and the query task reports plugins the same way** — *recommended.* Marker coordinates are real Maven coordinates, so the namespace and `core` stay as they are. I'm not sure how Renovate labels plugin rows in its PR table. Check it on a captured PR body.
- [ ] **B · A second namespace `gradle-plugin` with the plugin ID as package** — Explicit, but a second JVM namespace, against M2 Q6.
- [ ] **C · Don't index Gradle plugins** — No index change, but Spring Boot Gradle repos never get a recipe.

**Notes:** A (all recommended options accepted in chat)

### Q13 · Build-file edits by the fix loop

Gradle build files: `*.gradle`, `*.gradle.kts`, `gradle/*.versions.toml`, `gradle.properties`, and the sources of `buildSrc` and included build-logic builds. The wrapper (`gradle-wrapper.properties`, `gradle-wrapper.jar`) decides which code is downloaded and run.

- [x] **A · Build files editable as in Maven, with a Gradle blocklist (`enabled = false` or `onlyIf` on test tasks, `ignoreFailures`, new `exclude` / `excludeTestsMatching` / `excludeTags`). Wrapper files outside `editableScope`** — *recommended.* Consistent with M4. A model can't change the Gradle version or the download URL. The recipe can still update the wrapper, because recipe output isn't guarded (M4 Q10).
- [ ] **B · Same, but the wrapper editable too** — Lets the model raise the Gradle version when a plugin needs it, but a changed URL means running code from a new source.
- [ ] **C · Version catalog editable, build scripts read-only** — Safer, but blocks fixes like a changed plugin ID or a renamed DSL property.

**Notes:** A (all recommended options accepted in chat)

### Q14 · Groovy sources (Spock tests)

Gradle projects often have Groovy tests (Spock). M4 has guardrails for Java and Kotlin only. An unparsed Groovy test could be deleted or weakened without any rule noticing.

- [x] **A · `*.groovy` sources (not build scripts) are outside the Gradle adapter's `editableScope`. Failures in them are still reported** — *recommended.* Closes the loophole with one glob. The model can still fix the Java or Kotlin code a Spock test calls.
- [ ] **B · Add a Groovy grammar and queries to `LanguageSupport`** — Full support, but more work, and I don't know whether `tree-sitter-ng` ships a Groovy grammar.
- [ ] **C · Editable with only the generic rules (scope, diff size)** — No work, but test protection doesn't apply, against M4's goal.

**Notes:** A (all recommended options accepted in chat)

### Q15 · Android and Kotlin Multiplatform projects

The image has no Android SDK. An Android build fails at configuration with an error that has no location, which M1 counts as caused by the update, so the fix loop would try to "fix" the missing SDK.

- [x] **A · Unsupported: if the query task sees a `com.android.*` or `org.jetbrains.kotlin.multiplatform` plugin, tool error (exit `2`). Documented as a limitation** — *recommended.* A clear message instead of a model fighting the environment.
- [ ] **B · Let it fail and document it** — No detection code, but the fix loop wastes its budget and the comment blames the update.
- [ ] **C · Support Android (SDK in the image)** — Large image, licence terms of the SDK, and Android-specific diagnostics. Belongs to "Later".

**Notes:** A (all recommended options accepted in chat)

### Q16 · Proof for "done when"

- [x] **A · Tagged integration tests with real Gradle on fixtures (Groovy DSL, Kotlin DSL with version catalog, multi-project), including a Spring Boot 3.3 → 3.4 plugin bump that gets `UpgradeSpringBoot_3_4`. Plus a second GitHub test repository with Renovate and the M5 workflow** — *recommended.* The fixtures cover the variants quickly. The test repository proves the real flow, like M5.
- [ ] **B · Add a Gradle build to the M5 test repository** — One repo less, but runs into Q2 (both build tools in one repo) unless it's a subdirectory, and root detection doesn't see a subdirectory.
- [ ] **C · Integration tests only** — Cheaper, but doesn't prove "the M5 flow works the same".

**Notes:** A (all recommended options accepted in chat)

## Round 2

Round 1 accepted every recommendation. Two points follow from it.

### Q17 · Linters in `check`

Q10 runs `check` in stage 2. Many Gradle projects wire linters into `check` (Spotless, Checkstyle, detekt, ktlint). Their failures are build errors without a location, which count as caused by the update (M1) and come first in M3's progress tuple. So the attempt that fixes the last compile error can be rejected because the model's code isn't formatted the way Spotless wants: compile errors 3 → 0, but build errors 0 → 1.

- [x] **A · Stage 2 runs every `Test` task in every project (one aggregate task in our init script), not `check`** — *recommended.* Verify checks compile and tests, which is what an update can break. Integration test suites are still covered because they are `Test` tasks. Linters stay the job of the project's own CI.
- [ ] **B · Keep `check`, but exclude known linter tasks (`-x spotlessCheck`, …)** — Runs other `check` work, but needs a list of linter names that is never complete.
- [ ] **C · Keep `check` as decided in Q10** — Matches Maven's `verify`, with the rejection problem above.

The same problem exists for Maven when a linter is bound to `verify`. That is outside M7. If you pick A, I'll note it as an open point for M1/M3.

**Notes:** A (answered in chat)

### Q18 · Gradle wrapper update PRs

Renovate's `gradle-wrapper` manager opens PRs that only bump Gradle itself. The package is Gradle, not a Maven artifact, so the M2 namespace has no place for it, and Q13 keeps wrapper files out of the model's reach.

- [x] **A · The `gradle-wrapper` manager maps to no ecosystem. Its rows produce no `DependencyChange`, so a wrapper-only PR is skipped (M5 rule). Documented** — *recommended.* No new namespace and no special case. A broken Gradle upgrade is rare and usually needs build-logic changes that a reviewer should make.
- [ ] **B · Verify and fix loop without a recipe, under a new namespace `gradle-distribution`** — Covers Gradle upgrades, but adds a namespace, a version scheme, and grounding for something that isn't a library.

**Notes:** A (answered in chat)

## Round 3 (during implementation)

### Q19 · `rewriteRun` compiles the project first

The OpenRewrite Gradle plugin (7.39.0, built on the BOM's OpenRewrite 8.89.0) makes `rewriteRun` depend on the
`compileJava` task of every source set. On a scratch multi-project build with a compile error, `rewriteRun` failed at
`:app:compileJava` before the recipe ran: the same problem M2 found with Maven's `run` goal (spec section 5 asked to
reopen this point). With the dependency cleared in our init script (`rewriteRun.setDependsOn([])`, no build-file edit),
the recipe ran on the broken code and changed the file.

- [x] **A · Clear the compile dependency in our init script** — *recommended.* The recipe runs when the update broke
  compilation, like Maven's `runNoFork`. Cost: test sources lose the types of their own `main` source set, which come
  from compiled classes. Types from dependency JARs (the Spring Boot case) still resolve from the compile classpath.
- [ ] **B · Keep the plugin's compile dependency** — The recipe only runs when the PR head still compiles.
- [ ] **C · Leave the Gradle recipe runner out of M7** — No plugin in the image; AC29/AC30 move to a later milestone.

**Notes:** A (answered in chat)

---

## Decisions

### Round 1

| # | Topic | Decision |
|---|---|---|
| Q1 | Adapter structure | Two adapters (`MavenAdapter`, `GradleAdapter`) in `ecosystems:jvm`, sharing parsers, grounding, cluster keys, `LanguageSupport` |
| Q2 | Both `pom.xml` and Gradle at the root | Tool error (exit `2`) |
| Q3 | Gradle versions | 8.x and 9.x; older versions are a tool error |
| Q4 | JDK and toolchains | `gradle-daemon-jvm.properties` if present, otherwise the highest image JDK the Gradle version supports. Toolchains from image JDKs, auto-download off. Toolchain JDK not in the image → tool error |
| Q5 | Distribution | Always `./gradlew` with the repo's `distributionUrl`; no wrapper → unsupported |
| Q6 | Pass-through config | `--gradle-init <file>` (`-I` on every call) plus an action input |
| Q7 | OpenRewrite Gradle plugin | Shipped in the tool image as a local repository, version pinned; license and size to be checked |
| Q8 | Gradle model | One init-script query task writing JSON: resolved versions (incl. buildscript classpath), compile-classpath JARs, applied plugin IDs |
| Q9 | Diagnostics | Plain console parsing; shared javac and JUnit XML parsers, Gradle variants for kotlinc and build failures |
| Q10 | Verify stages | Stage 1: `classes` of every source set; stage 2: `check` (stage 2 reopened by Q17). `runTests` in the originating `Test` task |
| Q11 | Daemon | On during a run, stopped at the end |
| Q12 | Gradle plugins in the index | Gradle `UpgradePluginVersion` leaves under marker coordinates `<id>:<id>.gradle.plugin` in `maven`; Renovate plugin rows mapped the same way |
| Q13 | Build-file edits | Editable with a Gradle test-skip blocklist; wrapper files outside `editableScope` |
| Q14 | Groovy sources | `*.groovy` (not build scripts) outside `editableScope` |
| Q15 | Android / KMP | Detected by plugin ID → tool error, documented |
| Q16 | Proof | Tagged real-Gradle fixture tests (Groovy DSL, Kotlin DSL + catalog, multi-project, Spring Boot plugin bump) plus a second GitHub test repo with Renovate |

### Round 2

| # | Topic | Decision |
|---|---|---|
| Q17 | Stage 2 | Every `Test` task in every project (aggregate init-script task), not `check`. Replaces the stage-2 part of Q10. Same linter problem for Maven noted as an open point for M1/M3 |
| Q18 | Gradle wrapper PRs | `gradle-wrapper` maps to no ecosystem; wrapper-only PRs are skipped and documented |

### Round 3

| # | Topic | Decision |
|---|---|---|
| Q19 | `rewriteRun` compiles first | Our init script clears `rewriteRun`'s dependency on the compile tasks. Test sources lose the types of their own `main` source set |
