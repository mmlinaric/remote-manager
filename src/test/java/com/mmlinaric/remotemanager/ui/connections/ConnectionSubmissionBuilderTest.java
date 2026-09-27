package com.mmlinaric.remotemanager.ui.connections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mmlinaric.remotemanager.model.AuthenticationType;
import com.mmlinaric.remotemanager.ui.vault.IdentityEditor;
import com.mmlinaric.remotemanager.vault.VaultEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectionSubmissionBuilderTest {
    @Test
    void requiresAPasswordForANewHostManagedSshCredential() {
        ConnectionSubmissionBuilder builder = new ConnectionSubmissionBuilder(Set.of(), Map.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> builder.build(input(null, true, null), new char[0], new char[0]));

        assertEquals("Enter an SSH password for this host.", error.getMessage());
    }

    @Test
    void includesOnlyTheNewIdentityUsedByTheHost() {
        UUID identityId = UUID.randomUUID();
        IdentityEditor.Change draft = new IdentityEditor.Change("Admin", "root", "secret".toCharArray(), null);
        Map<UUID, IdentityEditor.Change> drafts = new LinkedHashMap<>();
        drafts.put(identityId, draft);
        VaultEntry identity = new VaultEntry(identityId, "Admin", "root", Map.of(), List.of(), true);
        ConnectionSubmissionBuilder builder = new ConnectionSubmissionBuilder(Set.of(), drafts);

        ConnectionEditor.Submission submission = builder.build(input(identity, false, null), new char[0], new char[0]);

        assertEquals(identityId, submission.connection().sshCredentialEntryId());
        assertEquals(Map.of(identityId, draft), submission.newIdentities());
        assertEquals(Map.of(), submission.hostPasswords());
        draft.clear();
    }

    private static ConnectionSubmissionBuilder.Input input(
            VaultEntry sshIdentity, boolean directSsh, VaultEntry sudoIdentity) {
        return new ConnectionSubmissionBuilder.Input(
                null,
                "Server",
                "server.example",
                22,
                "admin",
                null,
                AuthenticationType.PASSWORD,
                directSsh,
                sshIdentity,
                null,
                false,
                sudoIdentity != null,
                sudoIdentity,
                null,
                null,
                "",
                "");
    }
}
