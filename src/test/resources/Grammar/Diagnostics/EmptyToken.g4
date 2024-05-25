lexer grammar EmptyToken;

/*❗EmptyToken*/Empty/*❗*/: ;

/*❗EmptyToken*/EmptyAlternative/*❗*/
    : 'A'
    |
    | 'C'
    ;

/*❗EmptyToken*/EmptyClosure/*❗*/
    : ()*
    ;

/*❗EmptyToken*/EmptyClosure2/*❗*/
    : ('D' | )*
    ;