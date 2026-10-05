package yect;

import java.util.ArrayList;
import java.util.HashMap;

// este es el analizador sintactico
// su trabajo es revisar que los tokens esten en un orden correcto
public class AnalizadorSintactico {

    private ArrayList<Token> tokens;
    private int pos;

    private ArrayList<ErrorCompilador> errores;
    private HashMap<String, String> tablaSimbolos;
    private boolean exitoso;

    public AnalizadorSintactico() {
        errores = new ArrayList<>();
        tablaSimbolos = new HashMap<>();
        exitoso = false;
    }

    // metodo principal
    public void analizar(ArrayList<Token> listaTokens) {
        errores.clear();
        tablaSimbolos.clear();
        exitoso = false;

        tokens = listaTokens;
        pos = 0;

        if (tokens.isEmpty()) {
            agregarError("el programa esta vacio, se esperaba 'ye_init'", 1, 1);
            return;
        }

        if (!actualEs("INIT")) {
            agregarError("el programa debe comenzar con 'ye_init'", actual().getLinea(), actual().getColumna());
            return;
        }
        avanzar();

        revisarBloque();

        if (pos < tokens.size() && actualEs("END")) {
            avanzar();
        } else {
            agregarError("falta 'kanye_end' para cerrar el programa", ultimaLinea(), 0);
            return;
        }

        if (errores.isEmpty()) {
            exitoso = true;
        }
    }

    // revisa sentencias hasta encontrar el final del programa o '}'
    private void revisarBloque() {
        while (pos < tokens.size() && !actualEs("END") && !actualEs("RBRACE")) {
            String t = actual().getToken();

            if (t.equals("INT") || t.equals("FLOAT") || t.equals("STRING") || t.equals("BOOL")) {
                revisarDeclaracion();
            } else if (t.equals("ID")) {
                revisarAsignacion();
            } else if (t.equals("IF")) {
                revisarIf();
            } else if (t.equals("FOR")) {
                revisarFor();
            } else if (t.equals("WHILE")) {
                revisarWhile();
            } else if (t.equals("PRINT")) {
                revisarPrint();
            } else if (t.equals("INPUT")) {
                revisarInput();
            } else {
                agregarError("no se esperaba el simbolo '" + actual().getLexema() + "' aqui",
                        actual().getLinea(), actual().getColumna());
                avanzar();
            }
        }
    }

    // revisa la declaracion de variables
    private void revisarDeclaracion() {
        int linea = actual().getLinea();
        String tipo = actual().getToken();
        avanzar();

        boolean esArreglo = false;
        if (actualEs("LBRACKET")) {
            avanzar();
            if (!actualEs("RBRACKET")) {
                agregarError("falta ']' en la declaracion del arreglo", linea, 0);
                return;
            }
            avanzar();
            esArreglo = true;
        }

        if (!actualEs("ID")) {
            agregarError("se esperaba el nombre de la variable", linea, 0);
            return;
        }
        String nombre = actual().getLexema();
        avanzar();

        tablaSimbolos.put(nombre, esArreglo ? tipo + "[]" : tipo);

        if (actualEs("ASSIGN")) {
            avanzar();
            if (esArreglo) {
                revisarInicializacionArreglo(nombre, linea);
            } else {
                revisarValorAsignado(nombre, linea);
            }
        }

        if (!actualEs("SEMI")) {
            agregarError("falta ';' al final de la declaracion de '" + nombre + "'", linea, 0);
            return;
        }
        avanzar();
    }

