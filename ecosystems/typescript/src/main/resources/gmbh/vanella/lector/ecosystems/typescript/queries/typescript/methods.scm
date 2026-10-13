; Functions and methods with their bodies: function declarations, class methods (arrow functions in fields included),
; abstract methods, and functions assigned to exported constants.
(function_declaration name: (identifier) @name body: (_) @body) @method
(generator_function_declaration name: (identifier) @name body: (_) @body) @method
(method_definition name: (_) @name body: (_) @body) @method
(abstract_method_signature name: (_) @name) @method
(public_field_definition name: (_) @name value: [(arrow_function body: (_) @body) (function_expression body: (_) @body)]) @method
(export_statement
  declaration: (lexical_declaration
    (variable_declarator name: (identifier) @name value: [(arrow_function body: (_) @body) (function_expression body: (_) @body)]))) @method
