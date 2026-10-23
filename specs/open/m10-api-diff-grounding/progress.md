# Progress – M10 API diff as grounding

Spec: [`spec.md`](spec.md) · Tests: [`tests.md`](tests.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | Port `apiChanges` with a default, `FixLoop` puts all distinct API changes first, prompt heading `API` (T-D, T-E) | ✅ |
| 2 | API model from class files with the ClassFile API | ✅ |
| 3 | API diff (seven categories) and its rendering, Maven with old JARs in the local repository (T-A) | ⬜ |
| 4 | Maven: fetch missing old JARs once per run with `maven-dependency-plugin` (T-B, real Maven) | ⬜ |
| 5 | Gradle: old JARs from any model read in the run, kept apart from `isBuildFile` (T-C) | ⬜ |
| 6 | Proof cases: red at `cf183a8`, green with M10, in the benchmark corpus | ⬜ |
| 7 | Quality gates verified | ⬜ |
| 8 | Docs in sync | ⬜ |
