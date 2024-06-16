lexer grammar EmptyClosureDouble;
EmptyClosureDouble
    : ('A' |/*❗EmptyClosure*//*❗*/ )* (/*❗EmptyClosure*//*❗*/ | 'B')* 'C'
    ;