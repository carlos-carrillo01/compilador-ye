package yect;

import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.JOptionPane;

// esta clase es el interprete
// su trabajo es EJECUTAR de verdad el programa, no solo revisarlo
// osea que si el programa dice "ct_int total = 2 + 3;" el interprete
// de verdad calcula el 5 y lo guarda en la variable "total"
//
// esto solo se debe llamar cuando el lexico, el sintactico y el
// semantico ya revisaron el codigo y no encontraron ningun error
public class Interprete {


    private ArrayList<Token> tokens;
    private int pos;

    private HashMap<String, Object> valores;     // valor actual de cada variable normal
    private HashMap<String, Object[]> arreglos;  // valores de los arreglos
    private HashMap<String, String> tipos;       // tipo de cada variable (INT, FLOAT, STRING, BOOL)

    private StringBuilder salida; // aqui se va guardando todo lo que imprime el programa

    // limite de vueltas para evitar que un ciclo mal hecho deje pegado el programa
    private static final int LIMITE_VUELTAS = 200000;

    public Interprete() {
        valores = new HashMap<>();
        arreglos = new HashMap<>();
        tipos = new HashMap<>();
        salida = new StringBuilder();
    }

    // metodo principal: ejecuta el programa completo y regresa
    // el texto que se genero con los ye_print
    public String ejecutar(ArrayList<Token> listaTokens) {
        tokens = listaTokens;
        pos = 0;

        valores.clear();
        arreglos.clear();
        tipos.clear();
        salida.setLength(0);

        if (actualEs("INIT")) {
            avanzar();
        }

        ejecutarBloque();

        return salida.toString();
    }

    // ejecuta sentencias una tras otra hasta llegar al final
    // del programa o al cierre de un bloque { }
    private void ejecutarBloque() {
        while (pos < tokens.size() && !actualEs("END") && !actualEs("RBRACE")) {
            ejecutarSentencia();
        }
    }

    private void ejecutarSentencia() {
        String tk = actual().getToken();

        if (tk.equals("INT") || tk.equals("FLOAT") || tk.equals("STRING") || tk.equals("BOOL")) {
            ejecutarDeclaracion();
        } else if (tk.equals("ID")) {
            ejecutarAsignacion();
        } else if (tk.equals("IF")) {
            ejecutarIf();
        } else if (tk.equals("FOR")) {
            ejecutarFor();
        } else if (tk.equals("WHILE")) {
            ejecutarWhile();
        } else if (tk.equals("PRINT")) {
            ejecutarPrint();
        } else if (tk.equals("INPUT")) {
            ejecutarInput();
        } else {
            // algo raro, lo saltamos para no quedarnos trabados
            avanzar();
        }
    }

    // ------------------------------------------------------
    // declaracion de variables: ct_int edad = 20;
    // ------------------------------------------------------
    private void ejecutarDeclaracion() {
        String tipo = actual().getToken();
        avanzar();

        boolean esArreglo = false;
        if (actualEs("LBRACKET")) {
            avanzar(); // [
            avanzar(); // ]
            esArreglo = true;
        }

        String nombre = actual().getLexema();
        avanzar(); // nombre de la variable

        tipos.put(nombre, tipo);

        if (actualEs("ASSIGN")) {
            avanzar();

            if (esArreglo) {
                // esperamos algo como: new ct_int [ 5 ]
                avanzar(); // new
                avanzar(); // el tipo dentro de new
                avanzar(); // [
                Object tam = leerValorSimple();
                avanzar(); // ]

                int tamano = numeroEntero(tam);
                Object[] arreglo = new Object[tamano];
                Object valorInicial = valorPorDefecto(tipo);
                for (int i = 0; i < tamano; i++) {
                    arreglo[i] = valorInicial;
                }
                arreglos.put(nombre, arreglo);
            } else {
                int limite = buscarFinSentencia(pos);
                Object valor = evaluarExpresion(limite);
                valores.put(nombre, convertirAlTipo(tipo, valor));
            }
        } else if (esArreglo) {
            arreglos.put(nombre, new Object[0]);
        } else {
            valores.put(nombre, valorPorDefecto(tipo));
        }

        if (actualEs("SEMI")) {
            avanzar();
        }
    }

