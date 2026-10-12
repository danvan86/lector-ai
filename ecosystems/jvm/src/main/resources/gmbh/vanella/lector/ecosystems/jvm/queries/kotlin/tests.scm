; Test functions: JUnit 4 and 5, and kotlin.test, by the annotation's simple or qualified name.
(function_declaration
  (modifiers
    (annotation [(user_type (type_identifier) @annotation) (constructor_invocation (user_type (type_identifier) @annotation))]))
  (simple_identifier) @name
  (#match? @annotation "^(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)$")) @test
