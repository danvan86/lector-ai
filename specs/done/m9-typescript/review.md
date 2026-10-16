# Review – M9 Second ecosystem: TypeScript

Scope: commits `7bcf7bd..5e8d0c1` (17 commits on `danvan86/m9-typescript` against `origin/main`) against
[`spec.md`](spec.md).
Method: read every source, test, build, image, action, and docs file in scope (four parallel readers by area, every
finding below checked against the code); ran `./gradlew clean build --no-build-cache`; ran the installed `lector verify`
on the fixtures and on bad input (no lockfile, two lockfiles, `lockfileVersion: 1`, `.nvmrc` `20`, `lts/iron`, garbage,
unreadable `engines.node`, invalid `package.json`, missing `--npmrc` / `--yarnrc`); ran
`:ecosystems:typescript:integrationTest --rerun` with real npm, pnpm, Yarn 1 and 4; checked the uncertain claims with
the real tools (pnpm 10's install output, its workspace discovery, Vitest 4's `-t` matching); checked the task graph
with `./gradlew build --dry-run`. All probes reverted, `git status` clean.

Outcome per finding: **Fixed** · **Not changed** (with reason) · **Needs decision** (with options).

Severity: **High** = breaks a requirement or the external contract · **Medium** = real gap, should fix before the next milestone · **Low** = cleanup / nit.

## Summary

- 52 of 55 acceptance criteria are met. AC52 can't be verified from the repo (it needs a TypeScript test repository on
  GitHub). AC33/AC34 (skip markers) and AC36/AC37 (blocklist) are met as written, with caveats: `fit`, `.failing`, and
  multi-line ignore arrays get through (F6, F7), and no deletion is ever checked (F4).
- The main contract gap: the adapter re-reads the project on every call, and a model edit that makes it unreadable
  (`package.json` with a trailing comma, `typescript` removed) ends the run with exit `2` instead of rejecting the
  attempt (F1). Grounding crashes the same way on `import x = require("…")` (F2).
- The main guardrail gap: the build-file blocklist sees only added lines. In TypeScript, removing a line loosens:
  deleting the `test` script runs no tests for that package, deleting `"strict": true` turns strictness off (F4).
- The main verify gap: a test that a root runner ran (Vitest `projects`, Jest `projects`) is rerun in the package its
  file is in, finds no runner there, and is classified flaky (F12).
- Tests are thorough on parsers, command lines, and the fixtures; the integration tests pass with real toolchains.
  What the tests miss is mostly the second spelling of a rule.
- After the fix round: 30 findings fixed, 8 not changed with a reason, the three decisions answered (D1 A, D2 C, D3 A)
  and D1 and D3 built. `clean build --rerun-tasks --no-build-cache`: exit `0`, 863 unit tests, 21 end-to-end, 100 %
  coverage per module; the TypeScript integration tests: 7 of 7 with real toolchains.

## Acceptance criteria

