lexer grammar UnreachableElements;

UnreachableElement: 'A' | 'A/*❗UnreachableElement*//*❗*/';
UnreachableElement2: 'B' 'C' | 'B' 'C/*❗UnreachableElement*//*❗*/';
ReachableElements: 'D' 'E' | 'D' 'F';