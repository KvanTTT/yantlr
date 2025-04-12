lexer grammar FixedPoint;
FixedPoint
    : 'a' ('b' 'bc'? | 'b/*❗UnreachableElement*//*❗*/'? 'bc/*❗UnreachableElement*//*❗*/')* // 'a' ('b' | 'bbc' | 'bc' | 'bbc')*
    ;