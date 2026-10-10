// BERNAL MARTINEZ ANTONIO
package org.example;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.IntervalSet;
import java.util.*;

public class ErrorSintacticoListener extends BaseErrorListener {

    private final List<String> errores = new ArrayList<>();

    // ---------- Clasificacion de operadores ----------
    private static boolean esAritmetico(int t) {
        return t == CppLexer.PLUS || t == CppLexer.MINUS || t == CppLexer.MULT
                || t == CppLexer.DIV  || t == CppLexer.MOD;
    }
    private static boolean esLogico(int t) {
        return t == CppLexer.AND || t == CppLexer.OR || t == CppLexer.NOT;
    }
    private static boolean esRelacional(int t) {
        return t == CppLexer.EQ || t == CppLexer.NEQ || t == CppLexer.LT
                || t == CppLexer.GT || t == CppLexer.LE  || t == CppLexer.GE;
    }
    private static boolean esOperador(int t) {
        return esAritmetico(t) || esLogico(t) || esRelacional(t);
    }

    // ---------- Contexto: en que estructura de control ocurre el error ----------
    private static String nombreEstructura(RuleContext c) {
        if (c instanceof CppParser.SentenciaIfContext)    return "if";
        if (c instanceof CppParser.SentenciaWhileContext) return "while";
        if (c instanceof CppParser.SentenciaForContext)   return "for";
        return null;
    }

    /** Estructura que contiene este contexto (incluyendo su bloque). */
    private static String estructuraDe(RuleContext ctx) {
        for (RuleContext c = ctx; c != null; c = c.parent) {
            String n = nombreEstructura(c);
            if (n != null) return n;
        }
        return null;
    }

    /** Estructura solo si el error esta en su encabezado ( ... ), no dentro del bloque. */
    private static String encabezadoDe(RuleContext ctx) {
        for (RuleContext c = ctx; c != null; c = c.parent) {
            if (c instanceof CppParser.BloqueContext) return null;
            String n = nombreEstructura(c);
            if (n != null) return n;
        }
        return null;
    }

