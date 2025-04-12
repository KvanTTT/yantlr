lexer grammar DoubleStar;

DOUBLE_STAR: 'A'* 'A/*❗UnreachableElement*//*❗*/'* 'A'; // TODO: https://github.com/KvanTTT/yantlr/issues/5
