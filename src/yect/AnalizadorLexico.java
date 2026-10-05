package yect;

import java.util.ArrayList;

// este es el analizador lexico
// su trabajo es leer el codigo caracter por caracter
// e ir formando "tokens" (palabras con significado)
//
// por ejemplo si lee: ct_int edad = 20;
// va a generar los tokens: INT, ID(edad), ASSIGN, NUM(20), SEMI
public class AnalizadorLexico {

    // lista de palabras que ya tienen un significado especial en el lenguaje
    private static final String[] PALABRAS_RESERVADAS = {
        "ye_init", "kanye_end", "ye_if", "ye_else",
        "ct_for", "ct_while", "ye_print", "ye_input",
        "ct_int", "ct_float", "ct_string", "ct_bool",
        "ye_true", "ye_false", "new"
    };

    // el nombre del token que le corresponde a cada palabra reservada
    // (misma posicion que el arreglo de arriba)
    private static final String[] TOKENS_RESERVADOS = {
        "INIT", "END", "IF", "ELSE",
        "FOR", "WHILE", "PRINT", "INPUT",
        "INT", "FLOAT", "STRING", "BOOL",
        "TRUE", "FALSE", "NEW"
    };

    // codigo fuente que estamos leyendo
    private String codigo;
    private int pos;       // posicion actual dentro del texto
    private int linea;     // linea actual (para mostrar errores)
    private int columna;   // columna actual (para mostrar errores)

    private ArrayList<Token> listaTokens;
    private ArrayList<ErrorCompilador> listaErrores;

    public AnalizadorLexico() {
        listaTokens = new ArrayList<>();
        listaErrores = new ArrayList<>();
    }

    // metodo principal, recibe todo el codigo como un solo texto
    public void analizar(String codigoFuente) {
        listaTokens.clear();
        listaErrores.clear();

        codigo = codigoFuente;
        pos = 0;
        linea = 1;
        columna = 1;

        // vamos a recorrer el texto caracter por caracter
        while (pos < codigo.length()) {

            char c = codigo.charAt(pos);

            // espacios, tabs y retornos de carro se ignoran
            if (c == ' ' || c == '\t' || c == '\r') {
                avanzar();
                continue;
            }

            // salto de linea, aumentamos el contador de linea
            if (c == '\n') {
                linea = linea + 1;
                columna = 0;
                avanzar();
                continue;
            }

            // si empieza con letra o guion bajo, es una palabra
            // (puede ser palabra reservada o un identificador)
            if (esLetra(c) || c == '_') {
                leerPalabra();
                continue;
            }

            // si empieza con numero, leemos un numero entero o decimal
            if (esDigito(c)) {
                leerNumero();
                continue;
            }

            // si empieza con comillas, leemos una cadena de texto
            if (c == '"') {
                leerCadena();
                continue;
            }

            // comentario de una linea, empieza con //
            if (c == '/' && pos + 1 < codigo.length() && codigo.charAt(pos + 1) == '/') {
                saltarComentario();
                continue;
            }

            // simbolos de un solo caracter
            if (c == '+') { agregarToken("+", "PLUS", "operador"); avanzar(); continue; }
            if (c == '-') { agregarToken("-", "MINUS", "operador"); avanzar(); continue; }
            if (c == '*') { agregarToken("*", "MULT", "operador"); avanzar(); continue; }
            if (c == '%') { agregarToken("%", "MOD", "operador"); avanzar(); continue; }
            if (c == ';') { agregarToken(";", "SEMI", "delimitador"); avanzar(); continue; }
            if (c == ',') { agregarToken(",", "COMMA", "delimitador"); avanzar(); continue; }
            if (c == '(') { agregarToken("(", "LPAREN", "delimitador"); avanzar(); continue; }
            if (c == ')') { agregarToken(")", "RPAREN", "delimitador"); avanzar(); continue; }
            if (c == '{') { agregarToken("{", "LBRACE", "delimitador"); avanzar(); continue; }
            if (c == '}') { agregarToken("}", "RBRACE", "delimitador"); avanzar(); continue; }
            if (c == '[') { agregarToken("[", "LBRACKET", "delimitador"); avanzar(); continue; }
            if (c == ']') { agregarToken("]", "RBRACKET", "delimitador"); avanzar(); continue; }

            // simbolos que pueden ser de uno o dos caracteres
            if (c == '=') { leerIgual(); continue; }
            if (c == '!') { leerDiferente(); continue; }
            if (c == '>') { leerMayor(); continue; }
            if (c == '<') { leerMenor(); continue; }
            if (c == '&') { leerAnd(); continue; }
            if (c == '|') { leerOr(); continue; }
            if (c == '/') { agregarToken("/", "DIV", "operador"); avanzar(); continue; }

            // si llegamos aqui, es un caracter que no reconocemos
            agregarError("caracter no reconocido: '" + c + "'");
            avanzar();
        }
    }

    // lee una palabra completa (letras, numeros y guion bajo)
    // y decide si es palabra reservada o un identificador normal
    private void leerPalabra() {
        int colInicio = columna;
        int lineaInicio = linea;
        StringBuilder palabra = new StringBuilder();

        while (pos < codigo.length()) {
            char c = codigo.charAt(pos);
            if (esLetra(c) || esDigito(c) || c == '_') {
                palabra.append(c);
                avanzar();
            } else {
                break;
            }
        }

        String texto = palabra.toString();

        // revisamos si la palabra es una de las reservadas
        for (int i = 0; i < PALABRAS_RESERVADAS.length; i++) {
            if (PALABRAS_RESERVADAS[i].equals(texto)) {
                listaTokens.add(new Token(texto, TOKENS_RESERVADOS[i], "palabra reservada", lineaInicio, colInicio));
                return;
            }
        }

        // si no es reservada, entonces es un identificador (nombre de variable)
        listaTokens.add(new Token(texto, "ID", "identificador", lineaInicio, colInicio));
    }

