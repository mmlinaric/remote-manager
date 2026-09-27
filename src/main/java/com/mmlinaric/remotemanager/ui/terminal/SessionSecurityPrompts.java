package com.mmlinaric.remotemanager.ui.terminal;

import com.mmlinaric.remotemanager.ssh.KnownHostsVerifier;
import java.awt.Component;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;

/** Runs interactive SSH trust and key-passphrase prompts on the Swing event thread. */
final class SessionSecurityPrompts {
    private final Component dialogOwner;

    SessionSecurityPrompts(Component dialogOwner) {
        this.dialogOwner = dialogOwner;
    }

    KnownHostsVerifier.Prompt hostPrompt() {
        return new KnownHostsVerifier.Prompt() {
            @Override
            public boolean trustUnknown(String host, String address, String algorithm, String fingerprint) {
                return onEdt(() -> JOptionPane.showConfirmDialog(
                                dialogOwner,
                                "Unknown SSH host: "
                                        + host
                                        + "\nIP: "
                                        + address
                                        + "\nAlgorithm: "
                                        + algorithm
                                        + "\nFingerprint: "
                                        + fingerprint,
                                "Trust and connect",
                                JOptionPane.OK_CANCEL_OPTION,
                                JOptionPane.WARNING_MESSAGE)
                        == JOptionPane.OK_OPTION);
            }

            @Override
            public void warnChanged(String host, String oldFingerprint, String newFingerprint) {
                onEdt(() -> {
                    JOptionPane.showMessageDialog(
                            dialogOwner,
                            "SSH host key changed for "
                                    + host
                                    + "\nKnown: "
                                    + oldFingerprint
                                    + "\nPresented: "
                                    + newFingerprint,
                            "Host key mismatch",
                            JOptionPane.ERROR_MESSAGE);
                    return null;
                });
            }
        };
    }

    char[] askKeyPassphrase(String keyName) {
        return onEdt(() -> {
            JPasswordField field = new JPasswordField(24);
            int answer = JOptionPane.showConfirmDialog(
                    dialogOwner,
                    field,
                    "Passphrase for " + keyName,
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            char[] password = answer == JOptionPane.OK_OPTION ? field.getPassword() : null;
            field.setText("");
            return password;
        });
    }

    private <T> T onEdt(Callable<T> action) {
        try {
            if (SwingUtilities.isEventDispatchThread()) return action.call();
            AtomicReference<T> value = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    value.set(action.call());
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
            return value.get();
        } catch (Exception error) {
            throw new IllegalStateException("Could not show SSH dialog", error);
        }
    }
}
