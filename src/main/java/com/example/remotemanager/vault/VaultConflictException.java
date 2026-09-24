package com.example.remotemanager.vault;

public final class VaultConflictException extends VaultException {
  public VaultConflictException() {
    super("Vault changed on disk. Reload before saving.");
  }
}
