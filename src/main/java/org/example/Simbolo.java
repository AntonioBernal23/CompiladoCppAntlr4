// BERNAL MARTINEZ ANTONIO
package org.example;

import java.util.ArrayList;
import java.util.List;

public class Simbolo {

    public enum Categoria { VARIABLE, CONSTANTE, PARAMETRO, METODO, CLASE }

    public final String nombre;
    public final String tipo;          // tipo de dato (o tipo de retorno si es METODO)
    public final Categoria categoria;
    public final String alcance;       // global | clase | local
    public final String ambito;        // nombre del ambito donde se declaro
    public final int linea;
    public final int columna;
    public String valor;               // ultimo valor asignado (texto), o null
    public boolean inicializado;
    public Object valorConst;          // valor constante conocido (Long, Double o Boolean), o null
    public final List<String> parametros = new ArrayList<>();  // solo METODO

    public Simbolo(String nombre, String tipo, Categoria categoria,
                   String alcance, String ambito, int linea, int columna) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.categoria = categoria;
        this.alcance = alcance;
        this.ambito = ambito;
        this.linea = linea;
        this.columna = columna;
    }

    /** Texto para la columna VALOR de la tabla. */
    public String detalleValor() {
        if (categoria == Categoria.METODO) {
            return "(" + String.join(", ", parametros) + ")";
        }
        return valor != null ? valor : "-";
    }
}