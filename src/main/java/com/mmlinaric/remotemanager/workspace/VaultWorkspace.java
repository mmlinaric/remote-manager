package com.mmlinaric.remotemanager.workspace;

import com.mmlinaric.remotemanager.vault.WorkspaceVault;
import java.util.UUID;

/**
 * Application facade for vault reads and durable mutations. It deliberately has no Swing
 * dependency, so callers can run it on a worker thread and test its failure behavior directly.
 */
public final class VaultWorkspace {
    private final WorkspaceVault vault;

    public VaultWorkspace(WorkspaceVault vault) {
        this.vault = vault;
    }

    public WorkspaceSnapshot snapshot() throws Exception {
        return new WorkspaceSnapshot(vault.folders(), vault.connections(), vault.entries(), vault.credentialEntries());
    }

    /**
     * Performs one logical mutation and saves it. If persistence fails, reloads the vault to discard
     * in-memory changes that were not written to disk.
     */
    public UUID mutateAndSave(Mutation mutation) throws Exception {
        try {
            UUID revealId = mutation.apply(vault);
            vault.save();
            return revealId;
        } catch (Exception error) {
            vault.recoverAfterFailedSave();
            throw error;
        }
    }

    @FunctionalInterface
    public interface Mutation {
        UUID apply(WorkspaceVault vault) throws Exception;
    }
}
