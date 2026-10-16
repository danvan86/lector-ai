; Assertions: calls to `assert…` (JUnit, AssertJ, Hamcrest), `verify…` (Mockito), `fail`, and the `assert` statement.
((method_invocation name: (identifier) @name) @assertion
  (#match? @name "^(assert|verify|fail$)"))
(assert_statement) @assertion

; The statements an assertion's text is read from, so `assertThat(x).isEqualTo(5)` includes the expected value.
(expression_statement) @statement