    // ------------------------------------------------------
    // asignacion: edad = 21;      numeros[0] = 10;
    // ------------------------------------------------------
    private void ejecutarAsignacion() {
        String nombre = actual().getLexema();
        avanzar();

        if (actualEs("LBRACKET")) {
            avanzar();
            Object indiceObj = leerValorSimple();
            int indice = numeroEntero(indiceObj);
            avanzar(); // ]
            avanzar(); // =

            int limite = buscarFinSentencia(pos);
            Object valor = evaluarExpresion(limite);

            Object[] arreglo = arreglos.get(nombre);
            if (arreglo != null && indice >= 0 && indice < arreglo.length) {
                arreglo[indice] = valor;
            }
        } else {
            avanzar(); // =
            int limite = buscarFinSentencia(pos);
            Object valor = evaluarExpresion(limite);
            String tipo = tipos.get(nombre);
            valores.put(nombre, convertirAlTipo(tipo, valor));
        }

        if (actualEs("SEMI")) {
            avanzar();
        }
    }

    // ------------------------------------------------------
    // ye_print algo;
    // ------------------------------------------------------
    private void ejecutarPrint() {
        avanzar(); // ye_print
        int limite = buscarFinSentencia(pos);
        Object valor = evaluarExpresion(limite);
        salida.append(aTexto(valor));
        salida.append("\n");

        if (actualEs("SEMI")) {
            avanzar();
        }
    }

    // ------------------------------------------------------
    // ye_input variable;
    // aqui le preguntamos el valor a la persona con una ventanita
    // ------------------------------------------------------
    private void ejecutarInput() {
        avanzar(); // ye_input
        String nombre = actual().getLexema();
        avanzar();

        String tipo = tipos.get(nombre);
        String pregunta = "dame un valor para '" + nombre + "' (" + tipo + ")";
        String texto = JOptionPane.showInputDialog(pregunta);

        valores.put(nombre, convertirTextoIngresado(texto, tipo));

        if (actualEs("SEMI")) {
            avanzar();
        }
    }

    // ------------------------------------------------------
    // ye_if ( condicion ) { ... } ye_else { ... }
    // ------------------------------------------------------
    private void ejecutarIf() {
        avanzar(); // ye_if
        avanzar(); // (

        int cierreParentesis = buscarToken("RPAREN", pos);
        boolean condicion = evaluarCondicion(cierreParentesis);

        pos = cierreParentesis;
        avanzar(); // )
        avanzar(); // {

        int inicioBloque = pos;

        if (condicion) {
            ejecutarBloque();
            avanzar(); // }
        } else {
            pos = buscarCierreLlave(inicioBloque);
            avanzar(); // }
        }

        if (actualEs("ELSE")) {
            avanzar(); // ye_else
            avanzar(); // {
            int inicioElse = pos;

            if (!condicion) {
                ejecutarBloque();
                avanzar(); // }
            } else {
                pos = buscarCierreLlave(inicioElse);
                avanzar(); // }
            }
        }
    }

    // ------------------------------------------------------
    // ct_while ( condicion ) { ... }
    // ------------------------------------------------------
    private void ejecutarWhile() {
        avanzar(); // ct_while
        avanzar(); // (

        int inicioCondicion = pos;
        int cierreParentesis = buscarToken("RPAREN", pos);

        pos = cierreParentesis;
        avanzar(); // )
        avanzar(); // {
        int inicioCuerpo = pos;
        int finCuerpo = buscarCierreLlave(inicioCuerpo);

        int vueltas = 0;
        while (true) {
            pos = inicioCondicion;
            boolean condicion = evaluarCondicion(cierreParentesis);
            if (!condicion) break;

            pos = inicioCuerpo;
            ejecutarBloque();

            vueltas = vueltas + 1;
            if (vueltas > LIMITE_VUELTAS) {
                salida.append("(el ciclo ct_while se detuvo, dio demasiadas vueltas)\n");
                break;
            }
        }

        pos = finCuerpo;
        avanzar(); // }
    }

