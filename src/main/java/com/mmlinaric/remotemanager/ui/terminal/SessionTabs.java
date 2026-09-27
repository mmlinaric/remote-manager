package com.mmlinaric.remotemanager.ui.terminal;

import com.mmlinaric.remotemanager.model.AuthenticationType;
import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.ssh.KnownHostsVerifier;
import com.mmlinaric.remotemanager.ssh.SshRemoteSession;
import com.mmlinaric.remotemanager.ui.SilkIcons;
import com.mmlinaric.remotemanager.ui.settings.AppSettings;
import com.mmlinaric.remotemanager.util.SecureClipboard;
import com.mmlinaric.remotemanager.vault.Vault;
import java.awt.Component;
import java.awt.Toolkit;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

public final class SessionTabs extends JTabbedPane {
    public static final int MIN_FONT_SIZE = 6;
    public static final int MAX_FONT_SIZE = 48;
    private final Map<Component, OpenTab> openTabs = new HashMap<>();
    private final AtomicLong operationVersion = new AtomicLong();
    private final Supplier<Vault> vaultSupplier;
    private final Supplier<CompletableFuture<Boolean>> unlockVault;
    private final Consumer<String> status;
    private final Supplier<AppSettings> settings;
    private final Function<UUID, Connection> currentConnection;
    private final SecureClipboard clipboard;
    private final java.util.concurrent.ExecutorService worker = Executors.newCachedThreadPool();
    private final java.util.concurrent.ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    public SessionTabs(
            Supplier<Vault> vaultSupplier,
            Supplier<CompletableFuture<Boolean>> unlockVault,
            Consumer<String> status,
            Supplier<AppSettings> settings,
            Function<UUID, Connection> currentConnection) {
        this.vaultSupplier = vaultSupplier;
        this.unlockVault = unlockVault;
        this.status = status;
        this.settings = settings;
        this.currentConnection = currentConnection;
        clipboard = new SecureClipboard(Toolkit.getDefaultToolkit().getSystemClipboard(), scheduler);
    }

    public void open(Connection connection) {
        long version = operationVersion.get();
        boolean requiresVault = connection.authenticationType() == AuthenticationType.KDBX_PRIVATE_KEY
                || connection.authenticationType() == AuthenticationType.PASSWORD;
        CompletableFuture<Boolean> ready = requiresVault ? unlockVault.get() : CompletableFuture.completedFuture(true);
        ready.thenAccept(unlocked -> {
            if (unlocked && version == operationVersion.get()) {
                SwingUtilities.invokeLater(() -> {
                    if (version == operationVersion.get()) startTab(connection, version);
                });
            }
        });
    }

    public void disconnectSelected() {
        OpenTab tab = selectedTab();
        if (tab == null) {
            return;
        }
        worker.execute(tab.session()::disconnect);
        updateTitle(tab, "disconnected");
    }

    public void reconnectSelected() {
        OpenTab tab = selectedTab();
        if (tab == null) {
            return;
        }
        Connection connection = tab.connection();
        closeSelected();
        open(connection);
    }

    public void closeSelected() {
        OpenTab tab = selectedTab();
        if (tab != null) closeTab(tab);
    }

    private void closeTab(OpenTab tab) {
        if (tab.closed().getAndSet(true)) return;
        openTabs.remove(tab.session().component());
        remove(tab.session().component());
        worker.execute(tab.session()::disconnect);
    }

    public void copySudoPassword() {
        OpenTab tab = selectedTab();
        if (tab == null) return;
        copySudoPassword(
                currentConnection.apply(tab.connection().id()),
                () -> !tab.closed().get());
    }

    public Connection selectedConnection() {
        OpenTab tab = selectedTab();
        return tab == null ? null : currentConnection.apply(tab.connection().id());
    }

