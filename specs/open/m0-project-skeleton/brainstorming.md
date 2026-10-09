# M0 – Project skeleton · Brainstorming

Milestone: [M0 in features.md](../../../docs/features.md#m0--project-skeleton-) · Status: **Closed** (all questions decided)

**How to answer:** the recommended option is preselected with `[x]`. Move the `x` to choose another one. Write anything else under **Notes**.

## Already fixed by the concept

- Kotlin on the JVM, Gradle with Kotlin DSL.
- Modules `core` and `cli` only. Other modules are created when a milestone needs them.
- `core` depends on no other module; `cli` is the composition root.
- The CLI is the pipeline entry point, not a developer tool.

---

## Round 1

### Q1 · JDK version for the tool itself

This is the JVM our tool runs on inside the container image. It is independent of the JDKs needed to build target repos (that's M1/M5). It matters for M4: the official tree-sitter Java bindings use the FFM API, which needs JDK 22 or newer.

- [x] **A · JDK 25 (LTS)** — *recommended.* Current LTS. Keeps every tree-sitter binding option open and gives us the longest support window. We control the image, so there is no compatibility cost for users.
- [ ] **B · JDK 21 (LTS)** — Previous LTS, the most widely used. It rules out the official tree-sitter bindings (FFM), which leaves only the JNI-based ones.
- [ ] **C · JDK 17 (LTS)** — Maximum compatibility, no benefit for a tool that only runs in our own image.

**Notes:**

### Q2 · CLI library

The CLI only parses arguments and wires adapters. It needs subcommands (`verify`, `heal`, …), options, and help text.

- [x] **A · Clikt** — *recommended.* Kotlin-native, small, actively maintained, good subcommand support. One dependency.
- [ ] **B · picocli** — Mature Java library, annotation-based. Works fine from Kotlin but reads less naturally.
- [ ] **C · Hand-rolled parsing** — No dependency. Fine for `--version`, painful once we have three subcommands with options.

**Notes:**

### Q3 · Shared build configuration

Every module needs the same Kotlin version, JVM target, test setup, and lint config.

- [x] **A · Convention plugin in `buildSrc`** — *recommended.* One `kotlin-conventions.gradle.kts` applied by every module. Simple, standard, no extra setup.
- [ ] **B · Convention plugin in an included build (`build-logic/`)** — Gradle's current recommendation. Better caching for large builds, slightly more setup. Pays off later, not now.
- [ ] **C · `subprojects {}` block in the root build** — Shortest to write, but discouraged by Gradle and harder to keep apart as modules diverge.

**Notes:**

### Q4 · Dependency versions

- [x] **A · Version catalog (`gradle/libs.versions.toml`)** — *recommended.* All versions in one file. Renovate understands it, so our own dependencies get updated the same way our users' do.
- [ ] **B · Versions inline in each `build.gradle.kts`** — Less indirection, but versions drift between modules.

**Notes:**

### Q5 · Test framework

- [x] **A · JUnit 5 + `kotlin.test` assertions** — *recommended.* The default for Kotlin on the JVM, no extra libraries. JUnit tags cover the opt-in Ollama tests (`@Tag("ollama")`).
- [ ] **B · JUnit 5 + AssertJ** — Richer assertions for lists and objects (useful for `Diagnostic` lists). One more dependency.
- [ ] **C · Kotest** — Kotlin-first, many test styles. More to learn, and more than we need.

**Notes:**

### Q6 · Formatting and static analysis

- [x] **A · ktlint via Gradle plugin, part of `build`** — *recommended.* One formatter, no style debates, fails the build on violations. `ktlintFormat` fixes most of them.
- [ ] **B · ktlint + detekt** — Adds code-smell rules (complexity, long methods). Useful later, noisy now.
- [ ] **C · Nothing for now** — Fastest start, but style drift is harder to fix later.

**Notes:**

### Q7 · CI for this repository

This is CI for building our own tool, not the product pipeline. Our own code is not subject to the "code never leaves the infrastructure" rule for target repos, but you may want it to be.

- [x] **A · GitHub Actions on GitHub-hosted runners** — *recommended.* Zero setup, free for building and unit tests. The product targets GitHub first (M5), so we use the same platform.
- [ ] **B · GitHub Actions on self-hosted runners** — Same platform as the product. Needed later for GPU/Ollama tests anyway, but more setup in M0.
- [ ] **C · GitLab CI** — Only if the repository lives on GitLab.

**Notes:** Repository is hosted on GitHub.

### Q8 · How the CLI is packaged in M0

The container image comes in M5. Until then we need something runnable.

- [x] **A · Gradle `application` plugin (`installDist`)** — *recommended.* Produces `bin/` start scripts plus a `lib/` folder. Works directly as a container image layer later.
- [ ] **B · Fat JAR (Shadow plugin)** — One file. Can break with libraries that rely on `META-INF/services` merging. Not needed while we ship an image anyway.
- [ ] **C · GraalVM native image** — Fast start, but painful with reflection-heavy libraries. Startup time doesn't matter for a CI job that runs builds for minutes.

**Notes:**

### Q9 · Where the version number comes from

- [x] **A · Gradle `version` property, written into a generated resource** — *recommended.* `version = "0.1.0"` in the build, the CLI reads it at runtime. Simple and explicit.
- [ ] **B · Derived from Git tags (e.g. axion-release plugin)** — No manual bump, but another plugin and surprising results on untagged commits.

**Notes:**

### Q10 · Enforcing "core depends on nothing"

- [x] **A · By construction** — *recommended.* `core/build.gradle.kts` simply has no `project(...)` dependencies. Code review catches violations. Zero tooling.
- [ ] **B · Architecture test (Konsist or ArchUnit)** — A test fails if `core` imports adapter packages. Worth it once there are several contributors.

**Notes:**

### Q11 · Logging

- [x] **A · SLF4J API + a simple backend (`slf4j-simple`) writing to stderr** — *recommended.* Libraries we'll add later (OpenRewrite, HTTP clients) already log through SLF4J. Stdout stays free for the report.
- [ ] **B · Logback** — Configurable via XML, structured output possible. More than a CLI job needs.
- [ ] **C · Own tiny logger over `System.err`** — No dependency, but library logs would go nowhere.

**Notes:**

### Q12 · Base package and project license

These affect every file header and which OpenRewrite modules we may use (concept section 9).

Base package:

- [ ] **A · `ai.lector`** — *recommended if the domain is yours.* Short and product-oriented.
- [x] **B · `gmbh.vanella.lector`** — Company-based, safe if the product domain isn't registered.

License:

- [x] **A · Apache 2.0** — *recommended.* Same as OpenRewrite core, permissive, friendly for regulated companies to adopt.
- [ ] **B · Proprietary / internal for now** — Keeps every option open. Decide before the first public release.
- [ ] **C · AGPL or another copyleft license** — Protects against hosted forks, but many regulated companies won't touch it.

**Notes:**

---

## Round 2

Small follow-ups from Round 1.

### Q13 · Name of the CLI executable and the Gradle root project

The executable name shows up in every workflow file, log line, and report.

- [x] **A · Executable `lector`, root project `lector-ai`** — *recommended.* Short to type in workflow steps, matches the repository name for the build.
- [ ] **B · Executable `lector-ai`, root project `lector-ai`** — One name everywhere, slightly longer in scripts.

**Notes:**

### Q14 · Getting JDK 25 for building the tool

JDK 25 (Q1) must be available wherever `./gradlew build` runs: in CI and for contributors.

- [x] **A · Gradle toolchain (`jvmToolchain(25)`) with the foojay resolver plugin** — *recommended.* Gradle downloads JDK 25 if it's missing, so the build works on any machine with any JDK. In CI, `actions/setup-java` provides it up front.
- [ ] **B · `jvmToolchain(25)` without auto-download** — The build fails with a clear message if JDK 25 isn't installed. No downloads, but more manual setup.

**Notes:**

### Q15 · Dependency updates for this repository

- [ ] **A · Renovate** — *recommended.* We use the same bot our users do, so our own repo becomes a real test case for M5 later. It also understands the version catalog (Q4).
- [x] **B · Dependabot** — Built into GitHub, no app to install. Also our M8 test case, but later.
- [ ] **C · None for now** — Fewer PRs during early development.

**Notes:**

---

## Decisions

### Round 1

| # | Topic | Decision |
|---|---|---|
| Q1 | JDK for the tool | JDK 25 (LTS) |
| Q2 | CLI library | Clikt |
| Q3 | Shared build configuration | Convention plugin in `buildSrc` |
| Q4 | Dependency versions | Version catalog `gradle/libs.versions.toml` |
| Q5 | Test framework | JUnit 5 + `kotlin.test` |
| Q6 | Formatting | ktlint via Gradle plugin, part of `build` |
| Q7 | CI for this repo | GitHub Actions on GitHub-hosted runners (repo is on GitHub) |
| Q8 | Packaging | Gradle `application` plugin (`installDist`) |
| Q9 | Version number | Gradle `version` property, written into a generated resource |
| Q10 | "core depends on nothing" | By construction, no tooling |
| Q11 | Logging | SLF4J API + `slf4j-simple` to stderr |
| Q12 | Base package / license | `gmbh.vanella.lector` / Apache 2.0 |

### Round 2

| # | Topic | Decision |
|---|---|---|
| Q13 | Names | Executable `lector`, Gradle root project `lector-ai` |
| Q14 | JDK 25 for building the tool | `jvmToolchain(25)` with the foojay resolver (auto-download); `actions/setup-java` in CI |
| Q15 | Dependency updates for this repo | Dependabot |

### Revision (2026-10-09)

Q6 and the "no detekt" rule were revised on the owner's request: our own code gets strict quality gates from the start.

| # | Topic | Decision |
|---|---|---|
| Q6 | Formatting and static analysis | ktlint (formatting) + detekt 2.x (code smells, likely bugs) + Kover (coverage ≥ 80 % lines, ≥ 70 % branches per module), all failing `./gradlew build`. No baselines. CodeQL decided against (private repo without GitHub Code Security). |
