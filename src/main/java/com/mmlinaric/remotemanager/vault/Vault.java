package com.mmlinaric.remotemanager.vault;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Vault {
  void unlock(char[] masterPassword) throws VaultException;

  void lock();

  boolean isUnlocked();

  List<VaultEntry> entries() throws VaultException;

  Optional<VaultEntry> getEntry(UUID entryId) throws VaultException;

  Optional<char[]> getPassword(UUID entryId) throws VaultException;

  Optional<byte[]> getAttachment(UUID entryId, String attachmentName) throws VaultException;
}