    // lee un numero, puede ser entero (123) o decimal (12.5)
    private void leerNumero() {
        int colInicio = columna;
        StringBuilder numero = new StringBuilder();
        boolean esDecimal = false;

        while (pos < codigo.length() && esDigito(codigo.charAt(pos))) {
            numero.append(codigo.charAt(pos));
            avanzar();
        }

        // si despues de los digitos hay un punto y luego mas digitos, es decimal
        if (pos < codigo.length() && codigo.charAt(pos) == '.'
                && pos + 1 < codigo.length() && esDigito(codigo.charAt(pos + 1))) {
            esDecimal = true;
            numero.append('.');
            avanzar();
            while (pos < codigo.length() && esDigito(codigo.charAt(pos))) {
                numero.append(codigo.charAt(pos));
                avanzar();
            }
        }

        if (esDecimal) {
            listaTokens.add(new Token(numero.toString(), "FLOAT_LIT", "numero decimal", linea, colInicio));
        } else {
            listaTokens.add(new Token(numero.toString(), "NUM", "numero entero", linea, colInicio));
        }
    }

    // lee una cadena de texto entre comillas dobles
    private void leerCadena() {
        int colInicio = columna;
        int lineaInicio = linea;
        StringBuilder texto = new StringBuilder();

        avanzar(); // saltamos la comilla que abre
        boolean cerrada = false;

        while (pos < codigo.length()) {
            char c = codigo.charAt(pos);

            if (c == '"') {
                avanzar();
                cerrada = true;
                break;
            }

            if (c == '\n') {
                agregarError("la cadena de texto no se cerro antes del salto de linea");
                linea = linea + 1;
                columna = 0;
                avanzar();
                return;
            }

            texto.append(c);
            avanzar();
        }

        if (!cerrada) {
            agregarError("la cadena de texto se quedo sin cerrar (falto una comilla)");
            return;
        }

        listaTokens.add(new Token(texto.toString(), "STRING_LIT", "cadena de texto", lineaInicio, colInicio));
    }

    // pasa por encima de un comentario de linea (// ...) sin generar token
    private void saltarComentario() {
        while (pos < codigo.length() && codigo.charAt(pos) != '\n') {
            avanzar();
        }
    }

    // = o ==
    private void leerIgual() {
        int col = columna;
        avanzar();
        if (pos < codigo.length() && codigo.charAt(pos) == '=') {
            avanzar();
            listaTokens.add(new Token("==", "EQ", "comparacion", linea, col));
        } else {
            listaTokens.add(new Token("=", "ASSIGN", "asignacion", linea, col));
        }
    }

    // solo puede ser !=
    private void leerDiferente() {
        int col = columna;
        avanzar();
        if (pos < codigo.length() && codigo.charAt(pos) == '=') {
            avanzar();
            listaTokens.add(new Token("!=", "NEQ", "comparacion", linea, col));
        } else {
            agregarError("se esperaba '=' despues de '!'");
        }
    }

    // > o >=
    private void leerMayor() {
        int col = columna;
        avanzar();
        if (pos < codigo.length() && codigo.charAt(pos) == '=') {
            avanzar();
            listaTokens.add(new Token(">=", "GTE", "comparacion", linea, col));
        } else {
            listaTokens.add(new Token(">", "GT", "comparacion", linea, col));
        }
    }

    // < o <=
    private void leerMenor() {
        int col = columna;
        avanzar();
        if (pos < codigo.length() && codigo.charAt(pos) == '=') {
            avanzar();
            listaTokens.add(new Token("<=", "LTE", "comparacion", linea, col));
        } else {
            listaTokens.add(new Token("<", "LT", "comparacion", linea, col));
        }
    }

    // solo puede ser &&
    private void leerAnd() {
        int col = columna;
        avanzar();
        if (pos < codigo.length() && codigo.charAt(pos) == '&') {
            avanzar();
            listaTokens.add(new Token("&&", "AND", "logico", linea, col));
        } else {
            agregarError("se esperaba '&' para formar el operador '&&'");
        }
    }

    // solo puede ser ||
    private void leerOr() {
        int col = columna;
        avanzar();
        if (pos < codigo.length() && codigo.charAt(pos) == '|') {
            avanzar();
            listaTokens.add(new Token("||", "OR", "logico", linea, col));
        } else {
            agregarError("se esperaba '|' para formar el operador '||'");
        }
    }

    // ------- funciones pequenas de apoyo -------

    private void avanzar() {
        pos = pos + 1;
        columna = columna + 1;
    }

    private boolean esLetra(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private boolean esDigito(char c) {
        return c >= '0' && c <= '9';
    }

    private void agregarToken(String lexema, String token, String tipo) {
        listaTokens.add(new Token(lexema, token, tipo, linea, columna));
    }

    private void agregarError(String mensaje) {
        listaErrores.add(new ErrorCompilador(mensaje, linea, columna, "LEXICO"));
    }

    public ArrayList<Token> getTokens() {
        return listaTokens;
    }

    public ArrayList<ErrorCompilador> getErrores() {
        return listaErrores;
    }
}