    private static String prefijo(String enc) {
        if (enc == null) return "";
        if (enc.equals("for")) return "Componente invalido en la estructura 'for'. ";
        return "Condicion invalida en la estructura '" + enc + "'. ";
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPos, String msg, RecognitionException e) {

        // Errores que no vienen del parser (lexer)
        if (!(recognizer instanceof Parser) || !(offendingSymbol instanceof Token)) {
            registrar(line, charPos + 1, "Error lexico: " + msg);
            return;
        }

        Parser p = (Parser) recognizer;
        Token actual = (Token) offendingSymbol;
        Token previo = p.getTokenStream().LT(-1);
        IntervalSet esperados = p.getExpectedTokens();
        RuleContext ctx = p.getContext();
        String enc = encabezadoDe(ctx);
        int tipo = actual.getType();
        int l = actual.getLine();
        int c = actual.getCharPositionInLine() + 1;

        // 1. Errores que ya vienen marcados por el lexer
        if (tipo == CppLexer.BAD_ID) {
            registrar(l, c, "Identificador invalido '" + actual.getText()
                    + "'. Un identificador debe comenzar con una letra o guion bajo.");
            return;
        }
        if (tipo == CppLexer.BAD_NUMBER) {
            registrar(l, c, "Literal numerico invalido '" + actual.getText() + "'.");
            return;
        }
        if (tipo == CppLexer.UNCLOSED_STRING) {
            registrar(l, c, "Cadena sin cerrar. Falta la comilla de cierre (\").");
            return;
        }
        if (tipo == CppLexer.ERROR_CHAR) {
            registrar(l, c, "Caracter no reconocido '" + actual.getText() + "'.");
            return;
        }

        // 2. Tipo no valido: ID seguido de otro ID al inicio de una sentencia (flotante y;)
        if (e instanceof NoViableAltException) {
            Token inicio = ((NoViableAltException) e).getStartToken();
            if (inicio.getType() == CppLexer.ID && tipo == CppLexer.ID
                    && inicio.getTokenIndex() != actual.getTokenIndex()) {
                registrar(inicio.getLine(), inicio.getCharPositionInLine() + 1,
                        "Tipo no valido '" + inicio.getText() + "'. Los tipos validos son "
                                + "'int', 'float', 'double', 'char', 'bool' y 'string'.");
                return;
            }
        }

        // 3. 'else' sin 'if'
        if (tipo == CppLexer.ELSE) {
            registrar(l, c, "'else' sin 'if' correspondiente.");
            return;
        }

        // 4. Parentesis
        if (tipo == CppLexer.RPAREN && previo != null && previo.getType() == CppLexer.LPAREN) {
            registrar(l, c, prefijo(enc) + "Parentesis vacios: falta la expresion entre '(' y ')'.");
            return;
        }
        if (tipo == CppLexer.RPAREN && !esperados.contains(CppLexer.INT_LIT)) {
            registrar(l, c, "Parentesis desbalanceados: ')' sin '(' correspondiente"
                    + (enc != null ? " en la estructura '" + enc + "'" : "") + ".");
            return;
        }
        if (esperados.contains(CppLexer.RPAREN) && tipo != CppLexer.RPAREN) {
            int[] pos = finDe(previo, actual);
            registrar(pos[0], pos[1], "Parentesis desbalanceados"
                    + (enc != null ? " en la estructura '" + enc + "'" : "")
                    + ": falta cerrar con ')'.");
            return;
        }
        // Falta '(' despues de if / while / for
        if (esperados.contains(CppLexer.LPAREN) && !esperados.contains(CppLexer.INT_LIT)
                && tipo != CppLexer.LPAREN) {
            int[] pos = finDe(previo, actual);
            String kw = previo != null ? previo.getText() : "la estructura";
            registrar(pos[0], pos[1], "Parentesis faltante: se esperaba '(' despues de '" + kw + "'.");
            return;
        }

        // 5. Llaves y bloques
        if (esperados.contains(CppLexer.LBRACE) && tipo != CppLexer.LBRACE) {
            String nombre = (previo != null && previo.getType() == CppLexer.ELSE)
                    ? "else" : estructuraDe(ctx);
            int[] pos = finDe(previo, actual);
            registrar(pos[0], pos[1], "Bloque de sentencias faltante"
                    + (nombre != null ? " en la estructura '" + nombre + "'" : "")
                    + ": se esperaba '{'.");
            return;
        }
        if (tipo == CppLexer.RBRACE && !esperados.contains(CppLexer.RBRACE)) {
            registrar(l, c, "Llave '}' sin '{' correspondiente.");
            return;
        }
        if (tipo == Token.EOF && esperados.contains(CppLexer.RBRACE)) {
            int[] pos = finDe(previo, actual);
            registrar(pos[0], pos[1], "Falta '}' para cerrar el bloque"
                    + (estructuraDe(ctx) != null ? " de la estructura '" + estructuraDe(ctx) + "'" : "") + ".");
            return;
        }

        // 6. Operadores sin operando
        if (esperados.contains(CppLexer.INT_LIT)) {
            if (previo != null && esRelacional(previo.getType())) {
                registrar(previo.getLine(), previo.getCharPositionInLine() + 1,
                        prefijo(enc) + "Comparacion invalida: despues de '" + previo.getText()
                                + "' falta una expresion aritmetica.");
                return;
            }
            if (previo != null && (esAritmetico(previo.getType()) || esLogico(previo.getType()))) {
                registrar(previo.getLine(), previo.getCharPositionInLine() + 1,
                        prefijo(enc) + "Operador sin termino: despues de '" + previo.getText()
                                + "' se esperaba un operando (identificador, numero o '(').");
                return;
            }
            if (esOperador(tipo)) {
                registrar(l, c, prefijo(enc) + "Operador sin termino: '" + actual.getText()
                        + "' no tiene operando a su izquierda.");
                return;
            }
        }

        // 7. Constante sin inicializar
        if (ctx instanceof CppParser.DeclaracionConstanteContext
                && esperados.contains(CppLexer.ASSIGN)) {
            registrar(l, c, "Una constante debe inicializarse: const tipo NOMBRE = valor;");
            return;
        }

        // 8. Se esperaba un nombre y llego un literal (ej. string 123;)
        if (esperados.contains(CppLexer.ID) && !esperados.contains(CppLexer.INT_LIT)
                && (tipo == CppLexer.INT_LIT || tipo == CppLexer.FLOAT_LIT
                || tipo == CppLexer.STRING_LIT || tipo == CppLexer.CHAR_LIT
                || tipo == CppLexer.TRUE || tipo == CppLexer.FALSE)) {
            registrar(l, c, "Identificador invalido '" + actual.getText()
                    + "'. Un identificador debe comenzar con una letra o guion bajo.");
            return;
        }

        // 9. Comparaciones encadenadas (a < b < c)
        if (esRelacional(tipo) && esperados.contains(CppLexer.SEMI)) {
            registrar(l, c, "Comparacion invalida: no se pueden encadenar comparaciones ('"
                    + actual.getText() + "').");
            return;
        }

        // 10. Falta punto y coma (se reporta al final de la linea anterior)
        if (esperados.contains(CppLexer.SEMI)) {
            int[] pos = finDe(previo, actual);
            if ("for".equals(enc)) {
                registrar(pos[0], pos[1], prefijo(enc)
                        + "Se esperaba ';' para separar los componentes del encabezado.");
            } else {
                registrar(pos[0], pos[1], "Se esperaba ';' al final de la declaracion.");
            }
            return;
        }

        // 11. Falta un valor o expresion (ej. int z = ;)
        if (esperados.contains(CppLexer.INT_LIT)) {
            registrar(l, c, prefijo(enc) + "Se esperaba un valor o expresion pero se encontro '"
                    + actual.getText() + "'.");
            return;
        }

        // 12. Cualquier otro error
        registrar(l, c, "Token inesperado '" + actual.getText()
                + "'. Se esperaba: " + esperados.toString(p.getVocabulary()));
    }

    /** Posicion justo despues del token previo (o del actual si no hay previo). */
    private int[] finDe(Token previo, Token actual) {
        if (previo == null) {
            return new int[]{actual.getLine(), actual.getCharPositionInLine() + 1};
        }
        return new int[]{previo.getLine(),
                previo.getCharPositionInLine() + previo.getText().length() + 1};
    }

    private void registrar(int linea, int col, String mensaje) {
        errores.add(String.format("[ERROR SINTACTICO #%d] Linea %d, columna %d%n    %s",
                errores.size() + 1, linea, col, mensaje));
    }

    public boolean hayErrores() { return !errores.isEmpty(); }

    public void imprimirReporte() {
        System.out.println("\n=== REPORTE DE ERRORES SINTACTICOS ===");
        if (errores.isEmpty()) {
            System.out.println("Sin errores sintacticos.");
            return;
        }
        errores.forEach(System.out::println);
        System.out.println("\nTOTAL DE ERRORES: " + errores.size());
    }
}