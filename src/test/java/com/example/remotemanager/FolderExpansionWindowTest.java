package com.example.remotemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.JList;
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

  @Test void revealsNewSubfolderInsideClosedNestedFolders() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    Path vaultFile = temp.resolve("new-subfolder.kdbx");
    char[] password = "test-password".toCharArray();
    KdbxVault.create(vaultFile, password);
    KdbxVault vault = new KdbxVault(vaultFile);
    vault.unlock(password);
    ConnectionFolder outer = new ConnectionFolder(vault.createFolder(null, "Outer"), null, "Outer", 0);
    ConnectionFolder middle = new ConnectionFolder(vault.createFolder(outer.id(), "Middle"), outer.id(), "Middle", 0);
    ConnectionFolder parent = new ConnectionFolder(vault.createFolder(middle.id(), "Parent"), middle.id(), "Parent", 0);
    vault.save();

    MainWindow window = openWindow(new SettingsRepository(temp.resolve("settings.properties")), vault);
    try {
      ConnectionTreePanel panel = treePanel(window);
      waitForFolders(panel, 3);
      SwingUtilities.invokeAndWait(() -> assertFalse(tree(panel).isExpanded(pathFor(tree(panel), outer.id()))));

      Method createFolder = MainWindow.class.getDeclaredMethod("createFolder", ConnectionFolder.class, String.class);
      createFolder.setAccessible(true);
      SwingUtilities.invokeAndWait(() -> {
        try {
          createFolder.invoke(window, parent, "New child");
          assertFalse(tree(panel).isEnabled());
          assertFalse(identities(window).isEnabled());
          assertFalse(window.getJMenuBar().getMenu(0).getItem(1).isEnabled());
          assertEquals("Saving vault...", status(window).getText());
          createFolder.invoke(window, parent, "Duplicate child");
        }
        catch (Exception error) { throw new RuntimeException(error); }
      });
      waitForFolders(panel, 4);

      SwingUtilities.invokeAndWait(() -> {
        ConnectionFolder child = panel.folders().stream()
            .filter(folder -> folder.name().equals("New child"))
            .findFirst().orElseThrow();
        assertEquals(parent.id(), child.parentFolderId());
        assertEquals(child.id().toString(), panel.selectedId());
        for (ConnectionFolder ancestor : new ConnectionFolder[] {outer, middle, parent}) {
          assertTrue(tree(panel).isExpanded(pathFor(tree(panel), ancestor.id())));
          assertTrue(panel.expandedIds().contains(ancestor.id().toString()));
        }
        assertTrue(tree(panel).isVisible(pathFor(tree(panel), child.id())));
        assertTrue(tree(panel).isEnabled());
        assertTrue(identities(window).isEnabled());
        assertTrue(window.getJMenuBar().getMenu(0).getItem(1).isEnabled());
        assertEquals("Vault saved", status(window).getText());
        assertEquals(4, panel.folders().size());
      });
    } finally { closeWindow(window); }
  }

  @Test void restoresVaultControlsAfterSaveFailure() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    Path vaultFile = temp.resolve("failed-save.kdbx");
    char[] password = "test-password".toCharArray();
    KdbxVault.create(vaultFile, password);
    KdbxVault vault = new KdbxVault(vaultFile);
    vault.unlock(password);
    MainWindow window = openWindow(new SettingsRepository(temp.resolve("settings.properties")), vault);
    try {
      ConnectionTreePanel panel = treePanel(window);
      Class<?> mutationType = Arrays.stream(MainWindow.class.getDeclaredClasses())
          .filter(type -> type.getSimpleName().equals("VaultMutation"))
          .findFirst().orElseThrow();
      Method mutate = MainWindow.class.getDeclaredMethod("mutateAndReveal", mutationType);
      mutate.setAccessible(true);
      Object failing = Proxy.newProxyInstance(mutationType.getClassLoader(),
          new Class<?>[] {mutationType}, (proxy, method, args) -> {
            throw new IllegalStateException("Intentional save failure");
          });
      AtomicReference<CompletableFuture<?>> result = new AtomicReference<>();
      SwingUtilities.invokeAndWait(() -> {
        try { result.set((CompletableFuture<?>) mutate.invoke(window, failing)); }
        catch (Exception error) { throw new RuntimeException(error); }
        assertFalse(tree(panel).isEnabled());
      });
      org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
          () -> result.get().get(15, TimeUnit.SECONDS));
      waitForStatus(window, "Vault save failed");
      SwingUtilities.invokeAndWait(() -> {
        assertTrue(tree(panel).isEnabled());
        assertTrue(identities(window).isEnabled());
        assertTrue(window.getJMenuBar().getMenu(0).getItem(1).isEnabled());
      });
    } finally { closeWindow(window); }
  }

  @Test void clearsSavingStateWhenVaultLocks() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    Path vaultFile = temp.resolve("lock-during-save.kdbx");
    char[] password = "test-password".toCharArray();
    KdbxVault.create(vaultFile, password);
    KdbxVault vault = new KdbxVault(vaultFile);
    vault.unlock(password);
    MainWindow window = openWindow(new SettingsRepository(temp.resolve("settings.properties")), vault);
    try {
      ConnectionTreePanel panel = treePanel(window);
      Method createFolder = MainWindow.class.getDeclaredMethod("createFolder", ConnectionFolder.class, String.class);
      createFolder.setAccessible(true);
      Method lockVault = MainWindow.class.getDeclaredMethod("lockVault");
      lockVault.setAccessible(true);
      SwingUtilities.invokeAndWait(() -> {
        try {
          createFolder.invoke(window, null, "Child");
          assertEquals("Saving vault...", status(window).getText());
          lockVault.invoke(window);
        } catch (Exception error) { throw new RuntimeException(error); }
        assertEquals("Vault locked", status(window).getText());
        assertFalse(tree(panel).isEnabled());
        assertFalse(savingVault(window));
      });
    } finally { closeWindow(window); }
  }

  @Test void restoresOpenFolderAfterClosingAndReopeningWindow() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    Path vaultFile = temp.resolve("folders.kdbx");
    char[] password = "test-password".toCharArray();
    KdbxVault.create(vaultFile, password);
    KdbxVault firstVault = new KdbxVault(vaultFile);
    firstVault.unlock(password);
    ConnectionFolder parent = new ConnectionFolder(firstVault.createFolder(null, "Parent"), null, "Parent", 0);
    firstVault.createFolder(parent.id(), "Child");
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

  private static JList<?> identities(MainWindow window) {
    try {
      Field field = MainWindow.class.getDeclaredField("identities");
      field.setAccessible(true);
      return (JList<?>) field.get(window);
    } catch (Exception error) { throw new RuntimeException(error); }
  }

  private static JLabel status(MainWindow window) {
    try {
      Field field = MainWindow.class.getDeclaredField("status");
      field.setAccessible(true);
      return (JLabel) field.get(window);
    } catch (Exception error) { throw new RuntimeException(error); }
  }

  private static boolean savingVault(MainWindow window) {
    try {
      Field field = MainWindow.class.getDeclaredField("savingVault");
      field.setAccessible(true);
      return field.getBoolean(window);
    } catch (Exception error) { throw new RuntimeException(error); }
  }

  private static void waitForStatus(MainWindow window, String expected) throws Exception {
    for (int attempt = 0; attempt < 200; attempt++) {
      AtomicBoolean matched = new AtomicBoolean();
      SwingUtilities.invokeAndWait(() -> matched.set(expected.equals(status(window).getText())));
      if (matched.get()) return;
      Thread.sleep(25);
    }
    throw new AssertionError("Status did not become: " + expected);
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
    return (JTree) ((JScrollPane) panel.getComponent(0)).getViewport().getView();
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
