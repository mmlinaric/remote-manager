package com.mmlinaric.remotemanager.ui.connections;

import com.mmlinaric.remotemanager.model.AuthenticationType;
import com.mmlinaric.remotemanager.ui.SilkIcons;
import com.mmlinaric.remotemanager.vault.VaultEntry;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;

/** Builds the host editor's Swing controls and returns the parts used by its workflow controller. */
final class ConnectionEditorLayout {
    private ConnectionEditorLayout() {}

    static Content create(Fields fields, List<VaultEntry> entries, Actions actions) {
        populateCredentials(fields.sshCredential(), fields.sudoCredential(), entries);
        JPanel folderRow = folderRow(fields.folderPath(), actions.chooseFolder());
        JPanel credentialRow = credentialRow(fields.sshCredential(), actions.createSshIdentity());
        JPanel sudoRow = credentialRow(fields.sudoCredential(), actions.createSudoIdentity());
        JPanel attachmentRow = new JPanel(new BorderLayout());
        attachmentRow.add(fields.attachment(), BorderLayout.CENTER);
        JPanel keyFileRow = keyFileRow(fields.keyFile(), actions.browseKeyFile());
        JScrollPane notes = notesScroll(fields.notes());

        FormPanel form = new FormPanel();
        int row = 0;
        addRow(form, row++, "Name", fields.name());
        addRow(form, row++, "Host address", fields.host());
        addRow(form, row++, "Port", fields.port());
        addRow(form, row++, "SSH username", fields.user());
        addRow(form, row++, "Folder", folderRow);
        addRow(form, row++, "Authentication", fields.auth());
        JLabel sshSourceLabel = addRow(form, row++, "SSH password source", fields.sshSource());
        JLabel sshPasswordLabel = addRow(form, row++, "SSH password", fields.sshPassword());
        JLabel credentialLabel = addRow(form, row++, "Identity", credentialRow);
        JLabel attachmentLabel = addRow(form, row++, "Private key attachment", attachmentRow);
        JLabel keyFileLabel = addRow(form, row++, "Private key file", keyFileRow);
        addRow(form, row++, "Sudo password source", fields.sudoSource());
        JLabel sudoPasswordLabel = addRow(form, row++, "Sudo password", fields.sudoPassword());
        JLabel sudoIdentityLabel = addRow(form, row++, "Sudo identity", sudoRow);
        addRow(form, row++, "Notes", notes);
        addBottomSpace(form, row);

        fields.auth().addActionListener(event -> actions.authenticationChanged().run());
        fields.sshSource()
                .addActionListener(event -> actions.authenticationChanged().run());
        fields.sudoSource()
                .addActionListener(event -> actions.authenticationChanged().run());
        fields.sshCredential()
                .addActionListener(event -> actions.sshIdentityChanged().run());
        fields.sshPassword().setToolTipText("When editing, leave blank to keep the saved SSH password.");
        fields.sudoPassword().setToolTipText("When editing, leave blank to keep the saved sudo password.");

        JPanel footer = footer(fields.hint(), fields.save(), fields.cancel(), actions.save(), actions.cancel());
        return new Content(
                new JScrollPane(form),
                footer,
                folderRow,
                credentialRow,
                sudoRow,
                attachmentRow,
                keyFileRow,
                sshSourceLabel,
                sshPasswordLabel,
                credentialLabel,
                attachmentLabel,
                keyFileLabel,
                sudoPasswordLabel,
                sudoIdentityLabel);
    }

    private static void populateCredentials(
            JComboBox<VaultEntry> sshCredential, JComboBox<VaultEntry> sudoCredential, List<VaultEntry> entries) {
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
    }

    private static JPanel folderRow(JTextField folderPath, Runnable chooseFolder) {
        folderPath.setEditable(false);
        shrinkToAvailableWidth(folderPath);
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.add(folderPath, BorderLayout.CENTER);
        JButton choose = new JButton("Choose folder...", SilkIcons.FOLDER);
        choose.addActionListener(event -> chooseFolder.run());
        row.add(choose, BorderLayout.EAST);
        return row;
    }

