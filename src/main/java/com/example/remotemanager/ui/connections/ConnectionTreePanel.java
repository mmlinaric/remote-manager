package com.example.remotemanager.ui.connections;

import com.example.remotemanager.model.Connection;
import com.example.remotemanager.model.ConnectionFolder;
import com.example.remotemanager.ui.SilkIcons;
import com.example.remotemanager.model.AuthenticationType;
import com.example.remotemanager.vault.VaultEntry;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/** Displays folders and connections while retaining selection across reloads. */
public final class ConnectionTreePanel extends JPanel {
  private final JTree tree = new JTree(new DefaultMutableTreeNode("Connections"));
  private final JLabel emptyHint = new JLabel("  No hosts yet. Use New host in the toolbar.");
  private final ConnectionDetailsPanel details = new ConnectionDetailsPanel();
  private List<ConnectionFolder> folders = List.of();
  private List<Connection> connections = List.of();
  private Map<UUID, VaultEntry> identities = Map.of();
  private final Set<UUID> expandedFolderIds = new HashSet<>();
  private String selectedId = "";
  private boolean loaded;
  private boolean restoringTree;
  private Runnable selectionChanged = () -> {};

  public ConnectionTreePanel(Actions actions) {
    super(new BorderLayout());
    add(new JScrollPane(tree), BorderLayout.CENTER);
    add(emptyHint, BorderLayout.NORTH);
    tree.setCellRenderer(
        new DefaultTreeCellRenderer() {
          @Override
          public Component getTreeCellRendererComponent(
              JTree tree,
              Object value,
              boolean selected,
              boolean expanded,
              boolean leaf,
              int row,
              boolean hasFocus) {
            super.getTreeCellRendererComponent(
                tree, value, selected, expanded, leaf, row, false);
            Object item = value instanceof DefaultMutableTreeNode node ? node.getUserObject() : value;
            setIcon(
                item instanceof Connection connection
                    ? issue(connection) == null ? SilkIcons.CONNECTION : SilkIcons.FAILED
                    : item instanceof ConnectionFolder ? SilkIcons.FOLDER : SilkIcons.ROOT);
            if (item instanceof Connection connection && issue(connection) != null)
              setText(connection.name() + " — " + issue(connection));
            return this;
          }
        });
    tree.addTreeSelectionListener(event -> {
      if (selectedValue() instanceof Connection connection)
        details.showConnection(connection, issue(connection));
      else details.showConnection(null);
      selectionChanged.run();
    });
    tree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override public void treeExpanded(TreeExpansionEvent event) {
        recordExpansion(event.getPath(), true);
      }

      @Override public void treeCollapsed(TreeExpansionEvent event) {
        recordExpansion(event.getPath(), false);
      }
    });
    tree.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent event) {
            if (!tree.isEnabled()) return;
            if (SwingUtilities.isLeftMouseButton(event)
                && tree.getPathForLocation(event.getX(), event.getY()) == null) {
              TreePath path = pathAtRow(event.getY());
              if (path != null) tree.setSelectionPath(path);
            }
            showPopup(event);
          }

          @Override
          public void mouseReleased(MouseEvent event) {
            showPopup(event);
          }

          @Override
          public void mouseClicked(MouseEvent event) {
            if (!tree.isEnabled()) return;
            if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 2) {
              TreePath path = pathAtRow(event.getY());
              if (path != null
                  && ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject()
                      instanceof Connection connection) {
                actions.open(connection);
              }
            }
          }

          private void showPopup(MouseEvent event) {
            if (!tree.isEnabled() || !event.isPopupTrigger()) {
              return;
            }
            TreePath path = pathAtRow(event.getY());
            if (path == null) return;
            JPopupMenu popup = contextMenuFor((DefaultMutableTreeNode) path.getLastPathComponent(), actions);
            if (popup == null) return;
            tree.setSelectionPath(path);
            popup.show(tree, event.getX(), event.getY());
          }
        });
  }

  JPopupMenu contextMenuFor(DefaultMutableTreeNode node, Actions actions) {
    Object value = node.getUserObject();
    JPopupMenu popup = new JPopupMenu();
    if (node.isRoot()) {
      popup.add(item("New folder", SilkIcons.NEW_FOLDER, () -> actions.newFolder(null)));
      popup.add(item("New connection", SilkIcons.NEW_CONNECTION, () -> actions.newConnection(null)));
    } else if (value instanceof ConnectionFolder folder) {
      popup.add(item("New subfolder", SilkIcons.NEW_FOLDER, () -> actions.newFolder(folder)));
      popup.add(item("New connection", SilkIcons.NEW_CONNECTION, () -> actions.newConnection(folder)));
      popup.addSeparator();
      popup.add(item("Rename", SilkIcons.EDIT, () -> actions.rename(folder)));
      popup.add(item("Delete", SilkIcons.DELETE, () -> actions.deleteFolder(folder)));
    } else if (value instanceof Connection connection) {
      popup.add(item("Open", SilkIcons.CONNECT, () -> actions.open(connection)));
      JMenuItem copySudo = item("Copy sudo password", SilkIcons.COPY_PASSWORD,
          () -> actions.copySudoPassword(connection));
      copySudo.setEnabled(hasSudoPassword(connection));
      popup.add(copySudo);
      popup.add(item("Edit", SilkIcons.EDIT, () -> actions.edit(connection)));
      popup.addSeparator();
      popup.add(item("Delete", SilkIcons.DELETE, () -> actions.deleteConnection(connection)));
    } else {
      return null;
    }
    return popup;
  }

  private TreePath pathAtRow(int y) {
    TreePath path = tree.getClosestPathForLocation(0, y);
    Rectangle bounds = path == null ? null : tree.getPathBounds(path);
    return bounds != null && y >= bounds.y && y < bounds.y + bounds.height ? path : null;
  }

  private static JMenuItem item(String title, Icon icon, Runnable action) {
    JMenuItem item = new JMenuItem(title, icon);
    item.addActionListener(event -> action.run());
    return item;
  }

  public ConnectionDetailsPanel details() {
    return details;
  }

  public void onSelectionChanged(Runnable action) { selectionChanged = action; }

  public void setVaultBusy(boolean busy) { tree.setEnabled(!busy); }

  public void expandAll() {
    for (int row = 0; row < tree.getRowCount(); row++) {
      tree.expandRow(row);
    }
  }

  public void collapseAll() {
    List<TreePath> paths = new ArrayList<>();
    var nodes = ((DefaultMutableTreeNode) tree.getModel().getRoot()).depthFirstEnumeration();
    while (nodes.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
      if (node.getUserObject() instanceof ConnectionFolder)
        paths.add(new TreePath(node.getPath()));
    }
    paths.sort(Comparator.comparingInt(TreePath::getPathCount).reversed());
    paths.forEach(tree::collapsePath);
    expandedFolderIds.clear();
  }

  public List<ConnectionFolder> folders() {
    return folders;
  }

  public List<Connection> connections() {
    return connections;
  }

  public void setIdentities(List<VaultEntry> entries) {
    identities = entries.stream().collect(java.util.stream.Collectors.toMap(VaultEntry::id, entry -> entry));
    tree.repaint();
    if (selectedValue() instanceof Connection connection)
      details.showConnection(connection, issue(connection));
  }

  public String issue(Connection connection) {
    if (connection.sshCredentialEntryId() != null) {
      VaultEntry identity = identities.get(connection.sshCredentialEntryId());
      if (identity == null) return "identity missing";
      if (connection.authenticationType() == AuthenticationType.PASSWORD && !identity.hasPassword())
        return "SSH password missing";
      if (connection.authenticationType() == AuthenticationType.KDBX_PRIVATE_KEY
          && !identity.attachments().contains(connection.privateKeyAttachmentName()))
        return "private key missing";
    }
    if (connection.sudoCredentialEntryId() != null) {
      VaultEntry sudo = identities.get(connection.sudoCredentialEntryId());
      if (sudo == null) return "sudo identity missing";
      if (!sudo.hasPassword()) return "sudo password missing";
    }
    return null;
  }

  public boolean hasSudoPassword(Connection connection) {
    if (connection == null || connection.sudoCredentialEntryId() == null) return false;
    VaultEntry identity = identities.get(connection.sudoCredentialEntryId());
    return identity != null && identity.hasPassword();
  }

  public boolean isTreeFocused() {
    Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    return focus != null && SwingUtilities.isDescendingFrom(focus, tree);
  }

  public Object selectedValue() {
    if (tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node) {
      return node.getUserObject();
    }
    return null;
  }

  public void restoreOnNextLoad(String expandedIds, String selectedId) {
    expandedFolderIds.clear();
    for (String id : expandedIds.split(",")) {
      try { expandedFolderIds.add(UUID.fromString(id)); }
      catch (IllegalArgumentException ignored) { /* Ignore stale or malformed display state. */ }
    }
    this.selectedId = selectedId;
    // The current model may be the empty tree left by locking the vault.
    loaded = false;
  }

  public void clear() {
    folders = List.of();
    connections = List.of();
    expandedFolderIds.clear();
    selectedId = "";
    loaded = false;
    emptyHint.setVisible(true);
    restoringTree = true;
    try { tree.setModel(buildTree()); }
    finally { restoringTree = false; }
    details.showConnection(null);
  }

  public void showConnections(List<ConnectionFolder> folders, List<Connection> connections) {
    if (loaded) selectedId = selectedId();
    this.folders = folders;
    this.connections = connections;
    expandedFolderIds.retainAll(folders.stream().map(ConnectionFolder::id).collect(Collectors.toSet()));
    emptyHint.setVisible(connections.isEmpty());
    restoringTree = true;
    try {
      tree.setModel(buildTree());
      restoreState();
    } finally { restoringTree = false; }
    loaded = true;
  }

  public String expandedIds() {
    return expandedFolderIds.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
  }

  public String selectedId() {
    Object selected = selectedValue();
    if (selected instanceof Connection connection) {
      return connection.id().toString();
    }
    if (selected instanceof ConnectionFolder folder) {
      return folder.id().toString();
    }
    return "";
  }

  public void reveal(UUID id) {
    if (!(tree.getModel().getRoot() instanceof DefaultMutableTreeNode root)) {
      return;
    }
    var nodes = root.depthFirstEnumeration();
    while (nodes.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
      Object value = node.getUserObject();
      UUID nodeId =
          value instanceof ConnectionFolder folder
              ? folder.id()
              : value instanceof Connection connection ? connection.id() : null;
      if (id.equals(nodeId)) {
        TreePath path = new TreePath(node.getPath());
        tree.expandPath(path.getParentPath());
        tree.setSelectionPath(path);
        tree.scrollPathToVisible(path);
        return;
      }
    }
  }

  private DefaultTreeModel buildTree() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("Connections");
    Map<UUID, DefaultMutableTreeNode> nodes = new HashMap<>();
    for (ConnectionFolder folder : folders) {
      nodes.put(folder.id(), new DefaultMutableTreeNode(folder));
    }
    for (ConnectionFolder folder : folders) {
      nodes.getOrDefault(folder.parentFolderId(), root).add(nodes.get(folder.id()));
    }
    for (Connection connection : connections) {
      nodes
          .getOrDefault(connection.parentFolderId(), root)
          .add(new DefaultMutableTreeNode(connection));
    }
    return new DefaultTreeModel(root);
  }

  private void restoreState() {
    if (!(tree.getModel().getRoot() instanceof DefaultMutableTreeNode root)) {
      return;
    }
    Map<UUID, TreePath> folderPaths = new HashMap<>();
    Map<String, TreePath> selectionPaths = new HashMap<>();
    var nodes = root.depthFirstEnumeration();
    while (nodes.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
      Object value = node.getUserObject();
      if (value instanceof ConnectionFolder folder) {
        TreePath path = new TreePath(node.getPath());
        folderPaths.put(folder.id(), path);
        selectionPaths.put(folder.id().toString(), path);
      } else if (value instanceof Connection connection) {
        selectionPaths.put(connection.id().toString(), new TreePath(node.getPath()));
      }
    }
    folderPaths.entrySet().stream()
        .filter(entry -> expandedFolderIds.contains(entry.getKey()))
        .map(Map.Entry::getValue)
        .sorted(Comparator.comparingInt(TreePath::getPathCount))
        .forEach(tree::expandPath);
    // Expanding a hidden child also opens its parent; close unsaved parents afterward.
    folderPaths.entrySet().stream()
        .filter(entry -> !expandedFolderIds.contains(entry.getKey()))
        .map(Map.Entry::getValue)
        .sorted(Comparator.comparingInt(TreePath::getPathCount).reversed())
        .filter(tree::isExpanded)
        .forEach(tree::collapsePath);
    TreePath selectedPath = selectionPaths.get(selectedId);
    if (selectedPath != null && isVisibleInExpandedFolders(selectedPath))
      tree.setSelectionPath(selectedPath);
  }

  private boolean isVisibleInExpandedFolders(TreePath path) {
    for (int index = 1; index < path.getPathCount() - 1; index++) {
      if (path.getPathComponent(index) instanceof DefaultMutableTreeNode node
          && node.getUserObject() instanceof ConnectionFolder folder
          && !expandedFolderIds.contains(folder.id())) return false;
    }
    return true;
  }

  private void recordExpansion(TreePath path, boolean expanded) {
    if (restoringTree) return;
    if (path.getLastPathComponent() instanceof DefaultMutableTreeNode node
        && node.getUserObject() instanceof ConnectionFolder folder) {
      if (expanded) expandedFolderIds.add(folder.id());
      else expandedFolderIds.remove(folder.id());
    }
  }

  public interface Actions {
    void open(Connection connection);

    void copySudoPassword(Connection connection);

    void edit(Connection connection);

    void newFolder(ConnectionFolder parent);

    void newConnection(ConnectionFolder parent);

    void rename(ConnectionFolder folder);

    void deleteFolder(ConnectionFolder folder);

    void deleteConnection(Connection connection);
  }
}