    // ------------------------------------------------------
    // ct_for ( inicio ; condicion ; incremento ) { ... }
    // ------------------------------------------------------
    private void ejecutarFor() {
        avanzar(); // ct_for
        avanzar(); // (

        int inicioInit = pos;
        int primerPuntoComa = buscarToken("SEMI", pos);
        int inicioCondicion = primerPuntoComa + 1;
        int segundoPuntoComa = buscarToken("SEMI", inicioCondicion);
        int inicioIncremento = segundoPuntoComa + 1;
        int cierreParentesis = buscarToken("RPAREN", inicioIncremento);

        // ejecutamos la parte de inicio una sola vez
        pos = inicioInit;
        ejecutarInicioFor(primerPuntoComa);

        pos = cierreParentesis;
        avanzar(); // )
        avanzar(); // {
        int inicioCuerpo = pos;
        int finCuerpo = buscarCierreLlave(inicioCuerpo);

        int vueltas = 0;
        while (true) {
            pos = inicioCondicion;
            boolean condicion = evaluarCondicion(segundoPuntoComa);
            if (!condicion) break;

            pos = inicioCuerpo;
            ejecutarBloque();

            pos = inicioIncremento;
            ejecutarIncrementoFor(cierreParentesis);

            vueltas = vueltas + 1;
            if (vueltas > LIMITE_VUELTAS) {
                salida.append("(el ciclo ct_for se detuvo, dio demasiadas vueltas)\n");
                break;
            }
        }

        pos = finCuerpo;
        avanzar(); // }
    }

    // la parte de inicio del for puede traer un tipo (ct_int i = 0) o no (i = 0)
    private void ejecutarInicioFor(int limite) {
        if (esTipoDato(actual().getToken())) {
            String tipo = actual().getToken();
            avanzar();
            String nombre = actual().getLexema();
            avanzar();
            tipos.put(nombre, tipo);
            if (actualEs("ASSIGN")) {
                avanzar();
                Object valor = evaluarExpresion(limite);
                valores.put(nombre, convertirAlTipo(tipo, valor));
            }
        } else {
            String nombre = actual().getLexema();
            avanzar();
            avanzar(); // =
            Object valor = evaluarExpresion(limite);
            String tipo = tipos.get(nombre);
            valores.put(nombre, convertirAlTipo(tipo, valor));
        }
        pos = limite; // nos aseguramos de quedar justo en el punto y coma
    }

    // el incremento del for no trae punto y coma, solo termina en el limite dado
    private void ejecutarIncrementoFor(int limite) {
        if (pos >= limite) return;
        String nombre = actual().getLexema();
        avanzar();
        avanzar(); // =
        Object valor = evaluarExpresion(limite);
        String tipo = tipos.get(nombre);
        valores.put(nombre, convertirAlTipo(tipo, valor));
        pos = limite;
    }

    // ------------------------------------------------------
    // evaluar expresiones y condiciones
    // ojo: aqui las operaciones se hacen de izquierda a derecha,
    // sin jerarquia de operadores, para mantenerlo simple
    // ------------------------------------------------------

    // condicion: valor (comparador valor)? (y/o valor (comparador valor)?)*
    private boolean evaluarCondicion(int limite) {
        boolean resultado = evaluarComparacionSimple(limite);

        while (pos < limite && (actualEs("AND") || actualEs("OR"))) {
            String logico = actual().getToken();
            avanzar();
            boolean siguiente = evaluarComparacionSimple(limite);

            if (logico.equals("AND")) {
                resultado = resultado && siguiente;
            } else {
                resultado = resultado || siguiente;
            }
        }

        return resultado;
    }

    private boolean evaluarComparacionSimple(int limite) {
        Object izquierda = evaluarExpresion(limite);

        if (pos < limite && esComparador(actual().getToken())) {
            String comparador = actual().getToken();
            avanzar();
            Object derecha = evaluarExpresion(limite);
            return comparar(izquierda, comparador, derecha);
        }

        // si no hay comparador, el valor mismo se toma como verdadero o falso
        return aBooleano(izquierda);
    }

