lexer grammar EmptyToken;

/*❗EmptyToken*/Empty/*❗*/: ;

/*❗EmptyToken*/EmptyAlternative/*❗*/
    : 'A'
    |
    | 'C'
    ;

/*❗EmptyToken*/EmptyClosure/*❗*/
    : (/*❗EmptyClosure*//*❗*/)*
    ;

/*❗EmptyToken*/EmptyClosure2/*❗*/
    : ('D' |/*❗EmptyClosure*//*❗*/ )*
    ;