    public void changeFontSize(int change) {
        OpenTab tab = selectedTab();
        if (tab == null) return;
        int size = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, tab.session().fontSize() + change));
        tab.session().setTerminalFont(settings.get().terminalFont(), size);
        status.accept("Terminal font size: " + size);
    }

    public void resetFontSize() {
        OpenTab tab = selectedTab();
        if (tab == null) return;
        int size = settings.get().terminalFontSize();
        tab.session().setTerminalFont(settings.get().terminalFont(), size);
        status.accept("Terminal font size: " + size);
    }

    public void applyFontSettings(AppSettings preferences) {
        for (OpenTab tab : openTabs.values()) {
            tab.session().setTerminalFont(preferences.terminalFont(), preferences.terminalFontSize());
        }
    }

    public void copySudoPassword(Connection connection) {
        copySudoPassword(connection, () -> true);
    }

    private void copySudoPassword(Connection connection, BooleanSupplier sourceIsOpen) {
        if (connection == null || connection.sudoCredentialEntryId() == null) {
            status.accept("No sudo password is assigned to this host");
            return;
        }
        long version = operationVersion.get();
        unlockVault.get().thenAccept(unlocked -> {
            if (!unlocked || version != operationVersion.get() || !sourceIsOpen.getAsBoolean()) return;
            Duration timeout = Duration.ofSeconds(settings.get().clipboardSeconds());
            worker.execute(() -> copySudo(connection, timeout, version, sourceIsOpen));
        });
    }

    private void copySudo(Connection connection, Duration timeout, long version, BooleanSupplier sourceIsOpen) {
        try {
            char[] password = vaultSupplier
                    .get()
                    .getPassword(connection.sudoCredentialEntryId())
                    .orElseThrow(() -> new IllegalStateException("Sudo password is missing in KeePass"));
            try {
                SwingUtilities.invokeLater(() -> {
                    try {
                        if (version == operationVersion.get()
                                && sourceIsOpen.getAsBoolean()
                                && vaultSupplier.get() != null
                                && vaultSupplier.get().isUnlocked()) {
                            clipboard.copy(password, timeout);
                            status.accept("Copied sudo password for " + connection.name() + "; clipboard clears in "
                                    + timeout.toSeconds() + " seconds");
                        }
                    } catch (Exception error) {
                        showError("Could not copy sudo password", error);
                    } finally {
                        java.util.Arrays.fill(password, '\0');
                    }
                });
            } catch (Exception error) {
                java.util.Arrays.fill(password, '\0');
                throw error;
            }
        } catch (Exception error) {
            SwingUtilities.invokeLater(() -> {
                if (version == operationVersion.get() && sourceIsOpen.getAsBoolean())
                    showError("Could not copy sudo password", error);
            });
        }
    }

    private void startTab(Connection connection, long version) {
        AppSettings preferences = settings.get();
        SshRemoteSession session = new SshRemoteSession(
                connection,
                vaultSupplier.get(),
                preferences.knownHosts(),
                hostPrompt(),
                this::askKeyPassphrase,
                preferences.terminalFont(),
                preferences.terminalFontSize(),
                preferences.scrollbackLines());
        OpenTab tab = new OpenTab(connection, session, new AtomicBoolean());
        JComponent component = session.component();
        openTabs.put(component, tab);
        addTab(connection.name() + " (connecting)", SilkIcons.CONNECTING, component);
        setTabComponentAt(
                indexOfComponent(component),
                new SessionTabHeader(
                        connection.name() + " (connecting)",
                        SilkIcons.CONNECTING,
                        () -> closeTab(tab),
                        connection.name()));
        setSelectedComponent(component);
        worker.execute(() -> {
            try {
                session.connect();
                if (version != operationVersion.get() || tab.closed().get()) {
                    session.disconnect();
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    if (version != operationVersion.get() || tab.closed().get()) return;
                    updateTitle(tab, "connected");
                    status.accept("Connected to " + connection.hostname());
                });
            } catch (Exception error) {
                SwingUtilities.invokeLater(() -> {
                    if (version != operationVersion.get() || tab.closed().get()) return;
                    updateTitle(tab, "failed");
                    showError("SSH connection failed", error);
                });
            }
        });
    }

    private KnownHostsVerifier.Prompt hostPrompt() {
        return new KnownHostsVerifier.Prompt() {
            @Override
            public boolean trustUnknown(String host, String address, String algorithm, String fingerprint) {
                return onEdt(() -> JOptionPane.showConfirmDialog(
                                SessionTabs.this,
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
                            SessionTabs.this,
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

    private char[] askKeyPassphrase(String keyName) {
        return onEdt(() -> {
            JPasswordField field = new JPasswordField(24);
            int answer = JOptionPane.showConfirmDialog(
                    this,
                    field,
                    "Passphrase for " + keyName,
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            char[] password = answer == JOptionPane.OK_OPTION ? field.getPassword() : null;
            field.setText("");
            return password;
        });
    }

    private <T> T onEdt(java.util.concurrent.Callable<T> action) {
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                return action.call();
            }
            java.util.concurrent.atomic.AtomicReference<T> value = new java.util.concurrent.atomic.AtomicReference<>();
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

    private OpenTab selectedTab() {
        return openTabs.get(getSelectedComponent());
    }

    private void updateTitle(OpenTab tab, String state) {
        int index = indexOfComponent(tab.session().component());
        if (index >= 0) {
            String title = tab.connection().name() + " (" + state + ")";
            setTitleAt(index, title);
            Icon icon =
                    switch (state) {
                        case "connected" -> SilkIcons.CONNECTED;
                        case "failed" -> SilkIcons.FAILED;
                        default -> SilkIcons.DISCONNECTED;
                    };
            setIconAt(index, icon);
            if (getTabComponentAt(index) instanceof SessionTabHeader header) header.update(title, icon);
        }
    }

    private void showError(String message, Exception error) {
        JOptionPane.showMessageDialog(this, message + ": " + error.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }

    public void shutdown() {
        closeAll();
        worker.shutdown();
        scheduler.shutdownNow();
    }

    public void closeAll() {
        operationVersion.incrementAndGet();
        for (OpenTab tab : openTabs.values()) {
            tab.closed().set(true);
            worker.execute(tab.session()::disconnect);
        }
        openTabs.clear();
        removeAll();
    }

    private record OpenTab(Connection connection, SshRemoteSession session, AtomicBoolean closed) {}
}
