lexer grammar NotComplexBlock;

NotComplexBlock: ~('ab' | ~('cd' | 'e/*❗UnreachableElement*//*❗*/'));