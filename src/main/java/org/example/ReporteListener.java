// BERNAL MARTINEZ ANTONIO
package org.example;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

public class ReporteListener extends CppBaseListener {

    private final Parser parser;

    public ReporteListener(Parser parser) {
        this.parser = parser;
    }

    // ---------- Utilidades ----------
    /** Texto original del codigo fuente (conserva los espacios). */
    private String txt(ParserRuleContext ctx) {
        return parser.getTokenStream().getText(ctx);
    }

    /** Sangria segun cuantas clases o bloques { } contienen a la sentencia. */
    private String sangria(ParserRuleContext ctx) {
        int nivel = 0;
        for (RuleContext c = ctx.parent; c != null; c = c.parent) {
            if (c instanceof CppParser.BloqueContext || c instanceof CppParser.ClaseContext) nivel++;
        }
        return "    ".repeat(nivel);
    }

    private void linea(ParserRuleContext ctx, String etiqueta, String detalle) {
        System.out.printf("Linea %-3d %s%-11s| %s%n",
                ctx.getStart().getLine(), sangria(ctx), etiqueta, detalle);
    }

    private boolean esLogica(ParseTree nodo) {
        if (nodo instanceof TerminalNode) {
            int t = ((TerminalNode) nodo).getSymbol().getType();
            return t == CppLexer.AND || t == CppLexer.OR || t == CppLexer.NOT
                    || t == CppLexer.EQ  || t == CppLexer.NEQ || t == CppLexer.LT
                    || t == CppLexer.GT  || t == CppLexer.LE  || t == CppLexer.GE;
        }
        for (int i = 0; i < nodo.getChildCount(); i++) {
            if (esLogica(nodo.getChild(i))) return true;
        }
        return false;
    }

    // ---------- Clases y metodos ----------
    @Override
    public void enterClase(CppParser.ClaseContext ctx) {
        linea(ctx, "CLASE", "nombre: " + ctx.ID().getText());
    }

    @Override
    public void enterModificadorAcceso(CppParser.ModificadorAccesoContext ctx) {
        linea(ctx, "ACCESO", ctx.getChild(0).getText() + ":");
    }

    @Override
    public void enterDeclaracionMetodo(CppParser.DeclaracionMetodoContext ctx) {
        List<String> params = new ArrayList<>();
        if (ctx.parametros() != null) {
            for (CppParser.ParametroContext pr : ctx.parametros().parametro()) {
                params.add(pr.tipoDato().getText() + " " + pr.ID().getText());
            }
        }
        linea(ctx, "METODO", "retorno: " + ctx.tipoRetorno().getText()
                + " | nombre: " + ctx.ID().getText()
                + " | parametros: (" + String.join(", ", params) + ")");
    }

    @Override
    public void enterSentenciaReturn(CppParser.SentenciaReturnContext ctx) {
        String valor = ctx.expresion() != null ? txt(ctx.expresion()) : "(vacio)";
        linea(ctx, "RETURN", "valor: " + valor);
    }

    @Override
    public void enterLlamada(CppParser.LlamadaContext ctx) {
        linea(ctx, "LLAMADA", txt(ctx));
    }

    // ---------- Variables, constantes y usos ----------
    @Override
    public void enterDeclaracionSimple(CppParser.DeclaracionSimpleContext ctx) {
        String tipo = ctx.tipo().getText();
        for (CppParser.DeclaradorContext d : ctx.declarador()) {
            String valor = d.expresion() != null ? txt(d.expresion()) : "(sin inicializar)";
            linea(ctx, "VARIABLE", "tipo: " + tipo + " | nombre: " + d.ID().getText()
                    + " | valor: " + valor);
        }
    }

    @Override
    public void enterDeclaracionConstante(CppParser.DeclaracionConstanteContext ctx) {
        linea(ctx, "CONSTANTE", "tipo: " + ctx.tipo().getText() + " | nombre: "
                + ctx.ID().getText() + " | valor: " + txt(ctx.expresion()));
    }

    @Override
    public void enterAsignacionSimple(CppParser.AsignacionSimpleContext ctx) {
        linea(ctx, "USO", ctx.ID().getText() + " " + ctx.operadorAsignacion().getText()
                + " " + txt(ctx.expresion()));
    }

    @Override
    public void enterIncremento(CppParser.IncrementoContext ctx) {
        linea(ctx, "INCREMENTO", txt(ctx));
    }

    @Override
    public void enterSentenciaSalto(CppParser.SentenciaSaltoContext ctx) {
        linea(ctx, "SALTO", ctx.getChild(0).getText());
    }

    @Override
    public void enterSentenciaExpresion(CppParser.SentenciaExpresionContext ctx) {
        String tipo = esLogica(ctx.expresion()) ? "LOGICA" : "ARITMETICA";
        linea(ctx, "EXPRESION", "tipo: " + tipo + " | " + txt(ctx.expresion()));
    }

    // ---------- Estructuras de control ----------
    @Override
    public void enterSentenciaIf(CppParser.SentenciaIfContext ctx) {
        String extra = ctx.ELSE() != null ? " | con else" : "";
        linea(ctx, "IF", "condicion: " + txt(ctx.expresion()) + extra);
    }

    @Override
    public void enterSentenciaWhile(CppParser.SentenciaWhileContext ctx) {
        linea(ctx, "WHILE", "condicion: " + txt(ctx.expresion()));
    }

    @Override
    public void enterSentenciaFor(CppParser.SentenciaForContext ctx) {
        linea(ctx, "FOR", "init: " + txt(ctx.inicializacion())
                + " | cond: " + txt(ctx.expresion())
                + " | update: " + txt(ctx.actualizacion()));
    }
}