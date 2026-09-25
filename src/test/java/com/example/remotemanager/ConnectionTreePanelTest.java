package com.example.remotemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.remotemanager.model.Connection;
import com.example.remotemanager.model.ConnectionFolder;
import com.example.remotemanager.model.AuthenticationType;
import com.example.remotemanager.ui.connections.ConnectionTreePanel;
import com.example.remotemanager.vault.VaultEntry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.swing.SwingUtilities;
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
    @Override
    public void open(Connection connection) {}

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