    // revisa expresiones a la derecha del '=' soportando formulas y casteos
    private void revisarValorAsignado(String nombre, int linea) {
        // 1. comprueba si inicia con un casteo explicito como (ct_int)
        if (esInicioCasteo()) {
            avanzar(); // pasa '('
            avanzar(); // pasa el tipo de dato
            if (actualEs("RPAREN")) {
                avanzar(); // pasa ')'
            } else {
                agregarError("falta ')' en el casteo explicito", linea, 0);
            }
        }

        // 2. recorre todos los elementos de la expresion hasta encontrar ';'
        int contadorParentesis = 0;

        while (pos < tokens.size() && !actualEs("SEMI") && !actualEs("END") && !actualEs("RBRACE")) {
            String tokenActual = actual().getToken();

            // detecta si se puso otro signo '=' dentro de la misma asignacion
            if (tokenActual.equals("ASSIGN")) {
                agregarError("no se permite otro '=' dentro de la asignacion de '" + nombre + "'", linea, actual().getColumna());
                avanzar();
                continue;
            }

            if (tokenActual.equals("LPAREN")) {
                contadorParentesis++;
                avanzar();
                continue;
            }

            if (tokenActual.equals("RPAREN")) {
                contadorParentesis--;
                if (contadorParentesis < 0) {
                    agregarError("hay un ')' de mas en la expresion", linea, actual().getColumna());
                }
                avanzar();
                continue;
            }

            if (esOperadorAritmetico(tokenActual)) {
                avanzar();
                continue;
            }

            if (esValorValido(tokenActual)) {
                revisarValorConIndice();
                continue;
            }

            agregarError("no se esperaba el simbolo '" + actual().getLexema() + "' en la expresion", linea, actual().getColumna());
            avanzar();
        }

        if (contadorParentesis > 0) {
            agregarError("falta cerrar ')' en la expresion de '" + nombre + "'", linea, 0);
        }
    }

    // verifica si los proximos tokens forman la estructura de un casteo: '(' + tipo + ')'
    private boolean esInicioCasteo() {
        return pos + 2 < tokens.size() &&
               tokens.get(pos).getToken().equals("LPAREN") &&
               esTipoDato(tokens.get(pos + 1).getToken()) &&
               tokens.get(pos + 2).getToken().equals("RPAREN");
    }

    private void revisarValorConIndice() {
        avanzar();
        if (actualEs("LBRACKET")) {
            avanzar();
            if (actualEs("NUM") || actualEs("ID")) avanzar();
            if (actualEs("RBRACKET")) avanzar();
        }
    }

    private void revisarInicializacionArreglo(String nombre, int linea) {
        if (!actualEs("NEW")) {
            agregarError("se esperaba 'new' para crear el arreglo '" + nombre + "'", linea, 0);
            saltarHasta("SEMI");
            return;
        }
        avanzar();

        if (!esTipoDato(actual().getToken())) {
            agregarError("se esperaba el tipo del arreglo despues de 'new'", linea, 0);
            saltarHasta("SEMI");
            return;
        }
        avanzar();

        if (!actualEs("LBRACKET")) {
            agregarError("se esperaba '[' para el tamano del arreglo '" + nombre + "'", linea, 0);
            saltarHasta("SEMI");
            return;
        }
        avanzar();

        if (!actualEs("NUM") && !actualEs("ID")) {
            agregarError("el tamano del arreglo '" + nombre + "' debe ser un numero", linea, 0);
            saltarHasta("SEMI");
            return;
        }
        avanzar();

        if (!actualEs("RBRACKET")) {
            agregarError("se esperaba ']' para cerrar el tamano del arreglo '" + nombre + "'", linea, 0);
            saltarHasta("SEMI");
            return;
        }
        avanzar();
    }

    private void revisarAsignacion() {
        int linea = actual().getLinea();
        String nombre = actual().getLexema();
        avanzar();

        if (actualEs("LBRACKET")) {
            avanzar();
            if (!actualEs("NUM") && !actualEs("ID")) {
                agregarError("el indice de '" + nombre + "[]' debe ser un numero o variable", linea, 0);
                saltarHasta("SEMI");
                if (actualEs("SEMI")) avanzar();
                return;
            }
            avanzar();
            if (!actualEs("RBRACKET")) {
                agregarError("falta ']' en el acceso a '" + nombre + "[]'", linea, 0);
                saltarHasta("SEMI");
                if (actualEs("SEMI")) avanzar();
                return;
            }
            avanzar();
        }

        if (!actualEs("ASSIGN")) {
            agregarError("se esperaba '=' despues de '" + nombre + "'", linea, 0);
            saltarHasta("SEMI");
            if (actualEs("SEMI")) avanzar();
            return;
        }
        avanzar();

        revisarValorAsignado(nombre, linea);

        if (!actualEs("SEMI")) {
            agregarError("falta ';' al final de la asignacion de '" + nombre + "'", linea, 0);
            return;
        }
        avanzar();
    }

