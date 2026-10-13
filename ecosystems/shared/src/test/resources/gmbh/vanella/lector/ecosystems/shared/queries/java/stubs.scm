; Placeholders in place of logic: `return null`, an empty method body, a TODO comment.
(return_statement (null_literal)) @stub
((method_declaration body: (block) @stub)
  (#match? @stub "^\\{\\s*\\}$"))
((line_comment) @stub
  (#match? @stub "TODO"))
((block_comment) @stub
  (#match? @stub "TODO"))
