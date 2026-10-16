# Fixture repositories of the TypeScript adapter

Real projects with lockfiles written by the real package managers. The tagged integration tests copy them into a Git
repository of their own, with a `.gitignore` for what the tools write (Gradle leaves `.gitignore` files out of the test
resources), and install, type-check, and test them. Unit tests plan commands on them.

| Fixture | Package manager | Shape | Tests |
|---|---|---|---|
| `npm-jest` | npm (lockfile 3) | single package, TypeScript 5.9 | Jest 30 with ts-jest |
| `pnpm-workspace` | pnpm 10 (lockfile 9.0) | workspace: `app` imports `lib` through `lib`'s `dist/` | Vitest 4 in `app` |
| `yarn-classic` | Yarn 1.22 | single package, TypeScript 7 | `node --test` (no runner the adapter reads per test) |
| `yarn-berry-pnp` | Yarn 4 (Plug'n'Play) | single package, depends on `dequal` (types in its own `.d.ts`) | Jest 30 with ts-jest |
