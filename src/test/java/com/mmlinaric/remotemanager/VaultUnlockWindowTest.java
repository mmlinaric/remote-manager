package com.mmlinaric.remotemanager;

import com.mmlinaric.remotemanager.persistence.SettingsRepository;
import com.mmlinaric.remotemanager.ui.main.MainWindow;
import com.mmlinaric.remotemanager.vault.kdbx.KdbxVault;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultUnlockWindowTest {
    @TempDir
    Path temp;

    @Test
    void unlocksSelectedVaultAsynchronously() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        char[] password = "test-password".toCharArray();
        MainWindow window = openLockedWindow(password);
        try {
            unlock(window, password);

            waitForStatus(window, "Vault unlocked:");
        } finally {
            close(window);
        }
    }

    @Test
    void allowsRetryAfterIncorrectPassword() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        char[] password = "test-password".toCharArray();
        MainWindow window = openLockedWindow(password);
        try {
            openUnlockDialog(window);
            submit(unlockDialog(), "incorrect".toCharArray());
            waitForDialogText("Incorrect password. Try again.");

            submit(unlockDialog(), password);
            waitForStatus(window, "Vault unlocked:");
        } finally {
            close(window);
        }
    }

    private MainWindow openLockedWindow(char[] password) throws Exception {
        Path vaultPath = temp.resolve("vault.kdbx");
        KdbxVault.create(vaultPath, password);
        SettingsRepository settings = new SettingsRepository(temp.resolve("settings.properties"));
        settings.put("vault.path", vaultPath.toString());
        AtomicReference<MainWindow> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                MainWindow window = new MainWindow(settings);
                window.setExtendedState(Frame.NORMAL);
                window.setSize(800, 650);
                window.setLocation(-3000, -3000);
                window.setVisible(true);
                result.set(window);
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
        return result.get();
    }

    private static void unlock(MainWindow window, char[] password) throws Exception {
        openUnlockDialog(window);
        submit(unlockDialog(), password);
    }

    private static void openUnlockDialog(MainWindow window) throws Exception {
        SwingUtilities.invokeLater(() -> menuItem(window, "Unlock vault").doClick());
        for (int attempt = 0; attempt < 200; attempt++) {
            if (unlockDialogOrNull() != null) return;
            Thread.sleep(25);
        }
        throw new AssertionError("Unlock dialog did not open");
    }

    private static void submit(JDialog dialog, char[] password) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            passwordField(dialog).setText(new String(password));
            dialog.getRootPane().getDefaultButton().doClick();
        });
    }

    private static void waitForStatus(MainWindow window, String prefix) throws Exception {
        for (int attempt = 0; attempt < 400; attempt++) {
            AtomicReference<String> status = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> status.set(statusLabel(window).getText()));
            if (status.get().startsWith(prefix)) return;
            Thread.sleep(25);
        }
        throw new AssertionError("Window did not report: " + prefix);
    }

    private static void waitForDialogText(String expected) throws Exception {
        for (int attempt = 0; attempt < 400; attempt++) {
            JDialog dialog = unlockDialogOrNull();
            if (dialog != null && containsText(dialog, expected)) return;
            Thread.sleep(25);
        }
        throw new AssertionError("Unlock dialog did not report: " + expected);
    }

    private static JDialog unlockDialog() {
        JDialog dialog = unlockDialogOrNull();
        if (dialog == null) throw new AssertionError("Unlock dialog is not visible");
        return dialog;
    }

    private static JDialog unlockDialogOrNull() {
        for (Window window : Window.getWindows()) {
            if (window instanceof JDialog dialog && dialog.isShowing() && "Unlock vault".equals(dialog.getTitle())) {
                return dialog;
            }
        }
        return null;
    }

    private static JMenuItem menuItem(MainWindow window, String label) {
        for (int menu = 0; menu < window.getJMenuBar().getMenuCount(); menu++) {
            for (Component item : window.getJMenuBar().getMenu(menu).getMenuComponents()) {
                if (item instanceof JMenuItem menuItem && label.equals(menuItem.getText())) return menuItem;
            }
        }
        throw new AssertionError("Menu item is missing: " + label);
    }

    private static JPasswordField passwordField(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JPasswordField field) return field;
            if (child instanceof Container nested) {
                try {
                    return passwordField(nested);
                } catch (AssertionError ignored) {
                }
            }
        }
        throw new AssertionError("Password field is missing");
    }

    private static boolean containsText(Container container, String text) {
        for (Component child : container.getComponents()) {
            if (child instanceof javax.swing.JLabel label && text.equals(label.getText())) return true;
            if (child instanceof Container nested && containsText(nested, text)) return true;
        }
        return false;
    }

    private static javax.swing.JLabel statusLabel(MainWindow window) {
        try {
            Field field = MainWindow.class.getDeclaredField("status");
            field.setAccessible(true);
            return (javax.swing.JLabel) field.get(window);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static void close(MainWindow window) throws Exception {
        SwingUtilities.invokeAndWait(() -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
    }
}
