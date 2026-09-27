package com.mmlinaric.remotemanager.ui.vault;

import com.mmlinaric.remotemanager.vault.VaultEntry;
import com.mmlinaric.remotemanager.ui.KeyFilePicker;
import com.mmlinaric.remotemanager.ui.DialogEscape;
import com.mmlinaric.remotemanager.ui.SilkIcons;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Collects an identity without writing to the vault. The caller commits it with the host. */
public final class IdentityEditor {
  private IdentityEditor() {}

  /** Keeps the form open if a vault save fails. */
  public static void editAndSave(Window owner, VaultEntry current,
      Function<Change, CompletableFuture<Void>> saveAction) {
    JDialog dialog = new JDialog(owner, current == null ? "New identity" : "Edit identity",
        java.awt.Dialog.ModalityType.APPLICATION_MODAL);
    JTextField title = new JTextField(current == null ? "" : current.title(), 25);
    JTextField user = new JTextField(current == null ? "" : current.username(), 25);
    JPasswordField password = new JPasswordField(25);
    dialog.addWindowListener(new WindowAdapter() {
      @Override public void windowClosed(WindowEvent event) { password.setText(""); }
    });
    JTextField attachment = new JTextField(25);
    attachment.setToolTipText("Paste a private key path, or choose a file.");
    JPanel form = new JPanel(new GridBagLayout());
    row(form, 0, "Name", title);
    row(form, 1, "Username", user);
    row(form, 2, current == null ? "Password or key passphrase" : "New password (blank keeps current)", password);
    row(form, 3, "Private key attachment", attachment);
    JButton choose = new JButton("Choose key file...", SilkIcons.OPEN_VAULT);
    choose.addActionListener(event -> {
      Path selected = KeyFilePicker.choose(dialog, safePath(attachment.getText()));
      if (selected != null) {
        attachment.setText(selected.toAbsolutePath().toString());
      }
    });
    GridBagConstraints place = new GridBagConstraints();
    place.gridx = 1; place.gridy = 4; place.anchor = GridBagConstraints.WEST;
    form.add(choose, place);
    JButton save = new JButton("Save identity", SilkIcons.SAVE);
    JButton cancel = new JButton("Cancel", SilkIcons.CLOSE);
    save.addActionListener(event -> {
      if (title.getText().isBlank()) {
        JOptionPane.showMessageDialog(dialog, "Enter an identity name.");
        return;
      }
      Path attachmentPath;
      try { attachmentPath = KeyFilePicker.parsePath(attachment.getText()); }
      catch (java.nio.file.InvalidPathException invalid) {
        JOptionPane.showMessageDialog(dialog, "Enter a valid private key path.");
        return;
      }
      if (attachmentPath != null && !Files.isRegularFile(attachmentPath)) {
        JOptionPane.showMessageDialog(dialog, "The private key file does not exist: " + attachmentPath);
        return;
      }
      char[] secret = password.getPassword();
      Change change = new Change(title.getText().trim(), user.getText().trim(),
          secret.length == 0 ? null : secret, attachmentPath);
      save.setEnabled(false); cancel.setEnabled(false);
      dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
      saveAction.apply(change).whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
        change.clear();
        if (!dialog.isDisplayable()) return;
        if (error == null) {
          password.setText("");
          dialog.dispose();
        } else {
          save.setEnabled(true); cancel.setEnabled(true);
          dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
          Throwable cause = error;
          while (cause.getCause() != null) cause = cause.getCause();
          JOptionPane.showMessageDialog(dialog, cause.getMessage(), "Could not save identity",
              JOptionPane.ERROR_MESSAGE);
        }
      }));
    });
    cancel.addActionListener(event -> { password.setText(""); dialog.dispose(); });
    DialogEscape.bind(dialog, cancel);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(save); buttons.add(cancel);
    dialog.add(form, BorderLayout.CENTER);
    dialog.add(buttons, BorderLayout.SOUTH);
    dialog.getRootPane().setDefaultButton(save);
    dialog.pack(); dialog.setLocationRelativeTo(owner); dialog.setVisible(true);
  }

  public static Change show(Window owner, VaultEntry current) {
    JTextField title = new JTextField(current == null ? "" : current.title(), 25);
    JTextField user = new JTextField(current == null ? "" : current.username(), 25);
    JPasswordField password = new JPasswordField(25);
    JTextField attachment = new JTextField(25);
    attachment.setToolTipText("Paste a private key path, or choose a file.");
    JPanel form = new JPanel(new GridBagLayout());
    row(form, 0, "Name", title);
    row(form, 1, "Username", user);
    row(form, 2, current == null ? "Password or key passphrase" : "New password (blank keeps current)", password);
    row(form, 3, "Private key attachment", attachment);
    JButton choose = new JButton("Choose key file...", SilkIcons.OPEN_VAULT);
    choose.addActionListener(event -> {
      Path selected = KeyFilePicker.choose(SwingUtilities.getWindowAncestor(form), safePath(attachment.getText()));
      if (selected != null) {
        attachment.setText(selected.toAbsolutePath().toString());
      }
    });
    GridBagConstraints place = new GridBagConstraints();
    place.gridx = 1;
    place.gridy = 4;
    place.anchor = GridBagConstraints.WEST;
    form.add(choose, place);
    while (true) {
      if (!confirmIdentity(owner, form, current == null ? "New identity" : "Edit identity")) {
        password.setText("");
        return null;
      }
      if (!title.getText().isBlank()) {
        Path attachmentPath;
        try { attachmentPath = KeyFilePicker.parsePath(attachment.getText()); }
        catch (java.nio.file.InvalidPathException invalid) {
          JOptionPane.showMessageDialog(owner, "Enter a valid private key path.");
          continue;
        }
        if (attachmentPath != null && !Files.isRegularFile(attachmentPath)) {
          JOptionPane.showMessageDialog(owner, "The private key file does not exist: " + attachmentPath);
          continue;
        }
        char[] secret = password.getPassword();
        password.setText("");
        return new Change(title.getText().trim(), user.getText().trim(),
            secret.length == 0 ? null : secret, attachmentPath);
      }
      JOptionPane.showMessageDialog(owner, "Enter an identity name.", "Identity", JOptionPane.WARNING_MESSAGE);
    }
  }

  private static boolean confirmIdentity(Window owner, JPanel form, String title) {
    JDialog dialog = new JDialog(owner, title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
    JButton save = new JButton("Save identity", SilkIcons.SAVE);
    JButton cancel = new JButton("Cancel", SilkIcons.CLOSE);
    boolean[] accepted = {false};
    save.addActionListener(event -> { accepted[0] = true; dialog.dispose(); });
    cancel.addActionListener(event -> dialog.dispose());
    DialogEscape.bind(dialog, cancel);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(save);
    buttons.add(cancel);
    dialog.add(form, BorderLayout.CENTER);
    dialog.add(buttons, BorderLayout.SOUTH);
    dialog.getRootPane().setDefaultButton(save);
    dialog.pack();
    dialog.setLocationRelativeTo(owner);
    try { dialog.setVisible(true); }
    finally { dialog.dispose(); }
    return accepted[0];
  }

  private static void row(JPanel panel, int index, String label, java.awt.Component field) {
    GridBagConstraints left = new GridBagConstraints();
    left.gridx = 0; left.gridy = index; left.anchor = GridBagConstraints.WEST;
    left.insets = new Insets(4, 4, 4, 8);
    panel.add(new JLabel(label + ":"), left);
    GridBagConstraints right = new GridBagConstraints();
    right.gridx = 1; right.gridy = index; right.weightx = 1;
    right.fill = GridBagConstraints.HORIZONTAL; right.insets = new Insets(4, 0, 4, 4);
    panel.add(field, right);
  }

  private static Path safePath(String value) {
    try { return KeyFilePicker.parsePath(value); }
    catch (java.nio.file.InvalidPathException invalid) { return null; }
  }

  public record Change(String title, String username, char[] password, Path attachmentPath) {
    public void clear() { if (password != null) Arrays.fill(password, '\0'); }
    public String attachmentName() {
      return attachmentPath == null ? null : attachmentPath.getFileName().toString();
    }
  }
}
