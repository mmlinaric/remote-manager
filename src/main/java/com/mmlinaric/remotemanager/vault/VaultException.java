package com.mmlinaric.remotemanager.vault;

/** Signals a vault format, access, or persistence failure that callers can present to the user. */
public class VaultException extends Exception {
    public VaultException(String message) {
        super(message);
    }

    public VaultException(String message, Throwable cause) {
        super(message, cause);
    }
}
