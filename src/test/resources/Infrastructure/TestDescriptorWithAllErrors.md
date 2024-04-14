# Notes

Test runner should report `UnknownProperty` but preserved ANTLR errors

# Grammars

```antlrv4
grammar grammarExample
/*❗UnrecognizedToken*/`/*❗*/
/*❗MissingToken*//*❗*//*❗ExtraToken*/+/*❗*/
```

# <!--❌UnknownProperty-->UnknownProperty<!--❌-->