    private void revisarIf() {
        int linea = actual().getLinea();
        avanzar();

        if (!actualEs("LPAREN")) { agregarError("se esperaba '(' despues de 'ye_if'", linea, 0); return; }
        avanzar();

        revisarCondicion(linea);

        if (!actualEs("RPAREN")) { agregarError("falta ')' en 'ye_if'", linea, 0); return; }
        avanzar();

        if (!actualEs("LBRACE")) { agregarError("se esperaba '{' despues de 'ye_if (...)'", linea, 0); return; }
        avanzar();
        revisarBloque();
        if (!actualEs("RBRACE")) { agregarError("falta '}' para cerrar 'ye_if'", linea, 0); return; }
        avanzar();

        if (actualEs("ELSE")) {
            int lineaElse = actual().getLinea();
            avanzar();
            if (!actualEs("LBRACE")) { agregarError("se esperaba '{' despues de 'ye_else'", lineaElse, 0); return; }
            avanzar();
            revisarBloque();
            if (!actualEs("RBRACE")) { agregarError("falta '}' para cerrar 'ye_else'", lineaElse, 0); return; }
            avanzar();
        }
    }

    private void revisarFor() {
        int linea = actual().getLinea();
        avanzar();

        if (!actualEs("LPAREN")) { agregarError("se esperaba '(' despues de 'ct_for'", linea, 0); return; }
        avanzar();

        if (esTipoDato(actual().getToken())) {
            avanzar();
        }
        if (actualEs("ID")) {
            String nombreVar = actual().getLexema();
            if (!tablaSimbolos.containsKey(nombreVar)) {
                tablaSimbolos.put(nombreVar, "INT");
            }
        }
        saltarHasta("SEMI");
        if (!actualEs("SEMI")) { agregarError("falta ';' en el inicio de 'ct_for'", linea, 0); return; }
        avanzar();

        revisarCondicion(linea);
        if (!actualEs("SEMI")) { agregarError("falta ';' despues de la condicion de 'ct_for'", linea, 0); return; }
        avanzar();

        saltarHasta("RPAREN");
        if (!actualEs("RPAREN")) { agregarError("falta ')' en 'ct_for'", linea, 0); return; }
        avanzar();

        if (!actualEs("LBRACE")) { agregarError("se esperaba '{' despues de 'ct_for (...)'", linea, 0); return; }
        avanzar();
        revisarBloque();
        if (!actualEs("RBRACE")) { agregarError("falta '}' para cerrar 'ct_for'", linea, 0); return; }
        avanzar();
    }

    private void revisarWhile() {
        int linea = actual().getLinea();
        avanzar();

        if (!actualEs("LPAREN")) { agregarError("se esperaba '(' despues de 'ct_while'", linea, 0); return; }
        avanzar();

        revisarCondicion(linea);

        if (!actualEs("RPAREN")) { agregarError("falta ')' en 'ct_while'", linea, 0); return; }
        avanzar();

        if (!actualEs("LBRACE")) { agregarError("se esperaba '{' despues de 'ct_while (...)'", linea, 0); return; }
        avanzar();
        revisarBloque();
        if (!actualEs("RBRACE")) { agregarError("falta '}' para cerrar 'ct_while'", linea, 0); return; }
        avanzar();
    }

