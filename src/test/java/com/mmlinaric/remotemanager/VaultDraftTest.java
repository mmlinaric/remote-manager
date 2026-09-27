package com.mmlinaric.remotemanager;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.mmlinaric.remotemanager.vault.HostSecretDraft;
import com.mmlinaric.remotemanager.vault.IdentityDraft;
import com.mmlinaric.remotemanager.vault.VaultAttachment;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VaultDraftTest {
    @Test
    void identityDraftOwnsAndClearsSecretCopies() {
        char[] password = "secret".toCharArray();
        byte[] attachmentBytes = {1, 2, 3};
        VaultAttachment attachment = new VaultAttachment("id_ed25519", attachmentBytes);
        IdentityDraft draft = new IdentityDraft("SSH", "alice", password, Map.of(), attachment);

        password[0] = 'X';
        attachmentBytes[0] = 9;
        assertArrayEquals("secret".toCharArray(), draft.password());
        assertArrayEquals(new byte[] {1, 2, 3}, attachment.bytes());

        draft.clearSecrets();
        assertArrayEquals(new char[] {0, 0, 0, 0, 0, 0}, draft.password());
        assertArrayEquals(new byte[] {0, 0, 0}, attachment.bytes());
    }

    @Test
    void hostSecretDraftOwnsAndClearsPasswordCopy() {
        char[] password = "sudo".toCharArray();
        HostSecretDraft draft = new HostSecretDraft(
                UUID.randomUUID(), UUID.randomUUID(), "sudo", "Host sudo password", "alice", password);

        password[0] = 'X';
        assertArrayEquals("sudo".toCharArray(), draft.password());

        draft.clearSecrets();
        assertArrayEquals(new char[] {0, 0, 0, 0}, draft.password());
    }
}
