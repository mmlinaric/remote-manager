package com.example.remotemanager;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.remotemanager.model.ConnectionFolder;
import com.example.remotemanager.persistence.SettingsRepository;
import com.example.remotemanager.ui.connections.ConnectionTreePanel;
import com.example.remotemanager.ui.main.MainWindow;
import com.example.remotemanager.vault.kdbx.KdbxVault;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FolderExpansionWindowTest {
  @TempDir Path temp;

  @Test void restoresOpenFolderAfterClosingAndReopeningWindow() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    Path vaultFile = temp.resolve("folders.kdbx");
    char[] password = "test-password".toCharArray();
    KdbxVault.create(vaultFile, password);
    KdbxVault firstVault = new KdbxVault(vaultFile);
    firstVault.unlock(password);
    ConnectionFolder parent = new ConnectionFolder(UUID.randomUUID(), null, "Parent", 0);
    ConnectionFolder child = new ConnectionFolder(UUID.randomUUID(), parent.id(), "Child", 0);
    firstVault.putFolder(parent);
    firstVault.putFolder(child);
    firstVault.save();

    Path settingsFile = temp.resolve("settings.properties");
    MainWindow first = openWindow(new SettingsRepository(settingsFile), firstVault);
    try {
      ConnectionTreePanel panel = treePanel(first);
      waitForFolders(panel, 2);
      SwingUtilities.invokeAndWait(() -> tree(panel).expandPath(pathFor(tree(panel), parent.id())));
      assertTrue(panel.expandedIds().contains(parent.id().toString()));
    } finally { closeWindow(first); }

    KdbxVault reopenedVault = new KdbxVault(vaultFile);
    reopenedVault.unlock(password);
    MainWindow second = openWindow(new SettingsRepository(settingsFile), reopenedVault);
    try {
      ConnectionTreePanel panel = treePanel(second);
      waitForFolders(panel, 2);
      AtomicBoolean expanded = new AtomicBoolean();
      SwingUtilities.invokeAndWait(() ->
          expanded.set(tree(panel).isExpanded(pathFor(tree(panel), parent.id()))));
      assertTrue(expanded.get());
    } finally { closeWindow(second); }
  }

  private static MainWindow openWindow(SettingsRepository settings, KdbxVault vault) throws Exception {
    Field vaultField = MainWindow.class.getDeclaredField("vault");
    vaultField.setAccessible(true);
    Method showWorkspace = MainWindow.class.getDeclaredMethod("showWorkspace");
    showWorkspace.setAccessible(true);
    AtomicReference<MainWindow> result = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      try {
        MainWindow window = new MainWindow(settings);
        vaultField.set(window, vault);
        window.setExtendedState(Frame.NORMAL);
        window.setSize(800, 650);
        window.setLocation(-3000, -3000);
        window.setVisible(true);
        showWorkspace.invoke(window);
        result.set(window);
      } catch (Exception error) { throw new RuntimeException(error); }
    });
    return result.get();
  }

  private static ConnectionTreePanel treePanel(MainWindow window) throws Exception {
    Field field = MainWindow.class.getDeclaredField("connectionTree");
    field.setAccessible(true);
    return (ConnectionTreePanel) field.get(window);
  }

  private static void waitForFolders(ConnectionTreePanel panel, int expected) throws Exception {
    for (int attempt = 0; attempt < 200; attempt++) {
      AtomicBoolean loaded = new AtomicBoolean();
      SwingUtilities.invokeAndWait(() -> loaded.set(panel.folders().size() == expected));
      if (loaded.get()) return;
      Thread.sleep(25);
    }
    throw new AssertionError("Folder tree did not load");
  }

  private static void closeWindow(MainWindow window) throws Exception {
    SwingUtilities.invokeAndWait(() ->
        window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
  }

  private static JTree tree(ConnectionTreePanel panel) {
    return (JTree) ((JScrollPane) panel.getComponent(1)).getViewport().getView();
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
}
