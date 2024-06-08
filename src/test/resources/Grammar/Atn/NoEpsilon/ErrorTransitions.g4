lexer grammar ErrorTransitions;

ErrorToken
    : '0' [/*❗ReversedInterval*/z-a/*❗*/]
    | '1' /*❗EmptyStringOrSet*/[]/*❗*/
    | '2' /*❗MultiCharacterLiteralInRange*/'ab'/*❗*/../*❗MultiCharacterLiteralInRange*/'yz'/*❗*/
    ;