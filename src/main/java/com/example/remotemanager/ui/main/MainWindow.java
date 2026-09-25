package com.example.remotemanager.ui.main;

import com.example.remotemanager.model.Connection;
import com.example.remotemanager.model.ConnectionFolder;
import com.example.remotemanager.persistence.ConnectionRepository;
import com.example.remotemanager.persistence.SettingsRepository;
import com.example.remotemanager.ui.SilkIcons;
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
import java.awt.FileDialog;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.Icon;
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
  private long vaultOperationVersion;
  private final SessionTabs tabs =
      new SessionTabs(() -> vault, this::unlockVaultAsync, this::showStatus, () -> preferences);
  private final ConnectionTreePanel connectionTree =
      new ConnectionTreePanel(
          new ConnectionTreePanel.Actions() {
            @Override
            public void open(Connection connection) {
              tabs.open(connection);
            }

            @Override
            public void edit(Connection connection) {
              editConnection(connection);
            }

            @Override
            public void newFolder(ConnectionFolder parent) {
              MainWindow.this.newFolder(parent);
            }

            @Override
            public void newConnection(ConnectionFolder parent) {
              editConnection(null, parent.id());
            }

            @Override
            public void rename(ConnectionFolder folder) {
              renameFolder(folder);
            }

            @Override
            public void deleteFolder(ConnectionFolder folder) {
              deleteItem(folder);
            }

            @Override
            public void deleteConnection(Connection connection) {
              deleteItem(connection);
            }
          });
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
    vaultOperationVersion++;
    autoLockTimer.stop();
    tabs.shutdown();
    if (vault != null) {
      vault.lock();
    }
    super.dispose();
  }

  private JMenuBar createMenu() {
    JMenuBar menuBar = new JMenuBar();
    JMenu file = menu("File");
    file.add(item("New folder", SilkIcons.NEW_FOLDER, this::newFolder));
    file.add(item("New connection", SilkIcons.NEW_CONNECTION, () -> editConnection(null)));
    file.add(item("Edit selected", SilkIcons.EDIT, this::editSelected));
    file.add(item("Delete selected", SilkIcons.DELETE, this::deleteSelected));
    file.addSeparator();
    JMenu vaultMenu = new JMenu("Vault");
    vaultMenu.setIcon(SilkIcons.VAULT);
    vaultMenu.add(item("Open...", SilkIcons.VAULT, this::openVault));
    vaultMenu.add(item("Create...", SilkIcons.VAULT, this::createVault));
    vaultMenu.addSeparator();
    vaultMenu.add(item("Unlock", SilkIcons.UNLOCK, () -> unlockVaultAsync()));
    vaultMenu.add(item("Lock", SilkIcons.LOCK, this::lockVault));
    vaultMenu.add(item("Reload", SilkIcons.RECONNECT, this::reloadVault));
    vaultMenu.addSeparator();
    vaultMenu.add(item("Browse entries...", SilkIcons.VAULT, this::browseVault));
    file.add(vaultMenu);
    JMenu session = menu("Session");
    session.add(item("Disconnect", SilkIcons.DISCONNECT, tabs::disconnectSelected));
    session.add(item("Reconnect", SilkIcons.RECONNECT, tabs::reconnectSelected));
    session.add(item("Close tab", SilkIcons.CLOSE, tabs::closeSelected));
    session.add(item("Copy sudo password", SilkIcons.COPY_PASSWORD, tabs::copySudoPassword));
    file.addSeparator();
    file.add(item("Exit", SilkIcons.EXIT, this::closeWindow));
    menuBar.add(file);
    JMenu view = menu("View");
    view.add(item("Expand all folders", null, connectionTree::expandAll));
    view.add(item("Collapse all folders", null, connectionTree::collapseAll));
    view.addSeparator();
    view.add(item("Refresh connections", SilkIcons.RECONNECT, this::refreshTree));
    menuBar.add(view);
    JMenu tools = menu("Tools");
    tools.add(item("Settings", SilkIcons.SETTINGS, this::editSettings));
    menuBar.add(tools);
    menuBar.add(session);
    JMenu help = menu("Help");
    help.add(item("About Remote Manager", null, this::showAbout));
    menuBar.add(help);
    return menuBar;
  }

  private void showAbout() {
    JOptionPane.showMessageDialog(
        this,
        "Remote Manager\nA compact SSH connection manager.\n\n"
            + "Interface icons: FamFamFam Silk by Mark James (CC BY 2.5).\n"
            + "See THIRD_PARTY_NOTICES.md for details.",
        "About Remote Manager",
        JOptionPane.INFORMATION_MESSAGE);
  }

  private JToolBar createToolbar() {
    JToolBar toolbar = new JToolBar();
    toolbar.setFloatable(false);
    toolbar.add(new JLabel("Connect: "));
    toolbar.add(quickConnect);
    JButton connect = new JButton("Connect", SilkIcons.CONNECT);
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
    refreshTree(null);
  }

  private void refreshTree(UUID revealId) {
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
                        if (revealId != null) {
                          connectionTree.reveal(revealId);
                        }
                      }
                    }));
  }

  private void editSelected() {
    Object selected = connectionTree.selectedValue();
    if (selected instanceof Connection connection) {
      editConnection(connection);
    } else if (selected instanceof ConnectionFolder folder) {
      renameFolder(folder);
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
    UUID parent = current == null ? null : current.parentFolderId();
    if (current == null && connectionTree.selectedValue() instanceof ConnectionFolder folder) {
      parent = folder.id();
    }
    editConnection(current, parent);
  }

  private void editConnection(Connection current, UUID parent) {
    if (vault != null && !vault.isUnlocked()) {
      unlockVaultAsync()
          .thenAccept(
              unlocked -> {
                if (unlocked) {
                  SwingUtilities.invokeLater(() -> showConnectionEditor(current, parent));
                }
              });
      return;
    }
    showConnectionEditor(current, parent);
  }

  private void showConnectionEditor(Connection current, UUID parent) {
    List<VaultEntry> entries = vault != null && vault.isUnlocked() ? vaultEntries() : List.of();
    ConnectionEditor editor =
        new ConnectionEditor(this, current, parent, connectionTree.folders(), entries);
    editor.setVisible(true);
    Connection edited = editor.result();
    if (edited != null) {
      runDatabaseAction(
          () -> connections.save(edited), current == null ? edited.id() : null);
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
    newFolder(connectionTree.selectedValue() instanceof ConnectionFolder folder ? folder : null);
  }

  private void newFolder(ConnectionFolder parent) {
    String name = JOptionPane.showInputDialog(this, "Folder name:");
    if (name == null || name.isBlank()) {
      return;
    }
    UUID parentId = parent == null ? null : parent.id();
    ConnectionFolder folder = new ConnectionFolder(UUID.randomUUID(), parentId, name.trim(), 0);
    runDatabaseAction(
        () -> connections.save(folder), folder.id());
  }

  private void renameFolder(ConnectionFolder folder) {
    String name =
        (String)
            JOptionPane.showInputDialog(
                this,
                "Folder name:",
                "Rename folder",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                folder.name());
    if (name == null || name.isBlank() || name.trim().equals(folder.name())) {
      return;
    }
    runDatabaseAction(
        () ->
            connections.save(
                new ConnectionFolder(
                    folder.id(), folder.parentFolderId(), name.trim(), folder.sortOrder())));
  }

  private void deleteSelected() {
    deleteItem(connectionTree.selectedValue());
  }

  private void deleteItem(Object selected) {
    if (!(selected instanceof Connection || selected instanceof ConnectionFolder)) {
      return;
    }
    if (selected instanceof ConnectionFolder folder
        && (connectionTree.folders().stream()
                .anyMatch(child -> folder.id().equals(child.parentFolderId()))
            || connectionTree.connections().stream()
                .anyMatch(child -> folder.id().equals(child.parentFolderId())))) {
      JOptionPane.showMessageDialog(
          this,
          "Move or delete the connections and subfolders in this folder first.",
          "Folder is not empty",
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    if (JOptionPane.showConfirmDialog(
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
    Path file = chooseVaultFile(FileDialog.LOAD);
    if (file == null) {
      return;
    }
    KdbxVault selectedVault = new KdbxVault(file);
    long operation = ++vaultOperationVersion;
    unlockVaultAsync(selectedVault)
        .thenAccept(
            unlocked ->
                SwingUtilities.invokeLater(
                    () -> {
                      if (operation != vaultOperationVersion || !isDisplayable()) {
                        selectedVault.lock();
                        return;
                      }
                      if (unlocked) {
                        activateVault(selectedVault, "Vault opened: " + file);
                        new VaultBrowserDialog(this, selectedVault).setVisible(true);
                      }
                    }));
  }

  private void createVault() {
    Path file = chooseVaultFile(FileDialog.SAVE);
    if (file == null) {
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
    long operation = ++vaultOperationVersion;
    CompletableFuture.supplyAsync(
            () -> {
              try {
                KdbxVault.create(file, password);
                KdbxVault createdVault = new KdbxVault(file);
                createdVault.unlock(password);
                return createdVault;
              } catch (Exception error) {
                throw new RuntimeException(error);
              } finally {
                Arrays.fill(password, '\0');
              }
            })
        .whenComplete(
            (createdVault, error) ->
                SwingUtilities.invokeLater(
                    () -> {
                      if (operation != vaultOperationVersion || !isDisplayable()) {
                        if (createdVault != null) {
                          createdVault.lock();
                        }
                        return;
                      }
                      if (error == null) {
                        activateVault(createdVault, "Vault created: " + file);
                        new VaultBrowserDialog(this, createdVault).setVisible(true);
                      } else {
                        showError("Could not create or open vault", error);
                      }
                    }));
  }

  private void activateVault(KdbxVault selectedVault, String message) {
    if (vault != null) {
      vault.lock();
    }
    vault = selectedVault;
    lastVaultUse = System.nanoTime();
    runDatabaseAction(() -> settings.put("vault.path", selectedVault.path().toString()));
    showStatus(message);
  }

  private Path chooseVaultFile(int mode) {
    FileDialog chooser =
        new FileDialog(
            this, mode == FileDialog.SAVE ? "Create KeePass vault" : "Open KeePass vault", mode);
    if (mode == FileDialog.SAVE) {
      chooser.setFile("vault.kdbx");
    }
    try {
      chooser.setVisible(true);
      var files = chooser.getFiles();
      return files.length == 0 ? null : files[0].toPath();
    } finally {
      chooser.dispose();
    }
  }

  private void browseVault() {
    KdbxVault selectedVault = vault;
    if (selectedVault == null) {
      showStatus("Select a KeePass vault first");
      return;
    }
    unlockVaultAsync(selectedVault)
        .thenAccept(
            unlocked -> {
              if (unlocked) {
                SwingUtilities.invokeLater(
                    () -> {
                      if (vault == selectedVault) {
                        new VaultBrowserDialog(this, selectedVault).setVisible(true);
                      }
                    });
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
    return unlockVaultAsync(vault);
  }

  private CompletableFuture<Boolean> unlockVaultAsync(KdbxVault selectedVault) {
    if (selectedVault.isUnlocked()) {
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
    runDatabaseAction(action, null);
  }

  private void runDatabaseAction(DatabaseAction action, UUID revealId) {
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
                        refreshTree(revealId);
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

  private static JMenuItem item(String title, Icon icon, Runnable action) {
    JMenuItem item = new JMenuItem(title, icon);
    item.addActionListener(event -> action.run());
    return item;
  }

  private static JMenu menu(String title) {
    JMenu menu = new JMenu(title);
    menu.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
    return menu;
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
