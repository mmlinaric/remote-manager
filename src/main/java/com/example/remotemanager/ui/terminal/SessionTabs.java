package com.example.remotemanager.ui.terminal;

import com.example.remotemanager.model.AuthenticationType;
import com.example.remotemanager.model.Connection;
import com.example.remotemanager.ssh.KnownHostsVerifier;
import com.example.remotemanager.ssh.SshRemoteSession;
import com.example.remotemanager.ui.settings.AppSettings;
import com.example.remotemanager.util.SecureClipboard;
import com.example.remotemanager.vault.Vault;
import java.awt.Component;
import java.awt.Toolkit;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

public final class SessionTabs extends JTabbedPane {
  private final Map<Component, OpenTab> openTabs = new HashMap<>();
  private final Supplier<Vault> vaultSupplier;
  private final Supplier<CompletableFuture<Boolean>> unlockVault;
  private final Consumer<String> status;
  private final Supplier<AppSettings> settings;
  private final SecureClipboard clipboard;
  private final java.util.concurrent.ExecutorService worker = Executors.newCachedThreadPool();
  private final java.util.concurrent.ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor();

  public SessionTabs(
      Supplier<Vault> vaultSupplier,
      Supplier<CompletableFuture<Boolean>> unlockVault,
      Consumer<String> status,
      Supplier<AppSettings> settings) {
    this.vaultSupplier = vaultSupplier;
    this.unlockVault = unlockVault;
    this.status = status;
    this.settings = settings;
    clipboard = new SecureClipboard(Toolkit.getDefaultToolkit().getSystemClipboard(), scheduler);
  }

  public void open(Connection connection) {
    boolean requiresVault =
        connection.authenticationType() == AuthenticationType.KDBX_PRIVATE_KEY
            || connection.authenticationType() == AuthenticationType.PASSWORD;
    CompletableFuture<Boolean> ready =
        requiresVault ? unlockVault.get() : CompletableFuture.completedFuture(true);
    ready.thenAccept(
        unlocked -> {
          if (unlocked) {
            SwingUtilities.invokeLater(() -> startTab(connection));
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
    if (tab == null) {
      return;
    }
    openTabs.remove(tab.session().component());
    remove(tab.session().component());
    worker.execute(tab.session()::disconnect);
  }

  public void copySudoPassword() {
    OpenTab tab = selectedTab();
    if (tab == null || tab.connection().sudoCredentialEntryId() == null) {
      status.accept("No sudo credential is assigned to this tab");
      return;
    }
    unlockVault
        .get()
        .thenAccept(
            unlocked -> {
              if (!unlocked) {
                return;
              }
              Duration timeout = Duration.ofSeconds(settings.get().clipboardSeconds());
              worker.execute(() -> copySudo(tab.connection(), timeout));
            });
  }

  private void copySudo(Connection connection, Duration timeout) {
    try {
      char[] password =
          vaultSupplier
              .get()
              .getPassword(connection.sudoCredentialEntryId())
              .orElseThrow(() -> new IllegalStateException("Sudo password is missing in KeePass"));
      try {
        SwingUtilities.invokeAndWait(
            () -> {
              clipboard.copy(password, timeout);
              status.accept("Sudo password copied to clipboard");
            });
      } finally {
        java.util.Arrays.fill(password, '\0');
      }
    } catch (Exception error) {
      SwingUtilities.invokeLater(() -> showError("Could not copy sudo password", error));
    }
  }

  private void startTab(Connection connection) {
    AppSettings preferences = settings.get();
    SshRemoteSession session =
        new SshRemoteSession(
            connection,
            vaultSupplier.get(),
            preferences.knownHosts(),
            hostPrompt(),
            this::askKeyPassphrase,
            preferences.terminalFont(),
            preferences.terminalFontSize(),
            preferences.scrollbackLines());
    OpenTab tab = new OpenTab(connection, session);
    JComponent component = session.component();
    openTabs.put(component, tab);
    addTab(connection.name() + " (connecting)", component);
    setSelectedComponent(component);
    worker.execute(
        () -> {
          try {
            session.connect();
            SwingUtilities.invokeLater(
                () -> {
                  updateTitle(tab, "connected");
                  status.accept("Connected to " + connection.hostname());
                });
          } catch (Exception error) {
            SwingUtilities.invokeLater(
                () -> {
                  updateTitle(tab, "failed");
                  showError("SSH connection failed", error);
                });
          }
        });
  }

  private KnownHostsVerifier.Prompt hostPrompt() {
    return new KnownHostsVerifier.Prompt() {
      @Override
      public boolean trustUnknown(
          String host, String address, String algorithm, String fingerprint) {
        return onEdt(
            () ->
                JOptionPane.showConfirmDialog(
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
        onEdt(
            () -> {
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
    return onEdt(
        () -> {
          JPasswordField field = new JPasswordField(24);
          int answer =
              JOptionPane.showConfirmDialog(
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
      java.util.concurrent.atomic.AtomicReference<T> value =
          new java.util.concurrent.atomic.AtomicReference<>();
      SwingUtilities.invokeAndWait(
          () -> {
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
      setTitleAt(index, tab.connection().name() + " (" + state + ")");
    }
  }

  private void showError(String message, Exception error) {
    JOptionPane.showMessageDialog(
        this, message + ": " + error.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
  }

  public void shutdown() {
    for (OpenTab tab : openTabs.values()) {
      tab.session().disconnect();
    }
    openTabs.clear();
    worker.shutdownNow();
    scheduler.shutdownNow();
  }

  private record OpenTab(Connection connection, SshRemoteSession session) {}
}
