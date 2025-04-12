# Notes

Test runner should report `UnknownProperty` but preserve ANTLR errors

# Grammars

```antlrv4
grammar grammarExample
/*❗UnrecognizedToken*/`/*❗*/
/*❗MissingToken*//*❗*//*❗ExtraToken*/+/*❗*/
```

# <!--❌UnknownProperty-->UnknownProperty<!--❌-->