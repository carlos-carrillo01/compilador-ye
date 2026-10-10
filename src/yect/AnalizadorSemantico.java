package yect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class AnalizadorSemantico {

    private ArrayList<Token> tokens;
    private HashMap<String, String> tablaSimbolos;
    private ArrayList<ErrorCompilador> errores;
    private boolean exitoso;

    public AnalizadorSemantico() {
        errores = new ArrayList<>();
        exitoso = false;
    }

    // Punto de entrada: recibe los tokens y la tabla de simbolos que armo el sintactico.
    public void analizar(ArrayList<Token> listaTokens, HashMap<String, String> tabla) {
        errores.clear();
        exitoso = false;

        this.tokens = listaTokens;
        this.tablaSimbolos = tabla;

        if (tablaSimbolos == null) {
            exitoso = true;
            return;
        }

        revisarEstructurasYVariables();

        if (errores.isEmpty()) {
            exitoso = true;
        }
    }

    // Recorre los tokens una sola vez y aplica la revision que corresponda a cada uno.
    private void revisarEstructurasYVariables() {
        HashSet<String> yaDeclaradas = new HashSet<>();
        int i = 0;

        while (i < tokens.size()) {
            String nombreToken = tokens.get(i).getToken();

            if (esTipoDato(nombreToken)) {
                i = revisarDeclaracion(i, yaDeclaradas);
                continue;
            }

            if (nombreToken.equals("ID")) {
                revisarUsoDeVariable(i);
            }

            if (esInicioCasteo(i)) {
                validarCasteo(i);
            }

            if (nombreToken.equals("IF") || nombreToken.equals("WHILE") || nombreToken.equals("FOR")) {
                validarCondicionEstructura(i);
            }

            i++;
        }
    }

    // Revisa una declaracion (tipo [] nombre = valor) y devuelve la posicion desde donde seguir.
    private int revisarDeclaracion(int i, HashSet<String> yaDeclaradas) {
        Token tokenTipo = tokens.get(i);
        String tipoDeclarado = tokenTipo.getToken();
        int posNombre = i + 1;

        boolean esArreglo = false;
        if (posNombre + 1 < tokens.size() && tokens.get(posNombre).getToken().equals("LBRACKET")
                && tokens.get(posNombre + 1).getToken().equals("RBRACKET")) {
            esArreglo = true;
            posNombre += 2;
        }

        if (posNombre >= tokens.size() || !tokens.get(posNombre).getToken().equals("ID")) {
            return i + 1;
        }

        Token tokenNombre = tokens.get(posNombre);
        String nombreVariable = tokenNombre.getLexema();

        if (yaDeclaradas.contains(nombreVariable)) {
            errores.add(new ErrorCompilador(
                    "la variable '" + nombreVariable + "' ya habia sido declarada antes",
                    tokenNombre.getLinea(), tokenNombre.getColumna(), "SEMANTICO"));
        } else {
            yaDeclaradas.add(nombreVariable);
        }

        // La variable de control de un ct_for debe ser numerica: ct_for ( tipo variable ...
        boolean esVariableControlFor = (i > 1 && tokens.get(i - 1).getToken().equals("LPAREN")
                && tokens.get(i - 2).getToken().equals("FOR"));

        if (esVariableControlFor && (tipoDeclarado.equals("STRING") || tipoDeclarado.equals("BOOL"))) {
            errores.add(new ErrorCompilador(
                    "la variable de control '" + nombreVariable + "' en 'ct_for' debe ser numerica (ct_int o ct_float)",
                    tokenTipo.getLinea(), tokenTipo.getColumna(), "SEMANTICO"));
        }

        // Los arreglos se inicializan con 'new', por eso solo se validan los valores simples.
        int posAsignacion = posNombre + 1;
        if (!esArreglo && posAsignacion < tokens.size() && tokens.get(posAsignacion).getToken().equals("ASSIGN")) {
            validarAsignacion(tipoDeclarado, nombreVariable, posAsignacion + 1);
        }

        return posNombre + 1;
    }

    // Revisa un identificador que no esta siendo declarado: debe existir y, si se le asigna, el valor debe ser compatible.
    private void revisarUsoDeVariable(int i) {
        Token tokenActual = tokens.get(i);
        String nombreVariable = tokenActual.getLexema();

        if (!tablaSimbolos.containsKey(nombreVariable)) {
            errores.add(new ErrorCompilador(
                    "la variable '" + nombreVariable + "' se uso sin haber sido declarada",
                    tokenActual.getLinea(), tokenActual.getColumna(), "SEMANTICO"));
            return;
        }

        int posSiguiente = i + 1;
        boolean tieneIndice = posSiguiente < tokens.size() && tokens.get(posSiguiente).getToken().equals("LBRACKET");
        if (tieneIndice) {
            posSiguiente = posTrasIndice(posSiguiente);
        }

        if (posSiguiente < tokens.size() && tokens.get(posSiguiente).getToken().equals("ASSIGN")) {
            String tipoVar = tablaSimbolos.get(nombreVariable);
            // Con indice (arreglo[2] = ...) se compara contra el tipo de los elementos.
            if (tieneIndice) {
                tipoVar = tipoBase(tipoVar);
            }
            validarAsignacion(tipoVar, nombreVariable, posSiguiente + 1);
        }
    }

    // Valida todos los tokens del valor asignado contra el tipo de la variable destino.
    private void validarAsignacion(String tipoEsperado, String nombreVariable, int posValor) {
        int posFin = buscarFinExpresion(posValor);
        int k = posValor;

        while (k < posFin) {
            Token t = tokens.get(k);
            String tokType = t.getToken();

            // Un casteo cambia el tipo del valor que acompana: se revisa el tipo que produce y se salta su operando.
            if (esInicioCasteo(k)) {
                validarResultadoCasteo(tokens.get(k + 1).getToken(), tipoEsperado, nombreVariable, t);
                k = posFinOperando(k + 3);
                continue;
            }

            // El contenido de un indice de arreglo no es parte del valor asignado.
            if (tokType.equals("LBRACKET")) {
                k = posTrasIndice(k);
                continue;
            }

            validarLiteral(tipoEsperado, nombreVariable, t);
            validarVariableOrigen(tipoEsperado, nombreVariable, t);

            if (esOperadorAritmetico(tokType) && (tipoEsperado.equals("STRING") || tipoEsperado.equals("BOOL"))) {
                errores.add(new ErrorCompilador(
                        "no se permiten operaciones aritmeticas en variables de tipo " + tipoEsperado + " ('" + nombreVariable + "')",
                        t.getLinea(), t.getColumna(), "SEMANTICO"));
            }

            k++;
        }
    }

    // Compatibilidad de un valor literal (numero, texto, true/false) con el tipo de la variable.
    private void validarLiteral(String tipoEsperado, String nombreVariable, Token t) {
        String tokType = t.getToken();

        if (tipoEsperado.equals("STRING")) {
            if (tokType.equals("NUM") || tokType.equals("FLOAT_LIT")) {
                errores.add(new ErrorCompilador(
                        "no se puede asignar un numero a la variable '" + nombreVariable + "' de tipo ct_string",
                        t.getLinea(), t.getColumna(), "SEMANTICO"));
            } else if (tokType.equals("TRUE") || tokType.equals("FALSE")) {
                errores.add(new ErrorCompilador(
                        "no se puede asignar un valor booleano a la variable '" + nombreVariable + "' de tipo ct_string",
                        t.getLinea(), t.getColumna(), "SEMANTICO"));
            }
        } else if (tipoEsperado.equals("INT") || tipoEsperado.equals("FLOAT")) {
            if (tokType.equals("STRING_LIT")) {
                errores.add(new ErrorCompilador(
                        "no se puede asignar texto a la variable numerica '" + nombreVariable + "'",
                        t.getLinea(), t.getColumna(), "SEMANTICO"));
            } else if (tokType.equals("TRUE") || tokType.equals("FALSE")) {
                errores.add(new ErrorCompilador(
                        "no se puede asignar un valor booleano ('" + t.getLexema() + "') a la variable numerica '" + nombreVariable + "'",
                        t.getLinea(), t.getColumna(), "SEMANTICO"));
            }
        } else if (tipoEsperado.equals("BOOL")) {
            if (tokType.equals("NUM") || tokType.equals("FLOAT_LIT") || tokType.equals("STRING_LIT")) {
                errores.add(new ErrorCompilador(
                        "no se puede asignar un valor no booleano a la variable '" + nombreVariable + "' de tipo ct_bool",
                        t.getLinea(), t.getColumna(), "SEMANTICO"));
            }
        }
    }

    // Compatibilidad de una variable usada dentro del valor con el tipo de la variable destino.
    private void validarVariableOrigen(String tipoEsperado, String nombreVariable, Token t) {
        if (!t.getToken().equals("ID")) {
            return;
        }

        String varOrigen = t.getLexema();
        String tipoOrigen = tipoBase(tablaSimbolos.get(varOrigen));
        if (tipoOrigen == null) {
            return;
        }

        if ((tipoEsperado.equals("INT") || tipoEsperado.equals("FLOAT"))
                && (tipoOrigen.equals("STRING") || tipoOrigen.equals("BOOL"))) {
            errores.add(new ErrorCompilador(
                    "la variable '" + varOrigen + "' es de tipo " + tipoOrigen + " y no se puede usar para la variable numerica '" + nombreVariable + "'",
                    t.getLinea(), t.getColumna(), "SEMANTICO"));
        } else if (tipoEsperado.equals("STRING") && !tipoOrigen.equals("STRING")) {
            errores.add(new ErrorCompilador(
                    "la variable '" + varOrigen + "' es de tipo " + tipoOrigen + " y no se puede asignar a la variable '" + nombreVariable + "' de tipo ct_string",
                    t.getLinea(), t.getColumna(), "SEMANTICO"));
        } else if (tipoEsperado.equals("BOOL") && !tipoOrigen.equals("BOOL")) {
            errores.add(new ErrorCompilador(
                    "la variable '" + varOrigen + "' es de tipo " + tipoOrigen + " y no se puede asignar a la variable '" + nombreVariable + "' de tipo ct_bool",
                    t.getLinea(), t.getColumna(), "SEMANTICO"));
        }
    }

    // Comprueba que el casteo en la posicion i convierta entre tipos que el lenguaje permite.
    private void validarCasteo(int i) {
        String destino = tokens.get(i + 1).getToken();
        String origen = tipoDeOperando(i + 3);

        if (origen != null && !esCasteoPermitido(origen, destino)) {
            Token t = tokens.get(i);
            errores.add(new ErrorCompilador(
                    "no se puede convertir un valor de tipo " + nombreTipo(origen) + " a " + nombreTipo(destino) + " con un casteo explicito",
                    t.getLinea(), t.getColumna(), "SEMANTICO"));
        }
    }

    // Tabla de conversiones explicitas permitidas segun el tipo de origen y el tipo del casteo.
    // Un mismo tipo siempre se permite; ct_float no se convierte a ct_bool ni ct_bool a ct_float.
    private boolean esCasteoPermitido(String origen, String destino) {
        if (origen.equals(destino)) {
            return true;
        }
        switch (origen) {
            case "INT":
                return true;
            case "STRING":
                return true;
            case "FLOAT":
                return !destino.equals("BOOL");
            case "BOOL":
                return !destino.equals("FLOAT");
            default:
                return false;
        }
    }

    // El valor que produce un casteo debe pertenecer a la misma familia de tipos que la variable destino.
    private void validarResultadoCasteo(String tipoCasteo, String tipoEsperado, String nombreVariable, Token tokenCasteo) {
        String familiaEsperada = familiaDeTipo(tipoEsperado);
        String familiaCasteo = familiaDeTipo(tipoCasteo);

        if (familiaEsperada != null && !familiaEsperada.equals(familiaCasteo)) {
            errores.add(new ErrorCompilador(
                    "el casteo a " + nombreTipo(tipoCasteo) + " produce un valor que no es compatible con la variable '"
                            + nombreVariable + "' de tipo " + nombreTipo(tipoEsperado),
                    tokenCasteo.getLinea(), tokenCasteo.getColumna(), "SEMANTICO"));
        }
    }

    // Busca el tipo de un valor: literal, variable, o el tipo de otro casteo si estan encadenados.
    // Devuelve null cuando no se puede saber (por ejemplo, un grupo entre parentesis).
    private String tipoDeOperando(int posOperando) {
        if (posOperando >= tokens.size()) {
            return null;
        }
        if (esInicioCasteo(posOperando)) {
            return tokens.get(posOperando + 1).getToken();
        }

        Token t = tokens.get(posOperando);
        switch (t.getToken()) {
            case "NUM":
                return "INT";
            case "FLOAT_LIT":
                return "FLOAT";
            case "STRING_LIT":
                return "STRING";
            case "TRUE":
            case "FALSE":
                return "BOOL";
            case "ID":
                return tipoBase(tablaSimbolos.get(t.getLexema()));
            default:
                return null;
        }
    }

    // Posicion siguiente al operando que empieza en pos: salta casteos encadenados, grupos con parentesis e indices.
    private int posFinOperando(int pos) {
        if (pos >= tokens.size()) {
            return pos;
        }
        if (esInicioCasteo(pos)) {
            return posFinOperando(pos + 3);
        }
        if (tokens.get(pos).getToken().equals("LPAREN")) {
            int profundidad = 1;
            int p = pos + 1;
            while (p < tokens.size() && profundidad > 0) {
                if (tokens.get(p).getToken().equals("LPAREN")) profundidad++;
                if (tokens.get(p).getToken().equals("RPAREN")) profundidad--;
                p++;
            }
            return p;
        }
        if (pos + 1 < tokens.size() && tokens.get(pos + 1).getToken().equals("LBRACKET")) {
            return posTrasIndice(pos + 1);
        }
        return pos + 1;
    }

    // Dado el '[' de un indice, devuelve la posicion siguiente al ']' que lo cierra.
    private int posTrasIndice(int posCorchete) {
        int p = posCorchete + 1;
        while (p < tokens.size() && !tokens.get(p).getToken().equals("RBRACKET")) {
            p++;
        }
        return p + 1;
    }

    // La expresion termina en ';' o en el ')' que cierra el encabezado, sin contar parentesis internos.
    private int buscarFinExpresion(int posValor) {
        int profundidad = 0;
        int p = posValor;

        while (p < tokens.size()) {
            String tok = tokens.get(p).getToken();
            if (tok.equals("SEMI")) {
                break;
            }
            if (tok.equals("LPAREN")) {
                profundidad++;
            } else if (tok.equals("RPAREN")) {
                if (profundidad == 0) {
                    break;
                }
                profundidad--;
            }
            p++;
        }
        return p;
    }

    // Revisa los operadores relacionales dentro de los parentesis de ye_if, ct_while y ct_for.
    private void validarCondicionEstructura(int posEstructura) {
        int p = posEstructura + 1;
        while (p < tokens.size() && !tokens.get(p).getToken().equals("LPAREN")) {
            p++;
        }
        if (p >= tokens.size()) return;

        int pInicioCond = p + 1;
        int pFinCond = pInicioCond;
        int parentesisCount = 1;

        while (pFinCond < tokens.size() && parentesisCount > 0) {
            if (tokens.get(pFinCond).getToken().equals("LPAREN")) parentesisCount++;
            if (tokens.get(pFinCond).getToken().equals("RPAREN")) parentesisCount--;
            pFinCond++;
        }

        for (int k = pInicioCond; k < pFinCond - 1; k++) {
            Token t = tokens.get(k);

            if (esComparadorRelacional(t.getToken())) {
                if (k > pInicioCond) {
                    validarOperandoIzquierdo(k, t.getLexema());
                }
                if (k + 1 < pFinCond) {
                    validarOperandoDerecho(k, t.getLexema());
                }
            }
        }
    }

    // El operando izquierdo puede llevar casteo: ( tipo ) operando operador
    private void validarOperandoIzquierdo(int posOperador, String operador) {
        int posOperando = posOperador - 1;

        if (posOperando >= 3 && esInicioCasteo(posOperando - 3)) {
            validarCasteoRelacional(tokens.get(posOperando - 2).getToken(), tokens.get(posOperando - 3), operador);
        } else {
            validarOperandoRelacional(tokens.get(posOperando), operador);
        }
    }

    // El operando derecho puede empezar con un casteo: operador ( tipo ) operando
    private void validarOperandoDerecho(int posOperador, String operador) {
        int posOperando = posOperador + 1;

        if (esInicioCasteo(posOperando)) {
            validarCasteoRelacional(tokens.get(posOperando + 1).getToken(), tokens.get(posOperando), operador);
        } else {
            validarOperandoRelacional(tokens.get(posOperando), operador);
        }
    }

    // Un operando casteado a texto o booleano no se puede comparar con >, >=, < o <=.
    private void validarCasteoRelacional(String tipoCasteo, Token tokenCasteo, String operador) {
        if (tipoCasteo.equals("STRING") || tipoCasteo.equals("BOOL")) {
            errores.add(new ErrorCompilador(
                    "el casteo a " + nombreTipo(tipoCasteo) + " produce un valor de tipo " + tipoCasteo
                            + " que no se puede usar con el operador relacional '" + operador + "'",
                    tokenCasteo.getLinea(), tokenCasteo.getColumna(), "SEMANTICO"));
        }
    }

    private void validarOperandoRelacional(Token tokenOperando, String operador) {
        String tokType = tokenOperando.getToken();

        if (tokType.equals("TRUE") || tokType.equals("FALSE")) {
            errores.add(new ErrorCompilador(
                    "no se puede usar el valor booleano '" + tokenOperando.getLexema() + "' con el operador relacional '" + operador + "'",
                    tokenOperando.getLinea(), tokenOperando.getColumna(), "SEMANTICO"));
        } else if (tokType.equals("STRING_LIT")) {
            errores.add(new ErrorCompilador(
                    "no se puede usar el texto '" + tokenOperando.getLexema() + "' con el operador relacional '" + operador + "'",
                    tokenOperando.getLinea(), tokenOperando.getColumna(), "SEMANTICO"));
        } else if (tokType.equals("ID")) {
            String tipoVar = tablaSimbolos.get(tokenOperando.getLexema());
            if (tipoVar != null && (tipoVar.equals("STRING") || tipoVar.equals("BOOL"))) {
                errores.add(new ErrorCompilador(
                        "la variable '" + tokenOperando.getLexema() + "' de tipo " + tipoVar + " no se puede usar con el operador relacional '" + operador + "'",
                        tokenOperando.getLinea(), tokenOperando.getColumna(), "SEMANTICO"));
            }
        }
    }

    // Un casteo explicito tiene la forma: ( tipo )
    private boolean esInicioCasteo(int pos) {
        return pos >= 0 && pos + 2 < tokens.size()
                && tokens.get(pos).getToken().equals("LPAREN")
                && esTipoDato(tokens.get(pos + 1).getToken())
                && tokens.get(pos + 2).getToken().equals("RPAREN");
    }

    // Quita el sufijo de arreglo: "INT[]" pasa a "INT". Si el tipo es null devuelve null.
    private String tipoBase(String tipo) {
        if (tipo == null) {
            return null;
        }
        return tipo.endsWith("[]") ? tipo.substring(0, tipo.length() - 2) : tipo;
    }

    // Agrupa los tipos: INT y FLOAT son numericos, STRING es texto y BOOL es booleano.
    private String familiaDeTipo(String tipo) {
        if (tipo == null) {
            return null;
        }
        switch (tipo) {
            case "INT":
            case "FLOAT":
                return "NUMERICO";
            case "STRING":
                return "TEXTO";
            case "BOOL":
                return "BOOLEANO";
            default:
                return null;
        }
    }

    // Nombre del tipo como lo escribe el programador: INT pasa a ct_int.
    private String nombreTipo(String tipo) {
        return "ct_" + tipo.toLowerCase();
    }

    private boolean esTipoDato(String tok) {
        return tok.equals("INT") || tok.equals("FLOAT") || tok.equals("STRING") || tok.equals("BOOL");
    }

    private boolean esOperadorAritmetico(String tok) {
        return tok.equals("PLUS") || tok.equals("MINUS") || tok.equals("MULT") || tok.equals("DIV") || tok.equals("MOD");
    }

    private boolean esComparadorRelacional(String tok) {
        return tok.equals("GT") || tok.equals("GTE") || tok.equals("LT") || tok.equals("LTE");
    }

    public ArrayList<ErrorCompilador> getErrores() {
        return errores;
    }

    public boolean isExitoso() {
        return exitoso;
    }
}