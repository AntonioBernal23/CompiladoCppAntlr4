// BERNAL MARTINEZ ANTONIO
grammar Cpp;

/* =====================================================================
   REGLAS DEL PARSER (minusculas)
   ===================================================================== */

programa : sentencia* EOF ;

sentencia
    : declaracionVariable
    | declaracionConstante
    | asignacion
    ;

// ---------- Tipos ----------
tipo : INT | FLOAT | DOUBLE | CHAR | BOOL | STRING_T ;

// ---------- Declaracion de variables ----------
//   int x;      int a = 5, b = 10, c;
declaracionVariable : tipo declarador (COMMA declarador)* SEMI ;

declarador : ID (ASSIGN expresion)? ;

// ---------- Declaracion de constantes (siempre inicializadas) ----------
//   const int MAX = 100;
declaracionConstante : CONST tipo ID ASSIGN expresion SEMI ;

// ---------- Uso de variables ----------
//   x = 20;     a += b * 2;
asignacion : ID operadorAsignacion expresion SEMI ;

operadorAsignacion
    : ASSIGN | PLUS_ASSIGN | MINUS_ASSIGN | MULT_ASSIGN | DIV_ASSIGN
    ;

// ---------- Expresiones basicas ----------
expresion : valor ( (PLUS | MINUS | MULT | DIV | MOD) valor )* ;

valor
    : INT_LIT
    | FLOAT_LIT
    | STRING_LIT
    | CHAR_LIT
    | TRUE
    | FALSE
    | ID
    | LPAREN expresion RPAREN
    ;

/* =====================================================================
   REGLAS DEL LEXER (MAYUSCULAS)
   ===================================================================== */

// ---------- Palabras clave ----------
IF       : 'if';
ELSE     : 'else';
WHILE    : 'while';
FOR      : 'for';
DO       : 'do';
SWITCH   : 'switch';
CASE     : 'case';
DEFAULT  : 'default';
BREAK    : 'break';
CONTINUE : 'continue';
RETURN   : 'return';
CLASS    : 'class';
STRUCT   : 'struct';
PUBLIC   : 'public';
PRIVATE  : 'private';
PROTECTED: 'protected';
NEW      : 'new';
DELETE   : 'delete';
CONST    : 'const';
STATIC   : 'static';
VOID     : 'void';
INT      : 'int';
FLOAT    : 'float';
DOUBLE   : 'double';
CHAR     : 'char';
BOOL     : 'bool';
STRING_T : 'string';
TRUE     : 'true';
FALSE    : 'false';
USING    : 'using';
NAMESPACE: 'namespace';

// ---------- Literales numéricos ----------
FLOAT_LIT : [0-9]+ '.' [0-9]+ ([eE] [+-]? [0-9]+)?
          | [0-9]+ [eE] [+-]? [0-9]+
          ;
INT_LIT   : [0-9]+;

// ---------- ERRORES: literales numéricos incorrectos ----------
BAD_NUMBER
    : [0-9]+ '.' [0-9]+ ('.' [0-9]*)+      // 1.2.3
    | [0-9]+ '.'                           // 3.
    | [0-9]+ ('.' [0-9]+)? [eE] [+-]?      // 1e   2.5e+
    ;

// ---------- ERRORES: identificador mal formado (empieza con dígito) ----------
BAD_ID : [0-9]+ [a-zA-Z_] [a-zA-Z0-9_]*;   // 2cosas, 9var, 123abc

// ---------- Cadenas y caracteres ----------
STRING_LIT : '"' ( ~["\\\r\n] | '\\' . )* '"';
CHAR_LIT   : '\'' ( ~['\\\r\n] | '\\' . ) '\'';

// ---------- ERRORES: cadena sin cerrar ----------
UNCLOSED_STRING : '"' ( ~["\\\r\n] | '\\' . )*;

// ---------- Comentarios ----------
LINE_COMMENT  : '//' ~[\r\n]*              -> channel(HIDDEN);
BLOCK_COMMENT : '/*' .*? '*/'              -> channel(HIDDEN);

// ---------- Directivas de preprocesador ----------
PREPROCESSOR : '#' ~[\r\n]*;

// ---------- Operadores relacionales ----------
EQ  : '==';
NEQ : '!=';
LE  : '<=';
GE  : '>=';
LT  : '<';
GT  : '>';

// ---------- Operadores lógicos ----------
AND : '&&';
OR  : '||';
NOT : '!';

// ---------- Operadores aritméticos y asignación ----------
INC    : '++';
DEC    : '--';
PLUS_ASSIGN  : '+=';
MINUS_ASSIGN : '-=';
MULT_ASSIGN  : '*=';
DIV_ASSIGN   : '/=';
PLUS   : '+';
MINUS  : '-';
MULT   : '*';
DIV    : '/';
MOD    : '%';
ASSIGN : '=';

// ---------- Otros operadores ----------
SCOPE  : '::';
ARROW  : '->';
STREAM_OUT : '<<';
STREAM_IN  : '>>';

// ---------- Símbolos ----------
LPAREN : '(';
RPAREN : ')';
LBRACK : '[';
RBRACK : ']';
LBRACE : '{';
RBRACE : '}';
COMMA  : ',';
SEMI   : ';';
DOT    : '.';
COLON  : ':';

// ---------- Identificadores (después de las palabras clave) ----------
ID : [a-zA-Z_] [a-zA-Z0-9_]*;

// ---------- Espacios en blanco ----------
WS : [ \t\r\n]+ -> channel(HIDDEN);

// ---------- ERRORES: carácter no reconocido (SIEMPRE LA ÚLTIMA REGLA) ----------
ERROR_CHAR : . ;