package com.mmlinaric.remotemanager.ui.main;

import com.mmlinaric.remotemanager.ui.DialogEscape;
import com.mmlinaric.remotemanager.ui.SilkIcons;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.LayoutFocusTraversalPolicy;

/** Modal interactions used to select a vault and establish its master password. */
final class VaultDialogs {
    private VaultDialogs() {}

    static Path chooseVaultFile(Frame owner, int mode) {
        FileDialog dialog =
                new FileDialog(owner, mode == FileDialog.LOAD ? "Open KeePass vault" : "Create KeePass vault", mode);
        if (mode == FileDialog.SAVE) {
            dialog.setFile("vault.kdbx");
        }
        try {
            dialog.setVisible(true);
            return dialog.getFiles().length == 0 ? null : dialog.getFiles()[0].toPath();
        } finally {
            dialog.dispose();
        }
    }

    static char[] askNewVaultPassword(Frame owner) {
        JPasswordField first = new JPasswordField(24);
        JPasswordField second = new JPasswordField(24);
        JDialog dialog = new JDialog(owner, "Create KeePass vault", Dialog.ModalityType.APPLICATION_MODAL);
        JButton create = new JButton("Create vault", SilkIcons.CREATE_VAULT);
        JButton cancel = new JButton("Cancel", SilkIcons.CLOSE);
        JLabel feedback = new JLabel();
        feedback.setForeground(new Color(0xB0, 0x20, 0x20));
        feedback.setVisible(false);

        JPanel fields = passwordFields(first, second, feedback);
        char[][] accepted = {null};
        create.addActionListener(event -> validatePassword(dialog, first, second, feedback, accepted));
        cancel.addActionListener(event -> dialog.dispose());
        DialogEscape.bind(dialog, cancel);
        showPasswordDialog(owner, dialog, first, second, fields, create, cancel);
        return accepted[0];
    }

    private static JPanel passwordFields(JPasswordField first, JPasswordField second, JLabel feedback) {
        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints label = new GridBagConstraints();
        label.gridx = 0;
        label.anchor = GridBagConstraints.WEST;
        label.insets = new Insets(0, 0, 6, 10);
        fields.add(new JLabel("Master password:"), label);
        label.gridy = 1;
        label.insets = new Insets(0, 0, 0, 10);
        fields.add(new JLabel("Confirm password:"), label);
        GridBagConstraints input = new GridBagConstraints();
        input.gridx = 1;
        input.weightx = 1;
        input.fill = GridBagConstraints.HORIZONTAL;
        input.insets = new Insets(0, 0, 6, 0);
        fields.add(first, input);
        input.gridy = 1;
        input.insets = new Insets(0, 0, 0, 0);
        fields.add(second, input);
        GridBagConstraints message = new GridBagConstraints();
        message.gridx = 0;
        message.gridy = 2;
        message.gridwidth = 2;
        message.anchor = GridBagConstraints.WEST;
        message.insets = new Insets(8, 0, 0, 0);
        fields.add(feedback, message);
        return fields;
    }

    private static void validatePassword(
            JDialog dialog, JPasswordField first, JPasswordField second, JLabel feedback, char[][] accepted) {
        char[] password = first.getPassword();
        char[] confirmation = second.getPassword();
        try {
            JPasswordField correction;
            if (password.length == 0) {
                feedback.setText("Enter a master password.");
                correction = first;
            } else if (confirmation.length == 0) {
                feedback.setText("Confirm the master password.");
                correction = second;
            } else if (!Arrays.equals(password, confirmation)) {
                feedback.setText("Passwords do not match. Try again.");
                correction = second;
            } else {
                accepted[0] = password;
                dialog.dispose();
                return;
            }
            feedback.setVisible(true);
            dialog.pack();
            correction.requestFocusInWindow();
            if (correction == second && confirmation.length > 0) {
                second.selectAll();
            }
        } finally {
            Arrays.fill(confirmation, '\0');
            if (accepted[0] != password) {
                Arrays.fill(password, '\0');
            }
        }
    }

    private static void showPasswordDialog(
            Frame owner,
            JDialog dialog,
            JPasswordField first,
            JPasswordField second,
            JPanel fields,
            JButton create,
            JButton cancel) {
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));
        content.add(fields, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(create);
        buttons.add(cancel);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(create);
        dialog.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy() {
            @Override
            public Component getDefaultComponent(Container container) {
                return first;
            }
        });
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        try {
            dialog.setVisible(true);
        } finally {
            first.setText("");
            second.setText("");
            dialog.dispose();
        }
    }
}
