; Assertions: calls to `assert…` (JUnit, kotlin.test, AssertJ), `verify…` (Mockito, MockK), and `fail`.
((call_expression (simple_identifier) @name) @assertion
  (#match? @name "^(assert|verify|fail$)"))

; The statements an assertion's text is read from, so `assertThat(x).isEqualTo(5)` includes the expected value.
(statements (_) @statement)
