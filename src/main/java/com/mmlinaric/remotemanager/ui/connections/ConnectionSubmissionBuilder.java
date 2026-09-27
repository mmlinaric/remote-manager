package com.mmlinaric.remotemanager.ui.connections;

import com.mmlinaric.remotemanager.model.AuthenticationType;
import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.ui.vault.IdentityEditor;
import com.mmlinaric.remotemanager.vault.VaultEntry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Validates editor values and turns them into a single vault save request. */
final class ConnectionSubmissionBuilder {
    private final Set<UUID> availableIdentities;
    private final Map<UUID, IdentityEditor.Change> drafts;

    ConnectionSubmissionBuilder(Set<UUID> availableIdentities, Map<UUID, IdentityEditor.Change> drafts) {
        this.availableIdentities = availableIdentities;
        this.drafts = drafts;
    }

    ConnectionEditor.Submission build(Input input, char[] sshSecret, char[] sudoSecret) {
        VaultEntry selectedSsh = input.sshIdentity();
        VaultEntry selectedSudo = input.sudoIdentity();
        boolean usesSshIdentity = input.authentication() == AuthenticationType.KDBX_PRIVATE_KEY
                || (input.authentication() == AuthenticationType.PASSWORD && !input.directSsh());
        if (input.directSsh() && input.sshDirectId() == null && sshSecret.length == 0) {
            throw new IllegalArgumentException("Enter an SSH password for this host.");
        }
        if (input.directSudo() && input.sudoDirectId() == null && sudoSecret.length == 0) {
            throw new IllegalArgumentException("Enter a sudo password for this host.");
        }
        requireIdentity(usesSshIdentity, selectedSsh, "SSH");
        requireIdentity(input.usesSudoIdentity(), selectedSudo, "sudo");
        requireExistingIdentity(usesSshIdentity, selectedSsh, "SSH");
        requireExistingIdentity(input.usesSudoIdentity(), selectedSudo, "sudo");
        if (input.authentication() == AuthenticationType.PASSWORD && usesSshIdentity && !selectedSsh.hasPassword()) {
            throw new IllegalArgumentException("This identity has no SSH password.");
        }
        if (input.usesSudoIdentity() && !selectedSudo.hasPassword()) {
            throw new IllegalArgumentException("The sudo identity has no password.");
        }
        if (input.authentication() == AuthenticationType.KDBX_PRIVATE_KEY && input.attachment() == null) {
            throw new IllegalArgumentException("Choose an identity with a private key attachment.");
        }

        Map<UUID, IdentityEditor.Change> usedDrafts = new LinkedHashMap<>();
        addDraft(usedDrafts, usesSshIdentity, selectedSsh);
        addDraft(usedDrafts, input.usesSudoIdentity(), selectedSudo);

        UUID hostId =
                input.current() == null ? UUID.randomUUID() : input.current().id();
        UUID sshId = input.directSsh()
                ? input.sshDirectId() == null ? UUID.randomUUID() : input.sshDirectId()
                : usesSshIdentity ? selectedSsh.id() : null;
        UUID sudoId = input.directSudo()
                ? input.sudoDirectId() == null ? UUID.randomUUID() : input.sudoDirectId()
                : input.usesSudoIdentity() ? selectedSudo.id() : null;
        Map<UUID, ConnectionEditor.HostPassword> hostPasswords = new LinkedHashMap<>();
        if (input.directSsh()) {
            hostPasswords.put(
                    sshId, new ConnectionEditor.HostPassword("ssh", sshSecret.length == 0 ? null : sshSecret));
        }
        if (input.directSudo()) {
            hostPasswords.put(
                    sudoId, new ConnectionEditor.HostPassword("sudo", sudoSecret.length == 0 ? null : sudoSecret));
        }

        Connection connection = new Connection(
                hostId,
                input.name(),
                input.hostname(),
                input.port(),
                input.username(),
                input.parentFolderId(),
                input.authentication(),
                sshId,
                sudoId,
                input.authentication() == AuthenticationType.KDBX_PRIVATE_KEY ? input.attachment() : null,
                input.authentication() == AuthenticationType.PRIVATE_KEY_FILE ? input.privateKeyFile() : null,
                input.notes(),
                input.current() == null ? 0 : input.current().sortOrder());
        return new ConnectionEditor.Submission(connection, usedDrafts, hostPasswords);
    }

    private void requireIdentity(boolean required, VaultEntry selected, String purpose) {
        if (required && selected == null) {
            throw new IllegalArgumentException("Choose or create an " + purpose + " identity.");
        }
    }

    private void requireExistingIdentity(boolean required, VaultEntry selected, String purpose) {
        if (required && !drafts.containsKey(selected.id()) && !availableIdentities.contains(selected.id())) {
            throw new IllegalArgumentException("The selected " + purpose + " identity is missing.");
        }
    }

    private void addDraft(Map<UUID, IdentityEditor.Change> usedDrafts, boolean used, VaultEntry selected) {
        if (used && drafts.containsKey(selected.id())) usedDrafts.put(selected.id(), drafts.get(selected.id()));
    }

    record Input(
            Connection current,
            String name,
            String hostname,
            int port,
            String username,
            UUID parentFolderId,
            AuthenticationType authentication,
            boolean directSsh,
            VaultEntry sshIdentity,
            UUID sshDirectId,
            boolean directSudo,
            boolean usesSudoIdentity,
            VaultEntry sudoIdentity,
            UUID sudoDirectId,
            String attachment,
            String privateKeyFile,
            String notes) {}
}
