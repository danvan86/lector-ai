; Placeholders in place of logic: `return null`, `return undefined`, the empty body of a function or method (not of any
; arrow function: `() => {}` is a common callback), and a TODO comment.
(return_statement (null)) @stub
(return_statement (undefined)) @stub
((function_declaration body: (statement_block) @stub)
  (#match? @stub "^\\{\\s*\\}$"))
((method_definition body: (statement_block) @stub)
  (#match? @stub "^\\{\\s*\\}$"))
((public_field_definition value: [(arrow_function body: (statement_block) @stub) (function_expression body: (statement_block) @stub)])
  (#match? @stub "^\\{\\s*\\}$"))
((export_statement
   declaration: (lexical_declaration
     (variable_declarator value: [(arrow_function body: (statement_block) @stub) (function_expression body: (statement_block) @stub)])))
  (#match? @stub "^\\{\\s*\\}$"))
((comment) @stub
  (#match? @stub "TODO"))
