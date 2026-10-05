package yect;

import java.awt.Color;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

// aqui empieza el programa
public class Main {
    public static void main(String[] args) {

        
        UIManager.put("TabbedPane.selected", Color.WHITE);
        UIManager.put("TabbedPane.contentAreaColor", Color.WHITE);
        UIManager.put("TabbedPane.focus", Color.BLACK);
        UIManager.put("TabbedPane.borderHightlightColor", Color.BLACK);
        UIManager.put("TabbedPane.darkShadow", Color.BLACK);
        UIManager.put("TabbedPane.light", Color.WHITE);
        UIManager.put("TabbedPane.highlight", Color.WHITE);
        UIManager.put("ScrollBar.thumb", Color.BLACK);
        UIManager.put("ScrollBar.track", Color.WHITE);
        UIManager.put("ScrollBar.thumbHighlight", Color.BLACK);
        UIManager.put("ScrollBar.thumbShadow", Color.BLACK);
        UIManager.put("SplitPane.background", Color.WHITE);
        UIManager.put("SplitPaneDivider.draggingColor", Color.BLACK);
        UIManager.put("Button.select", Color.WHITE);
        UIManager.put("Button.focus", new Color(0, 0, 0, 0));

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                VentanaPrincipal ventana = new VentanaPrincipal();
                ventana.setVisible(true);
            }
        });
    }
}
