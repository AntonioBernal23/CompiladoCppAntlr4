// ===== Clase del enunciado =====
class MiClase {
    int miMetodo(int a, float b) {
        // cuerpo del metodo
    }
    void otroMetodo() {
        // cuerpo del metodo
    }
}

// ===== Clase completa =====
class Calculadora {
private:
    int resultado;
    const int MAXIMO = 100;
public:
    int sumar(int a, int b) {
        return a + b;
    }
    float promedio(float x, float y) {
        float r = (x + y) / 2;
        return r;
    }
    bool esMayor(int a, int b) {
        if (a > b) { return true; } else { return false; }
    }
    void contar(int n) {
        for (int i = 0; i < n; i++) {
            resultado += i;
        }
    }
    MiClase crear(MiClase otro, char letra) {
        return otro;
    }
};

// ===== Uso (llamadas a metodos) =====
int total = sumar(2, 3 * 4);
total = total + promedio(1.5, 2.5);
sumar(1, 2);