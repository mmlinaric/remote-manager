package com.mmlinaric.remotemanager.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mmlinaric.remotemanager.model.AuthenticationType;
import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.model.ConnectionFolder;
import com.mmlinaric.remotemanager.ui.connections.ConnectionEditor;
import com.mmlinaric.remotemanager.ui.vault.IdentityEditor;
import com.mmlinaric.remotemanager.vault.HostSecretDraft;
import com.mmlinaric.remotemanager.vault.IdentityDraft;
import com.mmlinaric.remotemanager.vault.VaultEntry;
import com.mmlinaric.remotemanager.vault.WorkspaceVault;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HostSubmissionPersistenceTest {
    @Test
    void savesPrerequisiteIdentityAndSecretBeforeTheirHost() throws Exception {
        UUID hostId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        UUID secretId = UUID.randomUUID();
        Connection host = new Connection(
                hostId,
                "Server",
                "server.example",
                22,
                "admin",
                null,
                AuthenticationType.PASSWORD,
                identityId,
                secretId,
                null,
                null,
                "",
                0);
        IdentityEditor.Change identity = new IdentityEditor.Change("SSH", "admin", "ssh".toCharArray(), null);
        ConnectionEditor.HostPassword password = new ConnectionEditor.HostPassword("sudo", "sudo".toCharArray());
        ConnectionEditor.Submission submission =
                new ConnectionEditor.Submission(host, Map.of(identityId, identity), Map.of(secretId, password));
        RecordingVault vault = new RecordingVault();

        try (HostSubmissionPersistence persistence = HostSubmissionPersistence.prepare(submission)) {
            persistence.saveTo(vault);
        }

        assertEquals(List.of("identity", "secret", "connection"), vault.operations);
        assertEquals(identityId, vault.identityId);
        assertEquals(secretId, vault.secretId);
        assertEquals(host, vault.connection);
        identity.clear();
        password.clear();
    }

    private static final class RecordingVault implements WorkspaceVault {
        private final List<String> operations = new ArrayList<>();
        private UUID identityId;
        private UUID secretId;
        private Connection connection;

        @Override
        public UUID addIdentity(UUID id, IdentityDraft draft) {
            operations.add("identity");
            identityId = id;
            return id;
        }

        @Override
        public void putHostSecret(HostSecretDraft draft) {
            operations.add("secret");
            secretId = draft.id();
        }

        @Override
        public void putConnection(Connection value) {
            operations.add("connection");
            connection = value;
        }

        @Override
        public Path path() {
            throw unsupported();
        }

        @Override
        public List<VaultEntry> credentialEntries() {
            throw unsupported();
        }

        @Override
        public List<ConnectionFolder> folders() {
            throw unsupported();
        }

        @Override
        public List<Connection> connections() {
            throw unsupported();
        }

        @Override
        public UUID addIdentity(IdentityDraft draft) {
            throw unsupported();
        }

        @Override
        public void updateIdentity(UUID id, IdentityDraft draft) {
            throw unsupported();
        }

        @Override
        public void deleteIdentity(UUID id) {
            throw unsupported();
        }

        @Override
        public UUID createFolder(UUID parentId, String name) {
            throw unsupported();
        }

        @Override
        public void renameFolder(UUID id, String name) {
            throw unsupported();
        }

        @Override
        public void deleteFolder(UUID id) {
            throw unsupported();
        }

        @Override
        public void deleteConnection(UUID id) {
            throw unsupported();
        }

        @Override
        public void save() {
            throw unsupported();
        }

        @Override
        public void recoverAfterFailedSave() {
            throw unsupported();
        }

        @Override
        public boolean isUnlocked() {
            throw unsupported();
        }

        @Override
        public List<VaultEntry> entries() {
            throw unsupported();
        }

        @Override
        public Optional<VaultEntry> getEntry(UUID id) {
            throw unsupported();
        }

        @Override
        public Optional<char[]> getPassword(UUID id) {
            throw unsupported();
        }

        @Override
        public Optional<byte[]> getAttachment(UUID id, String attachmentName) {
            throw unsupported();
        }

        @Override
        public void unlock(char[] masterPassword) {
            throw unsupported();
        }

        @Override
        public void lock() {
            throw unsupported();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException();
        }
    }
}