    // expresion: valor (operador valor)*
    private Object evaluarExpresion(int limite) {
        Object valor = leerValorSimple();

        while (pos < limite && esOperadorAritmetico(actual().getToken())) {
            String operador = actual().getToken();
            avanzar();
            Object siguiente = leerValorSimple();
            valor = operar(valor, operador, siguiente);
        }

        return valor;
    }

    // lee un solo valor: numero, texto, booleano, o variable (con o sin indice)
    private Object leerValorSimple() {
        Token t = actual();
        String tk = t.getToken();

        if (tk.equals("NUM")) {
            avanzar();
            return Integer.parseInt(t.getLexema());
        }
        if (tk.equals("FLOAT_LIT")) {
            avanzar();
            return Double.parseDouble(t.getLexema());
        }
        if (tk.equals("STRING_LIT")) {
            avanzar();
            return t.getLexema();
        }
        if (tk.equals("TRUE")) {
            avanzar();
            return Boolean.TRUE;
        }
        if (tk.equals("FALSE")) {
            avanzar();
            return Boolean.FALSE;
        }
        if (tk.equals("ID")) {
            String nombre = t.getLexema();
            avanzar();
            if (actualEs("LBRACKET")) {
                avanzar();
                Object indiceObj = leerValorSimple();
                avanzar(); // ]
                int indice = numeroEntero(indiceObj);
                Object[] arreglo = arreglos.get(nombre);
                if (arreglo != null && indice >= 0 && indice < arreglo.length) {
                    return arreglo[indice];
                }
                return 0;
            }
            Object valor = valores.get(nombre);
            return (valor == null) ? 0 : valor;
        }

        // algo no esperado, avanzamos para no quedarnos pegados
        avanzar();
        return 0;
    }

    // aplica un operador aritmetico entre dos valores
    private Object operar(Object a, String operador, Object b) {
        // si cualquiera de los dos es texto, solo se permite concatenar con +
        if (a instanceof String || b instanceof String) {
            if (operador.equals("PLUS")) {
                return aTexto(a) + aTexto(b);
            }
            return aTexto(a);
        }

        double x = aNumero(a);
        double y = aNumero(b);
        double resultado;

        if (operador.equals("PLUS")) resultado = x + y;
        else if (operador.equals("MINUS")) resultado = x - y;
        else if (operador.equals("MULT")) resultado = x * y;
        else if (operador.equals("DIV")) resultado = (y == 0) ? 0 : x / y;
        else if (operador.equals("MOD")) resultado = (y == 0) ? 0 : x % y;
        else resultado = 0;

        // si los dos valores originales eran enteros, el resultado tambien es entero
        if (a instanceof Integer && b instanceof Integer && operador.equals("DIV") == false) {
            return (int) resultado;
        }
        if (a instanceof Integer && b instanceof Integer && operador.equals("DIV")) {
            // division entre enteros: si es exacta la dejamos como entero
            if (y != 0 && x % y == 0) return (int) resultado;
            return resultado;
        }
        return resultado;
    }

    // compara dos valores segun el comparador
    private boolean comparar(Object a, String comparador, Object b) {
        if (a instanceof String || b instanceof String) {
            String sa = aTexto(a);
            String sb = aTexto(b);
            if (comparador.equals("EQ")) return sa.equals(sb);
            if (comparador.equals("NEQ")) return !sa.equals(sb);
            return false;
        }

        if (a instanceof Boolean || b instanceof Boolean) {
            boolean ba = aBooleano(a);
            boolean bb = aBooleano(b);
            if (comparador.equals("EQ")) return ba == bb;
            if (comparador.equals("NEQ")) return ba != bb;
            return false;
        }

        double x = aNumero(a);
        double y = aNumero(b);

        if (comparador.equals("EQ")) return x == y;
        if (comparador.equals("NEQ")) return x != y;
        if (comparador.equals("GT")) return x > y;
        if (comparador.equals("GTE")) return x >= y;
        if (comparador.equals("LT")) return x < y;
        if (comparador.equals("LTE")) return x <= y;
        return false;
    }

    // ------------------------------------------------------
    // funciones para convertir valores de un tipo a otro
    // ------------------------------------------------------

