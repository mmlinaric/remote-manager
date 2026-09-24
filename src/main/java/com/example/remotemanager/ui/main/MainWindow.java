package com.example.remotemanager.ui.main;

import com.example.remotemanager.model.Connection;
import com.example.remotemanager.model.ConnectionFolder;
import com.example.remotemanager.persistence.ConnectionRepository;
import com.example.remotemanager.persistence.SettingsRepository;
import com.example.remotemanager.ui.connections.ConnectionEditor;
import com.example.remotemanager.vault.VaultEntry;
import com.example.remotemanager.vault.kdbx.KdbxVault;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public final class MainWindow extends JFrame {
  private final ConnectionRepository connections;
  private final SettingsRepository settings;
  private final JTree tree = new JTree(new DefaultMutableTreeNode("Connections"));
  private final JTabbedPane tabs = new JTabbedPane();
  private final JLabel status = new JLabel("Ready");
  private final JTextField quickConnect = new JTextField(24);

  private KdbxVault vault;
  private List<ConnectionFolder> folders = List.of();

  public MainWindow(ConnectionRepository connections, SettingsRepository settings) {
    super("Remote Manager");
    this.connections = connections;
    this.settings = settings;

    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setMinimumSize(new Dimension(760, 480));
    setSize(1100, 700);
    setJMenuBar(createMenu());
    add(createToolbar(), BorderLayout.NORTH);
    add(createContent(), BorderLayout.CENTER);
    add(status, BorderLayout.SOUTH);

    tree.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent event) {
            if (event.getClickCount() == 2 && selectedValue() instanceof Connection connection) {
              showStatus("SSH session support is being added for " + connection.name());
            }
          }
        });

    loadSettings();
    refreshTree();
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
    file.add(item("Unlock vault", this::unlockVault));
    file.add(item("Lock vault", this::lockVault));
    file.addSeparator();
    file.add(item("Exit", this::dispose));
    menuBar.add(file);
    menuBar.add(new JMenu("View"));
    menuBar.add(new JMenu("Tools"));
    menuBar.add(new JMenu("Help"));
    return menuBar;
  }

  private JToolBar createToolbar() {
    JToolBar toolbar = new JToolBar();
    toolbar.setFloatable(false);
    toolbar.add(new JLabel("Connect: "));
    toolbar.add(quickConnect);
    JButton connect = new JButton("Connect");
    connect.addActionListener(event -> showStatus("Select a saved connection to connect"));
    toolbar.add(connect);
    return toolbar;
  }

  private JSplitPane createContent() {
    JPanel left = new JPanel(new BorderLayout());
    left.add(new JLabel("Connections"), BorderLayout.NORTH);
    left.add(new JScrollPane(tree), BorderLayout.CENTER);
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, tabs);
    split.setDividerLocation(300);
    return split;
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
                        folders = data.folders();
                        tree.setModel(buildTree(data));
                      }
                    }));
  }

  private DefaultTreeModel buildTree(TreeData data) {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("Connections");
    Map<UUID, DefaultMutableTreeNode> nodes = new HashMap<>();
    for (ConnectionFolder folder : data.folders()) {
      nodes.put(folder.id(), new DefaultMutableTreeNode(folder));
    }
    for (ConnectionFolder folder : data.folders()) {
      DefaultMutableTreeNode parent = nodes.getOrDefault(folder.parentFolderId(), root);
      parent.add(nodes.get(folder.id()));
    }
    for (Connection connection : data.connections()) {
      nodes
          .getOrDefault(connection.parentFolderId(), root)
          .add(new DefaultMutableTreeNode(connection));
    }
    return new DefaultTreeModel(root);
  }

  private void editSelected() {
    if (selectedValue() instanceof Connection connection) {
      editConnection(connection);
    }
  }

  private void editConnection(Connection current) {
    UUID parent = selectedValue() instanceof ConnectionFolder folder ? folder.id() : null;
    List<VaultEntry> entries = vault != null && vault.isUnlocked() ? vaultEntries() : List.of();
    ConnectionEditor editor = new ConnectionEditor(this, current, parent, folders, entries);
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
    UUID parent = selectedValue() instanceof ConnectionFolder folder ? folder.id() : null;
    runDatabaseAction(
        () -> connections.save(new ConnectionFolder(UUID.randomUUID(), parent, name.trim(), 0)));
  }

  private void deleteSelected() {
    Object selected = selectedValue();
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

  private void loadSettings() {
    CompletableFuture.supplyAsync(
            () -> {
              try {
                return settings.get("vault.path");
              } catch (SQLException error) {
                throw new RuntimeException(error);
              }
            })
        .whenComplete(
            (path, error) ->
                SwingUtilities.invokeLater(
                    () -> {
                      if (error == null && path.isPresent()) {
                        vault = new KdbxVault(Path.of(path.get()));
                      } else if (error != null) {
                        showError("Could not load settings", error);
                      }
                    }));
  }

  private void unlockVault() {
    if (vault == null) {
      showStatus("Select a KeePass vault first");
      return;
    }
    JPasswordField field = new JPasswordField(24);
    if (JOptionPane.showConfirmDialog(
            this, field, "Vault master password", JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) {
      return;
    }
    char[] password = field.getPassword();
    field.setText("");
    CompletableFuture.runAsync(
            () -> {
              try {
                vault.unlock(password);
              } catch (Exception error) {
                throw new RuntimeException(error);
              } finally {
                java.util.Arrays.fill(password, '\0');
              }
            })
        .whenComplete(
            (ignored, error) ->
                SwingUtilities.invokeLater(
                    () -> {
                      if (error == null) {
                        showStatus("Vault unlocked");
                      } else {
                        showError("Could not unlock vault", error);
                      }
                    }));
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

  private Object selectedValue() {
    if (tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node) {
      return node.getUserObject();
    }
    return null;
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

  @FunctionalInterface
  private interface DatabaseAction {
    void run() throws SQLException;
  }
}
