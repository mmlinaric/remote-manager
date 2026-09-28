package com.mmlinaric.remotemanager;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mmlinaric.remotemanager.vault.VaultException;
import com.mmlinaric.remotemanager.vault.kdbx.KdbxVault;
import com.mmlinaric.remotemanager.workspace.VaultWorkspace;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KdbxVaultPermissionsTest {
    @TempDir
    Path temp;

    @Test
    void createsPosixVaultAndNewDirectoriesWithOwnerOnlyPermissions() throws Exception {
        FileStore store = Files.getFileStore(temp);
        Assumptions.assumeTrue(store.supportsFileAttributeView(PosixFileAttributeView.class));
        var existingPermissions = Files.getPosixFilePermissions(temp);
        Path parent = temp.resolve("new").resolve("nested");
        Path file = parent.resolve("vault.kdbx");

        assertEquals(
                KdbxVault.CreationProtection.OWNER_ONLY,
                KdbxVault.createWithProtectionStatus(file, "master".toCharArray()));

        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(parent));
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(parent.getParent()));
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file));
        assertEquals(existingPermissions, Files.getPosixFilePermissions(temp));

        KdbxVault vault = new KdbxVault(file);
        vault.unlock("master".toCharArray());
        vault.createFolder(null, "Saved");
        vault.save();
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file));
    }

    @Test
    void createsWindowsVaultWithAnOwnerOnlyAcl() throws Exception {
        FileStore store = Files.getFileStore(temp);
        Assumptions.assumeTrue(
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"));
        Assumptions.assumeFalse(store.supportsFileAttributeView(PosixFileAttributeView.class));
        Assumptions.assumeTrue(store.supportsFileAttributeView(AclFileAttributeView.class));
        AclFileAttributeView parentView = Files.getFileAttributeView(temp, AclFileAttributeView.class);
        List<AclEntry> parentAcl = parentView.getAcl();
        Path file = temp.resolve("vault.kdbx");

        assertEquals(
                KdbxVault.CreationProtection.OWNER_ONLY,
                KdbxVault.createWithProtectionStatus(file, "master".toCharArray()));
        assertOwnerOnlyAcl(file);
        assertEquals(parentAcl, parentView.getAcl());

        KdbxVault vault = new KdbxVault(file);
        vault.unlock("master".toCharArray());
        vault.createFolder(null, "Saved");
        vault.save();
        assertOwnerOnlyAcl(file);
    }

    @Test
    void reportsUnsupportedProtectionWithoutRejectingCreation() throws Exception {
        Path archive = temp.resolve("unsupported.zip");
        URI uri = URI.create("jar:" + archive.toUri());
        char[] password = "master".toCharArray();
        try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
            Path file = fileSystem.getPath("/vault.kdbx");
            FileStore store = Files.getFileStore(fileSystem.getPath("/"));
            assertFalse(store.supportsFileAttributeView(PosixFileAttributeView.class));
            assertFalse(store.supportsFileAttributeView(AclFileAttributeView.class));

            VaultWorkspace.CreationResult result =
                    VaultWorkspace.createWithProtectionStatus(file, password).join();
            try (VaultWorkspace workspace = result.workspace()) {
                assertEquals(KdbxVault.CreationProtection.UNSUPPORTED, result.protection());
                assertTrue(Files.exists(file));
                assertTrue(workspace.vault().isUnlocked());
            }
        }
        assertArrayEquals(new char[password.length], password);
    }

    @Test
    void doesNotOverwriteAnExistingVault() throws Exception {
        Path file = temp.resolve("existing.kdbx");
        byte[] existing = "existing".getBytes();
        Files.write(file, existing);

        VaultException error = assertThrows(
                VaultException.class, () -> KdbxVault.createWithProtectionStatus(file, "master".toCharArray()));

        assertEquals("Vault file already exists", error.getMessage());
        assertArrayEquals(existing, Files.readAllBytes(file));
    }

    @Test
    void removesTheVaultFileWhenSerializationFails() {
        Path file = temp.resolve("failed.kdbx");

        assertThrows(VaultException.class, () -> KdbxVault.createWithProtectionStatus(file, null));

        assertFalse(Files.exists(file));
    }

    private static void assertOwnerOnlyAcl(Path file) throws Exception {
        AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
        UserPrincipal owner = view.getOwner();
        AclEntry expected = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .build();
        assertEquals(List.of(expected), view.getAcl());
    }
}
