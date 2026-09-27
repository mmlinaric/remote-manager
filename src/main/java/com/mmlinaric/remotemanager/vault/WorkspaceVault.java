package com.mmlinaric.remotemanager.vault;

import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.model.ConnectionFolder;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * The complete vault contract used by the workspace. Implementations own the persistence format
 * and the lifetime of secrets retained for saving an unlocked vault.
 */
public interface WorkspaceVault extends Vault {
    Path path();

    List<VaultEntry> credentialEntries() throws VaultException;

    List<ConnectionFolder> folders() throws VaultException;

    List<Connection> connections() throws VaultException;

    UUID addIdentity(IdentityDraft draft) throws VaultException;

    UUID addIdentity(UUID id, IdentityDraft draft) throws VaultException;

    void updateIdentity(UUID id, IdentityDraft draft) throws VaultException;

    void deleteIdentity(UUID id) throws VaultException;

    void putHostSecret(HostSecretDraft draft) throws VaultException;

    UUID createFolder(UUID parentId, String name) throws VaultException;

    void renameFolder(UUID id, String name) throws VaultException;

    void deleteFolder(UUID id) throws VaultException;

    void putConnection(Connection connection) throws VaultException;

    void deleteConnection(UUID id) throws VaultException;

    void save() throws VaultException;

    void recoverAfterFailedSave();
}
