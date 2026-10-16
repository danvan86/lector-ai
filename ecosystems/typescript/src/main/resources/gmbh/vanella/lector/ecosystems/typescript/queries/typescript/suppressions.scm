; Suppressed checks: `@ts-ignore`, `@ts-expect-error`, `@ts-nocheck`, and `eslint-disable…` comments, and a module
; declaration without a body (`declare module "dep";`), which types the whole package as `any` (review D3). Casts like
; `as any` are not suppressions (spec Q8).
((comment) @suppression
  (#match? @suppression "@ts-ignore|@ts-expect-error|@ts-nocheck|eslint-disable"))
((ambient_declaration (module name: (string) !body)) @suppression)
