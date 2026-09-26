package com.example.remotemanager.ui.connections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.remotemanager.model.ConnectionFolder;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.List;
import java.util.UUID;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class FolderPickerDialogTest {
  @Test void duplicateNamesKeepDistinctTreePathsAndFolderIds() {
    ConnectionFolder first = new ConnectionFolder(UUID.randomUUID(), null, "test", 0);
    ConnectionFolder second = new ConnectionFolder(UUID.randomUUID(), first.id(), "test", 0);
    ConnectionFolder third = new ConnectionFolder(UUID.randomUUID(), second.id(), "test", 0);
    List<ConnectionFolder> folders = List.of(third, first, second);
    JTree tree = new JTree(FolderPickerDialog.buildTree(folders));

    TreePath firstPath = FolderPickerDialog.pathFor(tree, first.id());
    TreePath secondPath = FolderPickerDialog.pathFor(tree, second.id());
    TreePath thirdPath = FolderPickerDialog.pathFor(tree, third.id());
    assertEquals(2, firstPath.getPathCount());
    assertEquals(3, secondPath.getPathCount());
    assertEquals(4, thirdPath.getPathCount());
    assertEquals(firstPath, secondPath.getParentPath());
    assertEquals(secondPath, thirdPath.getParentPath());
    assertEquals(third.id(), ((ConnectionFolder)
        ((DefaultMutableTreeNode) thirdPath.getLastPathComponent()).getUserObject()).id());
    assertEquals("(root) / test / test / test", FolderPickerDialog.displayPath(folders, third));
  }

  @Test void rootAndUnknownFolderUseRootPath() {
    JTree tree = new JTree(FolderPickerDialog.buildTree(List.of()));
    TreePath root = FolderPickerDialog.pathFor(tree, null);
    assertEquals(root, FolderPickerDialog.pathFor(tree, UUID.randomUUID()));
    assertEquals("(root)", FolderPickerDialog.displayPath(List.of(), null));
  }

  @Test void choosingNestedFolderReturnsItsIdAndClosingCancels() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    ConnectionFolder first = new ConnectionFolder(UUID.randomUUID(), null, "test", 0);
    ConnectionFolder second = new ConnectionFolder(UUID.randomUUID(), first.id(), "test", 0);
    SwingUtilities.invokeAndWait(() -> {
      Timer choose = new Timer(0, event -> {
        FolderPickerDialog dialog = openDialog();
        try {
          JScrollPane scroll = (JScrollPane) dialog.getContentPane().getComponent(1);
          JTree tree = (JTree) scroll.getViewport().getView();
          tree.setSelectionPath(FolderPickerDialog.pathFor(tree, second.id()));
          dialog.getRootPane().getDefaultButton().doClick();
        } finally { dialog.dispose(); }
      });
      choose.setRepeats(false);
      choose.start();
      assertEquals(second.id(), FolderPickerDialog.choose(null, List.of(first, second), null).folderId());

      Timer cancel = new Timer(0, event -> openDialog().dispose());
      cancel.setRepeats(false);
      cancel.start();
      assertNull(FolderPickerDialog.choose(null, List.of(first, second), second.id()));
    });
  }

  private static FolderPickerDialog openDialog() {
    for (Window window : Window.getWindows())
      if (window instanceof FolderPickerDialog dialog && dialog.isShowing()) return dialog;
    throw new AssertionError("Folder picker is not showing");
  }
}
