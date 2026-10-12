; Suppressed warnings and checks: `@SuppressWarnings`, `@SuppressFBWarnings`, and suppression comments.
((marker_annotation name: (_) @name) @suppression
  (#match? @name "(^|\\.)Suppress[A-Za-z]*$"))
((annotation name: (_) @name) @suppression
  (#match? @name "(^|\\.)Suppress[A-Za-z]*$"))
((line_comment) @suppression
  (#match? @suppression "noinspection|NOPMD|NOSONAR|CHECKSTYLE.OFF|checkstyle.off"))
((block_comment) @suppression
  (#match? @suppression "noinspection|NOPMD|NOSONAR|CHECKSTYLE.OFF|checkstyle.off"))
