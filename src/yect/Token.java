package yect;

// esta clase representa un token
// un token es un pedacito de codigo con significado, por ejemplo
// el numero 20, la palabra ct_int, el simbolo + , etc
public class Token {

    private String lexema;   // el texto tal cual aparece en el codigo
    private String token;    // el nombre del tipo de token, ejemplo: NUM, ID, PLUS
    private String tipo;     // una descripcion mas humana, ejemplo: "entero", "identificador"
    private int linea;       // en que linea del codigo aparecio
    private int columna;     // en que columna aparecio

    public Token(String lexema, String token, String tipo, int linea, int columna) {
        this.lexema = lexema;
        this.token = token;
        this.tipo = tipo;
        this.linea = linea;
        this.columna = columna;
    }

    public String getLexema() {
        return lexema;
    }

    public String getToken() {
        return token;
    }

    public String getTipo() {
        return tipo;
    }

    public int getLinea() {
        return linea;
    }

    public int getColumna() {
        return columna;
    }

    // esto sirve para mostrar el token de forma facil de leer
    public String toString() {
        return "[" + token + "  ->  '" + lexema + "'  (linea " + linea + ")]";
    }
}
