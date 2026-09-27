package com.mmlinaric.remotemanager.model;

import java.util.UUID;

public record Connection(
        UUID id,
        String name,
        String hostname,
        int port,
        String username,
        UUID parentFolderId,
        AuthenticationType authenticationType,
        UUID sshCredentialEntryId,
        UUID sudoCredentialEntryId,
        String privateKeyAttachmentName,
        String privateKeyFilePath,
        String notes,
        int sortOrder) {
    public Connection {
        if (id == null
                || name == null
                || name.isBlank()
                || hostname == null
                || hostname.isBlank()
                || username == null
                || username.isBlank()
                || authenticationType == null
                || port < 1
                || port > 65535) {
            throw new IllegalArgumentException("Name, hostname, username, authentication and port are required");
        }
        if ((authenticationType == AuthenticationType.KDBX_PRIVATE_KEY
                        || authenticationType == AuthenticationType.PASSWORD)
                && sshCredentialEntryId == null) {
            throw new IllegalArgumentException("A KeePass SSH credential is required");
        }
        if (authenticationType == AuthenticationType.KDBX_PRIVATE_KEY
                && (privateKeyAttachmentName == null || privateKeyAttachmentName.isBlank())) {
            throw new IllegalArgumentException("A private key attachment is required");
        }
        if (authenticationType == AuthenticationType.PRIVATE_KEY_FILE
                && (privateKeyFilePath == null || privateKeyFilePath.isBlank())) {
            throw new IllegalArgumentException("A private key file is required");
        }
        notes = notes == null ? "" : notes;
    }

    @Override
    public String toString() {
        return name;
    }
}
