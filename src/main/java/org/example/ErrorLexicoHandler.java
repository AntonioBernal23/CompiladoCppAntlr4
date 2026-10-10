// Antonio Bernal Martinez
package org.example;

import org.antlr.v4.runtime.*;
import java.util.*;

public class ErrorLexicoHandler extends BaseErrorListener {

    private final List<String> errores = new ArrayList<>();
    private final Map<String, Integer> porTipo = new TreeMap<>();

    /** Indica si un tipo de token es un token de error. */
    public static boolean esTokenError(int tipo) {
        return tipo == CppLexer.ERROR_CHAR
                || tipo == CppLexer.BAD_ID
                || tipo == CppLexer.BAD_NUMBER
                || tipo == CppLexer.UNCLOSED_STRING;
    }

    /** Reporta un token de error con tipo, posicion y descripcion. */
    public void reportar(Token t) {
        String tipo;
        String desc;
        String lexema = t.getText();

        switch (t.getType()) {
            case CppLexer.ERROR_CHAR:
                tipo = "Caracter no reconocido";
                desc = "El simbolo '" + lexema + "' no pertenece al alfabeto del lenguaje.";
                break;
            case CppLexer.BAD_ID:
                tipo = "Identificador mal formado";
                desc = "Un identificador debe iniciar con letra o '_', no con digito.";
                break;
            case CppLexer.BAD_NUMBER:
                tipo = "Literal numerico incorrecto";
                desc = descripcionNumero(lexema);
                break;
            case CppLexer.UNCLOSED_STRING:
                tipo = "Cadena mal formada";
                desc = "Falta la comilla de cierre (\") antes del fin de la linea.";
                break;
            default:
                tipo = "Error lexico";
                desc = "Token invalido.";
        }
        registrar(tipo, t.getLine(), t.getCharPositionInLine() + 1, lexema, desc);
    }

    private String descripcionNumero(String s) {
        if (s.matches("[0-9]+\\.[0-9]+(\\.[0-9]*)+"))
            return "El numero tiene mas de un punto decimal.";
        if (s.matches("[0-9]+\\."))
            return "Despues del punto decimal debe haber al menos un digito.";
        return "El exponente esta incompleto: faltan digitos despues de 'e'/'E'.";
    }

    /** Errores que ANTLR detecte por su cuenta (red de seguridad). */
    @Override
    public void syntaxError(Recognizer<?, ?> r, Object sym, int linea, int col,
                            String msg, RecognitionException e) {
        registrar("Error de ANTLR", linea, col + 1, "?", msg);
    }

    private void registrar(String tipo, int linea, int col, String lexema, String desc) {
        porTipo.merge(tipo, 1, Integer::sum);
        errores.add(String.format(
                "[ERROR LEXICO #%d] %s%n    Posicion   : linea %d, columna %d%n"
                        + "    Lexema     : '%s'%n    Descripcion: %s",
                errores.size() + 1, tipo, linea, col, lexema.replace("\n", "\\n"), desc));
    }

    public boolean hayErrores() { return !errores.isEmpty(); }

    public void imprimirReporte() {
        System.out.println("\n=== REPORTE DE ERRORES LEXICOS ===");
        if (errores.isEmpty()) {
            System.out.println("Sin errores lexicos.");
            return;
        }
        errores.forEach(System.out::println);
        System.out.println("\n--- Total por tipo ---");
        porTipo.forEach((k, v) -> System.out.printf("%-28s %d%n", k, v));
        System.out.println("TOTAL DE ERRORES: " + errores.size());
    }
}