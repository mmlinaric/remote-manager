package com.mmlinaric.remotemanager.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

class SelectionColorsTest {
  @Test
  void selectedTextContrastsWithSystemSelectionBackground() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      String[] types = {
          "TextField", "TextArea", "PasswordField", "FormattedTextField", "EditorPane", "TextPane"
      };
      Map<String, Object> previous = new HashMap<>();
      for (String type : types) {
        previous.put(type + ".selectionBackground", UIManager.get(type + ".selectionBackground"));
        previous.put(type + ".selectionForeground", UIManager.get(type + ".selectionForeground"));
      }
      try {
        for (String type : types)
          UIManager.put(type + ".selectionBackground", new Color(53, 132, 228));
        SelectionColors.configureTextInputs();

        assertEquals(Color.WHITE, new JTextField().getSelectedTextColor());
        assertEquals(Color.WHITE, new JPasswordField().getSelectedTextColor());
        assertEquals(Color.WHITE, new JTextArea().getSelectedTextColor());
        assertEquals(Color.BLACK, SelectionColors.foregroundFor(Color.YELLOW));
      } finally {
        previous.forEach(UIManager::put);
      }
    });
  }
}
