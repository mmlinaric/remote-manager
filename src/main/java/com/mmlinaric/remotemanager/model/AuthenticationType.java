package com.mmlinaric.remotemanager.model;

public enum AuthenticationType {
    KDBX_PRIVATE_KEY("KeePass private key"),
    SSH_AGENT("SSH agent"),
    PRIVATE_KEY_FILE("Private key file"),
    PASSWORD("KeePass password");

    private final String label;

    AuthenticationType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
