package com.mmlinaric.remotemanager.vault;

import java.util.Arrays;
import java.util.Map;

/**
 * Values supplied when an identity is created or edited. Call {@link #clearSecrets()} once the
 * vault operation has completed.
 */
public final class IdentityDraft {
    private final String title;
    private final String username;
    private final char[] password;
    private final Map<String, String> fields;
    private final VaultAttachment attachment;

    public IdentityDraft(
            String title, String username, char[] password, Map<String, String> fields, VaultAttachment attachment) {
        this.title = title;
        this.username = username;
        this.password = password == null ? null : password.clone();
        this.fields = fields == null ? Map.of() : Map.copyOf(fields);
        this.attachment = attachment;
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

    public Map<String, String> fields() {
        return fields;
    }

    public VaultAttachment attachment() {
        return attachment;
    }

    /** Wipes password and attachment bytes owned by this draft. */
    public void clearSecrets() {
        if (password != null) Arrays.fill(password, '\0');
        if (attachment != null) attachment.clear();
    }
}
