package com.mmlinaric.remotemanager.vault;

/** Signals that the vault file changed outside this process while it was unlocked. */
public final class VaultConflictException extends VaultException {
    public VaultConflictException() {
        super("Vault changed on disk. Reload before saving.");
    }
}
