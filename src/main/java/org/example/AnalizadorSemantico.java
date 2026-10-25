// BERNAL MARTINEZ ANTONIO
package org.example;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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

    /** Una fila de la tabla de evaluacion de expresiones. */
    private static class Evaluacion {
        final int linea;
        final String expresion, tipo, valor, esperado, prueba;
        Evaluacion(int linea, String expresion, String tipo, String valor,
                   String esperado, String prueba) {
            this.linea = linea;
            this.expresion = expresion;
            this.tipo = tipo;
            this.valor = valor;
            this.esperado = esperado;
            this.prueba = prueba;
        }
    }

    private final Parser parser;
    private final TablaSimbolos tabla = new TablaSimbolos();
    private final List<Diag> errores = new ArrayList<>();
    private final List<Diag> advertencias = new ArrayList<>();
    private final List<Evaluacion> evaluaciones = new ArrayList<>();
    private final Map<ParseTree, Object> valores = new IdentityHashMap<>();   // valor constante por nodo
    private Simbolo metodoActual = null;
    private int contAmbitos = 0;
    private int pruebasOk = 0;
    private int pruebasFallo = 0;
    private boolean divEntera = false;          // se vio una division entre enteros
    private boolean ultimaDivEntera = false;    // idem, de la ultima expresion evaluada

    public AnalizadorSemantico(Parser parser) {
        this.parser = parser;
    }

    // =====================================================================
    //  UTILIDADES
    // =====================================================================
    /** Texto original del codigo fuente (una sola linea). */
    private String txt(ParseTree n) {
        String s = (n instanceof ParserRuleContext)
                ? parser.getTokenStream().getText((ParserRuleContext) n)
                : n.getText();
        return s.replaceAll("\\s+", " ").trim();
    }

    private Token inicio(ParseTree n) {
        return (n instanceof ParserRuleContext)
                ? ((ParserRuleContext) n).getStart()
                : ((TerminalNode) n).getSymbol();
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

    private void setVal(ParseTree n, Object v) {
        if (v != null) valores.put(n, v);
    }

    private Object val(ParseTree n) {
        return valores.get(n);
    }

    private static boolean esReal(String t) {
        return t.equals("float") || t.equals("double");
    }

    private static boolean esNumerico(String t) {
        return t.equals("int") || t.equals("char") || esReal(t);
    }

    /** Jerarquia numerica: char < int < float < double (0 si no es numerico). */
    private static int rango(String t) {
        switch (t) {
            case "char":   return 1;
            case "int":    return 2;
            case "float":  return 3;
            case "double": return 4;
            default:       return 0;
        }
    }

    /**
     * Compatibilidad para asignaciones, inicializaciones, argumentos y return.
     * Reglas estrictas (como el ejemplo del enunciado): NO hay conversion implicita
     * int -> float ni float -> int. Unica flexibilidad: float <-> double.
     */
    private static boolean compatible(String destino, String origen) {
        if (ERROR.equals(destino) || ERROR.equals(origen)) return true;   // ya reportado
        if (destino.equals(origen)) return true;
        return esReal(destino) && esReal(origen);
    }

    /** Validez de una conversion explicita static_cast. */
    private static boolean convertible(String desde, String hacia) {
        if (ERROR.equals(desde) || ERROR.equals(hacia)) return true;
        return (esNumerico(desde) || desde.equals("bool"))
                && (esNumerico(hacia) || hacia.equals("bool"));
    }

    private static String nombreTipo(String t) {
        switch (t) {
            case "int":    return "un entero (int)";
            case "float":  return "un flotante (float)";
            case "double": return "un double";
            case "char":   return "un caracter (char)";
            case "bool":   return "un booleano (bool)";
            case "string": return "una cadena (string)";
            case "void":   return "void (sin valor)";
            default:       return "un objeto '" + t + "'";
        }
    }

    private static String verbo(String op) {
        switch (op) {
            case "+": return "sumar";
            case "-": return "restar";
            case "*": return "multiplicar";
            case "/": return "dividir";
            case "%": return "calcular el modulo de";
            default:  return "operar";
        }
    }

    /** Mensaje para una conversion implicita no permitida o un tipo incompatible. */
    private String mensajeConversion(String desc, String destino, String origen, boolean divEnt) {
        int rd = rango(destino), ro = rango(origen);
        String base;
        if (rd > 0 && ro > 0 && ro < rd) {
            base = "Conversion implicita de '" + origen + "' a '" + destino + "' no permitida en "
                    + desc + ": use static_cast<" + destino + ">(...) para convertir explicitamente.";
        } else if (rd > 0 && ro > 0) {
            base = "Conversion implicita con perdida de informacion de '" + origen + "' a '" + destino
                    + "' en " + desc + ": use static_cast<" + destino + ">(...) si es intencional.";
        } else {
            base = "Tipo de dato incompatible en " + desc + ": se encontro " + nombreTipo(origen)
                    + " y se esperaba " + nombreTipo(destino) + ".";
        }
        if (divEnt && esReal(destino) && origen.equals("int")) {
            base += " Nota: una division entre enteros descarta los decimales (5 / 2 = 2); "
                    + "convierta un operando con static_cast<float>(...).";
        }
        return base;
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
        tabla.entrarAmbito("for#" + (++contAmbitos), "local");
    }

    @Override
    public void exitSentenciaFor(CppParser.SentenciaForContext ctx) {
        tabla.salirAmbito();
    }

    // =====================================================================
    //  EVALUACION DE EXPRESIONES A NIVEL DE SENTENCIA
    // =====================================================================
    /** Calcula el tipo de una expresion completa y recuerda si hubo division entera. */
    private String tipoTop(ParseTree e) {
        divEntera = false;
        String t = tipoExpr(e);
        ultimaDivEntera = divEntera;
        return t;
    }

    /** Busca un comentario "// esperado: X" al final de la linea de la sentencia. */
    private String comentarioEsperado(ParseTree sentencia) {
        try {
            if (!(sentencia instanceof ParserRuleContext)
                    || !(parser.getTokenStream() instanceof CommonTokenStream)) return null;
            Token stop = ((ParserRuleContext) sentencia).getStop();
            if (stop == null) return null;
            List<Token> ocultos = ((CommonTokenStream) parser.getTokenStream())
                    .getHiddenTokensToRight(stop.getTokenIndex());
            if (ocultos == null) return null;
            for (Token o : ocultos) {
                if (o.getType() == CppLexer.LINE_COMMENT) {
                    String c = o.getText();
                    int i = c.toLowerCase().indexOf("esperado:");
                    if (i < 0) return null;
                    String resto = c.substring(i + "esperado:".length()).trim();
                    if (resto.isEmpty()) return null;
                    return resto.split("\\s+")[0];
                }
                if (o.getType() == CppLexer.WS && o.getText().contains("\n")) return null;
            }
        } catch (RuntimeException ex) {
            return null;
        }
        return null;
    }

    private static boolean coincide(Object obtenido, String esperado) {
        if (obtenido instanceof Boolean) return String.valueOf(obtenido).equalsIgnoreCase(esperado);
        if (obtenido instanceof Number) {
            try {
                return Math.abs(((Number) obtenido).doubleValue() - Double.parseDouble(esperado)) < 1e-9;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return false;
    }

    private static String formatoValor(Object v) {
        if (v == null) return "-";
        if (v instanceof Double) {
            double d = (Double) v;
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return ((long) d) + ".0";
            return String.valueOf(d);
        }
        return String.valueOf(v);
    }

    /** Registra la fila de evaluacion y compara contra el resultado esperado (si lo hay). */
    private void cerrarEvaluacion(ParseTree expr, ParseTree sentencia, String tipo,
                                  int erroresAntes, boolean permitirPrueba) {
        boolean hayError = errores.size() > erroresAntes || ERROR.equals(tipo);
        Object v = hayError ? null : val(expr);
        String esperado = permitirPrueba ? comentarioEsperado(sentencia) : null;
        String prueba = "-";
        if (esperado != null) {
            if (esperado.equalsIgnoreCase("error")) {
                prueba = hayError ? "OK" : "FALLO";
            } else if (hayError) {
                prueba = "FALLO";
            } else if (v == null) {
                prueba = "NO EVALUABLE";
            } else {
                prueba = coincide(v, esperado) ? "OK" : "FALLO";
            }
            if (prueba.equals("OK")) pruebasOk++;
            else if (prueba.equals("FALLO")) pruebasFallo++;
        }
        evaluaciones.add(new Evaluacion(inicio(expr).getLine(), txt(expr),
                hayError ? "ERROR" : tipo, formatoValor(v),
                esperado == null ? "-" : esperado, prueba));
    }

    // =====================================================================
    //  DECLARACIONES
    // =====================================================================
    @Override
    public void enterDeclaracionSimple(CppParser.DeclaracionSimpleContext ctx) {
        String tipo = ctx.tipo().getText();
        boolean unico = ctx.declarador().size() == 1;
        for (CppParser.DeclaradorContext d : ctx.declarador()) {
            int antes = errores.size();
            String tInit = null;
            String valor = null;
            if (d.expresion() != null) {
                tInit = tipoTop(d.expresion());   // antes de insertar: 'int x = x;' es error
                valor = txt(d.expresion());
            }
            Token t = d.ID().getSymbol();
            Simbolo s = declarar(d.ID().getText(), tipo, Simbolo.Categoria.VARIABLE,
                    t, valor, d.expresion() != null);
            if (s != null && tInit != null && !compatible(tipo, tInit)) {
                error(t, mensajeConversion("la asignacion a '" + s.nombre + "'",
                        tipo, tInit, ultimaDivEntera));
            }
            if (d.expresion() != null) {
                cerrarEvaluacion(d.expresion(), ctx.getParent(), tInit, antes, unico);
            }
        }
    }

    @Override
    public void enterDeclaracionConstante(CppParser.DeclaracionConstanteContext ctx) {
        int antes = errores.size();
        String tipo = ctx.tipo().getText();
        String tInit = tipoTop(ctx.expresion());
        Token t = ctx.ID().getSymbol();
        Simbolo s = declarar(ctx.ID().getText(), tipo, Simbolo.Categoria.CONSTANTE,
                t, txt(ctx.expresion()), true);
        if (s != null) {
            if (!compatible(tipo, tInit)) {
                error(t, mensajeConversion("la asignacion a la constante '" + s.nombre + "'",
                        tipo, tInit, ultimaDivEntera));
            } else {
                s.valorConst = val(ctx.expresion());
            }
        }
        cerrarEvaluacion(ctx.expresion(), ctx, tInit, antes, true);
    }

    // =====================================================================
    //  ASIGNACIONES E INCREMENTOS
    // =====================================================================
    @Override
    public void enterAsignacionSimple(CppParser.AsignacionSimpleContext ctx) {
        int antes = errores.size();
        String tRhs = tipoTop(ctx.expresion());
        procesarAsignacion(ctx, tRhs);
        cerrarEvaluacion(ctx.expresion(), ctx.getParent(), tRhs, antes, true);
    }

    private void procesarAsignacion(CppParser.AsignacionSimpleContext ctx, String tRhs) {
        Token t = ctx.ID().getSymbol();
        String nombre = ctx.ID().getText();
        String op = ctx.operadorAsignacion().getText();

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
            Object vr = val(ctx.expresion());
            if (op.equals("/=") && vr instanceof Number && ((Number) vr).doubleValue() == 0.0) {
                error(t, "Division por cero: '" + nombre + " /= " + txt(ctx.expresion())
                        + "' divide entre 0 y no esta permitido.");
            }
            esperado = tipoBinario(op.substring(0, 1), s.tipo, tRhs, t, txt(ctx));
        }

        if (!compatible(s.tipo, esperado)) {
            error(t, mensajeConversion("la asignacion a '" + nombre + "'",
                    s.tipo, esperado, ultimaDivEntera));
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
                    + nombre + "' es " + nombreTipo(s.tipo) + ".");
        }
    }

    // =====================================================================
    //  RETURN, EXPRESIONES SUELTAS Y CONDICIONES
    // =====================================================================
    @Override
    public void enterSentenciaReturn(CppParser.SentenciaReturnContext ctx) {
        String tExpr = ctx.expresion() != null ? tipoTop(ctx.expresion()) : "void";
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
            error(ctx, mensajeConversion("el retorno del metodo '" + metodoActual.nombre + "'",
                    esperado, tExpr, ultimaDivEntera));
        }
    }

    @Override
    public void enterSentenciaExpresion(CppParser.SentenciaExpresionContext ctx) {
        int antes = errores.size();
        String t = tipoTop(ctx.expresion());
        cerrarEvaluacion(ctx.expresion(), ctx, t, antes, true);
    }

    /** Las condiciones deben ser estrictamente booleanas. */
    private void checarCondicion(CppParser.ExpresionContext e, String estructura) {
        String t = tipoExpr(e);
        if (!ERROR.equals(t) && !t.equals("bool")) {
            error(e.getStart(), "La condicion de '" + estructura + "' debe ser booleana (bool), "
                    + "pero '" + txt(e) + "' es " + nombreTipo(t) + ".");
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
    //  TIPO (Y VALOR CONSTANTE) DE UNA EXPRESION
    // =====================================================================
    private String tipoExpr(ParseTree n) {
        if (n instanceof CppParser.ExpresionContext) {
            String t = tipoExpr(n.getChild(0));
            setVal(n, val(n.getChild(0)));
            return t;
        }
        if (n instanceof CppParser.ExpresionLogicaContext) return tipoLogico(n, "||");
        if (n instanceof CppParser.TerminoLogicoContext) return tipoLogico(n, "&&");
        if (n instanceof CppParser.FactorLogicoContext) return tipoFactorLogico(n);
        if (n instanceof CppParser.ComparacionContext) return tipoComparacion(n);
        if (n instanceof CppParser.ExpresionAritmeticaContext
                || n instanceof CppParser.TerminoContext) return tipoAritmetico(n);
        if (n instanceof CppParser.FactorContext) return tipoFactor((CppParser.FactorContext) n);
        return ERROR;
    }

    /** operando (op operando)*  con || o &&: todos los operandos deben ser bool. */
    private String tipoLogico(ParseTree n, String op) {
        if (n.getChildCount() == 1) {
            String t = tipoExpr(n.getChild(0));
            setVal(n, val(n.getChild(0)));
            return t;
        }
        Object acc = null;
        boolean plegable = true;
        for (int i = 0; i < n.getChildCount(); i += 2) {
            ParseTree operando = n.getChild(i);
            String t = tipoExpr(operando);
            Object v = val(operando);
            if (ERROR.equals(t)) {
                plegable = false;
            } else if (!t.equals("bool")) {
                error(inicio(operando), "El operador '" + op + "' requiere operandos booleanos: "
                        + "el operando '" + txt(operando) + "' es " + nombreTipo(t)
                        + ", no un booleano.");
                plegable = false;
            }
            if (!(v instanceof Boolean)) plegable = false;
            if (i == 0) {
                acc = v;
            } else if (plegable) {
                boolean x = (Boolean) acc, y = (Boolean) v;
                acc = op.equals("&&") ? (x && y) : (x || y);
            }
        }
        if (plegable) setVal(n, acc);
        return "bool";
    }

    private String tipoFactorLogico(ParseTree n) {
        if (n.getChildCount() == 2 && n.getChild(0) instanceof TerminalNode) {   // ! factorLogico
            Token op = ((TerminalNode) n.getChild(0)).getSymbol();
            ParseTree operando = n.getChild(1);
            String t = tipoExpr(operando);
            if (ERROR.equals(t)) return "bool";
            if (!t.equals("bool")) {
                error(op, "El operador '!' requiere un operando booleano: '" + txt(operando)
                        + "' es " + nombreTipo(t) + ", no un booleano.");
            } else {
                Object v = val(operando);
                if (v instanceof Boolean) setVal(n, !((Boolean) v));
            }
            return "bool";
        }
        String t = tipoExpr(n.getChild(0));
        setVal(n, val(n.getChild(0)));
        return t;
    }

    private String tipoComparacion(ParseTree n) {
        if (n.getChildCount() == 1) {
            String t = tipoExpr(n.getChild(0));
            setVal(n, val(n.getChild(0)));
            return t;
        }
        ParseTree izq = n.getChild(0);
        ParseTree der = n.getChild(2);
        String l = tipoExpr(izq);
        String r = tipoExpr(der);
        String op = n.getChild(1).getText();
        Token opTok = inicio(n.getChild(1));
        if (ERROR.equals(l) || ERROR.equals(r)) return "bool";

        if (l.equals("void") || r.equals("void")) {
            error(opTok, "No se puede comparar un valor void en la expresion '" + txt(n) + "'.");
            return "bool";
        }
        boolean ok;
        if (op.equals("==") || op.equals("!=")) {
            ok = l.equals(r) || (esNumerico(l) && esNumerico(r));
        } else {
            ok = esNumerico(l) && esNumerico(r);
        }
        if (!ok) {
            if (l.equals(r)) {
                error(opTok, "El operador '" + op + "' solo compara operandos numericos, pero se "
                        + "encontro " + nombreTipo(l) + " en ambos lados de '" + txt(n) + "'.");
            } else {
                error(opTok, "No se puede comparar " + nombreTipo(l) + " con " + nombreTipo(r)
                        + " usando '" + op + "' en la expresion '" + txt(n) + "'.");
            }
            return "bool";
        }
        Object a = val(izq);
        Object b = val(der);
        if (a != null && b != null) setVal(n, comparar(op, a, b));
        return "bool";
    }

    private Object comparar(String op, Object a, Object b) {
        if (a instanceof Boolean && b instanceof Boolean) {
            if (op.equals("==")) return a.equals(b);
            if (op.equals("!=")) return !a.equals(b);
            return null;
        }
        if (a instanceof Number && b instanceof Number) {
            double x = ((Number) a).doubleValue();
            double y = ((Number) b).doubleValue();
            switch (op) {
                case "==": return x == y;
                case "!=": return x != y;
                case "<":  return x < y;
                case ">":  return x > y;
                case "<=": return x <= y;
                case ">=": return x >= y;
                default:   return null;
            }
        }
        return null;
    }

    /** operando (op operando)*  con + - o * / % */
    private String tipoAritmetico(ParseTree n) {
        ParseTree primero = n.getChild(0);
        String acc = tipoExpr(primero);
        Object vAcc = val(primero);
        for (int i = 1; i + 1 < n.getChildCount(); i += 2) {
            Token op = ((TerminalNode) n.getChild(i)).getSymbol();
            ParseTree der = n.getChild(i + 1);
            String tr = tipoExpr(der);
            Object vDer = val(der);

            boolean divCero = false;
            if ((op.getType() == CppLexer.DIV || op.getType() == CppLexer.MOD)
                    && vDer instanceof Number && ((Number) vDer).doubleValue() == 0.0) {
                error(op, "Division por cero: el divisor '" + txt(der) + "' vale 0 y la operacion '"
                        + op.getText() + "' no esta permitida.");
                divCero = true;
            }
            String res = tipoBinario(op.getText(), acc, tr, op, txt(n));
            if (op.getType() == CppLexer.DIV && acc.equals("int") && tr.equals("int")) {
                divEntera = true;
            }
            vAcc = (divCero || ERROR.equals(res)) ? null : plegar(op, vAcc, vDer, res);
            acc = res;
        }
        setVal(n, vAcc);
        return acc;
    }

    /** Tipo resultante de a (op) b, o error si los tipos no son validos para el operador. */
    private String tipoBinario(String op, String l, String r, Token t, String expr) {
        if (ERROR.equals(l) || ERROR.equals(r)) return ERROR;
        if (op.equals("+") && l.equals("string") && r.equals("string")) return "string";
        if (esNumerico(l) && esNumerico(r)) {
            if (op.equals("%") && (esReal(l) || esReal(r))) {
                error(t, "El operador '%' solo se aplica a operandos enteros, pero en '" + expr
                        + "' se encontro " + nombreTipo(l) + " y " + nombreTipo(r) + ".");
                return ERROR;
            }
            if (l.equals("double") || r.equals("double")) return "double";
            if (l.equals("float") || r.equals("float")) return "float";
            return "int";
        }
        String ayuda = (op.equals("+") && (l.equals("string") || r.equals("string")))
                ? " Para concatenar, ambos operandos deben ser 'string'."
                : " El operador '" + op + "' requiere operandos numericos.";
        error(t, "No se puede " + verbo(op) + " " + nombreTipo(l) + " con " + nombreTipo(r)
                + " en la expresion '" + expr + "'." + ayuda);
        return ERROR;
    }

    /** Plegado de constantes: calcula a (op) b cuando ambos valores son conocidos. */
    private Object plegar(Token op, Object a, Object b, String tipoRes) {
        if (!(a instanceof Number) || !(b instanceof Number)) return null;
        try {
            if (a instanceof Long && b instanceof Long && tipoRes.equals("int")) {
                long x = (Long) a;
                long y = (Long) b;
                switch (op.getType()) {
                    case CppLexer.PLUS:  return x + y;
                    case CppLexer.MINUS: return x - y;
                    case CppLexer.MULT:  return x * y;
                    case CppLexer.DIV:
                        if (x % y != 0) {
                            advertencia(op, "La division entera " + x + " / " + y + " produce "
                                    + (x / y) + ": se descarta la parte decimal ("
                                    + ((double) x / y) + ").");
                        }
                        return x / y;
                    case CppLexer.MOD:   return x % y;
                    default:             return null;
                }
            }
            double x = ((Number) a).doubleValue();
            double y = ((Number) b).doubleValue();
            switch (op.getType()) {
                case CppLexer.PLUS:  return x + y;
                case CppLexer.MINUS: return x - y;
                case CppLexer.MULT:  return x * y;
                case CppLexer.DIV:   return x / y;
                default:             return null;
            }
        } catch (ArithmeticException ex) {
            error(op, "Excepcion aritmetica al evaluar la expresion: " + ex.getMessage());
            return null;
        }
    }

    private String tipoFactor(CppParser.FactorContext f) {
        int n = f.getChildCount();
        ParseTree c0 = f.getChild(0);

        if (n == 2) {   // - factor
            Token op = ((TerminalNode) c0).getSymbol();
            ParseTree operando = f.getChild(1);
            String t = tipoExpr(operando);
            if (ERROR.equals(t)) return ERROR;
            if (!esNumerico(t)) {
                error(op, "El operador '-' unario solo se aplica a operandos numericos, pero '"
                        + txt(operando) + "' es " + nombreTipo(t) + ".");
                return ERROR;
            }
            Object v = val(operando);
            if (v instanceof Long) setVal(f, -((Long) v));
            else if (v instanceof Double) setVal(f, -((Double) v));
            return t.equals("char") ? "int" : t;
        }
        if (n == 3) {   // ( expresion )
            String t = tipoExpr(f.getChild(1));
            setVal(f, val(f.getChild(1)));
            return t;
        }
        if (c0 instanceof CppParser.LlamadaContext) {
            return tipoLlamada((CppParser.LlamadaContext) c0);
        }
        if (c0 instanceof CppParser.ConversionContext) {
            String t = tipoConversion((CppParser.ConversionContext) c0);
            setVal(f, val(c0));
            return t;
        }

        Token tk = ((TerminalNode) c0).getSymbol();
        int tipoTok = tk.getType();
        if (tipoTok == CppLexer.INT_LIT) {
            try {
                setVal(f, Long.parseLong(tk.getText()));
            } catch (NumberFormatException ex) {
                advertencia(tk, "Literal entero fuera de rango: " + tk.getText());
            }
            return "int";
        }
        if (tipoTok == CppLexer.FLOAT_LIT) {
            setVal(f, Double.parseDouble(tk.getText()));
            return "float";
        }
        if (tipoTok == CppLexer.STRING_LIT) return "string";
        if (tipoTok == CppLexer.CHAR_LIT) return "char";
        if (tipoTok == CppLexer.TRUE || tipoTok == CppLexer.FALSE) {
            setVal(f, tipoTok == CppLexer.TRUE);
            return "bool";
        }
        if (tipoTok == CppLexer.ID) return tipoIdentificador(tk, f);
        return ERROR;
    }

    private String tipoIdentificador(Token t, ParseTree nodo) {
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
        if (s.categoria == Simbolo.Categoria.CONSTANTE && s.valorConst != null) {
            setVal(nodo, s.valorConst);
        }
        return s.tipo;
    }

    /** static_cast<tipo>(expresion) */
    private String tipoConversion(CppParser.ConversionContext c) {
        String destino = c.tipo().getText();
        ParseTree operando = c.expresion();
        String origen = tipoExpr(operando);
        if (ERROR.equals(origen)) return destino;   // ya reportado
        Token tok = c.getStart();

        if (!convertible(origen, destino)) {
            error(tok, "Conversion explicita invalida: no se puede convertir " + nombreTipo(origen)
                    + " a " + nombreTipo(destino) + " con static_cast en '" + txt(c) + "'.");
            return ERROR;
        }
        if (rango(origen) > 0 && rango(destino) > 0 && rango(origen) > rango(destino)) {
            String motivo = (esReal(origen) && !esReal(destino))
                    ? "se descarta la parte decimal"
                    : (origen.equals("double") && destino.equals("float"))
                    ? "se reduce la precision"
                    : "el valor puede salirse del rango";
            advertencia(tok, "Conversion explicita de '" + origen + "' a '" + destino
                    + "' con posible perdida de informacion (" + motivo + ").");
        }
        setVal(c, convertirValor(val(operando), destino));
        return destino;
    }

    private Object convertirValor(Object v, String destino) {
        if (v == null) return null;
        double d;
        if (v instanceof Boolean) d = ((Boolean) v) ? 1 : 0;
        else if (v instanceof Number) d = ((Number) v).doubleValue();
        else return null;
        switch (destino) {
            case "int":    return (long) d;
            case "float":
            case "double": return d;
            case "bool":   return d != 0;
            default:       return null;
        }
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
                error(args.get(i).getStart(), mensajeConversion(
                        "el argumento " + (i + 1) + " de '" + nombre + "'",
                        s.parametros.get(i), tiposArgs.get(i), false));
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

    public void imprimirEvaluaciones() {
        evaluaciones.sort(Comparator.comparingInt((Evaluacion ev) -> ev.linea));
        System.out.println("\n=== VALIDACION DE EXPRESIONES (tipo, resultado y prueba) ===");
        System.out.printf("%-6s %-38s %-8s %-10s %-10s %s%n",
                "LINEA", "EXPRESION", "TIPO", "VALOR", "ESPERADO", "PRUEBA");
        System.out.println("-".repeat(92));
        for (Evaluacion ev : evaluaciones) {
            System.out.printf("%-6d %-38.38s %-8s %-10.10s %-10.10s %s%n",
                    ev.linea, ev.expresion, ev.tipo, ev.valor, ev.esperado, ev.prueba);
        }
        System.out.println("-".repeat(92));
        System.out.println("Expresiones evaluadas: " + evaluaciones.size()
                + " | Pruebas OK: " + pruebasOk + " | Pruebas FALLO: " + pruebasFallo);
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