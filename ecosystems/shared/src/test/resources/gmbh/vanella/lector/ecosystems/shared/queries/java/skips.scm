; Markers that skip a test: `@Disabled…`, `@Enabled…` conditions, `@Ignore`, and assumptions.
((marker_annotation name: (_) @name) @skip
  (#match? @name "(^|\\.)(Disabled.*|Enabled[A-Z].*|Ignore)$"))
((annotation name: (_) @name) @skip
  (#match? @name "(^|\\.)(Disabled.*|Enabled[A-Z].*|Ignore)$"))
((method_invocation name: (identifier) @name) @skip
  (#match? @name "^(assume|abort$)"))
