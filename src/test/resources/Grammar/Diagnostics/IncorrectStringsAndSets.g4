lexer grammar IncorrectStringsAndSets;

EmptyString: /*❗EmptyStringOrSet*/''/*❗*/ '-';
EmptySet: /*❗EmptyStringOrSet*/[]/*❗*/ [+];
ReversedInterval: [/*❗ReversedInterval*/z-a/*❗*/0-9];