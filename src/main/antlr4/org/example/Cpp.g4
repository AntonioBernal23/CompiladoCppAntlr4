// BERNAL MARTINEZ ANTONIO
grammar Cpp;

/* =====================================================================
   REGLAS DEL PARSER (minusculas)
   ===================================================================== */

programa : ( clase | sentencia )* EOF ;

// ---------- CLASES Y METODOS ----------
clase : CLASS ID LBRACE miembro* RBRACE SEMI? ;

miembro
    : modificadorAcceso
    | declaracionMetodo
    | declaracionVariable
    | declaracionConstante
    | metodoSinTipo
    | metodoTipoInvalido
    ;

modificadorAcceso : ( PUBLIC | PRIVATE | PROTECTED ) COLON ;

// tipoRetorno nombre ( parametros ) { cuerpo }
// Produccion de error: si faltan los parentesis se reporta y se continua.
declaracionMetodo
    : tipoRetorno ID
      ( LPAREN parametros? RPAREN
      | { notifyErrorListeners("@@FALTAN_PARENTESIS"); }
      )
      bloque
    ;

// Produccion de error: metodo sin tipo de retorno (ej. calcular(int a) { })
metodoSinTipo
    : ID { notifyErrorListeners(_input.LT(-1), "@@SIN_TIPO_RETORNO", null); }
      LPAREN parametros? RPAREN bloque
    ;

// Produccion de error: tipo de retorno que es un literal (ej. 123 calcular() { })
metodoTipoInvalido
    : tokenTipoInvalido { notifyErrorListeners(_input.LT(-1), "@@TIPO_RETORNO_INVALIDO", null); }
      ID LPAREN parametros? RPAREN bloque
    ;

tokenTipoInvalido
    : INT_LIT | FLOAT_LIT | STRING_LIT | CHAR_LIT | TRUE | FALSE | BAD_ID | BAD_NUMBER
    ;

// Produccion de error: si falta la coma entre parametros se reporta y se continua.
parametros
    : parametro ( ( COMMA | { notifyErrorListeners("@@FALTA_COMA"); } ) parametro )*
    ;

parametro : tipoDato ID ;

tipoDato : tipo | ID ;

tipoRetorno : tipoDato | VOID ;

// ---------- SENTENCIAS ----------
sentencia
    : declaracionVariable
    | declaracionConstante
    | asignacion
    | sentenciaIncremento
    | sentenciaSalto
    | sentenciaReturn
    | estructuraControl
    | bloque
    | sentenciaExpresion
    ;

// ---------- Tipos ----------
tipo : INT | FLOAT | DOUBLE | CHAR | BOOL | STRING_T ;

// ---------- Declaracion de variables ----------
declaracionVariable : declaracionSimple SEMI ;
declaracionSimple   : tipo declarador (COMMA declarador)* ;
declarador          : ID (ASSIGN expresion)? ;

// ---------- Declaracion de constantes (siempre inicializadas) ----------
declaracionConstante : CONST tipo ID ASSIGN expresion SEMI ;

// ---------- Asignacion e incremento ----------
asignacion       : asignacionSimple SEMI ;
asignacionSimple : ID operadorAsignacion expresion ;

operadorAsignacion
    : ASSIGN | PLUS_ASSIGN | MINUS_ASSIGN | MULT_ASSIGN | DIV_ASSIGN
    ;

sentenciaIncremento : incremento SEMI ;
incremento : ID (INC | DEC) | (INC | DEC) ID ;

sentenciaSalto : (BREAK | CONTINUE) SEMI ;

sentenciaReturn : RETURN expresion? SEMI ;

// ---------- Expresion como sentencia ----------
sentenciaExpresion : expresion SEMI ;

// ---------- ESTRUCTURAS DE CONTROL ----------
estructuraControl : sentenciaIf | sentenciaWhile | sentenciaFor ;

bloque : LBRACE sentencia* RBRACE ;

sentenciaIf
    : IF LPAREN expresion RPAREN bloque ( ELSE ( bloque | sentenciaIf ) )?
    ;

sentenciaWhile
    : WHILE LPAREN expresion RPAREN bloque
    ;

sentenciaFor
    : FOR LPAREN inicializacion SEMI expresion SEMI actualizacion RPAREN bloque
    ;

inicializacion : declaracionSimple | asignacionSimple ;

actualizacion  : asignacionSimple | incremento | expresion ;

// ---------- EXPRESIONES (de menor a mayor precedencia) ----------
//   ||   <   &&   <   !   <   comparacion   <   + -   <   * / %
expresion : expresionLogica ;

expresionLogica : terminoLogico ( OR terminoLogico )* ;

terminoLogico : factorLogico ( AND factorLogico )* ;

factorLogico
    : NOT factorLogico
    | comparacion
    ;

comparacion : expresionAritmetica ( opRelacional expresionAritmetica )? ;

opRelacional : EQ | NEQ | LT | GT | LE | GE ;

expresionAritmetica : termino ( (PLUS | MINUS) termino )* ;

termino : factor ( (MULT | DIV | MOD) factor )* ;

factor
    : MINUS factor
    | INT_LIT
    | FLOAT_LIT
    | STRING_LIT
    | CHAR_LIT
    | TRUE
    | FALSE
    | conversion
    | llamada
    | ID
    | LPAREN expresion RPAREN
    ;

// ---------- Llamada a metodo:  sumar(1, x + 2) ----------
llamada    : ID LPAREN argumentos? RPAREN ;
argumentos : expresion ( COMMA expresion )* ;

// ---------- Conversion explicita:  static_cast<float>(x) ----------
conversion : STATIC_CAST LT tipo GT LPAREN expresion RPAREN ;

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
STATIC_CAST : 'static_cast';

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