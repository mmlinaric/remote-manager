package com.example.remotemanager;

import static org.junit.jupiter.api.Assertions.*;

import com.example.remotemanager.model.AuthenticationType;
import com.example.remotemanager.model.Connection;
import com.example.remotemanager.model.ConnectionFolder;
import com.example.remotemanager.persistence.ConnectionRepository;
import com.example.remotemanager.persistence.Database;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceTest {
  @TempDir Path temp;

  @Test
  void migrationAndCredentialReferences() throws SQLException {
    Database db = new Database(temp.resolve("app.db"));
    db.migrate();
    db.migrate();
    ConnectionRepository repo = new ConnectionRepository(db);
    UUID folderId = UUID.randomUUID();
    UUID credential = UUID.randomUUID();
    UUID sudo = UUID.randomUUID();
    repo.save(new ConnectionFolder(folderId, null, "Homelab", 0));
    Connection item =
        new Connection(
            UUID.randomUUID(),
            "pve1",
            "10.0.0.10",
            22,
            "mario",
            folderId,
            AuthenticationType.KDBX_PRIVATE_KEY,
            credential,
            sudo,
            "id_ed25519",
            null,
            "",
            0);
    repo.save(item);
    assertEquals(item, repo.connections().getFirst());
    assertEquals(folderId, repo.folders().getFirst().id());
    assertThrows(SQLException.class, () -> repo.deleteFolder(folderId));
  }
}
