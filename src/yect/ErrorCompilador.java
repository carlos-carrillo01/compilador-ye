package yect;

// esta clase representa un error que se encontro mientras
// se revisaba el codigo. puede ser un error lexico, sintactico
// o semantico, eso lo dice el campo "tipoError"
public class ErrorCompilador {

    private String mensaje;
    private int linea;
    private int columna;
    private String tipoError; // puede ser "LEXICO", "SINTACTICO" o "SEMANTICO"

    public ErrorCompilador(String mensaje, int linea, int columna, String tipoError) {
        this.mensaje = mensaje;
        this.linea = linea;
        this.columna = columna;
        this.tipoError = tipoError;
    }

    public String getMensaje() {
        return mensaje;
    }

    public int getLinea() {
        return linea;
    }

    public int getColumna() {
        return columna;
    }

    public String getTipoError() {
        return tipoError;
    }

    // texto listo para mostrar en pantalla
    public String toString() {
        return "[" + tipoError + "] linea " + linea + ", columna " + columna + ": " + mensaje;
    }
}
