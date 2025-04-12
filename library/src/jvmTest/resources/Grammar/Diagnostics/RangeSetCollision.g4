lexer grammar RangeSetCollision;

RangeSetCollision: ([A-D] | 'C'..'F'/*❗UnreachableElement*//*❗*/) 'Z';