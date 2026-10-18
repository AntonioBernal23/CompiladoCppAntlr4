// BERNAL MARTINEZ ANTONIO
package org.example;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class AnalizadorSemantico extends CppBaseListener {

    private static final String ERROR = "<error>";   // tipo ya reportado: evita errores en cascada
    private static final Set<String> PRIMITIVOS =
            Set.of("int", "float", "double", "char", "bool", "string");

    private static class Diag {
        final int linea, col;
        final String texto;
        Diag(int linea, int col, String texto) {
            this.linea = linea;
            this.col = col;
            this.texto = texto;
        }
    }

    private final Parser parser;
    private final TablaSimbolos tabla = new TablaSimbolos();
    private final List<Diag> errores = new ArrayList<>();
    private final List<Diag> advertencias = new ArrayList<>();
    private Simbolo metodoActual = null;
    private int contAmbitos = 0;

    public AnalizadorSemantico(Parser parser) {
        this.parser = parser;
    }

    // =====================================================================
    //  UTILIDADES
    // =====================================================================
    private String txt(ParserRuleContext c) {
        return parser.getTokenStream().getText(c);
    }

    private void error(Token t, String msg) {
        errores.add(new Diag(t.getLine(), t.getCharPositionInLine() + 1, msg));
    }

    private void error(ParserRuleContext c, String msg) {
        error(c.getStart(), msg);
    }

    private void advertencia(Token t, String msg) {
        advertencias.add(new Diag(t.getLine(), t.getCharPositionInLine() + 1, msg));
    }

    private static boolean esReal(String t) {
        return t.equals("float") || t.equals("double");
    }

    private static boolean esNumerico(String t) {
        return t.equals("int") || t.equals("char") || esReal(t);
    }

    /** Tipos aceptados en una condicion (bool o numerico, como en C++). */
    private static boolean esCondicion(String t) {
        return t.equals("bool") || esNumerico(t);
    }

    /**
     * Compatibilidad de tipos para asignaciones, inicializaciones, argumentos y return.
     * Reglas estrictas (como el ejemplo del enunciado): int -> float es ERROR.
     * Unica flexibilidad: float <-> double (misma familia real).
     */
    private static boolean compatible(String destino, String origen) {
        if (ERROR.equals(destino) || ERROR.equals(origen)) return true;   // ya reportado
        if (destino.equals(origen)) return true;
        return esReal(destino) && esReal(origen);
    }

    private TerminalNode idDe(ParserRuleContext ctx) {
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree c = ctx.getChild(i);
            if (c instanceof TerminalNode
                    && ((TerminalNode) c).getSymbol().getType() == CppLexer.ID) {
                return (TerminalNode) c;
            }
        }
        return null;
    }

    private int indiceEn(ParseTree padre, ParseTree hijo) {
        for (int i = 0; i < padre.getChildCount(); i++) {
            if (padre.getChild(i) == hijo) return i;
        }
        return -1;
    }

    /** Inserta un simbolo en el ambito actual; reporta error si ya existe. */
    private Simbolo declarar(String nombre, String tipo, Simbolo.Categoria cat,
                             Token t, String valor, boolean init) {
        TablaSimbolos.Ambito actual = tabla.ambitoActual();
        Simbolo previo = tabla.buscarLocal(nombre);
        if (previo != null) {
            error(t, "Identificador '" + nombre + "' ya declarado en el ambito '" + actual.nombre
                    + "' (primera declaracion en la linea " + previo.linea + ").");
            return null;
        }
        Simbolo s = new Simbolo(nombre, tipo, cat, actual.alcance, actual.nombre,
                t.getLine(), t.getCharPositionInLine() + 1);
        s.valor = valor;
        s.inicializado = init;
        tabla.insertarSimbolo(s);
        return s;
    }

    /** Verifica que un tipo exista (primitivo, void si se permite, o clase declarada). */
    private boolean tipoValido(String t, Token pos, boolean permitirVoid) {
        if (PRIMITIVOS.contains(t)) return true;
        if (permitirVoid && t.equals("void")) return true;
        Simbolo s = tabla.buscarSimbolo(t);
        if (s != null && s.categoria == Simbolo.Categoria.CLASE) return true;
        error(pos, "Tipo '" + t + "' no declarado. Use int, float, double, char, bool, string"
                + (permitirVoid ? ", void" : "") + " o el nombre de una clase declarada.");
        return false;
    }

    // =====================================================================
    //  AMBITOS: clase, metodo, bloques, for
    // =====================================================================
    @Override
    public void enterClase(CppParser.ClaseContext ctx) {
        declarar(ctx.ID().getText(), "class", Simbolo.Categoria.CLASE,
                ctx.ID().getSymbol(), null, true);
        tabla.entrarAmbito("clase " + ctx.ID().getText(), "clase");
    }

    @Override
    public void exitClase(CppParser.ClaseContext ctx) {
        tabla.salirAmbito();
    }

    @Override
    public void enterDeclaracionMetodo(CppParser.DeclaracionMetodoContext ctx) {
        String nombre = ctx.ID().getText();
        Token tNombre = ctx.ID().getSymbol();

        String ret = ctx.tipoRetorno().getText();
        if (!tipoValido(ret, ctx.tipoRetorno().getStart(), true)) ret = ERROR;

        // Parametros: se validan los tipos y se arma la firma
        List<String> tipos = new ArrayList<>();
        List<CppParser.ParametroContext> params = new ArrayList<>();
        if (ctx.parametros() != null) {
            for (CppParser.ParametroContext p : ctx.parametros().parametro()) {
                String tp = p.tipoDato().getText();
                if (!tipoValido(tp, p.tipoDato().getStart(), false)) tp = ERROR;
                tipos.add(tp);
                params.add(p);
            }
        }

        Simbolo m = declarar(nombre, ret, Simbolo.Categoria.METODO, tNombre, null, true);
        if (m == null) {   // metodo duplicado: se analiza el cuerpo igual, sin insertarlo
            m = new Simbolo(nombre, ret, Simbolo.Categoria.METODO, "local", "?",
                    tNombre.getLine(), tNombre.getCharPositionInLine() + 1);
        }
        m.parametros.addAll(tipos);
        metodoActual = m;

        tabla.entrarAmbito("metodo " + nombre, "local");
        for (int i = 0; i < params.size(); i++) {
            CppParser.ParametroContext p = params.get(i);
            declarar(p.ID().getText(), tipos.get(i), Simbolo.Categoria.PARAMETRO,
                    p.ID().getSymbol(), null, true);
        }
    }

    @Override
    public void exitDeclaracionMetodo(CppParser.DeclaracionMetodoContext ctx) {
        tabla.salirAmbito();
        metodoActual = null;
    }

    private String nombreBloque(CppParser.BloqueContext ctx) {
        ParseTree padre = ctx.getParent();
        String base = "bloque";
        if (padre instanceof CppParser.SentenciaIfContext) {
            int i = indiceEn(padre, ctx);
            base = (i > 0 && padre.getChild(i - 1).getText().equals("else")) ? "else" : "if";
        } else if (padre instanceof CppParser.SentenciaWhileContext) {
            base = "while";
        } else if (padre instanceof CppParser.SentenciaForContext) {
            base = "for-cuerpo";
        }
        return base + "#" + (++contAmbitos);
    }

    @Override
    public void enterBloque(CppParser.BloqueContext ctx) {
        // El cuerpo de un metodo comparte el ambito de sus parametros
        if (ctx.getParent() instanceof CppParser.DeclaracionMetodoContext) return;
        tabla.entrarAmbito(nombreBloque(ctx), "local");
    }

    @Override
    public void exitBloque(CppParser.BloqueContext ctx) {
        if (ctx.getParent() instanceof CppParser.DeclaracionMetodoContext) return;
        tabla.salirAmbito();
    }

    @Override
    public void enterSentenciaFor(CppParser.SentenciaForContext ctx) {
        tabla.entrarAmbito("for#" + (++contAmbitos), "local");   // la variable del for vive aqui
    }

    @Override
    public void exitSentenciaFor(CppParser.SentenciaForContext ctx) {
        tabla.salirAmbito();
    }

    // =====================================================================
    //  DECLARACIONES
    // =====================================================================
    @Override
    public void enterDeclaracionSimple(CppParser.DeclaracionSimpleContext ctx) {
        String tipo = ctx.tipo().getText();
        for (CppParser.DeclaradorContext d : ctx.declarador()) {
            String tInit = null;
            String valor = null;
            if (d.expresion() != null) {
                tInit = tipoExpr(d.expresion());   // antes de insertar: 'int x = x;' es error
                valor = txt(d.expresion());
            }
            Token t = d.ID().getSymbol();
            Simbolo s = declarar(d.ID().getText(), tipo, Simbolo.Categoria.VARIABLE,
                    t, valor, d.expresion() != null);
            if (s != null && tInit != null && !compatible(tipo, tInit)) {
                error(t, "Tipo de dato incompatible en la asignacion a '" + s.nombre
                        + "': no se puede asignar '" + tInit + "' a '" + tipo + "'.");
            }
        }
    }

    @Override
    public void enterDeclaracionConstante(CppParser.DeclaracionConstanteContext ctx) {
        String tipo = ctx.tipo().getText();
        String tInit = tipoExpr(ctx.expresion());
        Token t = ctx.ID().getSymbol();
        Simbolo s = declarar(ctx.ID().getText(), tipo, Simbolo.Categoria.CONSTANTE,
                t, txt(ctx.expresion()), true);
        if (s != null && !compatible(tipo, tInit)) {
            error(t, "Tipo de dato incompatible en la asignacion a la constante '" + s.nombre
                    + "': no se puede asignar '" + tInit + "' a '" + tipo + "'.");
        }
    }

    // =====================================================================
    //  ASIGNACIONES E INCREMENTOS
    // =====================================================================
    @Override
    public void enterAsignacionSimple(CppParser.AsignacionSimpleContext ctx) {
        Token t = ctx.ID().getSymbol();
        String nombre = ctx.ID().getText();
        String op = ctx.operadorAsignacion().getText();
        String tRhs = tipoExpr(ctx.expresion());

        Simbolo s = tabla.buscarSimbolo(nombre);
        if (s == null) {
            error(t, "Identificador '" + nombre + "' no declarado.");
            return;
        }
        if (s.categoria == Simbolo.Categoria.METODO || s.categoria == Simbolo.Categoria.CLASE) {
            error(t, "'" + nombre + "' es un " + s.categoria.name().toLowerCase()
                    + " y no puede recibir asignaciones.");
            return;
        }
        if (s.categoria == Simbolo.Categoria.CONSTANTE) {
            error(t, "No se puede modificar la constante '" + nombre + "'.");
            return;
        }

        String esperado;
        if (op.equals("=")) {
            esperado = tRhs;
        } else {
            if (!s.inicializado) {
                advertencia(t, "La variable '" + nombre + "' se usa sin haber sido inicializada.");
            }
            esperado = tipoBinario(op.substring(0, 1), s.tipo, tRhs, t);
        }

        if (!compatible(s.tipo, esperado)) {
            error(t, "Tipo de dato incompatible en la asignacion a '" + nombre
                    + "': no se puede asignar '" + esperado + "' a '" + s.tipo + "'.");
        } else {
            s.inicializado = true;
            if (op.equals("=")) s.valor = txt(ctx.expresion());
        }
    }

    @Override
    public void enterIncremento(CppParser.IncrementoContext ctx) {
        TerminalNode id = idDe(ctx);
        if (id == null) return;
        Token t = id.getSymbol();
        String nombre = id.getText();
        String op = ctx.getText().contains("++") ? "++" : "--";

        Simbolo s = tabla.buscarSimbolo(nombre);
        if (s == null) {
            error(t, "Identificador '" + nombre + "' no declarado.");
            return;
        }
        if (s.categoria == Simbolo.Categoria.CONSTANTE) {
            error(t, "No se puede modificar la constante '" + nombre + "'.");
            return;
        }
        if (s.categoria == Simbolo.Categoria.METODO || s.categoria == Simbolo.Categoria.CLASE) {
            error(t, "'" + nombre + "' no es una variable.");
            return;
        }
        if (!esNumerico(s.tipo)) {
            error(t, "El operador '" + op + "' solo se aplica a tipos numericos y '"
                    + nombre + "' es de tipo '" + s.tipo + "'.");
        }
    }

    // =====================================================================
    //  RETURN, EXPRESIONES SUELTAS Y CONDICIONES
    // =====================================================================
    @Override
    public void enterSentenciaReturn(CppParser.SentenciaReturnContext ctx) {
        String tExpr = ctx.expresion() != null ? tipoExpr(ctx.expresion()) : "void";
        if (metodoActual == null) {
            error(ctx, "'return' fuera de un metodo.");
            return;
        }
        String esperado = metodoActual.tipo;
        if (esperado.equals("void")) {
            if (ctx.expresion() != null) {
                error(ctx, "El metodo '" + metodoActual.nombre
                        + "' es void y no puede retornar un valor.");
            }
        } else if (ctx.expresion() == null) {
            error(ctx, "El metodo '" + metodoActual.nombre
                    + "' debe retornar un valor de tipo '" + esperado + "'.");
        } else if (!compatible(esperado, tExpr)) {
            error(ctx, "Tipo de retorno incompatible en '" + metodoActual.nombre
                    + "': se esperaba '" + esperado + "' y se encontro '" + tExpr + "'.");
        }
    }

    @Override
    public void enterSentenciaExpresion(CppParser.SentenciaExpresionContext ctx) {
        tipoExpr(ctx.expresion());
    }

    private void checarCondicion(CppParser.ExpresionContext e, String estructura) {
        String t = tipoExpr(e);
        if (!ERROR.equals(t) && !esCondicion(t)) {
            error(e.getStart(), "La condicion de '" + estructura
                    + "' debe ser booleana o numerica, se encontro '" + t + "'.");
        }
    }

    @Override
    public void enterSentenciaIf(CppParser.SentenciaIfContext ctx) {
        checarCondicion(ctx.expresion(), "if");
    }

    @Override
    public void enterSentenciaWhile(CppParser.SentenciaWhileContext ctx) {
        checarCondicion(ctx.expresion(), "while");
    }

    /** La condicion del for se evalua cuando la inicializacion ya declaro su variable. */
    @Override
    public void exitInicializacion(CppParser.InicializacionContext ctx) {
        if (ctx.getParent() instanceof CppParser.SentenciaForContext) {
            checarCondicion(((CppParser.SentenciaForContext) ctx.getParent()).expresion(), "for");
        }
    }

    @Override
    public void enterActualizacion(CppParser.ActualizacionContext ctx) {
        if (ctx.expresion() != null) tipoExpr(ctx.expresion());
    }

    // =====================================================================
    //  TIPO DE UNA EXPRESION (se calcula una sola vez por expresion)
    // =====================================================================
    private String tipoExpr(ParseTree n) {
        if (n instanceof CppParser.ExpresionContext) return tipoExpr(n.getChild(0));
        if (n instanceof CppParser.ExpresionLogicaContext) return tipoLogico(n, "||");
        if (n instanceof CppParser.TerminoLogicoContext) return tipoLogico(n, "&&");
        if (n instanceof CppParser.FactorLogicoContext) return tipoFactorLogico(n);
        if (n instanceof CppParser.ComparacionContext) return tipoComparacion(n);
        if (n instanceof CppParser.ExpresionAritmeticaContext
                || n instanceof CppParser.TerminoContext) return tipoAritmetico(n);
        if (n instanceof CppParser.FactorContext) return tipoFactor((CppParser.FactorContext) n);
        return ERROR;
    }

    /** operando (op operando)*  con || o && */
    private String tipoLogico(ParseTree n, String op) {
        if (n.getChildCount() == 1) return tipoExpr(n.getChild(0));
        Token opTok = ((TerminalNode) n.getChild(1)).getSymbol();
        for (int i = 0; i < n.getChildCount(); i += 2) {
            String t = tipoExpr(n.getChild(i));
            if (!ERROR.equals(t) && !esCondicion(t)) {
                error(opTok, "El operador '" + op + "' requiere operandos booleanos o numericos, "
                        + "se encontro '" + t + "'.");
            }
        }
        return "bool";
    }

    private String tipoFactorLogico(ParseTree n) {
        if (n.getChildCount() == 2 && n.getChild(0) instanceof TerminalNode) {   // ! factorLogico
            Token op = ((TerminalNode) n.getChild(0)).getSymbol();
            String t = tipoExpr(n.getChild(1));
            if (!ERROR.equals(t) && !esCondicion(t)) {
                error(op, "El operador '!' requiere un operando booleano o numerico, "
                        + "se encontro '" + t + "'.");
            }
            return "bool";
        }
        return tipoExpr(n.getChild(0));
    }

    private String tipoComparacion(ParseTree n) {
        if (n.getChildCount() == 1) return tipoExpr(n.getChild(0));
        String l = tipoExpr(n.getChild(0));
        String r = tipoExpr(n.getChild(2));
        String op = n.getChild(1).getText();
        Token opTok = ((ParserRuleContext) n.getChild(1)).getStart();
        if (ERROR.equals(l) || ERROR.equals(r)) return "bool";
        boolean ok;
        if (op.equals("==") || op.equals("!=")) {
            ok = l.equals(r) || (esNumerico(l) && esNumerico(r));
        } else {
            ok = esNumerico(l) && esNumerico(r);
        }
        if (!ok) {
            error(opTok, "Operandos incompatibles en la comparacion '" + op + "': '"
                    + l + "' y '" + r + "'.");
        }
        return "bool";
    }

    /** operando (op operando)*  con + - o * / % */
    private String tipoAritmetico(ParseTree n) {
        String acc = tipoExpr(n.getChild(0));
        for (int i = 1; i + 1 < n.getChildCount(); i += 2) {
            Token op = ((TerminalNode) n.getChild(i)).getSymbol();
            ParseTree der = n.getChild(i + 1);
            String tr = tipoExpr(der);
            if ((op.getType() == CppLexer.DIV || op.getType() == CppLexer.MOD)
                    && der.getText().matches("0+(\\.0+)?")) {
                error(op, "Division entre cero.");
            }
            acc = tipoBinario(op.getText(), acc, tr, op);
        }
        return acc;
    }

    private String tipoBinario(String op, String l, String r, Token t) {
        if (ERROR.equals(l) || ERROR.equals(r)) return ERROR;
        if (op.equals("+") && l.equals("string") && r.equals("string")) return "string";
        if (esNumerico(l) && esNumerico(r)) {
            if (op.equals("%") && (esReal(l) || esReal(r))) {
                error(t, "El operador '%' solo se aplica a operandos enteros, se encontro '"
                        + l + "' y '" + r + "'.");
                return ERROR;
            }
            if (l.equals("double") || r.equals("double")) return "double";
            if (l.equals("float") || r.equals("float")) return "float";
            return "int";
        }
        error(t, "Operacion '" + op + "' no valida entre operandos de tipo '" + l + "' y '" + r + "'.");
        return ERROR;
    }

    private String tipoFactor(CppParser.FactorContext f) {
        int n = f.getChildCount();
        ParseTree c0 = f.getChild(0);

        if (n == 2) {   // - factor
            Token op = ((TerminalNode) c0).getSymbol();
            String t = tipoExpr(f.getChild(1));
            if (ERROR.equals(t)) return ERROR;
            if (!esNumerico(t)) {
                error(op, "El operador '-' unario solo se aplica a tipos numericos, se encontro '"
                        + t + "'.");
                return ERROR;
            }
            return t.equals("char") ? "int" : t;
        }
        if (n == 3) return tipoExpr(f.getChild(1));   // ( expresion )
        if (c0 instanceof CppParser.LlamadaContext) return tipoLlamada((CppParser.LlamadaContext) c0);

        Token tk = ((TerminalNode) c0).getSymbol();
        switch (tk.getType()) {
            case CppLexer.INT_LIT:    return "int";
            case CppLexer.FLOAT_LIT:  return "float";
            case CppLexer.STRING_LIT: return "string";
            case CppLexer.CHAR_LIT:   return "char";
            case CppLexer.TRUE:
            case CppLexer.FALSE:      return "bool";
            case CppLexer.ID:         return tipoIdentificador(tk);
            default:                  return ERROR;
        }
    }

    private String tipoIdentificador(Token t) {
        String nombre = t.getText();
        Simbolo s = tabla.buscarSimbolo(nombre);
        if (s == null) {
            error(t, "Identificador '" + nombre + "' no declarado.");
            return ERROR;
        }
        if (s.categoria == Simbolo.Categoria.METODO) {
            error(t, "'" + nombre + "' es un metodo; use '" + nombre + "(...)' para llamarlo.");
            return ERROR;
        }
        if (s.categoria == Simbolo.Categoria.CLASE) {
            error(t, "'" + nombre + "' es una clase, no una variable.");
            return ERROR;
        }
        if (!s.inicializado) {
            advertencia(t, "La variable '" + nombre + "' se usa sin haber sido inicializada.");
        }
        return s.tipo;
    }

    private String tipoLlamada(CppParser.LlamadaContext c) {
        Token t = ((TerminalNode) c.getChild(0)).getSymbol();
        String nombre = t.getText();

        List<CppParser.ExpresionContext> args = new ArrayList<>();
        if (c.argumentos() != null) args.addAll(c.argumentos().expresion());
        List<String> tiposArgs = new ArrayList<>();
        for (CppParser.ExpresionContext e : args) tiposArgs.add(tipoExpr(e));

        Simbolo s = tabla.buscarSimbolo(nombre);
        if (s == null) {
            error(t, "Metodo '" + nombre + "' no declarado.");
            return ERROR;
        }
        if (s.categoria != Simbolo.Categoria.METODO) {
            error(t, "'" + nombre + "' no es un metodo y no puede ser llamado.");
            return ERROR;
        }
        if (args.size() != s.parametros.size()) {
            error(t, "El metodo '" + nombre + "' espera " + s.parametros.size()
                    + " argumento(s) y recibio " + args.size() + ".");
            return s.tipo;
        }
        for (int i = 0; i < args.size(); i++) {
            if (!compatible(s.parametros.get(i), tiposArgs.get(i))) {
                error(args.get(i).getStart(), "Argumento " + (i + 1) + " de '" + nombre
                        + "' incompatible: se esperaba '" + s.parametros.get(i)
                        + "' y se recibio '" + tiposArgs.get(i) + "'.");
            }
        }
        return s.tipo;
    }

    // =====================================================================
    //  REPORTES
    // =====================================================================
    public boolean hayErrores() { return !errores.isEmpty(); }

    public void imprimirBitacora() {
        System.out.println("\n=== BITACORA DE OPERACIONES DE LA TABLA DE SIMBOLOS ===");
        int n = 1;
        for (String linea : tabla.bitacora()) {
            System.out.printf("%3d. %s%n", n++, linea);
        }
    }

    public void imprimirTabla() {
        System.out.println("\n=== TABLA DE SIMBOLOS ===");
        System.out.printf("%-24s %-12s %-10s %-9s %-8s %-18s %s%n",
                "AMBITO", "NOMBRE", "CATEGORIA", "TIPO", "ALCANCE", "VALOR", "LINEA");
        System.out.println("-".repeat(92));
        int total = 0;
        for (TablaSimbolos.Ambito a : tabla.historial()) {
            for (Simbolo s : a.simbolos.values()) {
                System.out.printf("%-24s %-12s %-10s %-9s %-8s %-18.18s %d%n",
                        a.nombre, s.nombre, s.categoria.name(), s.tipo, s.alcance,
                        s.detalleValor(), s.linea);
                total++;
            }
        }
        System.out.println("-".repeat(92));
        System.out.println("Total de simbolos: " + total
                + " | Ambitos creados: " + tabla.historial().size());
    }

    public void imprimirReporte() {
        Comparator<Diag> orden = Comparator.comparingInt((Diag d) -> d.linea)
                .thenComparingInt(d -> d.col);
        errores.sort(orden);
        advertencias.sort(orden);

        System.out.println("\n=== REPORTE DE ERRORES SEMANTICOS ===");
        if (errores.isEmpty()) {
            System.out.println("Sin errores semanticos.");
        } else {
            int n = 1;
            for (Diag d : errores) {
                System.out.printf("[ERROR SEMANTICO #%d] Linea %d, columna %d%n    %s%n",
                        n++, d.linea, d.col, d.texto);
            }
            System.out.println("\nTOTAL DE ERRORES: " + errores.size());
        }

        if (!advertencias.isEmpty()) {
            System.out.println("\n--- Advertencias ---");
            int n = 1;
            for (Diag d : advertencias) {
                System.out.printf("[ADVERTENCIA #%d] Linea %d, columna %d%n    %s%n",
                        n++, d.linea, d.col, d.texto);
            }
        }
    }
}