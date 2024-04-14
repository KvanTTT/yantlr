# Grammars

```antlrv4
lexer grammar Test;
StringLiteral: 'abc' '\n' '\u00A9';
CharSet: [de-g\-\r];
Alternative: 'x' | 'y';
Block: '{' ('w' | 'z') '}';
```

# Atn

```dot
digraph ATN {
  rankdir=LR;

  StringLiteral -> s0 [label="ε"]
  s0 -> s1 [label="ε"]
  s1 -> s2 [label="a"]
  s2 -> s3 [label="b"]
  s3 -> s4 [label="c"]
  s4 -> s5 [label="ε"]
  s5 -> s6 [label="\\n"]
  s6 -> s7 [label="ε"]
  s7 -> s8 [label="©"]
  s8 -> s9 [label="ε"]
  s9 -> s10 [label="ε"]

  CharSet -> s11 [label="ε"]
  s11 -> s12 [label="ε"]
  s12 -> s13 [label="d, e..g, -, \\r"]
  s13 -> s14 [label="ε"]
  s14 -> s15 [label="ε"]

  Alternative -> s16 [label="ε"]
  s16 -> s17 [label="ε"]
  s17 -> s18 [label="x"]
  s18 -> s19 [label="ε"]
  s19 -> s20 [label="ε"]
  Alternative -> s21 [label="ε"]
  s21 -> s22 [label="ε"]
  s22 -> s23 [label="y"]
  s23 -> s19 [label="ε"]

  Block -> s24 [label="ε"]
  s24 -> s25 [label="ε"]
  s25 -> s26 [label="{"]
  s26 -> s27 [label="ε"]
  s27 -> s28 [label="ε"]
  s28 -> s29 [label="ε"]
  s29 -> s30 [label="ε"]
  s30 -> s31 [label="w"]
  s31 -> s32 [label="ε"]
  s32 -> s33 [label="ε"]
  s33 -> s34 [label="}"]
  s34 -> s35 [label="ε"]
  s35 -> s36 [label="ε"]
  s28 -> s37 [label="ε"]
  s37 -> s38 [label="ε"]
  s38 -> s39 [label="z"]
  s39 -> s32 [label="ε"]
}
```