// BERNAL MARTINEZ ANTONIO
package org.example;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

public class ReporteListener extends CppBaseListener {

    private final Parser parser;

    public ReporteListener(Parser parser) {
        this.parser = parser;
    }

    // ---------- Utilidades ----------
    /** Sangria segun cuantos bloques { } contienen a la sentencia. */
    private String sangria(ParserRuleContext ctx) {
        int nivel = 0;
        for (RuleContext c = ctx.parent; c != null; c = c.parent) {
            if (c instanceof CppParser.BloqueContext) nivel++;
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

    // ---------- Variables, constantes y usos ----------
    @Override
    public void enterDeclaracionSimple(CppParser.DeclaracionSimpleContext ctx) {
        String tipo = ctx.tipo().getText();
        for (CppParser.DeclaradorContext d : ctx.declarador()) {
            String valor = d.expresion() != null ? d.expresion().getText() : "(sin inicializar)";
            linea(ctx, "VARIABLE", "tipo: " + tipo + " | nombre: " + d.ID().getText()
                    + " | valor: " + valor);
        }
    }

    @Override
    public void enterDeclaracionConstante(CppParser.DeclaracionConstanteContext ctx) {
        linea(ctx, "CONSTANTE", "tipo: " + ctx.tipo().getText() + " | nombre: "
                + ctx.ID().getText() + " | valor: " + ctx.expresion().getText());
    }

    @Override
    public void enterAsignacionSimple(CppParser.AsignacionSimpleContext ctx) {
        linea(ctx, "USO", ctx.ID().getText() + " " + ctx.operadorAsignacion().getText()
                + " " + ctx.expresion().getText());
    }

    @Override
    public void enterIncremento(CppParser.IncrementoContext ctx) {
        linea(ctx, "INCREMENTO", ctx.getText());
    }

    @Override
    public void enterSentenciaSalto(CppParser.SentenciaSaltoContext ctx) {
        linea(ctx, "SALTO", ctx.getChild(0).getText());
    }

    @Override
    public void enterSentenciaExpresion(CppParser.SentenciaExpresionContext ctx) {
        String tipo = esLogica(ctx.expresion()) ? "LOGICA" : "ARITMETICA";
        linea(ctx, "EXPRESION", "tipo: " + tipo + " | " + ctx.expresion().getText());
    }

    // ---------- Estructuras de control ----------
    @Override
    public void enterSentenciaIf(CppParser.SentenciaIfContext ctx) {
        String extra = ctx.ELSE() != null ? " | con else" : "";
        linea(ctx, "IF", "condicion: " + ctx.expresion().getText() + extra);
    }

    @Override
    public void enterSentenciaWhile(CppParser.SentenciaWhileContext ctx) {
        linea(ctx, "WHILE", "condicion: " + ctx.expresion().getText());
    }

    @Override
    public void enterSentenciaFor(CppParser.SentenciaForContext ctx) {
        linea(ctx, "FOR", "init: " + ctx.inicializacion().getText()
                + " | cond: " + ctx.expresion().getText()
                + " | update: " + ctx.actualizacion().getText());
    }
}