| ID | Result | Evidence |
|---|---|---|
| AC1 | Met | `./gradlew clean build --no-build-cache`: exit `0`; unit tests core 203, jvm 193, cli 124, typescript 124, benchmark 68, sources 40, recipes 37, git 24, llm 20, shared 8; 21 end-to-end |
| AC2 | Met | `settings.gradle.kts`; `ecosystems/typescript/build.gradle.kts` depends on `core` and `ecosystems:shared` (home of the shared JUnit parser, AC22); `RootCommand.kt:56` |
| AC3 | Met | no `project(` in `core/build.gradle.kts`; the search finds only two KDoc examples naming `npm` as a namespace |
| AC4 | Met | `NodeProjectTest`, `TypeScriptAdapterTest` |
| AC5 | Met | `NodeProjectTest` on real lockfiles |
| AC6 | Met | installed `lector verify`: no lockfile → exit `2`, "has no lockfile"; two lockfiles → exit `2`, names both |
| AC7 | Met | installed `lector verify` on `lockfileVersion: 1` → exit `2`, "lockfileVersion 1, written by npm 6 or older" |
| AC8 | Met | `NodeProjectTest` |
| AC9 | Met | `NodeSelectionTest`; installed `lector verify` with `LECTOR_NODE_22/24`: `.nvmrc` `20` → exit `2` |
| AC10 | Met | integration test: `.modules.yaml` names `pnpm@10.34.6` |
| AC11 | Met | `NodeBuildTest` |
| AC12 | Met | `NodeBuildTest`: a real `env` process sees no token and no `INPUT_*` |
| AC13 | Met | `CommandsTest` |
| AC14 | Met | integration test "a fix that edits package_json lets the lockfile follow, in the same commit"; `FixLoopBuildFileTest` |
| AC15 | Met | `NodeBuildTest` on the pnpm workspace fixture |
| AC16 | Met | `NodeBuildTest` |
| AC17 | Met | `NodeBuildTest` |
| AC18 | Met | `NodeBuildTest`; integration test: `git status --porcelain` empty after verify on all four fixtures |
| AC19 | Met | `NodeBuildTest` |
| AC20 | Met, with caveats | `NodeBuildTest`; the integration test reruns a real Jest test. A test a root runner ran is rerun in the wrong package (F12) |
| AC21 | Met, with caveats | `TscOutputTest` on real output; tsc errors from a pnpm workspace's lifecycle script get pnpm's prefix in the path (F13) |
| AC22 | Met | `ecosystems/shared/.../JUnitReports.kt`, used by `TestReports` (JVM) and `NodeTestReports` |
| AC23 | Met | `TypeScriptClusterKeysTest` |
| AC24 | Met | `NodeBuildTest`; integration test on the PnP fixture |
| AC25 | Met | integration test (rerun, no cache): declarations of `dequal` read from Yarn's zip |
| AC26 | Met | `DeclarationGroundingTest`, one test per step |
| AC27 | Met | `DeclarationGroundingTest` |
| AC28 | Met | `NodeBuild.PNP_SCRIPT` is the only Node script |
| AC29 | Met | `SemverTest` |
| AC30 | Met | `LockfilesTest` |
| AC31 | Met | `recipeRunner = null`; the generated index has no `npm` entry |
| AC32 | Met | `gradle/libs.versions.toml`, module build file |
| AC33 | Met, with caveats | `TypeScriptLanguageSupportTest`; Jest's `fit`/`fdescribe`, `.failing`, Vitest's `.fails`/`skipIf`, and `it.concurrent.skip` aren't skip markers (F6) |
| AC34 | Met, with caveats | same as AC33 |
| AC35 | Met | `TypeScriptLanguageSupportTest` |
| AC36 | Met, with caveats | `TypeScriptGuardrailsTest`; removing `"strict": true` gets through (F4) |
| AC37 | Met, with caveats | `TypeScriptGuardrailsTest`; deleting or renaming the `test` script (F4), a new entry in a multi-line `testPathIgnorePatterns` (F7), and `test:unit` behind `"test": "npm run test:unit"` (F8) get through |
| AC38 | Met | `TypeScriptGuardrailsTest` |
| AC39 | Met | `TypeScriptGuardrailsTest` |
| AC40 | Met | `FileRulesTest` (since M4) |
| AC41 | Met | the rule is `ad61e4b` (M4), before any M9 commit; `features.md` records it |
| AC42 | Met | `RenovateSourceTest` on a real Node PR body; `PrCommandTest`: skipped, exit `0` |
| AC43 | Met | `DependabotSourceTest` on a real `fetch-metadata` output |
| AC44 | Met | installed `lector verify --npmrc /nope` and `--yarnrc /nope`: exit `2`, stderr names the file; `--help` of all four commands |
| AC45 | Met | `action/action.yml` |
| AC46 | Met | `docker/lector/Dockerfile`, `docker/lector/node/package.json` |
| AC47 | Met | `docs/setup.md` |
| AC48 | Met | `docs/licensing.md` |
| AC49 | Met | `ecosystems/typescript/src/test/resources/fixtures/` |
| AC50 | Met | `tasks.test` excludes `integration`; `./gradlew build --dry-run` lists no `integrationTest` |
| AC51 | Met | `:ecosystems:typescript:integrationTest --rerun`: 6 tests, 0 failures |
| AC52 | Can't verify | needs a TypeScript test repository on GitHub with the M5 workflow |
| AC53 | Met, with caveats | three TypeScript cases in `corpus.json`; the report shows them apart, but plans recipe runs for them and prints the recipe verdict "doesn't hold" (F19) |
| AC54 | Met | `features.md`; `git log origin/main.. -- core/src/main` is the one `updateLockfiles` commit |
| AC55 | Met | no Nx, Turborepo, Lerna, Bun, or Deno in the adapter |

