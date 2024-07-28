lexer grammar NotAltNot;

NotAltNot: ~('b' | ~'d/*❗UnreachableElement*//*❗*/'); // ~('b' | [-∞, c] | [e, ∞]) -> 'd'