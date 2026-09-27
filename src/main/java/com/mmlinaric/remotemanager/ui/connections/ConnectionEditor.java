package com.mmlinaric.remotemanager.ui.connections;

import com.mmlinaric.remotemanager.model.AuthenticationType;
import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.model.ConnectionFolder;
import com.mmlinaric.remotemanager.ui.DialogEscape;
import com.mmlinaric.remotemanager.ui.SilkIcons;
import com.mmlinaric.remotemanager.ui.vault.IdentityEditor;
import com.mmlinaric.remotemanager.vault.VaultEntry;
import com.mmlinaric.remotemanager.vault.kdbx.KdbxVault;
import java.awt.BorderLayout;
import java.awt.Window;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/** Host editor with host passwords and reusable identities in the same flow. */
public final class ConnectionEditor extends JDialog {
    public record Submission(
            Connection connection,
            Map<UUID, IdentityEditor.Change> newIdentities,
            Map<UUID, HostPassword> hostPasswords) {}

    public record HostPassword(String purpose, char[] password) {
        public void clear() {
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    private enum PasswordSource {
        HOST("Password saved with this host"),
        IDENTITY("Reusable identity");
        private final String label;

        PasswordSource(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum SudoSource {
        NONE("(none)"),
        HOST("Password saved with this host"),
        IDENTITY("Reusable identity");
        private final String label;

        SudoSource(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final JTextField name = new JTextField(26);
    private final JTextField host = new JTextField(26);
    private final JSpinner port = new JSpinner(new SpinnerNumberModel(22, 1, 65535, 1));
    private final JTextField user = new JTextField(26);
    private final JTextField folderPath = new JTextField();
    private final List<ConnectionFolder> folders;
    private ConnectionFolder selectedFolder;
    private final JComboBox<AuthenticationType> auth = new JComboBox<>(AuthenticationType.values());
    private final JComboBox<PasswordSource> sshSource = new JComboBox<>(PasswordSource.values());
    private final JPasswordField sshPassword = new JPasswordField(26);
    private final JComboBox<VaultEntry> sshCredential = new JComboBox<>();
    private final JComboBox<SudoSource> sudoSource = new JComboBox<>(SudoSource.values());
    private final JPasswordField sudoPassword = new JPasswordField(26);
    private final JComboBox<VaultEntry> sudoCredential = new JComboBox<>();
    private final JComboBox<String> attachment = new JComboBox<>();
    private final JTextField keyFile = new JTextField();
    private final JTextArea notes = new JTextArea(4, 26);
    private final JLabel hint = new JLabel(" ");
    private final JButton save = new JButton("Save host", SilkIcons.SAVE);
    private final JButton cancel = new JButton("Cancel", SilkIcons.CLOSE);
    private final Function<Submission, CompletableFuture<Void>> saveAction;
    private final Set<UUID> availableIdentities;
    private final Map<UUID, VaultEntry> credentialsById = new HashMap<>();
    private final Map<UUID, IdentityEditor.Change> drafts = new LinkedHashMap<>();
    private UUID sshDirectId;
    private UUID sudoDirectId;
    private final ConnectionEditorLayout.Content layout;

    public ConnectionEditor(
            Window owner,
            Connection current,
            UUID selectedFolder,
            List<ConnectionFolder> folders,
            List<VaultEntry> entries,
            List<VaultEntry> credentials,
            Function<Submission, CompletableFuture<Void>> saveAction) {
        super(owner, current == null ? "New host" : "Edit host", ModalityType.APPLICATION_MODAL);
        this.saveAction = saveAction;
        this.folders = List.copyOf(folders);
        this.availableIdentities = entries.stream().map(entry -> entry.id()).collect(Collectors.toSet());
        credentials.forEach(entry -> credentialsById.put(entry.id(), entry));
        setLayout(new BorderLayout(8, 8));
        layout = ConnectionEditorLayout.create(
                new ConnectionEditorLayout.Fields(
                        name,
                        host,
                        port,
                        user,
                        folderPath,
                        auth,
                        sshSource,
                        sshPassword,
                        sshCredential,
                        sudoSource,
                        sudoPassword,
                        sudoCredential,
                        attachment,
                        keyFile,
                        notes,
                        hint,
                        save,
                        cancel),
                entries,
                new ConnectionEditorLayout.Actions(
                        this::selectFolder,
                        () -> createIdentity(sshCredential),
                        () -> createIdentity(sudoCredential),
                        this::browseKeyFile,
                        this::updateAuthFields,
                        this::sshIdentityChanged,
                        () -> submit(current),
                        this::dispose));
        if (current == null) auth.setSelectedItem(AuthenticationType.PASSWORD);
        loadValues(current, selectedFolder);
        updateAuthFields();
        DialogEscape.bind(this, cancel);
        add(layout.formScroll(), BorderLayout.CENTER);
        add(layout.footer(), BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);
        pack();
        setMinimumSize(new java.awt.Dimension(590, 470));
        setLocationRelativeTo(owner);
    }

    private void createIdentity(JComboBox<VaultEntry> target) {
        IdentityEditor.Change change = IdentityEditor.show(this, null);
        if (change == null) return;
        VaultEntry staged = new VaultEntry(
                UUID.randomUUID(),
                change.title(),
                change.username(),
                Map.of(),
                change.attachmentName() == null ? List.of() : List.of(change.attachmentName()),
                change.password() != null && change.password().length > 0);
        drafts.put(staged.id(), change);
        sshCredential.addItem(staged);
        sudoCredential.addItem(staged);
        target.setSelectedItem(staged);
        if (target == sshCredential && user.getText().isBlank()) user.setText(change.username());
    }

    private void maybeFillUser() {
        if (user.getText().isBlank() && sshCredential.getSelectedItem() instanceof VaultEntry entry)
            user.setText(entry.username());
    }

    private void sshIdentityChanged() {
        refreshAttachments();
        maybeFillUser();
    }

    private void updateAuthFields() {
        AuthenticationType type = (AuthenticationType) auth.getSelectedItem();
        boolean passwordAuth = type == AuthenticationType.PASSWORD;
        boolean keyInVault = type == AuthenticationType.KDBX_PRIVATE_KEY;
        ConnectionEditorLayout.setRowVisible(layout.sshSourceLabel(), sshSource, passwordAuth);
        ConnectionEditorLayout.setRowVisible(
                layout.sshPasswordLabel(),
                sshPassword,
                passwordAuth && sshSource.getSelectedItem() == PasswordSource.HOST);
        ConnectionEditorLayout.setRowVisible(
                layout.credentialLabel(),
                layout.credentialRow(),
                keyInVault || (passwordAuth && sshSource.getSelectedItem() == PasswordSource.IDENTITY));
        ConnectionEditorLayout.setRowVisible(layout.attachmentLabel(), layout.attachmentRow(), keyInVault);
        ConnectionEditorLayout.setRowVisible(
                layout.keyFileLabel(), layout.keyFileRow(), type == AuthenticationType.PRIVATE_KEY_FILE);
        ConnectionEditorLayout.setRowVisible(
                layout.sudoPasswordLabel(), sudoPassword, sudoSource.getSelectedItem() == SudoSource.HOST);
        ConnectionEditorLayout.setRowVisible(
                layout.sudoIdentityLabel(), layout.sudoRow(), sudoSource.getSelectedItem() == SudoSource.IDENTITY);
        hint.setText(
                switch (type) {
                    case PASSWORD ->
                        sshSource.getSelectedItem() == PasswordSource.HOST
                                ? "This SSH password is saved with the host in your KeePass vault."
                                : "Choose an existing identity or create one to share across hosts.";
                    case KDBX_PRIVATE_KEY -> "Choose an identity with an attached OpenSSH private key.";
                    case SSH_AGENT -> "Your running SSH agent supplies the key; no saved identity is needed.";
                    case PRIVATE_KEY_FILE -> "Choose a local private key. Its passphrase is requested when connecting.";
                });
        revalidate();
        repaint();
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
        if (KdbxVault.isHostSecret(credentialsById.get(current.sshCredentialEntryId()), current.id(), "ssh")) {
            sshDirectId = current.sshCredentialEntryId();
            sshSource.setSelectedItem(PasswordSource.HOST);
            layout.sshPasswordLabel().setText("SSH password (blank keeps saved):");
        } else if (current.authenticationType() == AuthenticationType.PASSWORD) {
            sshSource.setSelectedItem(PasswordSource.IDENTITY);
        }
        if (sshDirectId == null) chooseEntry(sshCredential, current.sshCredentialEntryId());
        if (current.sudoCredentialEntryId() == null) sudoSource.setSelectedItem(SudoSource.NONE);
        else if (KdbxVault.isHostSecret(credentialsById.get(current.sudoCredentialEntryId()), current.id(), "sudo")) {
            sudoDirectId = current.sudoCredentialEntryId();
            sudoSource.setSelectedItem(SudoSource.HOST);
            layout.sudoPasswordLabel().setText("Sudo password (blank keeps saved):");
        } else {
            sudoSource.setSelectedItem(SudoSource.IDENTITY);
            chooseEntry(sudoCredential, current.sudoCredentialEntryId());
        }
        refreshAttachments();
        attachment.setSelectedItem(current.privateKeyAttachmentName());
    }

    private void submit(Connection current) {
        char[] sshSecret = sshPassword.getPassword();
        char[] sudoSecret = sudoPassword.getPassword();
        try {
            AuthenticationType selectedAuth = (AuthenticationType) auth.getSelectedItem();
            VaultEntry selectedSsh = (VaultEntry) sshCredential.getSelectedItem();
            VaultEntry selectedSudo = (VaultEntry) sudoCredential.getSelectedItem();
            boolean directSsh =
                    selectedAuth == AuthenticationType.PASSWORD && sshSource.getSelectedItem() == PasswordSource.HOST;
            boolean directSudo = sudoSource.getSelectedItem() == SudoSource.HOST;
            boolean usesSudoIdentity = sudoSource.getSelectedItem() == SudoSource.IDENTITY;
            ConnectionSubmissionBuilder.Input input = new ConnectionSubmissionBuilder.Input(
                    current,
                    name.getText().trim(),
                    host.getText().trim(),
                    (Integer) port.getValue(),
                    user.getText().trim(),
                    selectedFolder == null ? null : selectedFolder.id(),
                    selectedAuth,
                    directSsh,
                    selectedSsh,
                    sshDirectId,
                    directSudo,
                    usesSudoIdentity,
                    selectedSudo,
                    sudoDirectId,
                    (String) attachment.getSelectedItem(),
                    keyFile.getText().trim(),
                    notes.getText());
            Submission submission =
                    new ConnectionSubmissionBuilder(availableIdentities, drafts).build(input, sshSecret, sudoSecret);
            save.setEnabled(false);
            cancel.setEnabled(false);
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            hint.setText("Saving host to vault...");
            saveAction
                    .apply(submission)
                    .whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
                        if (error == null) {
                            dispose();
                        } else {
                            save.setEnabled(true);
                            cancel.setEnabled(true);
                            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                            hint.setText("Save failed. Your changes are still here.");
                            Throwable cause = error.getCause() == null ? error : error.getCause();
                            JOptionPane.showMessageDialog(
                                    this, cause.getMessage(), "Could not save host", JOptionPane.ERROR_MESSAGE);
                        }
                    }));
        } catch (IllegalArgumentException error) {
            JOptionPane.showMessageDialog(this, error.getMessage(), "Check host details", JOptionPane.WARNING_MESSAGE);
        } finally {
            Arrays.fill(sshSecret, '\0');
            Arrays.fill(sudoSecret, '\0');
        }
    }

    @Override
    public void dispose() {
        sshPassword.setText("");
        sudoPassword.setText("");
        drafts.values().forEach(change -> change.clear());
        super.dispose();
    }

    private void chooseFolder(UUID id) {
        selectedFolder = folders.stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElse(null);
        String path = FolderPickerDialog.displayPath(folders, selectedFolder);
        folderPath.setText(path);
        folderPath.setCaretPosition(path.length());
        folderPath.setToolTipText(path);
    }

    private void selectFolder() {
        FolderPickerDialog.Selection choice =
                FolderPickerDialog.choose(this, folders, selectedFolder == null ? null : selectedFolder.id());
        if (choice != null) chooseFolder(choice.folderId());
    }

    private void browseKeyFile() {
        String currentPath = keyFile.getText().trim();
        java.nio.file.Path selected = com.mmlinaric.remotemanager.ui.KeyFilePicker.choose(
                this, currentPath.isBlank() ? null : java.nio.file.Path.of(currentPath));
        if (selected != null) keyFile.setText(selected.toAbsolutePath().toString());
    }

    private void chooseEntry(JComboBox<VaultEntry> box, UUID id) {
        if (id == null) {
            box.setSelectedIndex(0);
            return;
        }
        for (int i = 0; i < box.getItemCount(); i++) {
            VaultEntry item = box.getItemAt(i);
            if (item != null && item.id().equals(id)) {
                box.setSelectedIndex(i);
                return;
            }
        }
        VaultEntry missing = new VaultEntry(id, "Missing identity: choose another", "", Map.of(), List.of());
        box.addItem(missing);
        box.setSelectedItem(missing);
    }

    private void refreshAttachments() {
        attachment.removeAllItems();
        if (sshCredential.getSelectedItem() instanceof VaultEntry entry)
            entry.attachments().forEach(attachment::addItem);
    }
}
