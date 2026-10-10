// BERNAL MARTINEZ ANTONIO
package org.example;

public class ReporteListener extends CppBaseListener {

    @Override
    public void enterDeclaracionVariable(CppParser.DeclaracionVariableContext ctx) {
        String tipo = ctx.tipo().getText();
        for (CppParser.DeclaradorContext d : ctx.declarador()) {
            String valor = d.expresion() != null ? d.expresion().getText() : "(sin inicializar)";
            System.out.printf("Linea %-3d VARIABLE   | tipo: %-7s | nombre: %-10s | valor: %s%n",
                    ctx.getStart().getLine(), tipo, d.ID().getText(), valor);
        }
    }

    @Override
    public void enterDeclaracionConstante(CppParser.DeclaracionConstanteContext ctx) {
        System.out.printf("Linea %-3d CONSTANTE  | tipo: %-7s | nombre: %-10s | valor: %s%n",
                ctx.getStart().getLine(), ctx.tipo().getText(),
                ctx.ID().getText(), ctx.expresion().getText());
    }

    @Override
    public void enterAsignacion(CppParser.AsignacionContext ctx) {
        System.out.printf("Linea %-3d USO        | %s %s %s%n",
                ctx.getStart().getLine(), ctx.ID().getText(),
                ctx.operadorAsignacion().getText(), ctx.expresion().getText());
    }
}