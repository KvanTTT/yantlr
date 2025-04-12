lexer grammar EmptyClosure;
EmptyClosure
    : 'A' (/*❗EmptyClosure*//*❗*/)*
    | 'B' ('foo' | /*❗EmptyClosure*/'bar'? 'bar2'?/*❗*/)+
    | 'C' (/*❗EmptyClosure*//*❗*/ +)
    ;