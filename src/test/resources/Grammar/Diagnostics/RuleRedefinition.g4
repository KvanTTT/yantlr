lexer grammar RuleRedefinition;
X : 'a';
/*❗RuleRedefinition*/X/*❗*/ : 'b';

mode Custom;
/*❗RuleRedefinition*/X/*❗*/ : 'c';