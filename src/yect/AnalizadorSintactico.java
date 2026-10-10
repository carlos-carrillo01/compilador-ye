package yect;

import java.util.ArrayList;
import java.util.HashMap;

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

    // Punto de entrada: valida que el programa vaya entre ye_init y kanye_end
    // y deja la tabla de simbolos lista para el analizador semantico.
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

    // Revisa una sentencia tras otra hasta encontrar el final del bloque actual.
    private void revisarBloque() {
        while (pos < tokens.size() && !actualEs("END") && !actualEs("RBRACE")) {
            String t = actual().getToken();

            if (esTipoDato(t)) {
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

    // Declaracion: tipo [ [] ] nombre [ = valor ] ;  y guarda el tipo en la tabla de simbolos.
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

    // Revisa la forma de una expresion asignada. Los casteos se aceptan en cualquier
    // posicion donde pueda ir un valor; si la conversion tiene sentido lo decide el semantico.
    private void revisarValorAsignado(String nombre, int linea) {
        int contadorParentesis = 0;

        while (pos < tokens.size() && !actualEs("SEMI") && !actualEs("END") && !actualEs("RBRACE")) {
            String tokenActual = actual().getToken();

            if (esInicioCasteo()) {
                consumirCasteo(linea);
                continue;
            }

            // Se escribio '(' y un tipo pero no se cerro el parentesis del casteo.
            if (esCasteoIncompleto()) {
                agregarError("falta ')' en el casteo explicito", linea, actual().getColumna());
                avanzar();
                avanzar();
                contadorParentesis++;
                continue;
            }

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

    // Un casteo explicito tiene la forma: ( tipo ) y se mira sin consumir tokens.
    private boolean esInicioCasteo() {
        return pos + 2 < tokens.size() &&
               tokens.get(pos).getToken().equals("LPAREN") &&
               esTipoDato(tokens.get(pos + 1).getToken()) &&
               tokens.get(pos + 2).getToken().equals("RPAREN");
    }

    // Parentesis de apertura seguido de un tipo pero sin el parentesis de cierre.
    private boolean esCasteoIncompleto() {
        return pos + 2 < tokens.size() &&
               tokens.get(pos).getToken().equals("LPAREN") &&
               esTipoDato(tokens.get(pos + 1).getToken()) &&
               !tokens.get(pos + 2).getToken().equals("RPAREN");
    }

    // Consume los tres tokens del casteo y exige que despues venga un valor o un grupo '('.
    private void consumirCasteo(int linea) {
        avanzar();
        avanzar();
        avanzar();

        if (!esValorValido(actual().getToken()) && !actualEs("LPAREN")) {
            agregarError("se esperaba un valor despues del casteo", linea, actual().getColumna());
        }
    }

    // Consume todos los casteos seguidos, por ejemplo (ct_string)(ct_int) x
    private void omitirCasteos(int linea) {
        while (esInicioCasteo()) {
            consumirCasteo(linea);
        }
    }

    // Consume un valor y, si trae indice de arreglo, tambien el indice.
    private void revisarValorConIndice() {
        avanzar();
        if (actualEs("LBRACKET")) {
            avanzar();
            if (actualEs("NUM") || actualEs("ID")) avanzar();
            if (actualEs("RBRACKET")) avanzar();
        }
    }

    // Revisa un operando (con casteos opcionales); devuelve false si falta el valor.
    private boolean revisarOperando(int linea, String mensajeError) {
        omitirCasteos(linea);
        if (!esValorValido(actual().getToken())) {
            agregarError(mensajeError, linea, 0);
            return false;
        }
        revisarValorConIndice();
        return true;
    }

    // Revisa: new tipo [ tamano ]
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

    // Asignacion: nombre [ [indice] ] = valor ;
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

    // ct_for ( [tipo] variable = valor ; condicion ; incremento ) { ... }
    private void revisarFor() {
        int linea = actual().getLinea();
        avanzar();

        if (!actualEs("LPAREN")) { agregarError("se esperaba '(' despues de 'ct_for'", linea, 0); return; }
        avanzar();

        String tipoVarFor = "";
        if (esTipoDato(actual().getToken())) {
            tipoVarFor = actual().getToken();
            avanzar();
        }

        if (actualEs("ID")) {
            String nombreVar = actual().getLexema();
            if (!tipoVarFor.isEmpty()) {
                tablaSimbolos.put(nombreVar, tipoVarFor);
            } else if (!tablaSimbolos.containsKey(nombreVar)) {
                tablaSimbolos.put(nombreVar, "INT");
            }
            avanzar();
        }

        if (actualEs("ASSIGN")) {
            avanzar();
            revisarValorAsignado("variable_for", linea);
        }

        if (!actualEs("SEMI")) { agregarError("falta ';' en la inicializacion de 'ct_for'", linea, 0); return; }
        avanzar();

        revisarCondicion(linea);
        if (!actualEs("SEMI")) { agregarError("falta ';' despues de la condicion de 'ct_for'", linea, 0); return; }
        avanzar();

        // El incremento se salta completo; los parentesis internos (como los de un casteo) no cierran el for.
        saltarHastaCierreParentesis();
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

    // ye_print valor [ operador valor ]... ;  (los valores pueden llevar casteo)
    private void revisarPrint() {
        int linea = actual().getLinea();
        avanzar();

        if (!revisarOperando(linea, "'ye_print' necesita algo para imprimir")) {
            saltarHasta("SEMI");
        } else {
            while (esOperadorAritmetico(actual().getToken())) {
                avanzar();
                if (!revisarOperando(linea, "se esperaba un valor despues del operador en 'ye_print'")) {
                    saltarHasta("SEMI");
                    break;
                }
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

    // Condicion: operando [comparador operando] [(&& | ||) operando [comparador operando]]...
    private void revisarCondicion(int linea) {
        if (!revisarOperando(linea, "se esperaba una condicion valida")) {
            return;
        }

        if (esComparador(actual().getToken())) {
            avanzar();
            if (!revisarOperando(linea, "se esperaba un valor despues del comparador")) {
                return;
            }
        }

        while (actualEs("AND") || actualEs("OR")) {
            avanzar();
            if (!revisarOperando(linea, "se esperaba un valor despues de '&&' u '||'")) {
                return;
            }
            if (esComparador(actual().getToken())) {
                avanzar();
                if (!revisarOperando(linea, "se esperaba un valor despues del comparador")) {
                    return;
                }
            }
        }
    }

    private void avanzar() {
        pos = pos + 1;
    }

    // Token actual; si ya no hay tokens devuelve uno de fin de archivo.
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

    // Recuperacion de errores: avanza hasta el token buscado sin entrar a bloques con llaves.
    private void saltarHasta(String tipoBuscado) {
        while (pos < tokens.size() && !actualEs(tipoBuscado)) {
            if (actualEs("LBRACE")) {
                saltarBloqueLlaves();
            } else {
                avanzar();
            }
        }
    }

    // Avanza hasta el ')' que cierra el encabezado, ignorando los parentesis anidados.
    private void saltarHastaCierreParentesis() {
        int profundidad = 0;
        while (pos < tokens.size()) {
            if (actualEs("RPAREN") && profundidad == 0) {
                break;
            }
            if (actualEs("LPAREN")) {
                profundidad = profundidad + 1;
            } else if (actualEs("RPAREN")) {
                profundidad = profundidad - 1;
            }

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