lexer grammar DoubleStar2;

DOUBLE_STAR_2: 'AB'* 'AB/*❗UnreachableElement*//*❗*/'* 'AB'; // TODO: https://github.com/KvanTTT/yantlr/issues/5
