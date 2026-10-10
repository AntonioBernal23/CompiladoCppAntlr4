// BERNAL MARTINEZ ANTONIO
package org.example;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.nio.file.*;

public class Main {
    static final String EQUIPO = "BERNAL MARTINEZ ANTONIO";

    public static void main(String[] args) throws Exception {
        System.out.println(EQUIPO);

        String archivo = args.length > 0 ? args[0] : "prueba.cpp";
        System.out.println("\n=== ANALISIS SINTACTICO: " + archivo + " ===");

        CppLexer lexer = new CppLexer(CharStreams.fromPath(Paths.get(archivo)));
        CppParser parser = new CppParser(new CommonTokenStream(lexer));

        ErrorSintacticoListener errores = new ErrorSintacticoListener();
        lexer.removeErrorListeners();
        lexer.addErrorListener(errores);
        parser.removeErrorListeners();
        parser.addErrorListener(errores);

        ParseTree arbol = parser.programa();

        if (!errores.hayErrores()) {
            System.out.println("\n--- Sentencias reconocidas (con sangria por anidamiento) ---");
            ParseTreeWalker.DEFAULT.walk(new ReporteListener(parser), arbol);
        }
        errores.imprimirReporte();

        System.out.println("\n" + EQUIPO);
    }
}