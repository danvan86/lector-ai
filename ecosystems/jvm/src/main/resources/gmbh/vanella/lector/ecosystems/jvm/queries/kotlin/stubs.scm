; Placeholders in place of logic: `return null`, an empty function body, `= null` as the body, `TODO()`, a TODO comment.
((jump_expression) @stub
  (#match? @stub "^return\\s+null$"))
((function_body) @stub
  (#match? @stub "^(\\{\\s*\\}|=\\s*null)$"))
((call_expression (simple_identifier) @name) @stub
  (#eq? @name "TODO"))
((line_comment) @stub
  (#match? @stub "TODO"))
((multiline_comment) @stub
  (#match? @stub "TODO"))
