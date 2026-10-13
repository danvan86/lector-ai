; Assertions: `expect(…)` (Jest, Vitest) and `assert…` (Node, Chai), called or as an object.
((call_expression function: (identifier) @name) @assertion
  (#match? @name "^(expect|assert)$"))
((call_expression function: (member_expression object: (identifier) @name)) @assertion
  (#match? @name "^(expect|assert)$"))

; The statements an assertion's text is read from, so `expect(x).toBe(5)` includes the expected value.
(expression_statement) @statement
