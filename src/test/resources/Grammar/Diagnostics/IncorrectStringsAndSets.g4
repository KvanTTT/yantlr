lexer grammar IncorrectStringsAndSets;

/*❗EmptyToken*/EmptyString/*❗*/: /*❗EmptyStringOrSet*/''/*❗*/;
/*❗EmptyToken*/EmptySet/*❗*/: /*❗EmptyStringOrSet*/[]/*❗*/;

/*❗EmptyToken*/ReversedIntervalInRange/*❗*/: [/*❗ReversedInterval*/d-a/*❗*/0-9];
/*❗EmptyToken*/ReversedIntervalInSet/*❗*/: /*❗ReversedInterval*/'z'..'x'/*❗*/;

/*❗EmptyToken*/MultiCharacterLiteralInRange/*❗*/: /*❗MultiCharacterLiteralInRange*/'ab'/*❗*/../*❗MultiCharacterLiteralInRange*/'cd'/*❗*/;