## Findings

### Exit codes and the run

**F1 · High · A model edit that makes the project unreadable ends the run with exit 2 instead of rejecting the attempt.**
Every `NodeBuild.Session` re-reads the project and picks Node (`NodeBuild.kt:188`, `:348`). `NodeProject.read` throws a
`ToolError` for content the model can edit: invalid JSON (`PackageJson.kt:57-61`), `typescript` removed
(`NodeProject.kt:211-217`), an unreadable or unsatisfiable `engines.node` (`NodeSelection.kt:88-90`), an `.nvmrc`
naming a Node that isn't installed (`:78`). `updateLockfiles` (`FixLoop.kt:272`) and the next `verify` don't catch it;
the fix loop resets and rethrows (`FixLoop.kt:182-184`). A 7B model that adds a dependency and leaves a trailing comma
ends the run as a tool bug, against rule 4 and the exit-code contract. The base commit has the same path: a bot PR that
moves pnpm 8 to 9 has a `5.4`/`6.0` lockfile on the base, and `runTests(base)` exits `2` instead of leaving the tests
unattributed. Expected: `detect` stays the place for tool errors; after it, an unreadable project is a `Diagnostic`
without a location in `verify` and `runTests`, and `LockfileUpdate.Failed` in `updateLockfiles`.

Outcome: **Fixed.** After `detect`, an unreadable project is a problem without a location in `verify` and `runTests`, a `LockfileUpdate.Failed` in `updateLockfiles`, and no location in the Plug'n'Play lookup; `NodeBuildTest` covers a trailing comma and an `.nvmrc` naming Node 26 (`dc15e21`).

