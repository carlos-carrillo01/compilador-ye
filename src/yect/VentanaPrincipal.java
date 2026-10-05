package yect;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.*;
import javax.swing.border.LineBorder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

// esta es la ventana principal del programa
// el diseno es a proposito muy simple: solo blanco, negro y lineas rectas
// no se usan colores, iconos ni cosas decorativas
public class VentanaPrincipal extends JFrame {

    private JTextArea areaCodigo;
    private JTextArea areaTokens;
    private JTextArea areaErrores;
    private JTextArea areaSalida;

    private JButton botonEjemplo;
    private JButton botonCompilar;
    private JButton botonLimpiar;

    private JLabel etiquetaEstado;

    // los tres analizadores y el interprete
    private AnalizadorLexico lexico;
    private AnalizadorSintactico sintactico;
    private AnalizadorSemantico semantico;
    private Interprete interprete;

    public VentanaPrincipal() {
        lexico = new AnalizadorLexico();
        sintactico = new AnalizadorSintactico();
        semantico = new AnalizadorSemantico();
        interprete = new Interprete();

        construirVentana();
    }
    
    private void cargarArchivoFactorial() {
    try {
        // busca el archivo factorial.txt dentro de la carpeta src/yect
            String contenido = Files.readString(Paths.get("src/yect/factorial.txt"));
            areaCodigo.setText(contenido);
            etiquetaEstado.setText("factorial.txt cargado correctamente");
        } catch (IOException e) {
            etiquetaEstado.setText("no se pudo encontrar el archivo factorial.txt");
            areaErrores.setText("error al leer el archivo: " + e.getMessage());
        }
    }

