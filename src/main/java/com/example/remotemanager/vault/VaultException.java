package com.example.remotemanager.vault;

public class VaultException extends Exception {
  public VaultException(String message) {
    super(message);
  }

  public VaultException(String message, Throwable cause) {
    super(message, cause);
  }
}
