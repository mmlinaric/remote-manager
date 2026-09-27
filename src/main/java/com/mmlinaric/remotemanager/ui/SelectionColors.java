package com.mmlinaric.remotemanager.ui;

import java.awt.Color;
import javax.swing.UIManager;

/** Keeps selected text readable when a system look and feel supplies mismatched colors. */
public final class SelectionColors {
  private static final String[] TEXT_TYPES = {
      "TextField", "TextArea", "PasswordField", "FormattedTextField", "EditorPane", "TextPane"
  };

  private SelectionColors() {}

  public static Color foregroundFor(Color background) {
    int brightness = (299 * background.getRed()
        + 587 * background.getGreen()
        + 114 * background.getBlue()) / 1000;
    return brightness < 150 ? Color.WHITE : Color.BLACK;
  }

  public static void configureTextInputs() {
    for (String type : TEXT_TYPES) {
      Color background = UIManager.getColor(type + ".selectionBackground");
      if (background != null)
        UIManager.put(type + ".selectionForeground", foregroundFor(background));
    }
  }
}
