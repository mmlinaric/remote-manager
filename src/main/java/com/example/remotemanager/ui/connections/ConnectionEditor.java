package com.example.remotemanager.ui.connections;

import com.example.remotemanager.model.AuthenticationType;
import com.example.remotemanager.model.Connection;
import com.example.remotemanager.model.ConnectionFolder;
import com.example.remotemanager.vault.VaultEntry;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.List;
import java.util.UUID;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

public final class ConnectionEditor extends JDialog {
  private final JTextField name = new JTextField(24);
  private final JTextField host = new JTextField(24);
  private final JSpinner port = new JSpinner(new SpinnerNumberModel(22, 1, 65535, 1));
  private final JTextField user = new JTextField(24);
  private final JComboBox<ConnectionFolder> folder = new JComboBox<>();
  private final JComboBox<AuthenticationType> auth = new JComboBox<>(AuthenticationType.values());
  private final JComboBox<VaultEntry> sshCredential = new JComboBox<>();
  private final JComboBox<VaultEntry> sudoCredential = new JComboBox<>();
  private final JComboBox<String> attachment = new JComboBox<>();
  private final JTextField keyFile = new JTextField(24);
  private final JTextArea notes = new JTextArea(4, 24);
  private Connection result;

  public ConnectionEditor(
      Window owner,
      Connection current,
      UUID selectedFolder,
      List<ConnectionFolder> folders,
      List<VaultEntry> entries) {
    super(
        owner,
        current == null ? "New connection" : "Edit connection",
        ModalityType.APPLICATION_MODAL);
    folder.addItem(null);
    folders.forEach(folder::addItem);
    sshCredential.addItem(null);
    sudoCredential.addItem(null);
    folder.setRenderer(new OptionalItemRenderer("(root)"));
    sshCredential.setRenderer(new OptionalItemRenderer("(none)"));
    sudoCredential.setRenderer(new OptionalItemRenderer("(none)"));
    entries.forEach(
        entry -> {
          sshCredential.addItem(entry);
          sudoCredential.addItem(entry);
        });
    sshCredential.addActionListener(event -> refreshAttachments());
    JPanel form = new JPanel(new GridBagLayout());
    int row = 0;
    addRow(form, row++, "Name", name);
    addRow(form, row++, "Host", host);
    addRow(form, row++, "Port", port);
    addRow(form, row++, "User", user);
    addRow(form, row++, "Folder", folder);
    addRow(form, row++, "SSH authentication", auth);
    addRow(form, row++, "SSH credential", sshCredential);
    addRow(form, row++, "Key attachment", attachment);
    addRow(form, row++, "Private key file", keyFile);
    addRow(form, row++, "Sudo credential", sudoCredential);
    addRow(form, row, "Notes", new JScrollPane(notes));
    loadValues(current, selectedFolder);
    JButton save = new JButton("Save");
    save.addActionListener(event -> saveConnection(current));
    JButton cancel = new JButton("Cancel");
    cancel.addActionListener(event -> dispose());
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(save);
    buttons.add(cancel);
    add(new JScrollPane(form), BorderLayout.CENTER);
    add(buttons, BorderLayout.SOUTH);
    pack();
    setLocationRelativeTo(owner);
  }

  public Connection result() {
    return result;
  }

  private void loadValues(Connection current, UUID selectedFolder) {
    if (current == null) {
      chooseFolder(selectedFolder);
      return;
    }

    name.setText(current.name());
    host.setText(current.hostname());
    port.setValue(current.port());
    user.setText(current.username());
    auth.setSelectedItem(current.authenticationType());
    keyFile.setText(current.privateKeyFilePath());
    notes.setText(current.notes());
    chooseFolder(current.parentFolderId());
    chooseEntry(sshCredential, current.sshCredentialEntryId());
    chooseEntry(sudoCredential, current.sudoCredentialEntryId());
    refreshAttachments();
    attachment.setSelectedItem(current.privateKeyAttachmentName());
  }

  private void saveConnection(Connection current) {
    try {
      ConnectionFolder selectedFolder = (ConnectionFolder) folder.getSelectedItem();
      VaultEntry selectedSshCredential = (VaultEntry) sshCredential.getSelectedItem();
      VaultEntry selectedSudoCredential = (VaultEntry) sudoCredential.getSelectedItem();
      result =
          new Connection(
              current == null ? UUID.randomUUID() : current.id(),
              name.getText().trim(),
              host.getText().trim(),
              (Integer) port.getValue(),
              user.getText().trim(),
              selectedFolder == null ? null : selectedFolder.id(),
              (AuthenticationType) auth.getSelectedItem(),
              selectedSshCredential == null ? null : selectedSshCredential.id(),
              selectedSudoCredential == null ? null : selectedSudoCredential.id(),
              (String) attachment.getSelectedItem(),
              keyFile.getText().trim(),
              notes.getText(),
              current == null ? 0 : current.sortOrder());
      dispose();
    } catch (IllegalArgumentException exception) {
      JOptionPane.showMessageDialog(
          this, exception.getMessage(), "Invalid connection", JOptionPane.WARNING_MESSAGE);
    }
  }

  private void chooseFolder(UUID id) {
    if (id == null) {
      folder.setSelectedIndex(0);
      return;
    }
    for (int i = 0; i < folder.getItemCount(); i++) {
      ConnectionFolder candidate = folder.getItemAt(i);
      if (candidate != null && candidate.id().equals(id)) {
        folder.setSelectedIndex(i);
        return;
      }
    }
  }

  private void chooseEntry(JComboBox<VaultEntry> box, UUID id) {
    if (id == null) {
      box.setSelectedIndex(0);
      return;
    }
    for (int i = 0; i < box.getItemCount(); i++) {
      VaultEntry candidate = box.getItemAt(i);
      if (candidate != null && candidate.id().equals(id)) {
        box.setSelectedIndex(i);
        return;
      }
    }
  }

  private void refreshAttachments() {
    attachment.removeAllItems();
    VaultEntry selected = (VaultEntry) sshCredential.getSelectedItem();
    if (selected != null) {
      selected.attachments().forEach(attachment::addItem);
    }
  }

  private static void addRow(JPanel form, int row, String label, JComponent field) {
    GridBagConstraints left = new GridBagConstraints();
    left.gridx = 0;
    left.gridy = row;
    left.anchor = GridBagConstraints.NORTHWEST;
    left.insets = new Insets(3, 5, 3, 6);
    form.add(new JLabel(label + ":"), left);
    GridBagConstraints right = new GridBagConstraints();
    right.gridx = 1;
    right.gridy = row;
    right.weightx = 1;
    right.fill = GridBagConstraints.HORIZONTAL;
    right.insets = new Insets(3, 0, 3, 5);
    form.add(field, right);
  }

  private static final class OptionalItemRenderer extends javax.swing.DefaultListCellRenderer {
    private final String emptyLabel;

    private OptionalItemRenderer(String emptyLabel) {
      this.emptyLabel = emptyLabel;
    }

    @Override
    public java.awt.Component getListCellRendererComponent(
        javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focused) {
      return super.getListCellRendererComponent(
          list, value == null ? emptyLabel : value, index, selected, focused);
    }
  }
}
