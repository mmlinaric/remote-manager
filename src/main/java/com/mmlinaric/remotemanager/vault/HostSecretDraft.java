package com.mmlinaric.remotemanager.vault;

import java.util.Arrays;
import java.util.UUID;

/** A host-owned password that must not be exposed as a reusable identity. */
public final class HostSecretDraft {
    private final UUID id;
    private final UUID ownerHostId;
    private final String purpose;
    private final String title;
    private final String username;
    private final char[] password;

    public HostSecretDraft(UUID id, UUID ownerHostId, String purpose, String title, String username, char[] password) {
        this.id = id;
        this.ownerHostId = ownerHostId;
        this.purpose = purpose;
        this.title = title;
        this.username = username;
        this.password = password == null ? null : password.clone();
    }

    public UUID id() {
        return id;
    }

    public UUID ownerHostId() {
        return ownerHostId;
    }

    public String purpose() {
        return purpose;
    }

    public String title() {
        return title;
    }

    public String username() {
        return username;
    }

    public char[] password() {
        return password == null ? null : password.clone();
    }

    /** Wipes the password retained by this draft. */
    public void clearSecrets() {
        if (password != null) Arrays.fill(password, '\0');
    }
}
