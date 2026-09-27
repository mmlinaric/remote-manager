package com.mmlinaric.remotemanager.workspace;

import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.ui.connections.ConnectionEditor;
import com.mmlinaric.remotemanager.ui.vault.IdentityEditor;
import com.mmlinaric.remotemanager.vault.HostSecretDraft;
import com.mmlinaric.remotemanager.vault.IdentityDraft;
import com.mmlinaric.remotemanager.vault.VaultAttachment;
import com.mmlinaric.remotemanager.vault.WorkspaceVault;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Owns secure copies of an editor submission until its identities, secrets, and host are saved together. */
public final class HostSubmissionPersistence implements AutoCloseable {
    private final Connection connection;
    private final Map<UUID, IdentityEditor.Change> identities;
    private final Map<UUID, ConnectionEditor.HostPassword> hostPasswords;

    private HostSubmissionPersistence(
            Connection connection,
            Map<UUID, IdentityEditor.Change> identities,
            Map<UUID, ConnectionEditor.HostPassword> hostPasswords) {
        this.connection = connection;
        this.identities = identities;
        this.hostPasswords = hostPasswords;
    }

    /** Clones all secret-bearing editor values before the asynchronous vault operation begins. */
    public static HostSubmissionPersistence prepare(ConnectionEditor.Submission submission) {
        Map<UUID, IdentityEditor.Change> identities = new LinkedHashMap<>();
        submission
                .newIdentities()
                .forEach((id, change) -> identities.put(
                        id,
                        new IdentityEditor.Change(
                                change.title(),
                                change.username(),
                                change.password() == null
                                        ? null
                                        : change.password().clone(),
                                change.attachmentPath())));
        Map<UUID, ConnectionEditor.HostPassword> hostPasswords = new LinkedHashMap<>();
        submission
                .hostPasswords()
                .forEach((id, change) -> hostPasswords.put(
                        id,
                        new ConnectionEditor.HostPassword(
                                change.purpose(),
                                change.password() == null
                                        ? null
                                        : change.password().clone())));
        return new HostSubmissionPersistence(submission.connection(), identities, hostPasswords);
    }

    public Connection connection() {
        return connection;
    }

    /** Writes all prerequisite identities and passwords before the host that references them. */
    public void saveTo(WorkspaceVault vault) throws Exception {
        for (Map.Entry<UUID, IdentityEditor.Change> item : identities.entrySet()) {
            saveIdentity(vault, item.getKey(), item.getValue());
        }
        for (Map.Entry<UUID, ConnectionEditor.HostPassword> item : hostPasswords.entrySet()) {
            saveHostPassword(vault, item.getKey(), item.getValue());
        }
        vault.putConnection(connection);
    }

    @Override
    public void close() {
        identities.values().forEach(IdentityEditor.Change::clear);
        hostPasswords.values().forEach(ConnectionEditor.HostPassword::clear);
    }

    private void saveIdentity(WorkspaceVault vault, UUID id, IdentityEditor.Change change) throws Exception {
        byte[] attachment = change.attachmentPath() == null ? null : Files.readAllBytes(change.attachmentPath());
        IdentityDraft identity = new IdentityDraft(
                change.title(),
                change.username(),
                change.password(),
                Map.of(),
                new VaultAttachment(change.attachmentName(), attachment));
        try {
            vault.addIdentity(id, identity);
        } finally {
            identity.clearSecrets();
            if (attachment != null) Arrays.fill(attachment, (byte) 0);
        }
    }

    private void saveHostPassword(WorkspaceVault vault, UUID id, ConnectionEditor.HostPassword change)
            throws Exception {
        HostSecretDraft secret = new HostSecretDraft(
                id,
                connection.id(),
                change.purpose(),
                connection.name() + ("ssh".equals(change.purpose()) ? " SSH password" : " sudo password"),
                connection.username(),
                change.password());
        try {
            vault.putHostSecret(secret);
        } finally {
            secret.clearSecrets();
        }
    }
}
