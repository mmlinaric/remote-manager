package com.mmlinaric.remotemanager.ui.connections;

import com.mmlinaric.remotemanager.model.AuthenticationType;
import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.model.ConnectionFolder;
import com.mmlinaric.remotemanager.ui.DialogEscape;
import com.mmlinaric.remotemanager.ui.KeyFilePicker;
import com.mmlinaric.remotemanager.ui.SilkIcons;
import com.mmlinaric.remotemanager.ui.vault.IdentityEditor;
import com.mmlinaric.remotemanager.vault.VaultEntry;
import com.mmlinaric.remotemanager.vault.kdbx.KdbxVault;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
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
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
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
    private final JPanel folderRow = new JPanel(new BorderLayout(4, 0));
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
    private final JPanel credentialRow = new JPanel(new BorderLayout(4, 0));
    private final JPanel sudoRow = new JPanel(new BorderLayout(4, 0));
    private final JPanel attachmentRow = new JPanel(new BorderLayout());
    private final JPanel keyFileRow = new JPanel(new BorderLayout(4, 0));
    private final JButton save = new JButton("Save host", SilkIcons.SAVE);
    private final JButton cancel = new JButton("Cancel", SilkIcons.CLOSE);
    private final Function<Submission, CompletableFuture<Void>> saveAction;
    private final Set<UUID> availableIdentities;
    private final Map<UUID, VaultEntry> credentialsById = new HashMap<>();
    private final Map<UUID, IdentityEditor.Change> drafts = new LinkedHashMap<>();
    private UUID sshDirectId;
    private UUID sudoDirectId;
    private JLabel sshSourceLabel,
            sshPasswordLabel,
            credentialLabel,
            attachmentLabel,
            keyFileLabel,
            sudoPasswordLabel,
            sudoIdentityLabel;

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
        sshCredential.addItem(null);
        sudoCredential.addItem(null);
        entries.forEach(entry -> {
            sshCredential.addItem(entry);
            sudoCredential.addItem(entry);
        });
        sshCredential.setRenderer(new OptionalRenderer("Choose identity..."));
        sudoCredential.setRenderer(new OptionalRenderer("(none)"));
        shrinkToAvailableWidth(sshCredential);
        shrinkToAvailableWidth(sudoCredential);

        folderPath.setEditable(false);
        shrinkToAvailableWidth(folderPath);
        folderRow.add(folderPath, BorderLayout.CENTER);
        JButton chooseFolder = new JButton("Choose folder...", SilkIcons.FOLDER);
        chooseFolder.addActionListener(event -> {
            FolderPickerDialog.Selection choice = FolderPickerDialog.choose(
                    this, this.folders, this.selectedFolder == null ? null : this.selectedFolder.id());
            if (choice != null) chooseFolder(choice.folderId());
        });
        folderRow.add(chooseFolder, BorderLayout.EAST);

        JButton createIdentity = new JButton("New identity...", SilkIcons.NEW_IDENTITY);
        createIdentity.addActionListener(event -> createIdentity(sshCredential));
        credentialRow.add(sshCredential, BorderLayout.CENTER);
        credentialRow.add(createIdentity, BorderLayout.EAST);
        JButton createSudo = new JButton("New identity...", SilkIcons.NEW_IDENTITY);
        createSudo.addActionListener(event -> createIdentity(sudoCredential));
        sudoRow.add(sudoCredential, BorderLayout.CENTER);
        sudoRow.add(createSudo, BorderLayout.EAST);
        attachmentRow.add(attachment, BorderLayout.CENTER);
        JButton browseKey = new JButton("Browse...", SilkIcons.OPEN_VAULT);
        browseKey.addActionListener(event -> {
            String currentPath = keyFile.getText().trim();
            java.nio.file.Path selected =
                    KeyFilePicker.choose(this, currentPath.isBlank() ? null : java.nio.file.Path.of(currentPath));
            if (selected != null) keyFile.setText(selected.toAbsolutePath().toString());
        });
        keyFileRow.add(keyFile, BorderLayout.CENTER);
        keyFileRow.add(browseKey, BorderLayout.EAST);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        JScrollPane notesScroll = new JScrollPane(notes);
        Insets notesInsets = notes.getInsets();
        Insets scrollInsets = notesScroll.getInsets();
        int notesHeight = notes.getFontMetrics(notes.getFont()).getHeight() * notes.getRows()
                + notesInsets.top
                + notesInsets.bottom
                + scrollInsets.top
                + scrollInsets.bottom;
        notesScroll.setPreferredSize(new Dimension(0, notesHeight));
        notesScroll.setMinimumSize(new Dimension(0, notesHeight));

        JPanel form = new FormPanel();
        int row = 0;
        addRow(form, row++, "Name", name);
        addRow(form, row++, "Host address", host);
        addRow(form, row++, "Port", port);
        addRow(form, row++, "SSH username", user);
        addRow(form, row++, "Folder", folderRow);
        addRow(form, row++, "Authentication", auth);
        sshSourceLabel = addRow(form, row++, "SSH password source", sshSource);
        sshPasswordLabel = addRow(form, row++, "SSH password", sshPassword);
        credentialLabel = addRow(form, row++, "Identity", credentialRow);
        attachmentLabel = addRow(form, row++, "Private key attachment", attachmentRow);
        keyFileLabel = addRow(form, row++, "Private key file", keyFileRow);
        addRow(form, row++, "Sudo password source", sudoSource);
        sudoPasswordLabel = addRow(form, row++, "Sudo password", sudoPassword);
        sudoIdentityLabel = addRow(form, row++, "Sudo identity", sudoRow);
        addRow(form, row++, "Notes", notesScroll);
        GridBagConstraints bottomSpace = new GridBagConstraints();
        bottomSpace.gridx = 0;
        bottomSpace.gridy = row;
        bottomSpace.gridwidth = 2;
        bottomSpace.weighty = 1;
        bottomSpace.fill = GridBagConstraints.VERTICAL;
        form.add(Box.createVerticalGlue(), bottomSpace);
        auth.addActionListener(event -> updateAuthFields());
        sshSource.addActionListener(event -> updateAuthFields());
        sudoSource.addActionListener(event -> updateAuthFields());
        sshCredential.addActionListener(event -> {
            refreshAttachments();
            maybeFillUser();
        });
        if (current == null) auth.setSelectedItem(AuthenticationType.PASSWORD);
        loadValues(current, selectedFolder);
        updateAuthFields();
        sshPassword.setToolTipText("When editing, leave blank to keep the saved SSH password.");
        sudoPassword.setToolTipText("When editing, leave blank to keep the saved sudo password.");

        JPanel bottom = new JPanel(new BorderLayout());
        hint.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 8, 0, 8));
        bottom.add(hint, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        save.addActionListener(event -> submit(current));
        cancel.addActionListener(event -> dispose());
        DialogEscape.bind(this, cancel);
        actions.add(save);
        actions.add(cancel);
        bottom.add(actions, BorderLayout.SOUTH);
        add(new JScrollPane(form), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
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

    private void updateAuthFields() {
        AuthenticationType type = (AuthenticationType) auth.getSelectedItem();
        boolean passwordAuth = type == AuthenticationType.PASSWORD;
        boolean keyInVault = type == AuthenticationType.KDBX_PRIVATE_KEY;
        rowVisible(sshSourceLabel, sshSource, passwordAuth);
        rowVisible(sshPasswordLabel, sshPassword, passwordAuth && sshSource.getSelectedItem() == PasswordSource.HOST);
        rowVisible(
                credentialLabel,
                credentialRow,
                keyInVault || (passwordAuth && sshSource.getSelectedItem() == PasswordSource.IDENTITY));
        rowVisible(attachmentLabel, attachmentRow, keyInVault);
        rowVisible(keyFileLabel, keyFileRow, type == AuthenticationType.PRIVATE_KEY_FILE);
        rowVisible(sudoPasswordLabel, sudoPassword, sudoSource.getSelectedItem() == SudoSource.HOST);
        rowVisible(sudoIdentityLabel, sudoRow, sudoSource.getSelectedItem() == SudoSource.IDENTITY);
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
            sshPasswordLabel.setText("SSH password (blank keeps saved):");
        } else if (current.authenticationType() == AuthenticationType.PASSWORD) {
            sshSource.setSelectedItem(PasswordSource.IDENTITY);
        }
        if (sshDirectId == null) chooseEntry(sshCredential, current.sshCredentialEntryId());
        if (current.sudoCredentialEntryId() == null) sudoSource.setSelectedItem(SudoSource.NONE);
        else if (KdbxVault.isHostSecret(credentialsById.get(current.sudoCredentialEntryId()), current.id(), "sudo")) {
            sudoDirectId = current.sudoCredentialEntryId();
            sudoSource.setSelectedItem(SudoSource.HOST);
            sudoPasswordLabel.setText("Sudo password (blank keeps saved):");
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
            boolean usesSshIdentity = selectedAuth == AuthenticationType.KDBX_PRIVATE_KEY
                    || (selectedAuth == AuthenticationType.PASSWORD && !directSsh);
            boolean directSudo = sudoSource.getSelectedItem() == SudoSource.HOST;
            boolean usesSudoIdentity = sudoSource.getSelectedItem() == SudoSource.IDENTITY;
            if (directSsh && sshDirectId == null && sshSecret.length == 0)
                throw new IllegalArgumentException("Enter an SSH password for this host.");
            if (directSudo && sudoDirectId == null && sudoSecret.length == 0)
                throw new IllegalArgumentException("Enter a sudo password for this host.");
            if (usesSshIdentity && selectedSsh == null)
                throw new IllegalArgumentException("Choose or create an SSH identity.");
            if (usesSudoIdentity && selectedSudo == null)
                throw new IllegalArgumentException("Choose or create a sudo identity.");
            if (usesSshIdentity
                    && !drafts.containsKey(selectedSsh.id())
                    && !availableIdentities.contains(selectedSsh.id()))
                throw new IllegalArgumentException("The selected SSH identity is missing.");
            if (usesSudoIdentity
                    && !drafts.containsKey(selectedSudo.id())
                    && !availableIdentities.contains(selectedSudo.id()))
                throw new IllegalArgumentException("The selected sudo identity is missing.");
            if (selectedAuth == AuthenticationType.PASSWORD && usesSshIdentity && !selectedSsh.hasPassword())
                throw new IllegalArgumentException("This identity has no SSH password.");
            if (usesSudoIdentity && !selectedSudo.hasPassword())
                throw new IllegalArgumentException("The sudo identity has no password.");
            if (selectedAuth == AuthenticationType.KDBX_PRIVATE_KEY && (String) attachment.getSelectedItem() == null)
                throw new IllegalArgumentException("Choose an identity with a private key attachment.");
            Map<UUID, IdentityEditor.Change> usedDrafts = new LinkedHashMap<>();
            if (usesSshIdentity && drafts.containsKey(selectedSsh.id()))
                usedDrafts.put(selectedSsh.id(), drafts.get(selectedSsh.id()));
            if (usesSudoIdentity && drafts.containsKey(selectedSudo.id()))
                usedDrafts.put(selectedSudo.id(), drafts.get(selectedSudo.id()));
            UUID hostId = current == null ? UUID.randomUUID() : current.id();
            UUID sshId = directSsh
                    ? sshDirectId == null ? UUID.randomUUID() : sshDirectId
                    : usesSshIdentity ? selectedSsh.id() : null;
            UUID sudoId = directSudo
                    ? sudoDirectId == null ? UUID.randomUUID() : sudoDirectId
                    : usesSudoIdentity ? selectedSudo.id() : null;
            Map<UUID, HostPassword> hostPasswords = new LinkedHashMap<>();
            if (directSsh) hostPasswords.put(sshId, new HostPassword("ssh", sshSecret.length == 0 ? null : sshSecret));
            if (directSudo)
                hostPasswords.put(sudoId, new HostPassword("sudo", sudoSecret.length == 0 ? null : sudoSecret));
            Connection result = new Connection(
                    hostId,
                    name.getText().trim(),
                    host.getText().trim(),
                    (Integer) port.getValue(),
                    user.getText().trim(),
                    selectedFolder == null ? null : selectedFolder.id(),
                    selectedAuth,
                    sshId,
                    sudoId,
                    selectedAuth == AuthenticationType.KDBX_PRIVATE_KEY ? (String) attachment.getSelectedItem() : null,
                    selectedAuth == AuthenticationType.PRIVATE_KEY_FILE
                            ? keyFile.getText().trim()
                            : null,
                    notes.getText(),
                    current == null ? 0 : current.sortOrder());
            save.setEnabled(false);
            cancel.setEnabled(false);
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            hint.setText("Saving host to vault...");
            saveAction
                    .apply(new Submission(result, usedDrafts, hostPasswords))
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

    private static void rowVisible(JLabel label, JComponent field, boolean visible) {
        label.setVisible(visible);
        field.setVisible(visible);
    }

    private static void shrinkToAvailableWidth(JComponent field) {
        field.setPreferredSize(new Dimension(0, field.getPreferredSize().height));
    }

    private static final class FormPanel extends JPanel implements Scrollable {
        FormPanel() {
            super(new GridBagLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(24, visible.height - 24);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static JLabel addRow(JPanel form, int index, String label, JComponent field) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = index;
        left.anchor = GridBagConstraints.NORTHWEST;
        left.insets = new Insets(5, 6, 5, 8);
        JLabel caption = new JLabel(label + ":");
        form.add(caption, left);
        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = index;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(5, 0, 5, 6);
        form.add(field, right);
        return caption;
    }

    private static final class OptionalRenderer extends javax.swing.DefaultListCellRenderer {
        private final String empty;

        OptionalRenderer(String empty) {
            this.empty = empty;
        }

        @Override
        public java.awt.Component getListCellRendererComponent(
                javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focused) {
            return super.getListCellRendererComponent(list, value == null ? empty : value, index, selected, focused);
        }
    }
}
