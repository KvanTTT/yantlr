lexer grammar IncorrectStringsAndSets;

/*❗EmptyToken*/EmptyString/*❗*/: /*❗EmptyStringOrSet*/''/*❗*/;
EmptySet: /*❗EmptyStringOrSet*/[]/*❗*/;

ReversedIntervalInRange: [/*❗ReversedInterval*/d-a/*❗*/0-9];
ReversedIntervalInSet: /*❗ReversedInterval*/'z'..'x'/*❗*/;

MultiCharacterLiteralInRange: /*❗MultiCharacterLiteralInRange*/'ab'/*❗*/../*❗MultiCharacterLiteralInRange*/'cd'/*❗*/;