package com.mmlinaric.remotemanager.vault;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only view of an unlocked vault, with secrets returned only for explicit credential lookups. */
public interface Vault {
    void unlock(char[] masterPassword) throws VaultException;

    void lock();

    boolean isUnlocked();

    List<VaultEntry> entries() throws VaultException;

    Optional<VaultEntry> getEntry(UUID entryId) throws VaultException;

    Optional<char[]> getPassword(UUID entryId) throws VaultException;

    Optional<byte[]> getAttachment(UUID entryId, String attachmentName) throws VaultException;
}
