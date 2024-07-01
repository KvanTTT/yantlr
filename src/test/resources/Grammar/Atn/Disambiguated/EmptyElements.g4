lexer grammar EmptyElements;

EmptyInterval: /*❗EmptyStringOrSet*/[]/*❗*/ | 'a'; // The first emtpy set should be skipped in ATN

EmptyStringLiteral: 'b' /*❗EmptyStringOrSet*/''/*❗*/ 'c'; // The second empty string should be skipped in ATN