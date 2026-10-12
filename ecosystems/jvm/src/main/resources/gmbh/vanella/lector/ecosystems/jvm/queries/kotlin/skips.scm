; Markers that skip a test: `@Disabled…`, `@Enabled…` conditions, `@Ignore`, and assumptions.
((annotation [(user_type (type_identifier) @name) (constructor_invocation (user_type (type_identifier) @name))]) @skip
  (#match? @name "^(Disabled.*|Enabled[A-Z].*|Ignore)$"))
((call_expression (simple_identifier) @name) @skip
  (#match? @name "^(assume|abort$)"))
