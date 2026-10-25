// BERNAL MARTINEZ ANTONIO
// ===== Tipos de datos correctos =====
int edad = 25;                          // esperado: 25
string nombre = "Juan";
float altura = 1.75;                    // esperado: 1.75
double pi = 3.1416;                     // esperado: 3.1416
bool activo = true;                     // esperado: true
char letra = 'z';
const int MAXIMO = 100;                 // esperado: 100
const float TASA = 0.5;                 // esperado: 0.5

// ===== Aritmeticas validas =====
int suma = 5 + 3;                       // esperado: 8
int resta = 10 - 4;                     // esperado: 6
int producto = 6 * 7;                   // esperado: 42
int cociente = 10 / 2;                  // esperado: 5
int residuo = 17 % 5;                   // esperado: 2
int negativo = -5 + 3;                  // esperado: -2
int complejo = (10 + 20) * (30 - 15);   // esperado: 450
float decimal = 2.5 * 2;                // esperado: 5
int mitad = MAXIMO / 2;                 // esperado: 50
float porcentaje = TASA * 100;          // esperado: 50

// ===== Logicas y comparaciones validas =====
bool l1 = true && false;                // esperado: false
bool l2 = true || false;                // esperado: true
bool l3 = !false;                       // esperado: true
bool esIgual = (5 == 5);                // esperado: true
bool mayor = 7 > 3;                     // esperado: true
bool menor = 7 < 3;                     // esperado: false
bool mixto = 5 > 2.5;                   // esperado: true
bool combinada = (10 > 5) && (5 < 10);  // esperado: true

// ===== Variables y conversiones explicitas =====
int a = 10, b = 5;
int resultado = a + b;                  // esperado: 15
bool resLogico = (a > b) && (b < 10);   // esperado: true
int entero = 10;
float flotante = static_cast<float>(entero);   // esperado: 10
int truncado = static_cast<int>(3.9);          // esperado: 3
float real = static_cast<float>(5) / 2;        // esperado: 2.5

// ===== Asignaciones compuestas e incrementos =====
int c = 1;
c += 2;
c -= 1;
c *= 3;
c++;
++c;

// ===== Condiciones booleanas =====
if (a > b && b < 10) {
    c = 1;
} else {
    c = 2;
}

while (c < 10) {
    c++;
}

for (int i = 0; i < 3; i++) {
    c += i;
}

// ===== Clase con metodos =====
class Operaciones {
public:
    int sumar(int x, int y) {
        return x + y;
    }
    float promedio(float x, float y) {
        return (x + y) / 2;
    }
    bool esMayor(int x, int y) {
        return x > y;
    }
    float aFloat(int x) {
        return static_cast<float>(x);
    }
    int doble(int x) {
        return sumar(x, x);
    }
};