# Progress – M9 Second ecosystem: TypeScript

Spec: [`spec.md`](spec.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | Core: the lockfile follows a build-file edit (`updateLockfiles`), committed with the edit, before the downgrade check | ✅ |
| 2 | `ecosystems:shared`: JUnit XML reports, tree-sitter facts, and the XML parser move out of `ecosystems:jvm` | ✅ |
| 3 | `ecosystems:typescript` project model: detection, lockfiles and their formats, workspaces, Node selection, tool errors, semver, resolved versions | ✅ |
| 4 | Verify: environment, install, type check, tests, `runTests`, `tsc` and JUnit parsing, cluster keys | ✅ |
| 5 | Fix loop: `LanguageSupport` (TS, TSX, JS), build files, blocklist, editable scope, lockfile update, grounding from `.d.ts` (PnP included) | ✅ |
| 6 | Wiring: CLI options and registration, Renovate's Node rows, action inputs, tool image, benchmark | ✅ |
| 7 | Tagged integration tests with real toolchains on the four fixtures and a base/head fixture | ✅ |
| 8 | Quality gates verified | ✅ |
| 9 | Docs in sync | ✅ |
| 10 | Review and fixes ([`review.md`](review.md)): 36 findings and 2 found while fixing; 30 fixed, 8 not changed with reasons; decisions D1 (core `loosening` port), D2 (no CI job for the image's Node toolchain), D3 (`declare module` is a suppression) | ✅ |

## Quality gates

Final check from a clean state, each on its own, all exit `0`: `ktlintCheck`, `detektMain detektTest`, `test`
(841 unit tests in all modules), `:cli:endToEndTest` (21), `koverVerify` (100 % lines and branches per module,
`ecosystems:shared` and `ecosystems:typescript` included), `clean build --rerun-tasks --no-build-cache`, and the opt-in
`:ecosystems:typescript:integrationTest` (6, real npm, pnpm, Yarn 1 and 4, tsc, Jest, Vitest) and
`:ecosystems:jvm:integrationTest` (5, after the JUnit parser moved).

After the review's fixes: `clean build --rerun-tasks --no-build-cache` exit `0`, 863 unit tests (core 205, jvm 193,
typescript 143, cli 124, benchmark 69, sources 40, recipes 37, git 24, llm 20, shared 8), 21 end-to-end, 100 % lines
and branches per module; `:ecosystems:typescript:integrationTest --rerun` 7 (a real Vitest rerun added). The tool image builds, and `lector verify` is green
on the four fixtures inside it.

Probes, each reverted afterwards (`git status` clean):

| Gate | Probe | Result |
|---|---|---|
| ktlint | bad formatting in `ecosystems:typescript` main, test, and build script; in `ecosystems:shared` main | fails on `ktlintMainSourceSetCheck`, `ktlintTestSourceSetCheck`, `ktlintKotlinScriptCheck` |
| detekt | `println` and `System.out.println` in typescript main, `System.out.printf` in its tests, `exitProcess` in shared | fails on `ForbiddenMethodCall`, `ExitOutsideMain` |
| Kover | an untested function with a branch in typescript and in shared | fails on `koverVerify` (`Rule violated`) |
| Tests | a failing test in typescript | fails on `test` |

`./gradlew build --dry-run`: every gate task of both new modules runs once; `integrationTest` and `installNodeToolchain`
don't run (AC50).

## Acceptance criteria

| ID | State | Evidence |
|---|---|---|
| AC1 | met | final check above |
| AC2 | met, with a deviation | `settings.gradle.kts`, `ecosystems/typescript/build.gradle.kts`, `RootCommand.adapters`. The module depends on `core` and on `ecosystems:shared`, the home of the shared JUnit parser that AC22 asks for; on no other adapter |
| AC3 | met | `core/build.gradle.kts` has no `project(`; the search finds only two KDoc examples from M0 that name `npm` as a namespace (`EcosystemAdapter.ecosystem`, `DependencyChange`) |
| AC4 | met | `NodeProjectTest`, `TypeScriptAdapterTest` |
| AC5 | met | `NodeProjectTest` on real lockfiles of npm, pnpm 10 and 12, Yarn 1 and 4 |
| AC6 | met | `NodeProjectTest`; exit `2` in `RootCommandTest` (no lockfile) and `TypeScriptAdapterTest` (two) |
| AC7 | met | `NodeProjectTest` |
| AC8 | met | `NodeProjectTest` |
| AC9 | met | `NodeSelectionTest` |
| AC10 | met | integration test: `node_modules/.modules.yaml` names `pnpm@10.34.6`; `NodeBuildTest` for the environment |
| AC11 | met | `NodeBuildTest`: `NPM_CONFIG_USERCONFIG` on every process; the yarnrc in a home of its own for Yarn Berry |
| AC12 | met | `NodeBuildTest`: a real `env` process sees no token and no `INPUT_*` |
| AC13 | met | `CommandsTest` |
| AC14 | met | integration test (a fix loop that edits `package.json` commits the lockfile with it); `FixLoopBuildFileTest` in core |
| AC15 | met | `NodeBuildTest` on the pnpm workspace fixture |
| AC16 | met | `NodeBuildTest` |
| AC17 | met | `NodeBuildTest` |
| AC18 | met | `NodeBuildTest` command lines; integration test: `git status --porcelain` empty after verify |
| AC19 | met | `NodeBuildTest`: `yarn test`, one diagnostic without a location. It carries a package-level test name, so it is rerun and checked on the base commit |
| AC20 | met | `NodeBuildTest`; the integration test reruns a real Jest test |
| AC21 | met | `TscOutputTest` on output captured from TypeScript 5.9 (7.0 prints the same) |
| AC22 | met | `ecosystems:shared` (`testCases`), used by `TestReports` (JVM) and `NodeTestReports` |
| AC23 | met | `TypeScriptClusterKeysTest` |
| AC24 | met | `NodeBuildTest` (PnP fixture: `yarn install`, `yarn run tsc`); integration test |
| AC25 | met | integration test: declarations of `dequal` read from Yarn's zip, no `node_modules` |
| AC26 | met | `DeclarationGroundingTest`, one test per step |
| AC27 | met | `DeclarationGroundingTest` |
| AC28 | met | `NodeBuild.PNP_SCRIPT` is the only Node script; declarations are read with tree-sitter |
| AC29 | met | `SemverTest` |
| AC30 | met | `LockfilesTest` on real workspace lockfiles of all four formats (pnpm 10 and 12) |
| AC31 | met | `recipeRunner = null` (`TypeScriptAdapterTest`); the generated index has 5578 entries, all `maven` |
| AC32 | met | `gradle/libs.versions.toml`, `ecosystems/typescript/build.gradle.kts` |
| AC33 | met | `TypeScriptLanguageSupportTest` |
| AC34 | met | `TypeScriptLanguageSupportTest` (`xtest` in the JavaScript sample) |
| AC35 | met | `TypeScriptLanguageSupportTest` |
| AC36 | met | `TypeScriptGuardrailsTest`, through core's `Guardrails` |
| AC37 | met | `TypeScriptGuardrailsTest` |
| AC38 | met | `TypeScriptGuardrailsTest` |
| AC39 | met | `TypeScriptGuardrailsTest` |
| AC40 | met | `FileRulesTest` "the guardrails check the scope against the files Git tracks" (since M4) |
| AC41 | met before M9 | the rule is `ad61e4b` (M4), long before the first `ecosystems:typescript` commit; M9 needed no commit for it |
| AC42 | met | `RenovateSourceTest` (npm rows, a real Node PR body); `PrCommandTest`: a Node PR is skipped with exit `0` |
| AC43 | met | `DependabotSourceTest` (`npm_and_yarn` from a real fetch-metadata output, and `npm`) |
| AC44 | met | `lector <verify\|migrate\|heal\|pr> --help`; `VerifyCommandTest`, `PrCommandTest` |
| AC45 | met | `action/action.yml` |
| AC46 | met | `docker/lector/Dockerfile` and `docker/lector/node/package.json`; the image was built and run |
| AC47 | met | `docs/setup.md` |
| AC48 | met | `docs/licensing.md` |
| AC49 | met | `ecosystems/typescript/src/test/resources/fixtures/` |
| AC50 | met | `tasks.test` excludes the tag; dry run above |
| AC51 | met | integration test passes |
| AC52 | can't verify yet | needs a TypeScript test repository on GitHub (Open) |
| AC53 | met in the repo | three TypeScript cases in `corpus.json`; the report shows them apart. The first run is open |
| AC54 | met | `docs/features.md`; `git log origin/main.. -- core/src/main` is the one `updateLockfiles` commit |
| AC55 | met | no Nx, Turborepo, Lerna, Bun, or Deno in the adapter's sources |

## Findings during implementation

- Section 9 ("only files tracked by Git") is already in `core` since M4 (`ad61e4b`, `Scope` in `FileRules.kt`, tested in
  `FileRulesTest`). M9 needs no commit for it; `features.md` records that instead.
- The one core change: `EcosystemAdapter.updateLockfiles`. A lockfile is a file the build tool derives from a build file;
  the fix loop knew no such file.
- Node 24's bundled Corepack (0.34) can't run pnpm 12, which is a native binary. Corepack 0.36 can: the image's pinned
  Corepack comes first on `PATH`, and the image runs pnpm once to cache its binary.
- Lockfile formats of the maintained majors (checked October 2026): npm `lockfileVersion` 2 and 3; pnpm 9–12 all
  write `'9.0'` (pnpm 12 as two YAML documents, the package manager's own first); Yarn classic `v1`; Yarn 4
  `__metadata.version` 8 and later (4.18: 10). Yarn 3 (6) and older are out.
- `tsc --pretty false` prints the same format in TypeScript 5.9 and 7 (the native compiler).
- Jest loads `jest-junit` from a path outside the project under Yarn PnP (section 5's check).
- Yarn 4.18 disables dependencies' build scripts unless the project enables them; pnpm 11 fails on ignored builds. The
  project's own setting applies.
- Found running the image on the fixtures and real repositories, and fixed: pnpm needs `CI` to purge an old
  `node_modules` without a terminal (every process now runs with `CI=true`); pnpm puts its store at the repository's
  mount root when `HOME` is on another file system (the store stays under `HOME`); Jest's array option `--reporters`
  swallowed the test file of a rerun (files now come first); a workspace package with `"test": "jest"` and no tests
  failed as a build problem blamed on the update (a runner that fails without a report is now a package-level test
  failure, checked on the base); a failed install's lifecycle scripts print `tsc` errors (now parsed); npm prints its
  error before its usage text (failures show the first and last lines).
- Real repositories hit the tool errors as designed: `engines.node` `22.12.0` exactly, `.nvmrc` 20, Yarn 3 and pnpm 8
  lockfiles, two lockfiles, no `typescript` dependency. Many failed bot PRs have lockfiles Renovate couldn't update;
  `npm ci` fails on them, which is reported as a build error caused by the update.
- `vite.config.*` and `vitest.workspace.*` are build files with the test blocklist too: Vitest reads its `test`
  options from them (AGENTS.md: a missed build file is a way around the guardrails).
- `NODE_AUTH_TOKEN` and `NPM_TOKEN` reach the package managers next to the spec's prefixes: an `.npmrc` reads them by
  convention, and none of the prefixes can name a registry token.

## Open

- AC52: a real Renovate PR in a TypeScript test repository on GitHub with the M5 workflow, recorded in
  `docs/features.md`; then M9 can be set to ✅.
- AC53: the first benchmark run with the TypeScript cases. Two of them are still open PRs
  (`georgeOsdDev/AppInsightsDetective#137`, `intuit/commently#680`): their heads should be mirrored to the corpus
  repository before the run, as M6 did, in case the bots force-push.
- Release: a version tag publishes the image with the Node toolchain; the MSAL check in `docs/licensing.md` still
  blocks it (unchanged by M9).
