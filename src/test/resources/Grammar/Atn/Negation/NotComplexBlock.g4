lexer grammar NotComplexBlock;

// Transformation:
// ~('ab' | ~'c' | 'c' ~'d' | ~'e')
// ~('a' 'b' | [-∞..b] | [d..+∞] | 'c' ~'d' | [-∞..d] | [f..∞])
// 'a' ~'b' | 'c' 'd' | ∅
NotComplexBlock: ~('ab' | ~('cd' | 'e'));