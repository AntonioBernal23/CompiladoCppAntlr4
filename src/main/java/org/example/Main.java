// BERNAL MARTINEZ ANTONIO
package org.example;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.nio.file.*;

public class Main {
    static final String EQUIPO = "BERNAL MARTINEZ ANTONIO";

    public static void main(String[] args) throws Exception {
        System.out.println(EQUIPO);

        String archivo = args.length > 0 ? args[0] : "prueba_valida.cpp";
        boolean verbose = args.length > 1 && args[1].equals("-v");   // -v: imprime la bitacora
        System.out.println("\n=== ANALISIS: " + archivo + " ===");

        CppLexer lexer = new CppLexer(CharStreams.fromPath(Paths.get(archivo)));
        CppParser parser = new CppParser(new CommonTokenStream(lexer));

        ErrorSintacticoListener errores = new ErrorSintacticoListener();
        lexer.removeErrorListeners();
        lexer.addErrorListener(errores);
        parser.removeErrorListeners();
        parser.addErrorListener(errores);

        ParseTree arbol = parser.programa();

        if (errores.hayErrores()) {
            System.out.println("\n[FASE 1] Analisis lexico/sintactico: CON ERRORES "
                    + "(no se ejecuta el analisis semantico)");
            errores.imprimirReporte();
        } else {
            System.out.println("\n[FASE 1] Analisis lexico/sintactico: correcto");
            System.out.println("[FASE 2] Analisis semantico: validacion de tipos en expresiones");

            AnalizadorSemantico semantico = new AnalizadorSemantico(parser);
            try {
                ParseTreeWalker.DEFAULT.walk(semantico, arbol);
            } catch (RuntimeException ex) {
                System.out.println("\nError: Excepcion lanzada durante la evaluacion de expresiones: "
                        + ex);
            }

            if (verbose) semantico.imprimirBitacora();
            semantico.imprimirEvaluaciones();
            semantico.imprimirTabla();
            semantico.imprimirReporte();
        }

        System.out.println("\n" + EQUIPO);
    }
}