package com.mmlinaric.remotemanager.ui.connections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.model.ConnectionFolder;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ConnectionRootMenuTest {
  @Test
  void rootOffersTopLevelFolderAndConnectionActions() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      RecordingActions actions = new RecordingActions();
      ConnectionTreePanel panel = new ConnectionTreePanel(actions);
      JPopupMenu menu = panel.contextMenuFor(new DefaultMutableTreeNode("Connections"), actions);

      assertEquals(2, menu.getComponentCount());
      JMenuItem folder = (JMenuItem) menu.getComponent(0);
      JMenuItem connection = (JMenuItem) menu.getComponent(1);
      assertEquals("New folder", folder.getText());
      assertEquals("New connection", connection.getText());

      folder.doClick();
      connection.doClick();
      assertEquals(1, actions.foldersCreated);
      assertEquals(1, actions.connectionsCreated);
      assertNull(actions.folderParent);
      assertNull(actions.connectionParent);
    });
  }

  private static final class RecordingActions implements ConnectionTreePanel.Actions {
    private int foldersCreated;
    private int connectionsCreated;
    private ConnectionFolder folderParent;
    private ConnectionFolder connectionParent;

    @Override public void open(Connection connection) {}
    @Override public void copySudoPassword(Connection connection) {}
    @Override public void edit(Connection connection) {}
    @Override public void newFolder(ConnectionFolder parent) {
      foldersCreated++;
      folderParent = parent;
    }
    @Override public void newConnection(ConnectionFolder parent) {
      connectionsCreated++;
      connectionParent = parent;
    }
    @Override public void rename(ConnectionFolder folder) {}
    @Override public void deleteFolder(ConnectionFolder folder) {}
    @Override public void deleteConnection(Connection connection) {}
  }
}
