package com.example.remotemanager;

import static org.junit.jupiter.api.Assertions.*;

import com.example.remotemanager.vault.kdbx.KdbxVault;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultTest {
  @TempDir Path temp;

  @Test
  void createsWritesAndReadsEntryByUuid() throws Exception {
    Path file = temp.resolve("test.kdbx");
    char[] master = "test-master".toCharArray();
    KdbxVault.create(file, master);
    KdbxVault vault = new KdbxVault(file);
    vault.unlock(master);
    byte[] key = "test-key-data".getBytes();
    var id =
        vault.addEntry(
            "SSH",
            "alice",
            "passphrase".toCharArray(),
            Map.of("Environment", "test"),
            "id_ed25519",
            key);
    assertThrows(
        com.example.remotemanager.vault.VaultException.class,
        () -> vault.save("incorrect-master".toCharArray()));
    vault.save(master);
    assertArrayEquals(key, vault.getAttachment(id, "id_ed25519").orElseThrow());
    vault.lock();
    vault.unlock(master);
    assertEquals(1, vault.entries().size());
    assertEquals("SSH", vault.getEntry(id).orElseThrow().title());
    assertArrayEquals(key, vault.getAttachment(id, "id_ed25519").orElseThrow());
    assertArrayEquals("passphrase".toCharArray(), vault.getPassword(id).orElseThrow());
    Files.writeString(file, "external edit");
    assertThrows(
        com.example.remotemanager.vault.VaultConflictException.class, () -> vault.save(master));
  }

  @Test
  void rejectsCustomFieldsThatOverwriteCredentials() throws Exception {
    Path file = temp.resolve("reserved.kdbx");
    char[] master = "test-master".toCharArray();
    KdbxVault.create(file, master);
    KdbxVault vault = new KdbxVault(file);
    vault.unlock(master);

    assertThrows(
        com.example.remotemanager.vault.VaultException.class,
        () -> vault.addEntry("SSH", "alice", null, Map.of("Password", "wrong"), null, null));
    assertTrue(vault.entries().isEmpty());
  }
}
