# Progress – M4 Guardrails

Spec: [`spec.md`](spec.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | `LanguageSupport` port in `core`; tree-sitter facts for Java and Kotlin in `ecosystems:jvm`, with query files and query tests | ✅ |
| 2 | Guardrail framework in `core` (line diff, `Guardrail`, `Violation`) and the file rules: scope, diff size, build-file blocklist (JVM scope and Maven blocklist in the adapter) | ✅ |
| 3 | Fact rules: symbol comparison, test deletion, skips, assertions, test count, stubbing, suppression, parse errors; Java and Kotlin tests | ✅ |
| 4 | Fix loop runs the guardrails per attempt, before verify; `--max-changed-lines` | ⬜ |
| 5 | Report: every rejection, a count per rule, the runtime test count of the final verify | ⬜ |
| 6 | Quality gates verified | ⬜ |
| 7 | Docs in sync | ⬜ |
