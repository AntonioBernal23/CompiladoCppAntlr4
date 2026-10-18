// ===== Ejemplos del enunciado =====
int x;
float y = 3.5;
x = y;
int a;
a = 5;
float b;
b = a;
c = 10;

// ===== Declaraciones =====
int x;
string texto = 25;
bool flag = "si";
int e = d + 1;
int sinValor;
int copia = sinValor + 1;

// ===== Constantes =====
const int LIMITE = 10;
LIMITE = 20;
LIMITE++;

// ===== Operaciones invalidas =====
int r = "hola" * 2;
int z = 10 / 0;
bool ok = x == "a";
int m = 5.5 % 2;

// ===== Condiciones y ambito del for =====
if ("texto") { x = 1; }
for (int i = 0; i < 3; i++) { }
i = 5;

// ===== Clase: ambitos, retornos y llamadas =====
class Prueba {
    int miembro = 1;
    int metodoA(int p) {
        int interna = p + miembro;
        return interna;
    }
    void metodoB() {
        interna = 5;
        return 1;
    }
    int metodoC(int p, int p) {
        return "texto";
    }
    int metodoD() {
        return;
    }
    int metodoA(int q) {
        return q;
    }
    Desconocida metodoE() {
        return 1;
    }
    void metodoF() {
        int r1 = metodoA(1, 2);
        int s1 = metodoA("hola");
        int t1 = noExiste(3);
        miembro();
        int u1 = metodoB();
    }
}