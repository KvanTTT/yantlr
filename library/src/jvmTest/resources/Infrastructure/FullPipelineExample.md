# Notes

# Grammars

```antlrv4
lexer grammar grammarWithoutErrors;
A: 'a';
```

```antlrv4
grammar grammarWithErrors
/*❗UnrecognizedToken*/`/*❗*/
/*❗MissingToken*//*❗*//*❗ExtraToken*/+/*❗*/
```