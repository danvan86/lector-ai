; Markers that skip tests or leave the others out: `.skip`, `.todo`, and `.only` on `it`, `test`, and `describe`, and
; `xit`, `xtest`, `xdescribe`.
((member_expression object: (identifier) @object property: (property_identifier) @property) @skip
  (#match? @object "^(it|test|describe|suite)$")
  (#match? @property "^(skip|todo|only)$"))
((call_expression function: (identifier) @skip)
  (#match? @skip "^(xit|xtest|xdescribe)$"))
