; The types that contain methods and tests: classes, and the `describe` blocks of Jest and Vitest (with `.each`, `.only`,
; `.skip`, and `xdescribe`). A test's type is the chain of the blocks around it.
(class_declaration name: (_) @name) @type
(abstract_class_declaration name: (_) @name) @type
((call_expression
   function: [(identifier) @function
              (member_expression object: (identifier) @function)
              (call_expression function: (member_expression object: (identifier) @function))]
   arguments: (arguments . [(string (string_fragment) @name) (template_string) @name]))
  @type
  (#match? @function "^(describe|xdescribe|fdescribe|suite)$"))
