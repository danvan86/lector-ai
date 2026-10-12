; Test methods: JUnit 4 and 5, and TestNG, by the annotation's simple or qualified name.
(method_declaration
  (modifiers [(marker_annotation name: (_) @annotation) (annotation name: (_) @annotation)])
  name: (identifier) @name
  (#match? @annotation "(^|\\.)(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)$")) @test
