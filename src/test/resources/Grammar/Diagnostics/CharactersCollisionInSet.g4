lexer grammar CharactersCollisionInSet;

SINGLE_RANGE: '0' [/*❗ElementsCollisionInSet*/aa-z/*❗*/];
RANGE_SINGLE: '1' [/*❗ElementsCollisionInSet*/a-za/*❗*/];
RANGE_RANGE: '2' [/*❗ElementsCollisionInSet*/a-za-z/*❗*/];
DIFFERENT_RANGES: '3' [/*❗ElementsCollisionInSet*/g-li-n/*❗*/];
