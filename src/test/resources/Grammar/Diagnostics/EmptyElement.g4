lexer grammar EmptyElement;

EmptyElement: 'A' (/*❗EmptyClosure*//*❗*/ +);
EmptyElementInsideAlternative: 'B' (/*❗EmptyClosure*//*❗*/+ | 'a');
NestedEmptyElement: 'C' (/*❗EmptyClosure*//*❗*/ ?*);
EmptyUnderQuestion: 'D' ( ?); // Allowed empty element under '?' (unlike ANTLR) despite it's useless