lexer grammar String;

String: '"' (~[\\"] | '\\' .)* '"';