# Progress – M9 Second ecosystem: TypeScript

Spec: [`spec.md`](spec.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | Core: the lockfile follows a build-file edit (`updateLockfiles`), committed with the edit, before the downgrade check | ✅ |
| 2 | `ecosystems:shared`: JUnit XML reports, tree-sitter facts, and the XML parser move out of `ecosystems:jvm` | ✅ |
| 3 | `ecosystems:typescript` project model: detection, lockfiles and their formats, workspaces, Node selection, tool errors, semver, resolved versions | ✅ |
| 4 | Verify: environment, install, type check, tests, `runTests`, `tsc` and JUnit parsing, cluster keys | ⬜ |
| 5 | Fix loop: `LanguageSupport` (TS, TSX, JS), build files, blocklist, editable scope, lockfile update, grounding from `.d.ts` (PnP included) | ⬜ |
| 6 | Wiring: CLI options and registration, Renovate's Node rows, action inputs, tool image, benchmark | ⬜ |
| 7 | Tagged integration tests with real toolchains on the four fixtures and a base/head fixture | ⬜ |
| 8 | Quality gates verified | ⬜ |
| 9 | Docs in sync | ⬜ |

## Findings during implementation

- Section 9 ("only files tracked by Git") is already in `core` since M4 (`ad61e4b`, `Scope` in `FileRules.kt`, tested in
  `FileRulesTest`). M9 needs no commit for it; `features.md` records that instead.
- Node 24's bundled Corepack (0.34) can't run pnpm 12, which is a native binary. Corepack 0.36 can: the image's pinned
  Corepack must come before Node's own on `PATH`.
- Lockfile formats of the maintained majors (checked October 2026): npm `lockfileVersion` 2 and 3; pnpm 9–12 all
  write `'9.0'` (pnpm 12 as two YAML documents, the package manager's own first); Yarn classic `v1`; Yarn 4
  `__metadata.version` 8 and later (4.18: 10). Yarn 3 (6) and older are out.
- `tsc --pretty false` prints the same format in TypeScript 5.9 and 7 (the native compiler).
- Jest loads `jest-junit` from a path outside the project under Yarn PnP (section 5's check).
- Yarn 4.18 disables dependencies' build scripts unless the project enables them: the project's own setting applies.

## Open
