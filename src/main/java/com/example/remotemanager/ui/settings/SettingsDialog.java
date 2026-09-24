package com.example.remotemanager.ui.settings;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.nio.file.Path;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

public final class SettingsDialog {
  private SettingsDialog() {}

  public static AppSettings edit(Window owner, AppSettings current) {
    JTextField font = new JTextField(current.terminalFont(), 24);
    JSpinner fontSize = spinner(current.terminalFontSize(), 6, 48);
    JSpinner scrollback = spinner(current.scrollbackLines(), 100, 100000);
    JSpinner clipboard = spinner(current.clipboardSeconds(), 5, 600);
    JComboBox<Integer> autoLock = new JComboBox<>(new Integer[] {0, 5, 15, 30});
    autoLock.setSelectedItem(current.vaultAutoLockMinutes());
    JTextField knownHosts = new JTextField(current.knownHosts().toString(), 24);

    JPanel form = new JPanel(new GridBagLayout());
    addRow(form, 0, "Terminal font", font);
    addRow(form, 1, "Font size", fontSize);
    addRow(form, 2, "Scrollback lines", scrollback);
    addRow(form, 3, "Sudo clipboard seconds", clipboard);
    addRow(form, 4, "Vault auto lock minutes", autoLock);
    addRow(form, 5, "Known hosts file", knownHosts);

    if (JOptionPane.showConfirmDialog(
            owner, form, "Settings", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        != JOptionPane.OK_OPTION) {
      return null;
    }
    if (knownHosts.getText().isBlank() || font.getText().isBlank()) {
      JOptionPane.showMessageDialog(owner, "Font and known hosts path are required");
      return null;
    }
    return new AppSettings(
        font.getText().trim(),
        (Integer) fontSize.getValue(),
        (Integer) scrollback.getValue(),
        (Integer) clipboard.getValue(),
        (Integer) autoLock.getSelectedItem(),
        Path.of(knownHosts.getText().trim()));
  }

  private static JSpinner spinner(int current, int minimum, int maximum) {
    return new JSpinner(new SpinnerNumberModel(current, minimum, maximum, 1));
  }

  private static void addRow(JPanel form, int row, String label, java.awt.Component field) {
    GridBagConstraints caption = new GridBagConstraints();
    caption.gridx = 0;
    caption.gridy = row;
    caption.anchor = GridBagConstraints.WEST;
    caption.insets = new Insets(3, 4, 3, 6);
    form.add(new JLabel(label + ":"), caption);

    GridBagConstraints input = new GridBagConstraints();
    input.gridx = 1;
    input.gridy = row;
    input.weightx = 1;
    input.fill = GridBagConstraints.HORIZONTAL;
    input.insets = new Insets(3, 0, 3, 4);
    form.add(field, input);
  }
}