**F2 · High · Grounding crashes the run on `import x = require("…")`.** `DeclarationGrounding.kt:214` reads the
`source` field of every `import_statement`; in the TypeScript grammar an `import x = require()` has it on the
`import_require_clause`, so the field is a null node and `text()` throws `TSException`. Grounding runs in
`FixLoop.request`, outside the attempt's `try` (`FixLoop.kt:170`): any failing file with such an import ends `heal` and
`pr` with exit `2`. Expected: skip imports without a `source` (and read `require` imports' module if it costs nothing);
a test with that import.

Outcome: **Fixed.** Imports without a module path are skipped; a test grounds a file with `import fs = require("fs")` (`bb927b9`).

**F3 · Medium · A Plug'n'Play lookup that times out throws instead of returning nothing.** `NodeBuild.pnpLocations`
promises "so is everything if the call fails" (`NodeBuild.kt:150-151`), but `runProcess` throws `ProcessTimedOut` after
`PNP_TIMEOUT`, again outside the attempt's `try`. Expected: catch it and return no locations, with a test.

Outcome: **Fixed.** A lookup that times out finds nothing and logs why (`bb927b9`).

### Guardrails

**F4 · High · Deleting a setting is never checked, and in TypeScript deleting loosens.** `BuildFileBlocklist`
(`core/.../FileRules.kt:52-58`) asks the adapter only about added lines of the new content. Routes that get through:
delete `"test": "jest"` from a package's scripts (or rename it to `"test:unit"`): `NodeBuild.kt:252` then runs no test
for that package and the failing test disappears; delete `"strict": true` or the `"extends"` that brings it; replace
`"include": ["src"]` by `"files": []` (`Tsconfig.compilesNothing` then skips `tsc` for the package, and
`TypeScriptGuardrailsTest.kt:150` asserts the edit is allowed); delete a project from the root `references`; narrow
`"workspaces"` or add `- "!packages/app"` to `pnpm-workspace.yaml`. Each is a one- or two-line edit the progress check
accepts. Maven and Gradle run tests by default, so the JVM never needed it; TypeScript needs `before` too, which the
port doesn't give.

Outcome: **Fixed** (D1, option A). A second core port change, `EcosystemAdapter.loosening(file, before, after)`: the build-file blocklist asks it with each build file's whole change. Maven and Gradle return nothing; `TypeScriptLoosening` rejects a removed test script, a removed strict option that was on or its `extends`, and narrowed `include`, `files`, `references`, or workspaces (`e0a00bf`). `features.md` lists it. Still line-only: JavaScript config files (`projects` removed from a Vitest workspace), which aren't parsed.

**F5 · Medium · A shorthand `declare module "dep";` types a whole package as `any`, and no guardrail sees it.** Added
to an existing tracked `.d.ts` (`global.d.ts`, `vite-env.d.ts`), it silences every TS7016 and TS2307 of the package.
It is the model's typical answer to those errors. Q8 B allowed `as any` casts, which are local; this one isn't.

Outcome: **Fixed** (D3, option A). A module declaration without a body is a suppression in the TypeScript queries; one with types stays allowed (`e90ff3d`).

**F6 · High · Skip markers are missing.** `queries/{typescript,javascript}/skips.scm` match `.skip`/`.todo`/`.only` on a
plain identifier and `xit`/`xtest`/`xdescribe`. Not matched: Jest's `fit` and `fdescribe` (the spelling of `.only`),
Jest's `it.failing` and Vitest's `test.fails` (an expected failure passes), Vitest's `test.skipIf(…)`/`runIf(…)`, and
any chained form like `it.concurrent.skip` or `describe.sequential.only`. `it("adds", …)` → `fit("adds", …)` makes every
other test in the file stop running, and no guardrail fires. AGENTS.md: a rule matches every spelling.

Outcome: **Fixed.** The skip queries match `fit`, `fdescribe`, `.failing`, `.fails`, `.skipIf`, `.runIf`, and chained forms, tested in all three grammars (`31efe3a`).

**F7 · High · New entries in a multi-line `testPathIgnorePatterns` or `modulePathIgnorePatterns` get through.**
`TypeScriptScope.kt:64`, `:80-83`: these keys only match on their own line; the "inside the array the key opened"
logic exists only for `exclude`. `testPathIgnorePatterns: [` with `"/node_modules/",` below it: adding
`"/src/broken.test.ts",` passes.

Outcome: **Fixed.** Every list option counts a line inside the list it opened, found by the innermost open bracket above (`31efe3a`).

**F8 · Medium · The test scripts behind the `test` script are unguarded, and a one-line `scripts` object too.**
`TEST_SCRIPT` (`TypeScriptScope.kt:45`) matches `"test":` only at the start of a line. `"test": "npm run test:unit"`
has no runner the adapter knows, so it runs `npm test`; changing `"test:unit": "jest"` to `"jest src/ok"` passes, and so
does a `pretest` that deletes test files. A minified `"scripts": {"test": "jest"}` passes as a whole. Expected: any
change to a script named `test`, `test:*`, `test-*`, `pretest`, or `posttest`, anywhere on the line.

Outcome: **Fixed.** Any change to a script named `test`, `test:*`, `test-*`, `pretest`, or `posttest` is rejected, anywhere on the line (`31efe3a`).

**F9 · Medium · Build files are missed.** `CONFIG_FILE` (`TypeScriptScope.kt:15-17`) allows only `[cm]?[jt]s`, so
`jest.config.json` and `vitest.workspace.json` aren't build files; nor are named variants a `test` script passes with
`--config` (`jest.unit.config.js`, `vitest.e2e.config.ts`), Jest presets (`jest.preset.js`), or `.pnpmfile.cjs`, whose
`readPackage` changes what the install resolves. AGENTS.md: an adapter's build files are every file the build tool reads
its configuration from.

Outcome: **Fixed.** JSON and named Jest and Vitest configs, `jest.preset.*`, `jest-e2e.json`, and `.pnpmfile.cjs` are build files (`31efe3a`). A tsconfig base not named `tsconfig*` (a Turborepo `typescript-config/base.json`) still isn't: `isBuildFile` sees only the path. Revisit with D1, whose whole-file check could read what a file is.

**F10 · Medium · Options that narrow which tests run are not blocked, unlike the JVM's `include` filters.** Jest's
`testMatch`, `testRegex`, `roots`, `testNamePattern`, and Vitest's `include`, `dir`, `testNamePattern` pass in
`package.json` and the Jest/Vitest config. The JVM blocks Gradle's `include*` filters and Maven's `<test>`
(`GradleGuardrails.kt:37,41`, `MavenGuardrails.kt`). `testMatch: ["**/ok.test.ts"]` leaves every other test out.
(Narrowing the type check's `include`/`files`/`references` and the workspaces is part of F4.)

Outcome: **Fixed.** `testMatch`, `testRegex`, `roots`, and `testNamePattern` are rejected in `package.json` and the test configs; Vitest's `include` and `dir` only inside its `test` options, so Vite's `optimizeDeps.include` stays editable (`31efe3a`). Narrowing the type check's `include`/`files`/`references` and the workspaces is D1.

**F11 · Medium · The `exclude` rule rejects lines after the `exclude` array closed.** `exclude()`
(`TypeScriptScope.kt:88-95`) counts brackets from the nearest `exclude` key above, also when that array ended on its
own line. With `"exclude": ["dist"]` above `compilerOptions`, adding `"vitest/globals",` to a multi-line `"types": [`
is rejected as a new `exclude` entry: a real fix the model can't make.

Outcome: **Fixed.** The innermost list still open above the line decides, not the nearest `exclude` key (`31efe3a`).

### Verify

**F12 · High · `runTests` reruns a test in the package its file is in, not the package whose runner ran it.**
`NodeBuild.kt:119` groups by `owner(file)`, the longest package directory containing the file (`:318`). A root
`"test": "vitest"` with Vitest `projects` (or Jest `projects`) reports `packages/app/src/a.test.ts`; the rerun goes to
`packages/app`, which has no `test` script, returns no failure (`:252`), and core classifies the test as flaky: never
fixed, never attributed. Expected: verify remembers which package ran each test (IDs are repo-relative, so it holds on
the base too); `owner` is the fallback for a working tree that wasn't verified.

Outcome: **Fixed.** Verify remembers which package ran each failed test; `runTests` reruns it there, the file's package is the fallback. Found with it: Vitest's projects report names the failure's place from the project, so the line was lost; the report reader now matches the file's path from any directory down (`c265703`, test on a real Vitest 4.1 report).

**F13 · Medium · tsc errors from a pnpm workspace's lifecycle script get pnpm's prefix in their path.** Real pnpm 10
output of a failed `pnpm install` (a `prepare: tsc` in `packages/lib`):
`packages/lib prepare: src/index.ts(4,14): error TS2322: …`. `NodeBuild.kt:205` parses it with `workDir = root`, and
`TscOutput`'s `^(.+)\(` makes the file `packages/lib prepare: src/index.ts`. The diagnostic points at no file, and the
generic failure with the real output is suppressed because the list isn't empty. Expected: read pnpm's
`<dir> <script>: ` prefix and resolve the file in that directory; a test on the captured output.

Outcome: **Fixed.** `TscOutput` reads pnpm's `<dir> <script>: ` prefix and resolves the file in that directory; the test uses the captured pnpm 10 output (`295ebed`).

**F14 · Low · The Jest rerun's file filter matches more files than the test's.** `files.map(::jsRegex)`
(`NodeBuild.kt:306`) is an unanchored pattern: `src/a.test.ts` also selects `src/a.test.tsx` and `x-src/a.test.ts`.
Jest matches it against the absolute path, so `/src/a\.test\.ts$` is exact.

Outcome: **Fixed.** Each file argument is `/<file>$`; the integration test reruns a real Jest test with it (`295ebed`).

**F15 · Low · `Tsconfig.compilesNothing` ignores `extends`.** `{"extends": "./base.json", "files": []}` compiles the
base's `include`, but the package is skipped (`Tsconfig.kt:36-37`).

Outcome: **Fixed.** A tsconfig with `extends` never compiles nothing (`295ebed`).

**F16 · Low · A root `test` script that fans out runs every workspace's tests twice.** Root `"test": "pnpm -r test"`
runs once at the root (`<pm> test`) and once per package (`NodeBuild.kt:94`).

Outcome: **Not changed.** The spec asks for the root and every package; task runners and fan-out are out of scope
(What not to do). The cost is time, not a wrong result. Revisit if the benchmark or the test repository shows it.

### Project model

**F17 · Medium · Workspace discovery differs from the package managers'.** `NodeProject.kt:188` strips `./` before
splitting off `!`, so `"!./packages/old"` keeps `./`, never matches, and the package stays in. Java's glob `**` also
enters dot directories, which npm and pnpm skip. Real pnpm 10 on `packages/**` with `!./packages/old`: lists
`packages/a`, `packages/a/test/fx`, `packages/b/tests/fx`, not `packages/old` or `packages/.hidden`; the adapter lists
both. Tests and `tsc` then run in packages the package manager doesn't know.

Outcome: **Fixed.** `!` is split off before `./`, and dot directories are skipped (`a6ff0c9`).

**F18 · Medium · A `packageManager` that disagrees with the lockfile is not a tool error.** `pnpm-lock.yaml` with
`"packageManager": "yarn@4.18.1"`: Corepack refuses to run pnpm, the frozen install fails, and the failure is reported
as caused by the update (exit `1`). A Berry lockfile with `packageManager: pnpm@…` says "package.json names no
packageManager" (`NodeProject.kt:150`), which is wrong, and `NodeProjectTest` asserts it. Expected: a tool error that
names both, as M7 Q2 does for ambiguous repos.

Outcome: **Fixed.** A `packageManager` naming another manager than the lockfile's is a tool error that names both (`a6ff0c9`).

**F19 · Medium · The benchmark plans recipe runs for TypeScript cases and prints the recipe verdict for them.**
`Plan.kt:46` schedules every mode for every case; `Report.kt` puts the 90/10 verdict into each ecosystem's section. The
adapter has no recipe runner, so the first run prints "# TypeScript (`npm`) … The hypothesis **doesn't hold** … recipe
commits changed 0 %", after 12 recipe-mode jobs that repeat the fix-loop-only one. Expected: a case of an ecosystem
without recipes runs only fix-loop-only, and its section says there is no recipe step.

Outcome: **Fixed.** `plan` gives an ecosystem without recipes only the fix-loop runs; its report section says there is no recipe step and shows only that mode (`c113cd2`).

**F20 · Low · Version numbers over `Int.MAX_VALUE` throw `NumberFormatException`.** `Semver.kt:99`,
`NodeSelection.kt:75` use `toInt()`; the exception isn't a `ToolError`, so it reaches the CLI as "unexpected failure".
Numeric pre-release identifiers longer than an `Int` compare as text (`Semver.kt:125-131`).

Outcome: **Fixed.** Versions take at most nine digits per number; numeric pre-release identifiers compare by length, then digits (`a6ff0c9`).

**F21 · Low · Wrong or swallowed messages.** `PackageJson.kt:14-15` says the first dependency kind wins, but
`dependencies` wins (`:38-40`, as the test expects). `berryMetadata` (`NodeProject.kt:159-169`) swallows the YAML error,
so a corrupt Berry lockfile says "neither Yarn classic's format nor Berry's".

Outcome: **Fixed.** The KDoc says which kind wins; a Berry lockfile that isn't YAML reports the parser's error (`a6ff0c9`).

**F22 · Low · `PackageFiles.real` can throw a `NullPointerException`.** `Path.parent` (`PackageFiles.kt:45`) is a
platform type; a location from Yarn deeper than its path gives `null`. AGENTS.md: check Java's nulls.

Outcome: **Fixed.** A virtual path deeper than its directories reads nothing (`bb927b9`).

**F23 · Low · `.nvmrc` and `.node-version` are editable and no build files.**

Outcome: **Not changed.** Moving the Node major can be the right fix for an update that needs a newer Node; with F1
fixed, a Node the image lacks is a rejected attempt, not a crash.

### Grounding

**F24 · Medium · Grounding repeats per-change work.** `DeclarationGrounding.ground` runs for every change of the PR on
every attempt (`FixLoop.request`): under Plug'n'Play one `yarn node` process per change, for a labelled PR hundreds per
attempt. Every package is looked up even if no failing file imports it, because the error messages' quoted names are
always there. `TSNode.text` (`DeclarationGrounding.kt:281-284`) re-encodes the whole file for every node. AGENTS.md:
per-change work reads what the changes share once per run. Expected: ground only a package a failing file imports or a
message names; encode the source once.

Outcome: **Fixed.** Only a package that a failing file imports, or whose name an error quotes, is looked up; the source is encoded once per parse (`bb927b9`).

**F25 · Low · Subpath `@types` and `exports` patterns aren't resolved.** The `@types` fallback always takes `"."`
(`DeclarationGrounding.kt:89`), so `lodash/fp` gets `@types/lodash/index.d.ts`; `exports` patterns like `"./*"` aren't
read.

Outcome: **Not changed.** The spec's order covers most packages; grounding is a hint, and a miss falls back to no
grounding. Revisit if the benchmark shows a miss.

### Tests

**F26 · Low · The CLI tests of the new options don't all assert exit code, stdout, and stderr.** `RootCommandTest`
"verify detects TypeScript": no stdout; `PrCommandTest` Node skip: no stderr; the missing npmrc/yarnrc tests: no stdout;
"an empty npmrc or yarnrc means none": no exit code. AGENTS.md: all three on every path.

Outcome: **Fixed.** Each new CLI test asserts all three (`5278103`).

**F27 · Low · No core test pins the per-attempt reset of `lockfiles`.** Without `lockfiles = emptyList()`
(`FixLoop.kt:172`), a lockfile a rejected attempt updated would go into the next attempt's commit.

Outcome: **Fixed.** `FixLoopBuildFileTest`: a lockfile a rejected attempt created stays out of the next commit. With the reset commented out, the test fails (`git add` of a missing path); with it, it passes (`5278103`).

**F28 · Low · The Vitest rerun was never run against a real Vitest.** The integration test reruns only Jest. (Checked by
hand with Vitest 4.1: the space-joined name the adapter builds matches; `>`-joined wouldn't.)

Outcome: **Fixed.** The integration test breaks a Vitest test in the pnpm workspace and reruns it alone (`295ebed`).

**F29 · Low · `NodeBuildTest` never closes its `NodeBuild`s.** Each leaks a `lector-node*` temp directory.

Outcome: **Fixed.** `NodeBuildTest` closes every build after each test; a full module run leaves no new `lector-node` directory (`5278103`).

### Wiring, versions, docs

**F30 · Medium · Nothing checks the image's Node toolchain when Dependabot updates it.** `.github/workflows/build.yml`
runs `./gradlew build` only; `:ecosystems:typescript:integrationTest`, the only consumer of `docker/lector/node`, is
opt-in, and the image is built only on release. A jest-junit or Corepack major that renames an option or a variable
(`NodeBuild.kt:294-302`, the Dockerfile's `corepack enable --install-directory`) merges green and breaks the next image.

Outcome: **Not changed** (D2, option C). The release is blocked by the MSAL check; the image's toolchain gets checked when the release is built. Revisit when the MSAL check is done and releases start.

**F31 · Low · The TypeScript and TSX grammars have two version keys.** Both come from the same upstream repository,
and `TypeScriptLanguageSupport` runs the same queries on both; Dependabot would bump them apart. AGENTS.md: versions
that must match get one source.

Outcome: **Fixed.** One `tree-sitter-typescript` version for both libraries (`7ee5b1d`).

**F32 · Low · The benchmark reads `LECTOR_JEST_JUNIT` differently from the CLI.** `BenchmarkCommand.kt` turns a blank
value into `Path("")`; `RootCommand.kt:52` drops it.

Outcome: **Not changed.** The only caller, the Benchmark workflow, always sets a real path (`benchmark.yml:114`), and the fix couldn't be observed by a test. Revisit if another caller sets it.

**F33 · Low · Docs.** `concept.md` 2.1's module diagram has no `ecosystems:shared`; the "no downgrades" rule (`:278`)
doesn't say the lockfile follows first. `licensing.md` lists npm as Artistic-2.0 only (it bundles ISC, MIT, BSD) and
Maven as 3.9 (the image has 3.10). `setup.md` doesn't warn that `NODE_AUTH_TOKEN` and `NPM_TOKEN` reach lifecycle
scripts and tests: setup-node's common `NODE_AUTH_TOKEN: ${{ secrets.GITHUB_TOKEN }}` hands a GitHub token to
dependency code.

Outcome: **Fixed.** All four (`d167486`).

**F34 · Medium · `docs/architecture/` is stale.** `project-skeleton.md` (eight modules, no `shared` or `typescript`),
`guardrails.md` (tree-sitter in `ecosystems:jvm`), `verify-pipeline.md`, `recipe-migration.md`, `benchmark.md`,
`fix-loop.md` (no `updateLockfiles`).

Outcome: **Not changed.** The architecture-docs step after this review writes them; it is next.

### Leftovers and readability

**F35 · Low · ktlint wrapped long conditions into unreadable shapes.** `if (result.exitCode !=\n0\n)`
(`NodeBuild.kt:202`, `:217`, `:349`), `TestScripts.kt:47`, `NodeTestReports.kt:58`, `DeclarationGrounding.kt:271`,
`Report.kt:30-47`. A local value or a shorter expression reads better. Three things in `NodeBuild` are named
`variables`.

Outcome: **Fixed.** A `when` or a local value instead; `managerVariables` and `environmentOf` (`7ee5b1d`; `Report.kt` in `c113cd2`).

**F36 · Low · The shared module's test queries copy the JVM's Java queries.**

Outcome: **Not changed.** They are fixtures for `TreeSitterLanguageTest`, which tests the loader, not the queries.

## Found while fixing

**F37 · Medium · The fix round checked with `detekt`, which skips the rules that need type resolution.** `build` runs
`detektMain` and `detektTest`; `detekt` alone passed while `ReturnCount`, `ThrowsCount`, `TooManyFunctions`, and
`DestructuringDeclarationWithTooManyEntries` failed in four fixes. The clean build would have caught it, but three
commits weren't green on their own.

Outcome: **Fixed.** The four places are restructured (`69a018f`), and every later check ran `check`, which is what
`build` runs.

**F38 · Low · 888 `lector-node*` directories are left in the temp directory from earlier test runs.** F29's leak.

Outcome: **Not changed.** They are outside the repository; F29 stops new ones.

## Decisions

**D1 (F4) · How the blocklist catches deletions and narrowing.** Example: the update breaks
`packages/app/src/a.test.ts`; the model deletes `"test": "vitest run",` from `packages/app/package.json`. The next
verify runs no test in `packages/app`, the failure is gone, the attempt is accepted. Same for deleting
`"strict": true,` or replacing `"include": ["src"]` by `"files": []`.

- **A · Core passes the file's before and after to the adapter (recommended).** A second port change:
  `testDisablingConfig` also gets the content before the edit (or a new whole-file `loosens(file, before, after)`). The
  TypeScript adapter compares the parsed JSON: a `test*` script removed or renamed, a strict-family option no longer
  on, `include`/`files`/`references`/`workspaces` narrowed. Listed in `features.md` as the second core change.
- **B · Adapter only, by lines.** Block every changed line in `include`, `files`, `references`, `workspaces`, and the
  pnpm `packages` list. Deletions stay open and are documented as a limitation.
- **C · Leave it.** Document the gap; rely on review of the commits.

Answer: **A.**

**D2 (F30) · A CI check for the image's Node toolchain.** Example: Dependabot bumps `jest-junit` to 18, which renames
`JEST_JUNIT_ADD_FILE_ATTRIBUTE`. `./gradlew build` is green, the PR merges, every Jest test in the next image loses its
file and line.

- **A · A workflow job on `docker/lector/**` and `ecosystems/typescript/**` that runs
  `:ecosystems:typescript:integrationTest` with setup-node (recommended).** About two minutes per such PR.
- **B · Build the image and run `lector verify` on the fixtures inside it.** Closer to the release, slower (image build).
- **C · Leave it.** The release is still blocked by the MSAL check; catch it then.

Answer: **C.**

**D3 (F5) · Is a shorthand `declare module "dep";` a suppression?** Example: the update drops `dep`'s bundled types;
`tsc` reports TS7016 in five files. The model adds `declare module "dep";` to `src/global.d.ts`; every error is gone,
and `dep` is `any` everywhere.

- **A · Count it as a suppression (recommended).** A `declare module "…";` without a body, added in an edit, is
  rejected like `@ts-ignore`. A module declaration with a body (real types) stays allowed.
- **B · Allow it, like `as any` (Q8 B).** The commit shows it; review catches it.

Answer: **A.**

## What's good

- The core change is small and language-neutral: `updateLockfiles` runs before the downgrade check, its files go into
  the attempt's commit by name (never `add --all`), and every way out resets the tree.
- Every tool error of spec section 1 has a clear message, is tested, and reaches exit `2` through `detect`. The
  TypeScript adapter is registered last, so JVM repositories with a frontend are unaffected.
- Processes: all through `runProcess` with a timeout and drained output; the environment is the allowlisted one plus
  the package managers' prefixes, tested with a real `env`; `CI=true` and no Corepack prompt.
- The working tree stays the user's: reports and the yarnrc home live in a temp directory, and the integration test
  checks `git status --porcelain` after verify on all four fixtures.
- Tool output in tests is real (tsc 5.9, jest-junit, Vitest, five lockfile formats), and the integration tests pass
  with real npm, pnpm, Yarn 1 and 4.
- Semver follows 2.0 precedence and npm's range rules, including pre-releases only on the same version.
- Grounding guards against path traversal (`normalizePackagePath`), reads zip entries by name, and stops re-export
  cycles.
- The image pins every Node tool in `docker/lector/node/package.json` with integrity hashes, so Dependabot sees them.
