// BERNAL MARTINEZ ANTONIO
package org.example;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.IntervalSet;
import java.util.*;

public class ErrorSintacticoListener extends BaseErrorListener {

    private final List<String> errores = new ArrayList<>();

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
        int tipo = actual.getType();
        int l = actual.getLine();
        int c = actual.getCharPositionInLine() + 1;

        // 1. Errores que ya vienen marcados por el lexer
        if (tipo == CppLexer.BAD_ID) {
            registrar(l, c, "Identificador invalido '" + actual.getText()
                    + "'. Un identificador debe comenzar con una letra o guion bajo.");
        } else if (tipo == CppLexer.BAD_NUMBER) {
            registrar(l, c, "Literal numerico invalido '" + actual.getText() + "'.");
        } else if (tipo == CppLexer.UNCLOSED_STRING) {
            registrar(l, c, "Cadena sin cerrar. Falta la comilla de cierre (\").");
        } else if (tipo == CppLexer.ERROR_CHAR) {
            registrar(l, c, "Caracter no reconocido '" + actual.getText() + "'.");

            // 2. Se esperaba un nombre y llego un literal (ej. string 123;)
        } else if (esperados.contains(CppLexer.ID) && !esperados.contains(CppLexer.INT_LIT)
                && (tipo == CppLexer.INT_LIT || tipo == CppLexer.FLOAT_LIT
                || tipo == CppLexer.STRING_LIT || tipo == CppLexer.CHAR_LIT
                || tipo == CppLexer.TRUE || tipo == CppLexer.FALSE)) {
            registrar(l, c, "Identificador invalido '" + actual.getText()
                    + "'. Un identificador debe comenzar con una letra o guion bajo.");

            // 3. Tipo no valido (ID seguido de otro ID, ej. flotante y;)
        } else if (previo != null && previo.getType() == CppLexer.ID
                && tipo == CppLexer.ID && esperados.contains(CppLexer.ASSIGN)) {
            registrar(previo.getLine(), previo.getCharPositionInLine() + 1,
                    "Tipo no valido '" + previo.getText() + "'. Los tipos validos son "
                            + "'int', 'float', 'double', 'char', 'bool' y 'string'.");

            // 4. Constante sin inicializar
        } else if (p.getContext() instanceof CppParser.DeclaracionConstanteContext
                && esperados.contains(CppLexer.ASSIGN)) {
            registrar(l, c, "Una constante debe inicializarse: const tipo NOMBRE = valor;");

            // 5. Falta punto y coma (se reporta al final de la linea anterior)
        } else if (esperados.contains(CppLexer.SEMI)) {
            if (previo != null) {
                l = previo.getLine();
                c = previo.getCharPositionInLine() + previo.getText().length() + 1;
            }
            registrar(l, c, "Se esperaba ';' al final de la declaracion.");

            // 6. Falta un valor o expresion (ej. int z = ;)
        } else if (esperados.contains(CppLexer.INT_LIT)) {
            registrar(l, c, "Se esperaba un valor o expresion pero se encontro '"
                    + actual.getText() + "'.");

            // 7. Cualquier otro error
        } else {
            registrar(l, c, "Token inesperado '" + actual.getText()
                    + "'. Se esperaba: " + esperados.toString(p.getVocabulary()));
        }
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