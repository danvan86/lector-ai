; Tests: `it` and `test` calls with their name, also with `.only`, `.skip`, `.todo`, `.each`, and as `xit`, `xtest`, `fit`.
((call_expression
   function: [(identifier) @function
              (member_expression object: (identifier) @function)
              (call_expression function: (member_expression object: (identifier) @function))]
   arguments: (arguments . [(string (string_fragment) @name) (template_string) @name]))
  @test
  (#match? @function "^(it|test|xit|xtest|fit)$"))
