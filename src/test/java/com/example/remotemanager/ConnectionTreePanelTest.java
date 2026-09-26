package com.example.remotemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.remotemanager.model.Connection;
import com.example.remotemanager.model.ConnectionFolder;
import com.example.remotemanager.model.AuthenticationType;
import com.example.remotemanager.ui.connections.ConnectionTreePanel;
import com.example.remotemanager.vault.VaultEntry;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

class ConnectionTreePanelTest {
  @Test
  void revealsNewSubfolderAfterReload() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ConnectionTreePanel panel = new ConnectionTreePanel(new NoopActions());
          ConnectionFolder parent = new ConnectionFolder(UUID.randomUUID(), null, "Parent", 0);
          ConnectionFolder child = new ConnectionFolder(UUID.randomUUID(), parent.id(), "Child", 0);

          panel.showConnections(List.of(parent), List.of());
          panel.showConnections(List.of(parent, child), List.of());
          panel.reveal(child.id());

          assertEquals(child.id().toString(), panel.selectedId());
          assertTrue(panel.expandedIds().contains(parent.id().toString()));

          panel.collapseAll();
          assertFalse(panel.expandedIds().contains(parent.id().toString()));
          panel.expandAll();
          assertTrue(panel.expandedIds().contains(parent.id().toString()));
        });
  }

  @Test
  void restoresExpandedChildInsideCollapsedParent() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      ConnectionTreePanel panel = new ConnectionTreePanel(new NoopActions());
      ConnectionFolder parent = new ConnectionFolder(UUID.randomUUID(), null, "Parent", 0);
      ConnectionFolder child = new ConnectionFolder(UUID.randomUUID(), parent.id(), "Child", 0);
      ConnectionFolder grandchild = new ConnectionFolder(UUID.randomUUID(), child.id(), "Grandchild", 0);
      List<ConnectionFolder> folders = List.of(parent, child, grandchild);
      panel.showConnections(folders, List.of());

      JTree tree = ((JTree) ((JScrollPane) panel.getComponent(0)).getViewport().getView());
      tree.expandPath(pathFor(tree, child.id()));
      tree.setSelectionPath(pathFor(tree, grandchild.id()));
      tree.collapsePath(pathFor(tree, parent.id()));
      String saved = panel.expandedIds();
      assertTrue(saved.contains(child.id().toString()));
      assertFalse(saved.contains(parent.id().toString()));

      panel.showConnections(folders, List.of());
      assertFalse(tree.isExpanded(pathFor(tree, parent.id())));
      assertTrue(panel.expandedIds().contains(child.id().toString()));

      panel.clear();
      panel.restoreOnNextLoad(saved, "");
      panel.showConnections(folders, List.of());
      assertFalse(tree.isExpanded(pathFor(tree, parent.id())));
      tree.expandPath(pathFor(tree, parent.id()));
      assertTrue(tree.isExpanded(pathFor(tree, child.id())));

      panel.collapseAll();
      tree.expandPath(pathFor(tree, parent.id()));
      assertFalse(tree.isExpanded(pathFor(tree, child.id())));
    });
  }

  private static TreePath pathFor(JTree tree, UUID id) {
    var nodes = ((DefaultMutableTreeNode) tree.getModel().getRoot()).depthFirstEnumeration();
    while (nodes.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
      if (node.getUserObject() instanceof ConnectionFolder folder && folder.id().equals(id))
        return new TreePath(node.getPath());
    }
    throw new AssertionError("Folder missing from tree: " + id);
  }

  @Test
  void selectsAndOpensHostFromEmptySpaceOnItsRow() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      NoopActions actions = new NoopActions();
      ConnectionTreePanel panel = new ConnectionTreePanel(actions);
      Connection host = new Connection(UUID.randomUUID(), "Server", "server.example", 22,
          "admin", null, AuthenticationType.PASSWORD, UUID.randomUUID(), null,
          null, null, "", 0);
      panel.showConnections(List.of(), List.of(host));

      JTree tree = (JTree) ((JScrollPane) panel.getComponent(0)).getViewport().getView();
      tree.setSize(400, 200);
      Rectangle row = tree.getRowBounds(1);
      int x = row.x + row.width + 20;
      int y = row.y + row.height / 2;
      assertTrue(x < tree.getWidth());
      assertEquals(null, tree.getPathForLocation(x, y));

      tree.dispatchEvent(new MouseEvent(tree, MouseEvent.MOUSE_PRESSED,
          System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
      assertEquals(host.id().toString(), panel.selectedId());
      tree.dispatchEvent(new MouseEvent(tree, MouseEvent.MOUSE_CLICKED,
          System.currentTimeMillis(), 0, x, y, 2, false, MouseEvent.BUTTON1));
      assertEquals(host, actions.opened);

      tree.clearSelection();
      tree.dispatchEvent(new MouseEvent(tree, MouseEvent.MOUSE_PRESSED,
          System.currentTimeMillis(), 0, x, row.y + row.height + 5, 1, false,
          MouseEvent.BUTTON1));
      assertEquals("", panel.selectedId());
    });
  }

  @Test
  void sudoCopyAvailabilityDoesNotDependOnSshIdentity() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      ConnectionTreePanel panel = new ConnectionTreePanel(new NoopActions());
      UUID sudoId = UUID.randomUUID();
      Connection host = new Connection(UUID.randomUUID(), "Server", "server.example", 22,
          "admin", null, AuthenticationType.PASSWORD, UUID.randomUUID(), sudoId,
          null, null, "", 0);
      assertFalse(panel.hasSudoPassword(host));
      panel.setIdentities(List.of(new VaultEntry(sudoId, "Sudo", "admin", Map.of(),
          List.of(), false)));
      assertFalse(panel.hasSudoPassword(host));
      panel.setIdentities(List.of(new VaultEntry(sudoId, "Sudo", "admin", Map.of(),
          List.of(), true)));
      assertEquals("identity missing", panel.issue(host));
      assertTrue(panel.hasSudoPassword(host));
    });
  }

  private static final class NoopActions implements ConnectionTreePanel.Actions {
    private Connection opened;
    @Override
    public void open(Connection connection) { opened = connection; }

    @Override
    public void copySudoPassword(Connection connection) {}

    @Override
    public void edit(Connection connection) {}

    @Override
    public void newFolder(ConnectionFolder parent) {}

    @Override
    public void newConnection(ConnectionFolder parent) {}

    @Override
    public void rename(ConnectionFolder folder) {}

    @Override
    public void deleteFolder(ConnectionFolder folder) {}

    @Override
    public void deleteConnection(Connection connection) {}
  }
}
