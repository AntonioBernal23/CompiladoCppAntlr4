// BERNAL MARTINEZ ANTONIO
// ===== Base valida =====
int edad = 25;
string nombre = "Juan";
float altura = 1.75;
bool activo = true;
char letra = 'z';

// ===== 1. Operaciones con tipos no numericos =====
int r1 = edad + nombre;                 // esperado: error
int r2 = 5 + nombre;                    // esperado: error
string r3 = nombre - nombre;            // esperado: error
int r4 = -nombre;                       // esperado: error
edad += nombre;                         // esperado: error

// ===== 2. Asignaciones incompatibles / conversion implicita =====
int r5 = "hola";                        // esperado: error
float r6 = edad;                        // esperado: error
float r7 = 5 / 2;                       // esperado: error
bool r8 = 1;                            // esperado: error
char r9 = "a";                          // esperado: error

// ===== 3. Division por cero =====
int r10 = 10 / 0;                       // esperado: error
int r11 = edad / 0;                     // esperado: error
int r12 = 10 % 0;                       // esperado: error
float r13 = altura / 0.0;               // esperado: error

// ===== 4. Operadores logicos con no booleanos =====
bool r14 = true && 1;                   // esperado: error
bool r15 = (edad > 5) && "texto";       // esperado: error
bool r16 = !edad;                       // esperado: error
bool r17 = edad || activo;              // esperado: error

// ===== 5. Comparaciones entre tipos incompatibles =====
bool r18 = ("5" == 5);                  // esperado: error
bool r19 = nombre < edad;               // esperado: error
bool r20 = letra == nombre;             // esperado: error

// ===== 6. Conversiones explicitas invalidas =====
int r21 = static_cast<int>(nombre);           // esperado: error
float r22 = static_cast<float>("3.5");        // esperado: error

// ===== 7. Condiciones no booleanas =====
if (edad) {                             // esperado: error
}
while (nombre) {                        // esperado: error
}

// ===== Contraste: estas SI son validas =====
int ok1 = edad + 5;                     // esperado: 30
bool ok2 = (edad > 5) && activo;        // esperado: true
float ok3 = static_cast<float>(edad);   // esperado: 25
bool ok4 = (5 == 5);                    // esperado: true