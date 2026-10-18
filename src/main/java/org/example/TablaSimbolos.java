// BERNAL MARTINEZ ANTONIO
package org.example;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TablaSimbolos {

    /** Un ambito: global, clase, metodo, bloque... */
    public static class Ambito {
        public final String nombre;
        public final String alcance;   // global | clase | local
        public final int nivel;
        public final Map<String, Simbolo> simbolos = new LinkedHashMap<>();

        Ambito(String nombre, String alcance, int nivel) {
            this.nombre = nombre;
            this.alcance = alcance;
            this.nivel = nivel;
        }
    }

    private final Deque<Ambito> pila = new ArrayDeque<>();       // ambitos abiertos
    private final List<Ambito> historial = new ArrayList<>();    // todos, en orden de creacion
    private final List<String> bitacora = new ArrayList<>();

    public TablaSimbolos() {
        entrarAmbito("global", "global");
    }

    // ---------- Ambitos ----------
    public void entrarAmbito(String nombre, String alcance) {
        Ambito a = new Ambito(nombre, alcance, pila.size());
        pila.push(a);
        historial.add(a);
        bitacora.add(String.format("ENTRAR   ambito '%s' (nivel %d, alcance %s)",
                nombre, a.nivel, alcance));
    }

    public void salirAmbito() {
        if (pila.size() > 1) {
            Ambito a = pila.pop();
            bitacora.add(String.format("SALIR    ambito '%s' (%d simbolo(s))",
                    a.nombre, a.simbolos.size()));
        }
    }

    public Ambito ambitoActual() {
        return pila.peek();
    }

    // ---------- Insercion y busqueda ----------
    /** inserten_simbolo: false si ya existe en el ambito actual. */
    public boolean insertarSimbolo(Simbolo s) {
        Ambito actual = pila.peek();
        if (actual.simbolos.containsKey(s.nombre)) {
            bitacora.add(String.format("INSERTAR '%s' RECHAZADO: ya existe en '%s'",
                    s.nombre, actual.nombre));
            return false;
        }
        actual.simbolos.put(s.nombre, s);
        bitacora.add(String.format("INSERTAR '%s' : %s (%s) en '%s' [alcance %s]",
                s.nombre, s.tipo, s.categoria, actual.nombre, actual.alcance));
        return true;
    }

    /** busquen_simbolo: busca del ambito mas interno al global. */
    public Simbolo buscarSimbolo(String nombre) {
        for (Ambito a : pila) {   // el iterador recorre del ultimo ambito abierto al global
            Simbolo s = a.simbolos.get(nombre);
            if (s != null) {
                bitacora.add(String.format("BUSCAR   '%s' -> encontrado en '%s' (%s)",
                        nombre, a.nombre, s.tipo));
                return s;
            }
        }
        bitacora.add(String.format("BUSCAR   '%s' -> NO ENCONTRADO", nombre));
        return null;
    }

    /** Busca solo en el ambito actual (para detectar duplicados). */
    public Simbolo buscarLocal(String nombre) {
        return pila.peek().simbolos.get(nombre);
    }

    public List<Ambito> historial() { return historial; }

    public List<String> bitacora() { return bitacora; }
}