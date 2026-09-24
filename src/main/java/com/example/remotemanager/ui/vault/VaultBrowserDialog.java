package com.example.remotemanager.ui.vault;

import com.example.remotemanager.vault.VaultEntry;
import com.example.remotemanager.vault.kdbx.KdbxVault;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public final class VaultBrowserDialog extends JDialog {
  private final KdbxVault vault;
  private final JList<VaultEntry> entries = new JList<>();
  private final JLabel status = new JLabel(" ");

  public VaultBrowserDialog(Window owner, KdbxVault vault) {
    super(owner, "KeePass entries", ModalityType.APPLICATION_MODAL);
    this.vault = vault;
    setSize(560, 420);
    setLocationRelativeTo(owner);
    add(new JScrollPane(entries), BorderLayout.CENTER);
    add(buttons(), BorderLayout.SOUTH);
    refresh();
  }

  private JPanel buttons() {
    JPanel panel = new JPanel(new BorderLayout());
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JButton create = new JButton("New entry");
    JButton edit = new JButton("Edit entry");
    JButton close = new JButton("Close");
    create.addActionListener(event -> editEntry(null));
    edit.addActionListener(
        event -> {
          VaultEntry selected = entries.getSelectedValue();
          if (selected != null) {
            editEntry(selected);
          }
        });
    close.addActionListener(event -> dispose());
    actions.add(create);
    actions.add(edit);
    actions.add(close);
    panel.add(actions, BorderLayout.CENTER);
    panel.add(status, BorderLayout.SOUTH);
    return panel;
  }

  private void refresh() {
    try {
      List<VaultEntry> current = vault.entries();
      entries.setListData(current.toArray(VaultEntry[]::new));
    } catch (Exception error) {
      showError(error);
    }
  }

  private void editEntry(VaultEntry existing) {
    EntryForm form = new EntryForm(existing);
    int choice =
        JOptionPane.showConfirmDialog(
            this,
            form,
            existing == null ? "New KeePass entry" : "Edit KeePass entry",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
    if (choice != JOptionPane.OK_OPTION) {
      form.clearPassword();
      return;
    }

    char[] masterPassword = askMasterPassword();
    if (masterPassword == null) {
      form.clearPassword();
      return;
    }
    EntryChange change;
    try {
      change = form.values();
    } catch (IllegalArgumentException error) {
      Arrays.fill(masterPassword, '\0');
      form.clearPassword();
      showError(error);
      return;
    }
    form.clearPassword();
    status.setText("Saving vault...");
    CompletableFuture.runAsync(() -> saveChange(existing, change, masterPassword))
        .whenComplete(
            (ignored, error) ->
                SwingUtilities.invokeLater(
                    () -> {
                      status.setText(error == null ? "Vault saved" : "Vault save failed");
                      if (error == null) {
                        refresh();
                      } else {
                        showError(error);
                      }
                    }));
  }

  private void saveChange(VaultEntry existing, EntryChange change, char[] masterPassword) {
    byte[] attachment = null;
    try {
      if (change.attachmentPath() != null) {
        attachment = Files.readAllBytes(change.attachmentPath());
      }
      if (existing == null) {
        vault.addEntry(
            change.title(),
            change.username(),
            change.password(),
            change.fields(),
            change.attachmentName(),
            attachment);
      } else {
        vault.updateEntry(
            existing.id(),
            change.title(),
            change.username(),
            change.password(),
            change.fields(),
            change.attachmentName(),
            attachment);
      }
      vault.save(masterPassword);
    } catch (Exception error) {
      throw new RuntimeException(error);
    } finally {
      Arrays.fill(masterPassword, '\0');
      if (change.password() != null) {
        Arrays.fill(change.password(), '\0');
      }
      if (attachment != null) {
        Arrays.fill(attachment, (byte) 0);
      }
    }
  }

  private char[] askMasterPassword() {
    JPasswordField field = new JPasswordField(24);
    int choice =
        JOptionPane.showConfirmDialog(
            this, field, "Confirm vault master password to save", JOptionPane.OK_CANCEL_OPTION);
    char[] password = choice == JOptionPane.OK_OPTION ? field.getPassword() : null;
    field.setText("");
    return password;
  }

  private void showError(Throwable error) {
    Throwable cause = error.getCause() == null ? error : error.getCause();
    JOptionPane.showMessageDialog(
        this, cause.getMessage(), "Vault error", JOptionPane.ERROR_MESSAGE);
  }

  private record EntryChange(
      String title,
      String username,
      char[] password,
      Map<String, String> fields,
      String attachmentName,
      Path attachmentPath) {}

  private static final class EntryForm extends JPanel {
    private final JTextField title = new JTextField(28);
    private final JTextField username = new JTextField(28);
    private final JPasswordField password = new JPasswordField(28);
    private final JTextArea fields = new JTextArea(5, 28);
    private final JTextField attachment = new JTextField(28);
    private Path attachmentPath;

    private EntryForm(VaultEntry existing) {
      super(new GridBagLayout());
      int row = 0;
      addRow(row++, "Title", title);
      addRow(row++, "Username", username);
      addRow(row++, "Password or key passphrase", password);
      addRow(row++, "Custom fields, key=value", new JScrollPane(fields));
      addRow(row, "Attachment", attachment);
      JButton browse = new JButton("Choose attachment...");
      browse.addActionListener(event -> chooseAttachment());
      GridBagConstraints browseConstraints = new GridBagConstraints();
      browseConstraints.gridx = 1;
      browseConstraints.gridy = row + 1;
      browseConstraints.anchor = GridBagConstraints.WEST;
      add(browse, browseConstraints);

      if (existing != null) {
        title.setText(existing.title());
        username.setText(existing.username());
        existing
            .fields()
            .forEach(
                (key, value) -> {
                  if (!List.of("Title", "UserName", "Password", "URL", "Notes").contains(key)) {
                    fields.append(key + "=" + value + "\n");
                  }
                });
        if (!existing.attachments().isEmpty()) {
          attachment.setText(existing.attachments().getFirst());
        }
      }
    }

    private void chooseAttachment() {
      JFileChooser chooser = new JFileChooser();
      if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
        attachmentPath = chooser.getSelectedFile().toPath();
        attachment.setText(attachmentPath.getFileName().toString());
      }
    }

    private EntryChange values() {
      if (title.getText().isBlank()) {
        throw new IllegalArgumentException("Entry title is required");
      }
      Map<String, String> custom = new LinkedHashMap<>();
      for (String line : fields.getText().split("\\R")) {
        if (line.isBlank()) {
          continue;
        }
        int equals = line.indexOf('=');
        if (equals <= 0) {
          throw new IllegalArgumentException("Custom fields must use key=value lines");
        }
        String key = line.substring(0, equals).trim();
        if (List.of("Title", "UserName", "Password", "URL", "Notes").contains(key)) {
          throw new IllegalArgumentException(
              "Standard KeePass fields cannot be custom fields: " + key);
        }
        custom.put(key, line.substring(equals + 1));
      }
      char[] enteredPassword = password.getPassword();
      return new EntryChange(
          title.getText().trim(),
          username.getText().trim(),
          enteredPassword.length == 0 ? null : enteredPassword,
          custom,
          attachmentPath == null ? null : attachment.getText().trim(),
          attachmentPath);
    }

    private void clearPassword() {
      password.setText("");
    }

    private void addRow(int row, String label, java.awt.Component field) {
      GridBagConstraints caption = new GridBagConstraints();
      caption.gridx = 0;
      caption.gridy = row;
      caption.anchor = GridBagConstraints.NORTHWEST;
      caption.insets = new Insets(3, 4, 3, 6);
      add(new JLabel(label + ":"), caption);

      GridBagConstraints input = new GridBagConstraints();
      input.gridx = 1;
      input.gridy = row;
      input.weightx = 1;
      input.fill = GridBagConstraints.HORIZONTAL;
      input.insets = new Insets(3, 0, 3, 4);
      add(field, input);
    }
  }
}
