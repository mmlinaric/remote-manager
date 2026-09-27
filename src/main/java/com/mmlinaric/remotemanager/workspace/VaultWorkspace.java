package com.mmlinaric.remotemanager.workspace;

import com.mmlinaric.remotemanager.vault.WorkspaceVault;
import com.mmlinaric.remotemanager.vault.kdbx.KdbxVault;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Application facade for vault reads and durable mutations. It deliberately has no Swing
 * dependency, so callers can run it on a worker thread and test its failure behavior directly.
 */
public final class VaultWorkspace implements AutoCloseable {
    private final WorkspaceVault vault;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public VaultWorkspace(WorkspaceVault vault) {
        this.vault = vault;
    }

    /** Returns the vault port used by editors and SSH sessions. */
    public WorkspaceVault vault() {
        return vault;
    }

    public WorkspaceSnapshot snapshot() throws Exception {
        return new WorkspaceSnapshot(vault.folders(), vault.connections(), vault.entries(), vault.credentialEntries());
    }

    /** Reads the current workspace on its dedicated serialized worker. */
    public CompletableFuture<WorkspaceSnapshot> refresh() {
        return submit(this::snapshot);
    }

    /** Unlocks the vault without blocking the Swing event thread. */
    public CompletableFuture<Void> unlock(char[] password) {
        return submit(() -> {
            vault.unlock(password);
            return null;
        });
    }

    /** Runs one durable mutation without blocking the Swing event thread. */
    public CompletableFuture<UUID> mutateAndSaveAsync(Mutation mutation) {
        return submit(() -> mutateAndSave(mutation));
    }

    /** Schedules a lock after already queued vault work. */
    public void lock() {
        worker.execute(vault::lock);
    }

    /** Creates and unlocks a KDBX-backed workspace on its own serialized worker. */
    public static CompletableFuture<VaultWorkspace> create(Path path, char[] password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                KdbxVault.create(path, password);
                KdbxVault vault = new KdbxVault(path);
                vault.unlock(password);
                return new VaultWorkspace(vault);
            } catch (Exception error) {
                throw new RuntimeException(error);
            } finally {
                Arrays.fill(password, '\0');
            }
        });
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

    @Override
    public void close() {
        lock();
        worker.shutdown();
    }

    private <T> CompletableFuture<T> submit(ThrowingSupplier<T> operation) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return operation.get();
                    } catch (Exception error) {
                        throw new RuntimeException(error);
                    }
                },
                worker);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
