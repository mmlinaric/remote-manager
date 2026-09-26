package com.example.remotemanager.ui;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;

/** Makes Escape take the same path as a dialog's Cancel button. */
public final class DialogEscape {
  private DialogEscape() {}

  public static void bind(JDialog dialog, JButton cancel) {
    dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelDialog");
    dialog.getRootPane().getActionMap().put("cancelDialog", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent event) {
        if (cancel.isEnabled()) cancel.doClick();
      }
    });
  }
}