    private static JPanel credentialRow(JComboBox<VaultEntry> credential, Runnable createIdentity) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        JButton create = new JButton("New identity...", SilkIcons.NEW_IDENTITY);
        create.addActionListener(event -> createIdentity.run());
        row.add(credential, BorderLayout.CENTER);
        row.add(create, BorderLayout.EAST);
        return row;
    }

    private static JPanel keyFileRow(JTextField keyFile, Runnable browseKeyFile) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        JButton browse = new JButton("Browse...", SilkIcons.OPEN_VAULT);
        browse.addActionListener(event -> browseKeyFile.run());
        row.add(keyFile, BorderLayout.CENTER);
        row.add(browse, BorderLayout.EAST);
        return row;
    }

    private static JScrollPane notesScroll(JTextArea notes) {
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(notes);
        Insets notesInsets = notes.getInsets();
        Insets scrollInsets = scroll.getInsets();
        int height = notes.getFontMetrics(notes.getFont()).getHeight() * notes.getRows()
                + notesInsets.top
                + notesInsets.bottom
                + scrollInsets.top
                + scrollInsets.bottom;
        scroll.setPreferredSize(new Dimension(0, height));
        scroll.setMinimumSize(new Dimension(0, height));
        return scroll;
    }

    private static JPanel footer(
            JLabel hint, JButton save, JButton cancel, Runnable saveAction, Runnable cancelAction) {
        JPanel footer = new JPanel(new BorderLayout());
        hint.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 8));
        footer.add(hint, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        save.addActionListener(event -> saveAction.run());
        cancel.addActionListener(event -> cancelAction.run());
        actions.add(save);
        actions.add(cancel);
        footer.add(actions, BorderLayout.SOUTH);
        return footer;
    }

    private static void addBottomSpace(JPanel form, int row) {
        GridBagConstraints bottomSpace = new GridBagConstraints();
        bottomSpace.gridx = 0;
        bottomSpace.gridy = row;
        bottomSpace.gridwidth = 2;
        bottomSpace.weighty = 1;
        bottomSpace.fill = GridBagConstraints.VERTICAL;
        form.add(Box.createVerticalGlue(), bottomSpace);
    }

    static void setRowVisible(JLabel label, JComponent field, boolean visible) {
        label.setVisible(visible);
        field.setVisible(visible);
    }

    static void shrinkToAvailableWidth(JComponent field) {
        field.setPreferredSize(new Dimension(0, field.getPreferredSize().height));
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

    record Fields(
            JTextField name,
            JTextField host,
            JSpinner port,
            JTextField user,
            JTextField folderPath,
            JComboBox<AuthenticationType> auth,
            JComboBox<?> sshSource,
            JPasswordField sshPassword,
            JComboBox<VaultEntry> sshCredential,
            JComboBox<?> sudoSource,
            JPasswordField sudoPassword,
            JComboBox<VaultEntry> sudoCredential,
            JComboBox<String> attachment,
            JTextField keyFile,
            JTextArea notes,
            JLabel hint,
            JButton save,
            JButton cancel) {}

    record Actions(
            Runnable chooseFolder,
            Runnable createSshIdentity,
            Runnable createSudoIdentity,
            Runnable browseKeyFile,
            Runnable authenticationChanged,
            Runnable sshIdentityChanged,
            Runnable save,
            Runnable cancel) {}

    record Content(
            JScrollPane formScroll,
            JPanel footer,
            JPanel folderRow,
            JPanel credentialRow,
            JPanel sudoRow,
            JPanel attachmentRow,
            JPanel keyFileRow,
            JLabel sshSourceLabel,
            JLabel sshPasswordLabel,
            JLabel credentialLabel,
            JLabel attachmentLabel,
            JLabel keyFileLabel,
            JLabel sudoPasswordLabel,
            JLabel sudoIdentityLabel) {}

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

    private static final class OptionalRenderer extends DefaultListCellRenderer {
        private final String empty;

        OptionalRenderer(String empty) {
            this.empty = empty;
        }

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focused) {
            return super.getListCellRendererComponent(list, value == null ? empty : value, index, selected, focused);
        }
    }
}
