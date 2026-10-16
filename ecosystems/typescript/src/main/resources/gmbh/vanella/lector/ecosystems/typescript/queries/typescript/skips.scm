; Markers that skip tests, leave the others out, or let a failure pass: `.skip`, `.todo`, `.only`, `.failing`, `.fails`,
; `.skipIf`, and `.runIf` on `it`, `test`, `describe`, and `suite`, also chained (`it.concurrent.skip`), and `xit`,
; `xtest`, `xdescribe`, `fit`, `fdescribe`.
((member_expression
   object: [(identifier) @object (member_expression object: (identifier) @object)]
   property: (property_identifier) @property) @skip
  (#match? @object "^(it|test|describe|suite)$")
  (#match? @property "^(skip|todo|only|failing|fails|skipIf|runIf)$"))
((call_expression function: (identifier) @skip)
  (#match? @skip "^(xit|xtest|xdescribe|fit|fdescribe)$"))
