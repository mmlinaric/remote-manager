package com.mmlinaric.remotemanager;

import static org.junit.jupiter.api.Assertions.*;

import com.mmlinaric.remotemanager.model.AuthenticationType;
import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.model.ConnectionFolder;
import com.mmlinaric.remotemanager.vault.kdbx.KdbxVault;
import com.mmlinaric.remotemanager.workspace.VaultWorkspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultTest {
    @TempDir
    Path temp;

    @Test
    void opensAndPreservesCommonKeePassXcVault() throws Exception {
        Path file = temp.resolve("keepassxc.kdbx");
        try (var fixture = getClass().getResourceAsStream("/keepassxc-v4.fixture")) {
            assertNotNull(fixture);
            Files.copy(fixture, file, StandardCopyOption.REPLACE_EXISTING);
        }
        char[] master = "test-master".toCharArray();
        KdbxVault vault = new KdbxVault(file);
        vault.unlock(master);
        var external = vault.entries().getFirst();
        assertEquals("External identity", external.title());
        assertEquals("alice", external.username());
        assertArrayEquals(
                "passphrase".toCharArray(), vault.getPassword(external.id()).orElseThrow());
        assertArrayEquals(
                "test-key-data".getBytes(),
                vault.getAttachment(external.id(), "id_ed25519").orElseThrow());

        vault.addEntry("App identity", "bob", "new secret".toCharArray(), Map.of(), null, null);
        vault.save();
        vault.save();
        vault.lock();
        vault.unlock(master);
        assertEquals(2, vault.entries().size());
        assertEquals(
                external.id(),
                vault.entries().stream()
                        .filter(entry -> entry.title().equals("External identity"))
                        .findFirst()
                        .orElseThrow()
                        .id());
        assertArrayEquals(
                "passphrase".toCharArray(), vault.getPassword(external.id()).orElseThrow());
        assertArrayEquals(
                "test-key-data".getBytes(),
                vault.getAttachment(external.id(), "id_ed25519").orElseThrow());
    }

    @Test
    void createsWritesAndReadsEntryByUuid() throws Exception {
        Path file = temp.resolve("test.kdbx");
        char[] master = "test-master".toCharArray();
        KdbxVault.create(file, master);
        KdbxVault vault = new KdbxVault(file);
        vault.unlock(master);
        byte[] key = "test-key-data".getBytes();
        var id = vault.addEntry(
                "SSH", "alice", "passphrase".toCharArray(), Map.of("Environment", "test"), "id_ed25519", key);
        assertThrows(
                com.mmlinaric.remotemanager.vault.VaultException.class,
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
        assertThrows(com.mmlinaric.remotemanager.vault.VaultConflictException.class, () -> vault.save(master));
    }

    @Test
    void rejectsCustomFieldsThatOverwriteCredentials() throws Exception {
        Path file = temp.resolve("reserved.kdbx");
        char[] master = "test-master".toCharArray();
        KdbxVault.create(file, master);
        KdbxVault vault = new KdbxVault(file);
        vault.unlock(master);

        assertThrows(
                com.mmlinaric.remotemanager.vault.VaultException.class,
                () -> vault.addEntry("SSH", "alice", null, Map.of("Password", "wrong"), null, null));
        assertTrue(vault.entries().isEmpty());
    }

    @Test
    void storesHostsAndFoldersInsideVaultAndKeepsIdentitiesSeparate() throws Exception {
        Path file = temp.resolve("hosts.kdbx");
        char[] master = "master".toCharArray();
        KdbxVault.create(file, master);
        KdbxVault vault = new KdbxVault(file);
        vault.unlock(master);
        UUID identity = vault.addEntry("SSH password", "alice", "secret".toCharArray(), Map.of(), null, null);
        ConnectionFolder folder = new ConnectionFolder(vault.createFolder(null, "Production"), null, "Production", 0);
        vault.renameFolder(folder.id(), "Production renamed");
        ConnectionFolder renamedFolder = new ConnectionFolder(folder.id(), null, "Production renamed", 0);
        Connection host = new Connection(
                UUID.randomUUID(),
                "Web",
                "web.example.org",
                2222,
                "alice",
                folder.id(),
                AuthenticationType.PASSWORD,
                identity,
                null,
                null,
                null,
                "Important host",
                0);
        vault.putConnection(host);
        Connection agentHost = new Connection(
                UUID.randomUUID(),
                "Agent",
                "agent.example.org",
                22,
                "alice",
                null,
                AuthenticationType.SSH_AGENT,
                null,
                null,
                null,
                null,
                "",
                0);
        vault.putConnection(agentHost);
        vault.save();
        assertEquals(1, vault.entries().size());
        assertTrue(vault.connections().contains(host));
        assertTrue(vault.connections().contains(agentHost));
        vault.lock();
        assertThrows(com.mmlinaric.remotemanager.vault.VaultException.class, vault::connections);

        vault.unlock(master);
        assertEquals(List.of(renamedFolder), vault.folders());
        assertTrue(vault.connections().contains(host));
        assertEquals(identity, vault.entries().getFirst().id());

        Connection moved = new Connection(
                host.id(),
                "Web renamed",
                host.hostname(),
                host.port(),
                host.username(),
                null,
                host.authenticationType(),
                identity,
                null,
                null,
                null,
                host.notes(),
                0);
        vault.putConnection(moved);
        vault.deleteFolder(folder.id());
        vault.save();
        vault.lock();
        vault.unlock(master);
        assertTrue(vault.folders().isEmpty());
        assertTrue(vault.connections().contains(moved));
        assertTrue(vault.connections().contains(agentHost));
        assertThrows(com.mmlinaric.remotemanager.vault.VaultException.class, () -> vault.deleteIdentity(identity));
    }

    @Test
    void hostPasswordsStayPrivateAndFollowTheirHost() throws Exception {
        Path file = temp.resolve("host-passwords.kdbx");
        char[] master = "master".toCharArray();
        KdbxVault.create(file, master);
        KdbxVault vault = new KdbxVault(file);
        vault.unlock(master);
        UUID firstPassword = UUID.randomUUID(), firstSudo = UUID.randomUUID();
        UUID secondPassword = UUID.randomUUID();
        Connection first = new Connection(
                UUID.randomUUID(),
                "First",
                "first.example",
                22,
                "alice",
                null,
                AuthenticationType.PASSWORD,
                firstPassword,
                firstSudo,
                null,
                null,
                "",
                0);
        Connection second = new Connection(
                UUID.randomUUID(),
                "Second",
                "second.example",
                22,
                "bob",
                null,
                AuthenticationType.PASSWORD,
                secondPassword,
                null,
                null,
                null,
                "",
                0);
        vault.putHostSecret(
                firstPassword, first.id(), "ssh", "First SSH password", "alice", "first-secret".toCharArray());
        vault.putHostSecret(firstSudo, first.id(), "sudo", "First sudo password", "alice", "sudo-secret".toCharArray());
        vault.putHostSecret(
                secondPassword, second.id(), "ssh", "Second SSH password", "bob", "second-secret".toCharArray());
        vault.putConnection(first);
        vault.putConnection(second);
        vault.save();
        vault.lock();
        vault.unlock(master);

        assertTrue(vault.entries().isEmpty());
        assertEquals(3, vault.credentialEntries().size());
        assertTrue(KdbxVault.isHostSecret(vault.getEntry(firstPassword).orElseThrow(), first.id(), "ssh"));
        assertArrayEquals(
                "first-secret".toCharArray(), vault.getPassword(firstPassword).orElseThrow());
        assertArrayEquals(
                "sudo-secret".toCharArray(), vault.getPassword(firstSudo).orElseThrow());
        assertArrayEquals(
                "second-secret".toCharArray(), vault.getPassword(secondPassword).orElseThrow());

        vault.putHostSecret(firstPassword, first.id(), "ssh", "Renamed", "alice", null);
        assertArrayEquals(
                "first-secret".toCharArray(), vault.getPassword(firstPassword).orElseThrow());
        assertThrows(
                com.mmlinaric.remotemanager.vault.VaultException.class,
                () -> vault.putHostSecret(firstPassword, second.id(), "ssh", "Wrong", "bob", null));
        Connection switched = new Connection(
                first.id(),
                first.name(),
                first.hostname(),
                first.port(),
                first.username(),
                null,
                AuthenticationType.SSH_AGENT,
                null,
                firstSudo,
                null,
                null,
                "",
                0);
        vault.putConnection(switched);
        assertTrue(vault.getEntry(firstPassword).isEmpty());
        assertArrayEquals(
                "sudo-secret".toCharArray(), vault.getPassword(firstSudo).orElseThrow());
        vault.deleteConnection(first.id());
        assertTrue(vault.getEntry(firstPassword).isEmpty());
        assertTrue(vault.getEntry(firstSudo).isEmpty());
        assertArrayEquals(
                "second-secret".toCharArray(), vault.getPassword(secondPassword).orElseThrow());
        vault.save();
        vault.lock();
        vault.unlock(master);
        assertEquals(List.of(second), vault.connections());
    }

    @Test
    void rejectedExternalConflictDoesNotLeaveAnUnsavedHostVisible() throws Exception {
        Path file = temp.resolve("conflict.kdbx");
        char[] master = "master".toCharArray();
        KdbxVault.create(file, master);
        KdbxVault local = new KdbxVault(file);
        KdbxVault external = new KdbxVault(file);
        local.unlock(master);
        external.unlock(master);
        external.addEntry("Changed externally", "alice", "secret".toCharArray(), Map.of(), null, null);
        external.save();
        local.putConnection(new Connection(
                UUID.randomUUID(),
                "Unsaved",
                "localhost",
                22,
                "alice",
                null,
                AuthenticationType.SSH_AGENT,
                null,
                null,
                null,
                null,
                "",
                0));
        assertThrows(com.mmlinaric.remotemanager.vault.VaultConflictException.class, local::save);
        local.recoverAfterFailedSave();
        assertTrue(local.connections().isEmpty());
        assertEquals("Changed externally", local.entries().getFirst().title());
    }

    @Test
    void workspaceFacadeReturnsSnapshotsAndRecoversFailedMutations() throws Exception {
        Path file = temp.resolve("workspace.kdbx");
        char[] master = "master".toCharArray();
        KdbxVault.create(file, master);
        KdbxVault vault = new KdbxVault(file);
        vault.unlock(master);
        try (VaultWorkspace workspace = new VaultWorkspace(vault)) {
            UUID folderId = workspace
                    .mutateAndSaveAsync(current -> current.createFolder(null, "Production"))
                    .join();
            assertEquals(
                    folderId, workspace.refresh().join().folders().getFirst().id());

            KdbxVault external = new KdbxVault(file);
            external.unlock(master);
            external.addEntry("External", "alice", "secret".toCharArray(), Map.of(), null, null);
            external.save();

            assertThrows(
                    com.mmlinaric.remotemanager.vault.VaultConflictException.class,
                    () -> workspace.mutateAndSave(current -> current.createFolder(null, "Unsaved")));
            assertEquals(
                    List.of("Production"),
                    workspace.snapshot().folders().stream()
                            .map(ConnectionFolder::name)
                            .toList());
        }
    }

    @Test
    void workspaceCreationRunsAsynchronouslyAndClearsSuppliedPasswords() throws Exception {
        Path file = temp.resolve("created-workspace.kdbx");
        char[] password = "master".toCharArray();

        try (VaultWorkspace workspace = VaultWorkspace.create(file, password).join()) {
            assertTrue(Files.exists(file));
            assertTrue(workspace.vault().isUnlocked());
            assertTrue(workspace.refresh().join().folders().isEmpty());
        }
        assertArrayEquals(new char[password.length], password);

        char[] rejectedPassword = "master".toCharArray();
        assertThrows(
                CompletionException.class,
                () -> VaultWorkspace.create(file, rejectedPassword).join());
        assertArrayEquals(new char[rejectedPassword.length], rejectedPassword);
    }
}
