package com.example.remotemanager.ui.main;

import com.example.remotemanager.model.Connection;
import com.example.remotemanager.model.ConnectionFolder;
import com.example.remotemanager.persistence.ConnectionRepository;
import com.example.remotemanager.persistence.SettingsRepository;
import com.example.remotemanager.ui.connections.ConnectionEditor;
import com.example.remotemanager.ui.connections.ConnectionTreePanel;
import com.example.remotemanager.ui.settings.AppSettings;
import com.example.remotemanager.ui.settings.SettingsDialog;
import com.example.remotemanager.ui.terminal.SessionTabs;
import com.example.remotemanager.ui.vault.VaultBrowserDialog;
import com.example.remotemanager.vault.VaultEntry;
import com.example.remotemanager.vault.kdbx.KdbxVault;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public final class MainWindow extends JFrame {
  private final ConnectionRepository connections;
  private final SettingsRepository settings;
  private final JLabel status = new JLabel("Ready");
  private final JTextField quickConnect = new JTextField(24);
  private AppSettings preferences = AppSettings.defaults();
  private volatile long lastVaultUse = System.nanoTime();
  private KdbxVault vault;
  private final SessionTabs tabs =
      new SessionTabs(() -> vault, this::unlockVaultAsync, this::showStatus, () -> preferences);
  private final ConnectionTreePanel connectionTree = new ConnectionTreePanel(tabs::open);
  private final JSplitPane split = createContent();
  private final Timer autoLockTimer = new Timer(15000, event -> autoLockVault());

  public MainWindow(ConnectionRepository connections, SettingsRepository settings) {
    super("Remote Manager");
    this.connections = connections;
    this.settings = settings;

    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    setMinimumSize(new Dimension(760, 480));
    setSize(1100, 700);
    setJMenuBar(createMenu());
    add(createToolbar(), BorderLayout.NORTH);
    add(split, BorderLayout.CENTER);
    add(status, BorderLayout.SOUTH);

    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent event) {
            closeWindow();
          }
        });

    autoLockTimer.start();
    loadSettings();
  }

  @Override
  public void dispose() {
    autoLockTimer.stop();
    tabs.shutdown();
    if (vault != null) {
      vault.lock();
    }
    super.dispose();
  }

  private JMenuBar createMenu() {
    JMenuBar menuBar = new JMenuBar();
    JMenu file = new JMenu("File");
    file.add(item("New folder", this::newFolder));
    file.add(item("New connection", () -> editConnection(null)));
    file.add(item("Edit selected", this::editSelected));
    file.add(item("Delete selected", this::deleteSelected));
    file.addSeparator();
    file.add(item("Open vault", this::openVault));
    file.add(item("Create vault", this::createVault));
    file.add(item("Unlock vault", () -> unlockVaultAsync()));
    file.add(item("Lock vault", this::lockVault));
    file.add(item("Reload vault", this::reloadVault));
    file.add(item("Browse vault entries", this::browseVault));
    JMenu session = new JMenu("Session");
    session.add(item("Disconnect", tabs::disconnectSelected));
    session.add(item("Reconnect", tabs::reconnectSelected));
    session.add(item("Close tab", tabs::closeSelected));
    session.add(item("Copy sudo password", tabs::copySudoPassword));
    file.addSeparator();
    file.add(item("Exit", this::closeWindow));
    menuBar.add(file);
    menuBar.add(new JMenu("View"));
    JMenu tools = new JMenu("Tools");
    tools.add(item("Settings", this::editSettings));
    menuBar.add(tools);
    menuBar.add(session);
    menuBar.add(new JMenu("Help"));
    return menuBar;
  }

  private JToolBar createToolbar() {
    JToolBar toolbar = new JToolBar();
    toolbar.setFloatable(false);
    toolbar.add(new JLabel("Connect: "));
    toolbar.add(quickConnect);
    JButton connect = new JButton("Connect");
    connect.addActionListener(event -> connectFromToolbar());
    toolbar.add(connect);
    return toolbar;
  }

  private JSplitPane createContent() {
    JSplitPane left =
        new JSplitPane(JSplitPane.VERTICAL_SPLIT, connectionTree, connectionTree.details());
    left.setResizeWeight(1.0);
    left.setDividerLocation(420);
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, tabs);
    split.setDividerLocation(300);
    return split;
  }

  private void editSettings() {
    AppSettings edited = SettingsDialog.edit(this, preferences);
    if (edited == null) {
      return;
    }
    preferences = edited;
    runDatabaseAction(() -> edited.save(settings));
  }

  private void autoLockVault() {
    int minutes = preferences.vaultAutoLockMinutes();
    if (vault == null || !vault.isUnlocked() || minutes == 0) {
      return;
    }
    long elapsed = System.nanoTime() - lastVaultUse;
    if (elapsed >= java.util.concurrent.TimeUnit.MINUTES.toNanos(minutes)) {
      lockVault();
    }
  }

  private void closeWindow() {
    int x = getX();
    int y = getY();
    int width = getWidth();
    int height = getHeight();
    int divider = split.getDividerLocation();
    String expanded = connectionTree.expandedIds();
    String selected = connectionTree.selectedId();
    CompletableFuture.runAsync(
            () -> {
              try {
                settings.put("window.x", Integer.toString(x));
                settings.put("window.y", Integer.toString(y));
                settings.put("window.width", Integer.toString(width));
                settings.put("window.height", Integer.toString(height));
                settings.put("window.divider", Integer.toString(divider));
                settings.put("tree.expanded", expanded);
                settings.put("tree.selected", selected);
              } catch (SQLException error) {
                throw new RuntimeException(error);
              }
            })
        .whenComplete(
            (ignored, error) ->
                SwingUtilities.invokeLater(
                    () -> {
                      if (error != null) {
                        showError("Could not save window settings", error);
                      }
                      dispose();
                    }));
  }

  private void refreshTree() {
    CompletableFuture.supplyAsync(
            () -> {
              try {
                return new TreeData(connections.folders(), connections.connections());
              } catch (SQLException error) {
                throw new RuntimeException(error);
              }
            })
        .whenComplete(
            (data, error) ->
                SwingUtilities.invokeLater(
                    () -> {
                      if (error != null) {
                        showError("Could not load connections", error);
                      } else {
                        connectionTree.showConnections(data.folders(), data.connections());
                      }
                    }));
  }

  private void editSelected() {
    if (connectionTree.selectedValue() instanceof Connection connection) {
      editConnection(connection);
    }
  }

  private void connectFromToolbar() {
    String target = quickConnect.getText().trim();
    connectionTree.connections().stream()
        .filter(
            connection ->
                connection.name().equalsIgnoreCase(target)
                    || connection.hostname().equalsIgnoreCase(target))
        .findFirst()
        .ifPresentOrElse(tabs::open, () -> showStatus("No saved connection matches " + target));
  }

  private void editConnection(Connection current) {
    if (vault != null && !vault.isUnlocked()) {
      unlockVaultAsync()
          .thenAccept(
              unlocked -> {
                if (unlocked) {
                  SwingUtilities.invokeLater(() -> showConnectionEditor(current));
                }
              });
      return;
    }
    showConnectionEditor(current);
  }

  private void showConnectionEditor(Connection current) {
    UUID parent =
        connectionTree.selectedValue() instanceof ConnectionFolder folder ? folder.id() : null;
    List<VaultEntry> entries = vault != null && vault.isUnlocked() ? vaultEntries() : List.of();
    ConnectionEditor editor =
        new ConnectionEditor(this, current, parent, connectionTree.folders(), entries);
    editor.setVisible(true);
    if (editor.result() != null) {
      runDatabaseAction(() -> connections.save(editor.result()));
    }
  }

  private List<VaultEntry> vaultEntries() {
    try {
      return vault.entries();
    } catch (Exception error) {
      showError("Could not read vault entries", error);
      return List.of();
    }
  }

  private void newFolder() {
    String name = JOptionPane.showInputDialog(this, "Folder name:");
    if (name == null || name.isBlank()) {
      return;
    }
    UUID parent =
        connectionTree.selectedValue() instanceof ConnectionFolder folder ? folder.id() : null;
    runDatabaseAction(
        () -> connections.save(new ConnectionFolder(UUID.randomUUID(), parent, name.trim(), 0)));
  }

  private void deleteSelected() {
    Object selected = connectionTree.selectedValue();
    if (selected == null
        || JOptionPane.showConfirmDialog(
                this, "Delete " + selected + "?", "Confirm deletion", JOptionPane.YES_NO_OPTION)
            != JOptionPane.YES_OPTION) {
      return;
    }
    if (selected instanceof Connection connection) {
      runDatabaseAction(() -> connections.deleteConnection(connection.id()));
    } else if (selected instanceof ConnectionFolder folder) {
      runDatabaseAction(() -> connections.deleteFolder(folder.id()));
    }
  }

  private void openVault() {
    JFileChooser chooser = new JFileChooser();
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    lockVault();
    vault = new KdbxVault(chooser.getSelectedFile().toPath());
    runDatabaseAction(() -> settings.put("vault.path", vault.path().toString()));
    showStatus("Vault selected: " + vault.path());
  }

  private void createVault() {
    JFileChooser chooser = new JFileChooser();
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    JPasswordField first = new JPasswordField(24);
    JPasswordField second = new JPasswordField(24);
    JPanel fields = new JPanel(new java.awt.GridLayout(2, 2, 4, 4));
    fields.add(new JLabel("Master password:"));
    fields.add(first);
    fields.add(new JLabel("Confirm password:"));
    fields.add(second);
    if (JOptionPane.showConfirmDialog(
            this, fields, "Create KeePass vault", JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) {
      return;
    }
    char[] password = first.getPassword();
    char[] confirmation = second.getPassword();
    first.setText("");
    second.setText("");
    if (password.length == 0 || !Arrays.equals(password, confirmation)) {
      Arrays.fill(password, '\0');
      Arrays.fill(confirmation, '\0');
      showStatus("Vault passwords did not match");
      return;
    }
    Arrays.fill(confirmation, '\0');
    Path file = chooser.getSelectedFile().toPath();
    CompletableFuture.runAsync(
            () -> {
              try {
                KdbxVault.create(file, password);
              } catch (Exception error) {
                throw new RuntimeException(error);
              } finally {
                Arrays.fill(password, '\0');
              }
            })
        .whenComplete(
            (ignored, error) ->
                SwingUtilities.invokeLater(
                    () -> {
                      if (error == null) {
                        lockVault();
                        vault = new KdbxVault(file);
                        runDatabaseAction(
                            () -> settings.put("vault.path", file.toAbsolutePath().toString()));
                        showStatus("Vault created: " + file);
                      } else {
                        showError("Could not create vault", error);
                      }
                    }));
  }

  private void browseVault() {
    if (vault == null) {
      showStatus("Select a KeePass vault first");
      return;
    }
    unlockVaultAsync()
        .thenAccept(
            unlocked -> {
              if (unlocked) {
                SwingUtilities.invokeLater(
                    () -> new VaultBrowserDialog(this, vault).setVisible(true));
              }
            });
  }

  private void reloadVault() {
    if (vault == null) {
      return;
    }
    vault.lock();
    unlockVaultAsync();
  }

  private void loadSettings() {
    CompletableFuture.supplyAsync(
            () -> {
              try {
                return new LoadedSettings(
                    settings.get("vault.path").orElse(null),
                    AppSettings.load(settings),
                    intSetting("window.x", -1),
                    intSetting("window.y", -1),
                    intSetting("window.width", 1100),
                    intSetting("window.height", 700),
                    intSetting("window.divider", 300),
                    settings.get("tree.expanded").orElse(""),
                    settings.get("tree.selected").orElse(""));
              } catch (SQLException error) {
                throw new RuntimeException(error);
              }
            })
        .whenComplete(
            (loaded, error) ->
                SwingUtilities.invokeLater(
                    () -> {
                      if (error == null) {
                        preferences = loaded.preferences();
                        if (loaded.vaultPath() != null) {
                          vault = new KdbxVault(Path.of(loaded.vaultPath()));
                        }
                        setSize(loaded.width(), loaded.height());
                        if (loaded.x() >= 0 && loaded.y() >= 0) {
                          setLocation(loaded.x(), loaded.y());
                        }
                        split.setDividerLocation(loaded.divider());
                        connectionTree.restoreOnNextLoad(
                            loaded.expandedFolders(), loaded.selection());
                      } else if (error != null) {
                        showError("Could not load settings", error);
                      }
                      refreshTree();
                    }));
  }

  private int intSetting(String key, int fallback) throws SQLException {
    try {
      return settings.get(key).map(Integer::parseInt).orElse(fallback);
    } catch (NumberFormatException invalid) {
      return fallback;
    }
  }

  private CompletableFuture<Boolean> unlockVaultAsync() {
    if (vault == null) {
      showStatus("Select a KeePass vault first");
      return CompletableFuture.completedFuture(false);
    }
    if (vault.isUnlocked()) {
      lastVaultUse = System.nanoTime();
      return CompletableFuture.completedFuture(true);
    }
    JPasswordField field = new JPasswordField(24);
    if (JOptionPane.showConfirmDialog(
            this, field, "Vault master password", JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) {
      return CompletableFuture.completedFuture(false);
    }
    char[] password = field.getPassword();
    field.setText("");
    KdbxVault selectedVault = vault;
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            selectedVault.unlock(password);
            lastVaultUse = System.nanoTime();
            return true;
          } catch (Exception error) {
            SwingUtilities.invokeLater(() -> showError("Could not unlock vault", error));
            return false;
          } finally {
            java.util.Arrays.fill(password, '\0');
          }
        });
  }

  private void lockVault() {
    if (vault != null) {
      vault.lock();
      showStatus("Vault locked");
    }
  }

  private void runDatabaseAction(DatabaseAction action) {
    CompletableFuture.runAsync(
            () -> {
              try {
                action.run();
              } catch (SQLException error) {
                throw new RuntimeException(error);
              }
            })
        .whenComplete(
            (ignored, error) ->
                SwingUtilities.invokeLater(
                    () -> {
                      if (error == null) {
                        refreshTree();
                      } else {
                        showError("Could not save change", error);
                      }
                    }));
  }

  private void showStatus(String text) {
    status.setText(text);
  }

  private void showError(String message, Throwable error) {
    Throwable cause = error.getCause() == null ? error : error.getCause();
    JOptionPane.showMessageDialog(
        this, message + ": " + cause.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
  }

  private static JMenuItem item(String title, Runnable action) {
    JMenuItem item = new JMenuItem(title);
    item.addActionListener(event -> action.run());
    return item;
  }

  private record TreeData(List<ConnectionFolder> folders, List<Connection> connections) {}

  private record LoadedSettings(
      String vaultPath,
      AppSettings preferences,
      int x,
      int y,
      int width,
      int height,
      int divider,
      String expandedFolders,
      String selection) {}

  @FunctionalInterface
  private interface DatabaseAction {
    void run() throws SQLException;
  }
}
