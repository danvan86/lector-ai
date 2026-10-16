; Suppressed checks: `@ts-ignore`, `@ts-expect-error`, `@ts-nocheck`, and `eslint-disable…` comments. Casts like
; `as any` are not suppressions (spec Q8).
((comment) @suppression
  (#match? @suppression "@ts-ignore|@ts-expect-error|@ts-nocheck|eslint-disable"))