    private Object convertirAlTipo(String tipo, Object valor) {
        if (tipo == null) return valor;

        if (tipo.equals("INT")) return numeroEntero(valor);
        if (tipo.equals("FLOAT")) return aNumero(valor);
        if (tipo.equals("STRING")) return aTexto(valor);
        if (tipo.equals("BOOL")) return aBooleano(valor);

        return valor;
    }

    private Object convertirTextoIngresado(String texto, String tipo) {
        if (texto == null) texto = "";

        if (tipo == null) return texto;

        if (tipo.equals("INT")) {
            try {
                return Integer.parseInt(texto.trim());
            } catch (Exception error) {
                return 0;
            }
        }
        if (tipo.equals("FLOAT")) {
            try {
                return Double.parseDouble(texto.trim());
            } catch (Exception error) {
                return 0.0;
            }
        }
        if (tipo.equals("BOOL")) {
            return texto.trim().equalsIgnoreCase("ye_true") || texto.trim().equalsIgnoreCase("true");
        }

        return texto;
    }

    private Object valorPorDefecto(String tipo) {
        if (tipo.equals("INT")) return 0;
        if (tipo.equals("FLOAT")) return 0.0;
        if (tipo.equals("BOOL")) return Boolean.FALSE;
        return "";
    }

    private double aNumero(Object valor) {
        if (valor instanceof Integer) return (Integer) valor;
        if (valor instanceof Double) return (Double) valor;
        if (valor instanceof Boolean) return ((Boolean) valor) ? 1 : 0;
        return 0;
    }

    private int numeroEntero(Object valor) {
        return (int) aNumero(valor);
    }

    private boolean aBooleano(Object valor) {
        if (valor instanceof Boolean) return (Boolean) valor;
        if (valor instanceof Integer) return ((Integer) valor) != 0;
        if (valor instanceof Double) return ((Double) valor) != 0;
        if (valor instanceof String) return !((String) valor).isEmpty();
        return false;
    }

    private String aTexto(Object valor) {
        if (valor == null) return "";
        if (valor instanceof Double) {
            double d = (Double) valor;
            if (d == Math.floor(d)) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        if (valor instanceof Boolean) {
            return ((Boolean) valor) ? "ye_true" : "ye_false";
        }
        return String.valueOf(valor);
    }

    // ------------------------------------------------------
    // funciones pequenas de apoyo, moverse entre tokens
    // ------------------------------------------------------

    private void avanzar() {
        pos = pos + 1;
    }

    private Token actual() {
        if (pos < tokens.size()) {
            return tokens.get(pos);
        }
        return new Token("fin", "EOF", "fin de archivo", 0, 0);
    }

    private boolean actualEs(String tipoToken) {
        return actual().getToken().equals(tipoToken);
    }

    private boolean esTipoDato(String token) {
        return token.equals("INT") || token.equals("FLOAT") || token.equals("STRING") || token.equals("BOOL");
    }

    private boolean esOperadorAritmetico(String token) {
        return token.equals("PLUS") || token.equals("MINUS") || token.equals("MULT")
                || token.equals("DIV") || token.equals("MOD");
    }

    private boolean esComparador(String token) {
        return token.equals("EQ") || token.equals("NEQ") || token.equals("GT")
                || token.equals("GTE") || token.equals("LT") || token.equals("LTE");
    }

    // busca desde "desde" el primer token de un tipo dado
    private int buscarToken(String tipoBuscado, int desde) {
        int i = desde;
        while (i < tokens.size() && !tokens.get(i).getToken().equals(tipoBuscado)) {
            i = i + 1;
        }
        return i;
    }

    // busca el punto y coma que termina la sentencia actual
    private int buscarFinSentencia(int desde) {
        return buscarToken("SEMI", desde);
    }

    // "pos" debe estar justo despues de la llave que abre '{'
    // esta funcion busca la llave que la cierra, contando anidamientos
    private int buscarCierreLlave(int desde) {
        int i = desde;
        int profundidad = 1;
        while (i < tokens.size() && profundidad > 0) {
            String tk = tokens.get(i).getToken();
            if (tk.equals("LBRACE")) profundidad = profundidad + 1;
            else if (tk.equals("RBRACE")) profundidad = profundidad - 1;
            if (profundidad > 0) i = i + 1;
        }
        return i;
    }
}