    private void revisarPrint() {
        int linea = actual().getLinea();
        avanzar();

        if (!esValorValido(actual().getToken())) {
            agregarError("'ye_print' necesita algo para imprimir", linea, 0);
            saltarHasta("SEMI");
        } else {
            revisarValorConIndice();
            while (esOperadorAritmetico(actual().getToken())) {
                avanzar();
                if (!esValorValido(actual().getToken())) {
                    agregarError("se esperaba un valor despues del operador en 'ye_print'", linea, 0);
                    saltarHasta("SEMI");
                    break;
                }
                revisarValorConIndice();
            }
        }

        if (!actualEs("SEMI")) { agregarError("falta ';' despues de 'ye_print'", linea, 0); return; }
        avanzar();
    }

    private void revisarInput() {
        int linea = actual().getLinea();
        avanzar();

        if (!actualEs("ID")) {
            agregarError("'ye_input' necesita el nombre de una variable", linea, 0);
            return;
        }
        avanzar();

        if (!actualEs("SEMI")) { agregarError("falta ';' despues de 'ye_input'", linea, 0); return; }
        avanzar();
    }

    private void revisarCondicion(int linea) {
        if (!esValorValido(actual().getToken())) {
            agregarError("se esperaba una condicion valida", linea, 0);
            return;
        }
        revisarValorConIndice();

        if (esComparador(actual().getToken())) {
            avanzar();
            if (!esValorValido(actual().getToken())) {
                agregarError("se esperaba un valor despues del comparador", linea, 0);
                return;
            }
            revisarValorConIndice();
        }

        while (actualEs("AND") || actualEs("OR")) {
            avanzar();
            if (!esValorValido(actual().getToken())) {
                agregarError("se esperaba un valor despues de '&&' u '||'", linea, 0);
                return;
            }
            revisarValorConIndice();
            if (esComparador(actual().getToken())) {
                avanzar();
                if (!esValorValido(actual().getToken())) {
                    agregarError("se esperaba un valor despues del comparador", linea, 0);
                    return;
                }
                revisarValorConIndice();
            }
        }
    }

    private void avanzar() {
        pos = pos + 1;
    }

    private Token actual() {
        if (pos < tokens.size()) {
            return tokens.get(pos);
        }
        return new Token("fin", "EOF", "fin de archivo", ultimaLinea(), 0);
    }

    private int ultimaLinea() {
        if (tokens.isEmpty()) return 1;
        return tokens.get(tokens.size() - 1).getLinea();
    }

    private boolean actualEs(String tipoToken) {
        return actual().getToken().equals(tipoToken);
    }

    private boolean esTipoDato(String token) {
        return token.equals("INT") || token.equals("FLOAT") || token.equals("STRING") || token.equals("BOOL");
    }

    private boolean esValorValido(String token) {
        return token.equals("NUM") || token.equals("FLOAT_LIT") || token.equals("STRING_LIT")
                || token.equals("TRUE") || token.equals("FALSE") || token.equals("ID");
    }

    private boolean esOperadorAritmetico(String token) {
        return token.equals("PLUS") || token.equals("MINUS") || token.equals("MULT")
                || token.equals("DIV") || token.equals("MOD");
    }

    private boolean esComparador(String token) {
        return token.equals("EQ") || token.equals("NEQ") || token.equals("GT")
                || token.equals("GTE") || token.equals("LT") || token.equals("LTE");
    }

    private void saltarHasta(String tipoBuscado) {
        while (pos < tokens.size() && !actualEs(tipoBuscado)) {
            if (actualEs("LBRACE")) {
                saltarBloqueLlaves();
            } else {
                avanzar();
            }
        }
    }

    private void saltarBloqueLlaves() {
        avanzar();
        int profundidad = 1;
        while (pos < tokens.size() && profundidad > 0) {
            if (actualEs("LBRACE")) profundidad = profundidad + 1;
            else if (actualEs("RBRACE")) profundidad = profundidad - 1;
            avanzar();
        }
    }

    private void agregarError(String mensaje, int linea, int columna) {
        errores.add(new ErrorCompilador(mensaje, linea, columna, "SINTACTICO"));
    }

    public ArrayList<ErrorCompilador> getErrores() {
        return errores;
    }

    public boolean isExitoso() {
        return exitoso;
    }

    public HashMap<String, String> getTablaSimbolos() {
        return tablaSimbolos;
    }
}