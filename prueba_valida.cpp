// ===== Variables globales =====
int variableGlobal;
int a = 5;
float b = 3.5;
double pi = 3.1416;
bool activo = true;
string nombre = "Compilador";
char letra = 'z';
const int MAXIMO = 100;

// ===== Asignaciones validas =====
variableGlobal = 10;
a = a + 1;
b = b * 2.5;
b += 1.5;
pi = b;
activo = a > 5 && b < 10.0;
nombre = nombre + "!";

// ===== Clase con metodos y ambitos =====
class Calculadora {
    int resultado = 0;
    int sumar(int x, int y) {
        int local = x + y;
        variableGlobal = local;
        return local;
    }
    float promedio(float x, float y) {
        float r = (x + y) / 2;
        return r;
    }
    bool esMayor(int x, int y) {
        if (x > y) { return true; } else { return false; }
    }
    void contar(int n) {
        for (int i = 0; i < n; i++) {
            resultado += i;
        }
        while (resultado > 100) {
            resultado -= 10;
        }
    }
    int usar() {
        int total = sumar(2, 3 * 4);
        return total;
    }
};

// ===== Bloques anidados (una variable local oculta a la global) =====
{
    int a = 99;
    a = a + 1;
    {
        int a = 7;
        a++;
    }
}