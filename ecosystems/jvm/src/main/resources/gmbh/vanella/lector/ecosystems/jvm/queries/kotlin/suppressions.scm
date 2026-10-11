; Suppressed warnings and checks: `@Suppress`, `@SuppressWarnings`, `@file:Suppress`, and suppression comments.
((annotation [(user_type (type_identifier) @name) (constructor_invocation (user_type (type_identifier) @name))]) @suppression
  (#match? @name "^Suppress[A-Za-z]*$"))
((file_annotation (constructor_invocation (user_type (type_identifier) @name))) @suppression
  (#match? @name "^Suppress[A-Za-z]*$"))
((line_comment) @suppression
  (#match? @suppression "noinspection|NOSONAR|ktlint-disable|detekt:"))
((multiline_comment) @suppression
  (#match? @suppression "noinspection|NOSONAR|ktlint-disable|detekt:"))
