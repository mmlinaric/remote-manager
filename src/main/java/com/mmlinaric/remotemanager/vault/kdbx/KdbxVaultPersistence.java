package com.mmlinaric.remotemanager.vault.kdbx;

import com.mmlinaric.remotemanager.vault.VaultConflictException;
import com.mmlinaric.remotemanager.vault.VaultException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import org.linguafranca.pwdb.kdbx.jackson.JacksonDatabase;

/** Verifies and atomically persists one KDBX database without changing the active vault session. */
final class KdbxVaultPersistence {
    private final Path path;

    KdbxVaultPersistence(Path path) {
        this.path = path;
    }

    SavedDatabase save(JacksonDatabase database, char[] masterPassword, byte[] loadedDigest) throws VaultException {
        try {
            verifyUnchanged(loadedDigest);
            verifyMasterPassword(masterPassword);
            Map<String, String> before = KdbxFileStore.snapshot(database);
            Path temporary = VaultFilePermissions.createTemporaryFile(path, ".remote-manager-", ".kdbx");
            try {
                try (OutputStream output = Files.newOutputStream(temporary)) {
                    KdbxFileStore.write(database, masterPassword, output);
                }
                JacksonDatabase saved = KdbxFileStore.read(temporary, masterPassword);
                if (!before.equals(KdbxFileStore.snapshot(saved))) {
                    throw new VaultException("KeePass vault changed during serialization");
                }
                verifyUnchanged(loadedDigest);
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return new SavedDatabase(saved, KdbxFileStore.digest(path));
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (VaultConflictException error) {
            throw error;
        } catch (Exception error) {
            throw new VaultException("Could not save KeePass vault", error);
        }
    }

    private void verifyUnchanged(byte[] loadedDigest) throws Exception {
        if (!java.security.MessageDigest.isEqual(loadedDigest, KdbxFileStore.digest(path))) {
            throw new VaultConflictException();
        }
    }

    private void verifyMasterPassword(char[] masterPassword) throws VaultException {
        try {
            KdbxFileStore.read(path, masterPassword);
        } catch (Exception error) {
            throw new VaultException("The vault master password is incorrect", error);
        }
    }

    record SavedDatabase(JacksonDatabase database, byte[] digest) {}
}
