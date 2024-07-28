lexer grammar NotAltNot;

// ~('b' | [-∞, c] | [e, ∞]) -> 'd'
NotAltNot: ~('b' | ~'d');