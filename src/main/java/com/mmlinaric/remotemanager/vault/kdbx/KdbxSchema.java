package com.mmlinaric.remotemanager.vault.kdbx;

/**
 * Remote Manager's private KeePass metadata vocabulary. These keys are part of the persisted KDBX
 * compatibility contract and must remain stable once released.
 */
final class KdbxSchema {
    static final String PREFIX = "RemoteManager.";
    static final String ROLE = PREFIX + "Role";
    static final String ID = PREFIX + "Id";
    static final String HOST_ROOT = "hosts-root";
    static final String IDENTITY_ROOT = "identities-root";
    static final String HOST_SECRET_ROOT = "host-secrets-root";
    static final String MARKER = "group-marker";
    static final String HOST = "host";
    static final String HOST_SECRET = "host-secret";
    static final String SECRET_OWNER = PREFIX + "OwnerHostId";
    static final String SECRET_PURPOSE = PREFIX + "SecretPurpose";

    private KdbxSchema() {}
}