    // arma toda la interfaz
    private void construirVentana() {
        setTitle("Analizador ye/ct");
        setSize(1000, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        getContentPane().setBackground(Color.WHITE);
        setLayout(new BorderLayout(10, 10));

        add(construirEncabezado(), BorderLayout.NORTH);
        add(construirCentro(), BorderLayout.CENTER);
        add(construirPiePagina(), BorderLayout.SOUTH);
    }

    // parte de arriba: titulo y botones
    private JPanel construirEncabezado() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

        JLabel titulo = new JLabel("Analiszador ye/ct");
        titulo.setFont(new Font("Monospaced", Font.BOLD, 20));
        titulo.setForeground(Color.BLACK);

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panelBotones.setBackground(Color.WHITE);

        botonEjemplo = crearBoton("cargar ejemplo");
        botonCompilar = crearBoton("Analizar");
        botonLimpiar = crearBoton("limpiar");

        panelBotones.add(botonEjemplo);
        panelBotones.add(botonCompilar);
        panelBotones.add(botonLimpiar);

        panel.add(titulo, BorderLayout.WEST);
        panel.add(panelBotones, BorderLayout.EAST);

        botonEjemplo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                cargarEjemplo();
            }
        });

        botonCompilar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                compilarYEjecutar();
            }
        });

        botonLimpiar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                limpiarTodo();
            }
        });

        return panel;
    }

    // parte central: el editor de codigo a la izquierda,
    // y las pestanas (tokens, errores, salida) a la derecha
    private JSplitPane construirCentro() {
        areaCodigo = crearAreaTexto();
        JScrollPane scrollCodigo = new JScrollPane(areaCodigo);
        scrollCodigo.setBorder(new LineBorder(Color.BLACK, 1));

        JPanel panelIzquierdo = new JPanel(new BorderLayout());
        panelIzquierdo.setBackground(Color.WHITE);
        JLabel etiquetaCodigo = etiquetaSeccion("codigo fuente");
        panelIzquierdo.add(etiquetaCodigo, BorderLayout.NORTH);
        panelIzquierdo.add(scrollCodigo, BorderLayout.CENTER);

        areaTokens = crearAreaTexto();
        areaTokens.setEditable(false);

        areaErrores = crearAreaTexto();
        areaErrores.setEditable(false);

        areaSalida = crearAreaTexto();
        areaSalida.setEditable(false);

        JTabbedPane pestanas = new JTabbedPane();
        pestanas.setBackground(Color.WHITE);
        pestanas.setForeground(Color.BLACK);
        pestanas.setFont(new Font("Monospaced", Font.PLAIN, 13));

        //pestanas.addTab("salida", envolver(areaSalida));
        pestanas.addTab("errores", envolver(areaErrores));
        pestanas.addTab("tokens", envolver(areaTokens));

        JSplitPane divisor = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panelIzquierdo, pestanas);
        divisor.setDividerLocation(480);
        divisor.setBorder(null);
        divisor.setBackground(Color.WHITE);

        return divisor;
    }

    // parte de abajo: una linea de estado simple
    private JPanel construirPiePagina() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        etiquetaEstado = new JLabel("listo");
        etiquetaEstado.setFont(new Font("Monospaced", Font.PLAIN, 12));
        etiquetaEstado.setForeground(Color.BLACK);

        panel.add(etiquetaEstado, BorderLayout.WEST);
        return panel;
    }

    // mete un area de texto dentro de un scroll con borde negro
    private JScrollPane envolver(JTextArea area) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(new LineBorder(Color.BLACK, 1));
        return scroll;
    }

    private JLabel etiquetaSeccion(String texto) {
        JLabel etiqueta = new JLabel(texto);
        etiqueta.setFont(new Font("Monospaced", Font.BOLD, 13));
        etiqueta.setForeground(Color.BLACK);
        etiqueta.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 0));
        return etiqueta;
    }

    // crea un area de texto con el estilo blanco y negro
    private JTextArea crearAreaTexto() {
        JTextArea area = new JTextArea();
        area.setFont(new Font("Monospaced", Font.PLAIN, 13));
        area.setBackground(Color.WHITE);
        area.setForeground(Color.BLACK);
        area.setCaretColor(Color.BLACK);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    // crea un boton simple, blanco con borde negro
    private JButton crearBoton(String texto) {
        JButton boton = new JButton(texto);
        boton.setFont(new Font("Monospaced", Font.PLAIN, 13));
        boton.setBackground(Color.WHITE);
        boton.setForeground(Color.BLACK);
        boton.setFocusPainted(false);
        boton.setBorder(new LineBorder(Color.BLACK, 1));
        boton.setOpaque(true);
        return boton;
    }

    // ------------------------------------------------------
    // logica del programa
    // ------------------------------------------------------

    // pone en el editor un programa de ejemplo que usa
    // casi todas las partes del lenguaje
    private void cargarEjemplo() {
        String ejemplo =
            "ye_init\n" +
            "\n" +
            "    // declaraciones de variable\n" +
            "    ct_int    edad     = 20;\n" +
            "    ct_float  promedio = 8.75;\n" +
            "    ct_string nombre   = \"Kanye West\";\n" +
            "    ct_bool   activo   = ye_true;\n" +
            "\n" +
            "    ct_int suma = edad + 10;\n" +
            "    ye_print suma;\n" +
            "\n" +
            "    // condicional simple\n" +
            "    ye_if (edad >= 18) {\n" +
            "        ye_print \"eres mayor de edad\";\n" +
            "    } ye_else {\n" +
            "        ye_print \"eres menor de edad\";\n" +
            "    }\n" +
            "\n" +
            "kanye_end";

        areaCodigo.setText(ejemplo);
        etiquetaEstado.setText("ejemplo cargado");
    }

    // borra todo, tanto el codigo como los resultados
    private void limpiarTodo() {
        areaCodigo.setText("");
        areaTokens.setText("");
        areaErrores.setText("");
        //areaSalida.setText("");
        etiquetaEstado.setText("listo");
    }

    
   
    private void compilarYEjecutar() {
        
        String codigoFuente = areaCodigo.getText();
        
        //String codigoFuente = "";
        
        //try {
        //    codigoFuente = Files.readString(Paths.get("src/yect/factorial.txt"));
        //} catch (IOException e) {
        //    etiquetaEstado.setText("no se pudo encontrar el archivo factorial.txt");
        //    areaErrores.setText("error al leer el archivo: " + e.getMessage());
        //    return;
            
        //}

        areaTokens.setText("");
        areaErrores.setText("");
        areaSalida.setText("");

        //  analisis lexico 
        lexico.analizar(codigoFuente);
        ArrayList<Token> tokens = lexico.getTokens();
        mostrarTokens(tokens);

        if (!lexico.getErrores().isEmpty()) {
            mostrarErrores(lexico.getErrores());
            etiquetaEstado.setText("se encontraron errores lexicos");
            return;
        }

        //   analisis sintactico
        sintactico.analizar(tokens);

        if (!sintactico.getErrores().isEmpty()) {
            mostrarErrores(sintactico.getErrores());
            etiquetaEstado.setText("se encontraron errores sintacticos");
            return;
        }

        //  analisis semantico 
        HashMap<String, String> tablaSimbolos = sintactico.getTablaSimbolos();
        semantico.analizar(tokens, tablaSimbolos);

        if (!semantico.getErrores().isEmpty()) {
            mostrarErrores(semantico.getErrores());
            etiquetaEstado.setText("se encontraron errores semanticos");
            return;
        }

        // si no hubo ningun error, se ejecuta 
        areaErrores.setText("no se encontraron errores, el programa es correcto");
        //String resultado = interprete.ejecutar(tokens);
        //areaSalida.setText(resultado);
        etiquetaEstado.setText("programa ejecutado correctamente");
    }

    private void mostrarTokens(ArrayList<Token> tokens) {
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            texto.append(tokens.get(i).toString());
            texto.append("\n");
        }
        areaTokens.setText(texto.toString());
    }

    private void mostrarErrores(ArrayList<ErrorCompilador> errores) {
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < errores.size(); i++) {
            texto.append(errores.get(i).toString());
            texto.append("\n");
        }
        areaErrores.setText(texto.toString());
    }
    

}
