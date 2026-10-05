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

        revisarVariables();

        if (errores.isEmpty()) {
            exitoso = true;
        }
    }

    private void revisarVariables() {
        HashSet<String> yaDeclaradas = new HashSet<>();
        int indiceToken = 0;

        while (indiceToken < tokens.size()) {
            Token tokenActual = tokens.get(indiceToken);
            String nombreTokenActual = tokenActual.getToken();

            // 1. declaracion de variables nuevas (ejemplo: ct_int edad = 20)
            if (esTipoDato(nombreTokenActual)) {
                int posNombre = indiceToken + 1;

                if (posNombre < tokens.size() && tokens.get(posNombre).getToken().equals("ID")) {
                    String nombreVariable = tokens.get(posNombre).getLexema();

                    if (yaDeclaradas.contains(nombreVariable)) {
                        errores.add(new ErrorCompilador(
                                "la variable '" + nombreVariable + "' ya habia sido declarada antes",
                                tokens.get(posNombre).getLinea(), tokens.get(posNombre).getColumna(), "SEMANTICO"));
                    } else {
                        yaDeclaradas.add(nombreVariable);
                    }

                    int posAsignacion = posNombre + 1;
                    if (posAsignacion < tokens.size() && tokens.get(posAsignacion).getToken().equals("ASSIGN")) {
                        int posValor = posAsignacion + 1;
                        if (posValor < tokens.size()) {
                            validarAsignacion(nombreTokenActual, nombreVariable, posValor);
                        }
                    }
                }
                indiceToken++;
                continue;
            }

            // 2. reasignacion o uso de variables existentes
            if (nombreTokenActual.equals("ID")) {
                String nombreVariable = tokenActual.getLexema();

                String tokenAnterior = (indiceToken > 0) ? tokens.get(indiceToken - 1).getToken() : "";
                if (esTipoDato(tokenAnterior)) {
                    indiceToken++;
                    continue;
                }

                if (!tablaSimbolos.containsKey(nombreVariable)) {
                    errores.add(new ErrorCompilador(
                            "la variable '" + nombreVariable + "' se uso sin haber sido declarada",
                            tokenActual.getLinea(), tokenActual.getColumna(), "SEMANTICO"));
                    indiceToken++;
                    continue;
                }

                int posAsignacion = indiceToken + 1;
                if (posAsignacion < tokens.size() && tokens.get(posAsignacion).getToken().equals("ASSIGN")) {
                    int posValor = posAsignacion + 1;
                    if (posValor < tokens.size()) {
                        String tipoDeclarado = tablaSimbolos.get(nombreVariable);
                        validarAsignacion(tipoDeclarado, nombreVariable, posValor);
                    }
                }
            }

            indiceToken++;
        }
    }

    // evalua la expresion completa que esta a la derecha de '=' hasta el ';'
    private void validarAsignacion(String tipoEsperado, String nombreVariable, int posValor) {
        // busca el final de la expresion
        int posFin = posValor;
        while (posFin < tokens.size() && !tokens.get(posFin).getToken().equals("SEMI")) {
            posFin++;
        }

        // si hay un casteo explicito al inicio de la expresion: (tipo)
        if (esCasteoExplicito(posValor)) {
            String mensajeError = verificarCasteoExplicito(tipoEsperado, posValor);
            if (mensajeError != null) {
                Token tokenValor = tokens.get(posValor);
                errores.add(new ErrorCompilador(mensajeError + " (variable '" + nombreVariable + "')",
                        tokenValor.getLinea(), tokenValor.getColumna(), "SEMANTICO"));
            }
            return;
        }

        // recorre cada token de la expresion para verificar compatibilidad de tipos
        boolean tieneOperadoresMatematicos = false;

        for (int i = posValor; i < posFin; i++) {
            Token t = tokens.get(i);
            String tokenNombre = t.getToken();

            if (esOperadorAritmetico(tokenNombre)) {
                tieneOperadoresMatematicos = true;
            }

            // revisa las variables que aparecen dentro de la expresion
            if (tokenNombre.equals("ID")) {
                String nombreVarOrigen = t.getLexema();
                String tipoVarOrigen = tablaSimbolos.get(nombreVarOrigen);

                if (tipoVarOrigen != null) {
                    // si la variable destino es numerica (INT o FLOAT) o la formula tiene operadores matematicos,
                    // no se permite usar variables de tipo STRING o BOOL
                    if ((tipoEsperado.equals("INT") || tipoEsperado.equals("FLOAT") || tieneOperadoresMatematicos)
                            && (tipoVarOrigen.equals("STRING") || tipoVarOrigen.equals("BOOL"))) {

                        errores.add(new ErrorCompilador(
                                "la variable '" + nombreVarOrigen + "' es de tipo " + tipoVarOrigen +
                                " y no se puede usar en operaciones numericas para '" + nombreVariable + "' (" + tipoEsperado + ")",
                                t.getLinea(), t.getColumna(), "SEMANTICO"));
                    }
                }
            }

            // revisa si se uso una literal de cadena ("texto") en una operacion numerica
            if (tokenNombre.equals("STRING_LIT") && (tipoEsperado.equals("INT") || tipoEsperado.equals("FLOAT"))) {
                errores.add(new ErrorCompilador(
                        "no se puede usar una cadena de texto en un calculo numerico para '" + nombreVariable + "'",
                        t.getLinea(), t.getColumna(), "SEMANTICO"));
            }
        }
    }

    private boolean esCasteoExplicito(int posValor) {
        return posValor + 3 < tokens.size() &&
               tokens.get(posValor).getToken().equals("LPAREN") &&
               esTipoDato(tokens.get(posValor + 1).getToken()) &&
               tokens.get(posValor + 2).getToken().equals("RPAREN");
    }

    private String verificarCasteoExplicito(String tipoEsperado, int posValor) {
        String tipoCasteoDeseado = tokens.get(posValor + 1).getToken();
        Token tokenValorOriginal = tokens.get(posValor + 3);

        String tipoOrigen = obtenerTipoToken(tokenValorOriginal);

        if (tipoOrigen == null) {
            return "la variable '" + tokenValorOriginal.getLexema() + "' no ha sido declarada";
        }

        boolean casteoValido = false;
        if (tipoOrigen.equals("INT") && (tipoCasteoDeseado.equals("INT") || tipoCasteoDeseado.equals("FLOAT"))) casteoValido = true;
        if (tipoOrigen.equals("FLOAT") && (tipoCasteoDeseado.equals("INT") || tipoCasteoDeseado.equals("FLOAT"))) casteoValido = true;
        if (tipoOrigen.equals("STRING") && tipoCasteoDeseado.equals("STRING")) casteoValido = true;
        if (tipoOrigen.equals("BOOL") && tipoCasteoDeseado.equals("BOOL")) casteoValido = true;

        if (!casteoValido) {
            return "no se puede hacer casteo explicito de " + tipoOrigen + " a " + tipoCasteoDeseado;
        }

        if (!tipoCasteoDeseado.equals(tipoEsperado) && !(tipoEsperado.equals("FLOAT") && tipoCasteoDeseado.equals("INT"))) {
            return "el casteo resulta en " + tipoCasteoDeseado + " pero la variable es de tipo " + tipoEsperado;
        }

        return null;
    }

    private String obtenerTipoToken(Token tokenEvaluado) {
        String nombreToken = tokenEvaluado.getToken();
        if (nombreToken.equals("NUM")) return "INT";
        if (nombreToken.equals("FLOAT_LIT")) return "FLOAT";
        if (nombreToken.equals("STRING_LIT")) return "STRING";
        if (nombreToken.equals("TRUE") || nombreToken.equals("FALSE")) return "BOOL";
        if (nombreToken.equals("ID")) return tablaSimbolos.get(tokenEvaluado.getLexema());
        return null;
    }

    private boolean esTipoDato(String nombreToken) {
        return nombreToken.equals("INT") || nombreToken.equals("FLOAT") || nombreToken.equals("STRING") || nombreToken.equals("BOOL");
    }

    private boolean esOperadorAritmetico(String nombreToken) {
        return nombreToken.equals("PLUS") || nombreToken.equals("MINUS") || nombreToken.equals("MULT")
                || nombreToken.equals("DIV") || nombreToken.equals("MOD");
    }

    public ArrayList<ErrorCompilador> getErrores() {
        return errores;
    }

    public boolean isExitoso() {
        return exitoso;
    }
}