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

    private void revisarEstructurasYVariables() {
        HashSet<String> yaDeclaradas = new HashSet<>();
        int i = 0;

        while (i < tokens.size()) {
            Token tokenActual = tokens.get(i);
            String nombreToken = tokenActual.getToken();

            // 1. Declaracion de variables
            if (esTipoDato(nombreToken)) {
                String tipoDeclarado = nombreToken;
                int posNombre = i + 1;

                if (posNombre < tokens.size() && tokens.get(posNombre).getToken().equals("ID")) {
                    String nombreVariable = tokens.get(posNombre).getLexema();

                    if (yaDeclaradas.contains(nombreVariable)) {
                        errores.add(new ErrorCompilador(
                                "la variable '" + nombreVariable + "' ya habia sido declarada antes",
                                tokens.get(posNombre).getLinea(), tokens.get(posNombre).getColumna(), "SEMANTICO"));
                    } else {
                        yaDeclaradas.add(nombreVariable);
                    }

                    // Validar si la variable pertenece al encabezado de un ct_for
                    boolean esVariableControlFor = (i > 0 && tokens.get(i - 1).getToken().equals("LPAREN")
                            && i > 1 && tokens.get(i - 2).getToken().equals("FOR"));

                    if (esVariableControlFor && (tipoDeclarado.equals("STRING") || tipoDeclarado.equals("BOOL"))) {
                        errores.add(new ErrorCompilador(
                                "la variable de control '" + nombreVariable + "' en 'ct_for' debe ser numerica (ct_int o ct_float)",
                                tokenActual.getLinea(), tokenActual.getColumna(), "SEMANTICO"));
                    }

                    int posAsignacion = posNombre + 1;
                    if (posAsignacion < tokens.size() && tokens.get(posAsignacion).getToken().equals("ASSIGN")) {
                        validarAsignacion(tipoDeclarado, nombreVariable, posAsignacion + 1);
                    }
                }
                i++;
                continue;
            }

            // 2. Reasignacion de variables (ID = ...)
            if (nombreToken.equals("ID")) {
                String nombreVariable = tokenActual.getLexema();
                String tokenAnterior = (i > 0) ? tokens.get(i - 1).getToken() : "";

                if (!esTipoDato(tokenAnterior)) {
                    if (!tablaSimbolos.containsKey(nombreVariable)) {
                        errores.add(new ErrorCompilador(
                                "la variable '" + nombreVariable + "' se uso sin haber sido declarada",
                                tokenActual.getLinea(), tokenActual.getColumna(), "SEMANTICO"));
                    } else {
                        int posAsignacion = i + 1;
                        if (posAsignacion < tokens.size() && tokens.get(posAsignacion).getToken().equals("ASSIGN")) {
                            String tipoVar = tablaSimbolos.get(nombreVariable);
                            validarAsignacion(tipoVar, nombreVariable, posAsignacion + 1);
                        }
                    }
                }
            }

            // 3. Validacion de expresiones condicionales (ye_if, ct_while, ct_for)
            if (nombreToken.equals("IF") || nombreToken.equals("WHILE") || nombreToken.equals("FOR")) {
                validarCondicionEstructura(i);
            }

            i++;
        }
    }

    private void validarAsignacion(String tipoEsperado, String nombreVariable, int posValor) {
        int posFin = posValor;
        while (posFin < tokens.size() && !tokens.get(posFin).getToken().equals("SEMI")
                && !tokens.get(posFin).getToken().equals("RPAREN")) {
            posFin++;
        }

        for (int k = posValor; k < posFin; k++) {
            Token t = tokens.get(k);
            String tokType = t.getToken();

            // Compatibilidad de tipos en literales
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

            // Uso de variables dentro de operaciones
            if (tokType.equals("ID")) {
                String varOrigen = t.getLexema();
                String tipoOrigen = tablaSimbolos.get(varOrigen);

                if (tipoOrigen != null) {
                    if ((tipoEsperado.equals("INT") || tipoEsperado.equals("FLOAT"))
                            && (tipoOrigen.equals("STRING") || tipoOrigen.equals("BOOL"))) {
                        errores.add(new ErrorCompilador(
                                "la variable '" + varOrigen + "' es de tipo " + tipoOrigen + " y no se puede usar para la variable numerica '" + nombreVariable + "'",
                                t.getLinea(), t.getColumna(), "SEMANTICO"));
                    }
                }
            }

            // Operaciones aritmeticas no permitidas en variables de texto o booleanos
            if (esOperadorAritmetico(tokType)) {
                if (tipoEsperado.equals("STRING") || tipoEsperado.equals("BOOL")) {
                    errores.add(new ErrorCompilador(
                            "no se permiten operaciones aritmeticas en variables de tipo " + tipoEsperado + " ('" + nombreVariable + "')",
                            t.getLinea(), t.getColumna(), "SEMANTICO"));
                }
            }
        }
    }

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
            String tokType = t.getToken();

            if (esComparadorRelacional(tokType)) {
                if (k > pInicioCond) {
                    Token izq = tokens.get(k - 1);
                    validarOperandoRelacional(izq, t.getLexema());
                }
                if (k + 1 < pFinCond) {
                    Token der = tokens.get(k + 1);
                    validarOperandoRelacional(der, t.getLexema());
                }
            }
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