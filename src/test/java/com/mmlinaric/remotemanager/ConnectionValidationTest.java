package com.mmlinaric.remotemanager;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mmlinaric.remotemanager.model.AuthenticationType;
import com.mmlinaric.remotemanager.model.Connection;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectionValidationTest {
    @Test
    void requiresAValidPortAndCredentialForVaultKeyAuthentication() {
        assertThrows(IllegalArgumentException.class, () -> connection(0, UUID.randomUUID(), "id_ed25519"));
        assertThrows(IllegalArgumentException.class, () -> connection(22, null, "id_ed25519"));
        assertThrows(IllegalArgumentException.class, () -> connection(22, UUID.randomUUID(), null));
    }

    private static Connection connection(int port, UUID credentialId, String attachment) {
        return new Connection(
                UUID.randomUUID(),
                "pve1",
                "10.0.0.10",
                port,
                "mario",
                null,
                AuthenticationType.KDBX_PRIVATE_KEY,
                credentialId,
                null,
                attachment,
                null,
                "",
                0);
    }
}
