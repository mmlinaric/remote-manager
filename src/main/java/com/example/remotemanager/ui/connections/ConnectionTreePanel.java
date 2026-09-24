package com.example.remotemanager.ui.connections;

import com.example.remotemanager.model.Connection;
import com.example.remotemanager.model.ConnectionFolder;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/** Displays folders and connections while retaining selection across reloads. */
public final class ConnectionTreePanel extends JPanel {
  private final JTree tree = new JTree(new DefaultMutableTreeNode("Connections"));
  private final ConnectionDetailsPanel details = new ConnectionDetailsPanel();
  private List<ConnectionFolder> folders = List.of();
  private List<Connection> connections = List.of();
  private String expandedIds = "";
  private String selectedId = "";
  private boolean loaded;

  public ConnectionTreePanel(Consumer<Connection> openConnection) {
    super(new BorderLayout());
    add(new JLabel("Connections"), BorderLayout.NORTH);
    add(new JScrollPane(tree), BorderLayout.CENTER);
    tree.addTreeSelectionListener(
        event ->
            details.showConnection(
                selectedValue() instanceof Connection connection ? connection : null));
    tree.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent event) {
            if (event.getClickCount() == 2 && selectedValue() instanceof Connection connection) {
              openConnection.accept(connection);
            }
          }
        });
  }

  public ConnectionDetailsPanel details() {
    return details;
  }

  public List<ConnectionFolder> folders() {
    return folders;
  }

  public List<Connection> connections() {
    return connections;
  }

  public Object selectedValue() {
    if (tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node) {
      return node.getUserObject();
    }
    return null;
  }

  public void restoreOnNextLoad(String expandedIds, String selectedId) {
    this.expandedIds = expandedIds;
    this.selectedId = selectedId;
  }

  public void showConnections(List<ConnectionFolder> folders, List<Connection> connections) {
    if (loaded) {
      expandedIds = expandedIds();
      selectedId = selectedId();
    }
    this.folders = folders;
    this.connections = connections;
    tree.setModel(buildTree());
    restoreState();
    loaded = true;
  }

  public String expandedIds() {
    Object root = tree.getModel().getRoot();
    var paths = tree.getExpandedDescendants(new TreePath(root));
    if (paths == null) {
      return "";
    }
    List<String> ids = new ArrayList<>();
    while (paths.hasMoreElements()) {
      Object node = paths.nextElement().getLastPathComponent();
      if (node instanceof DefaultMutableTreeNode treeNode
          && treeNode.getUserObject() instanceof ConnectionFolder folder) {
        ids.add(folder.id().toString());
      }
    }
    return String.join(",", ids);
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
    List<String> expanded = List.of(expandedIds.split(","));
    var nodes = root.depthFirstEnumeration();
    while (nodes.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
      Object value = node.getUserObject();
      String id = null;
      if (value instanceof ConnectionFolder folder) {
        id = folder.id().toString();
        if (expanded.contains(id)) {
          tree.expandPath(new TreePath(node.getPath()));
        }
      } else if (value instanceof Connection connection) {
        id = connection.id().toString();
      }
      if (selectedId.equals(id)) {
        tree.setSelectionPath(new TreePath(node.getPath()));
      }
    }
  }
}
