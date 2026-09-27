package com.mmlinaric.remotemanager.ui.main;

import com.mmlinaric.remotemanager.ui.DialogEscape;
import com.mmlinaric.remotemanager.ui.SilkIcons;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.LayoutFocusTraversalPolicy;
import javax.swing.UIManager;

/** Collects a vault password and presents the state of one asynchronous unlock attempt. */
final class VaultUnlockDialog extends JDialog {
    private final JPasswordField password = new JPasswordField(24);
    private final JButton unlock = new JButton("Unlock", SilkIcons.UNLOCK);
    private final JButton cancel = new JButton("Cancel", SilkIcons.CLOSE);
    private final JLabel feedback = new JLabel();
    private Consumer<char[]> unlockHandler;

    VaultUnlockDialog(Window owner) {
        super(owner, "Unlock vault", Dialog.ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        feedback.setVisible(false);
        unlock.addActionListener(event -> submit());
        cancel.addActionListener(event -> dispose());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (cancel.isEnabled()) cancel.doClick();
            }
        });
        DialogEscape.bind(this, cancel);
        password.addActionListener(event -> unlock.doClick());
        setContentPane(content());
        getRootPane().setDefaultButton(unlock);
        setFocusTraversalPolicy(new LayoutFocusTraversalPolicy() {
            @Override
            public Component getDefaultComponent(Container container) {
                return password;
            }
        });
        pack();
        setLocationRelativeTo(owner);
    }

    void setUnlockHandler(Consumer<char[]> handler) {
        unlockHandler = handler;
    }

    void showFailure(String message) {
        feedback.setForeground(new Color(0xB0, 0x20, 0x20));
        feedback.setText(message);
        feedback.setVisible(true);
        password.setEnabled(true);
        unlock.setEnabled(true);
        cancel.setEnabled(true);
        pack();
        password.requestFocusInWindow();
    }

    @Override
    public void dispose() {
        password.setText("");
        super.dispose();
    }

    private JPanel content() {
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));
        JPanel passwordRow = new JPanel(new BorderLayout(0, 6));
        passwordRow.add(new JLabel("Vault password:"), BorderLayout.NORTH);
        passwordRow.add(password, BorderLayout.CENTER);
        passwordRow.add(feedback, BorderLayout.SOUTH);
        content.add(passwordRow, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(unlock);
        buttons.add(cancel);
        content.add(buttons, BorderLayout.SOUTH);
        return content;
    }

    private void submit() {
        char[] value = password.getPassword();
        if (value.length == 0) {
            Arrays.fill(value, '\0');
            showFailure("Enter your vault password.");
            return;
        }
        password.setText("");
        password.setEnabled(false);
        unlock.setEnabled(false);
        cancel.setEnabled(false);
        feedback.setForeground(UIManager.getColor("Label.foreground"));
        feedback.setText("Unlocking vault...");
        feedback.setVisible(true);
        pack();
        unlockHandler.accept(value);
    }
}
