package com.mmlinaric.remotemanager.vault.kdbx;

import com.sun.jna.platform.win32.Secur32;
import com.sun.jna.platform.win32.Secur32Util;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class VaultFilePermissions {
    static final class PermissionException extends IOException {
        private PermissionException(String message) {
            super(message);
        }

        private PermissionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    enum Protection {
        OWNER_ONLY,
        UNSUPPORTED
    }

    record CreatedFile(Path path, Protection protection) {}

    private enum Scheme {
        POSIX,
        WINDOWS_ACL,
        UNSUPPORTED
    }

    private static final Set<PosixFilePermission> FILE_PERMISSIONS = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = PosixFilePermissions.fromString("rwx------");
    private static final Set<AclEntryPermission> ACL_PERMISSIONS = EnumSet.allOf(AclEntryPermission.class);

    private VaultFilePermissions() {}

    static CreatedFile createVaultFile(Path path) throws IOException {
        Path file = path.toAbsolutePath();
        Path parent = file.getParent();
        Scheme scheme = schemeFor(parent);
        createParentDirectories(parent, scheme);
        return switch (scheme) {
            case POSIX -> new CreatedFile(createPosixFile(file), Protection.OWNER_ONLY);
            case WINDOWS_ACL -> createAclFileOrFallback(file);
            case UNSUPPORTED -> new CreatedFile(Files.createFile(file), Protection.UNSUPPORTED);
        };
    }

    static Path createTemporaryFile(Path target, String prefix, String suffix) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        Scheme scheme = schemeFor(parent);
        if (scheme == Scheme.POSIX && Files.getPosixFilePermissions(target).equals(FILE_PERMISSIONS)) {
            Path temporary = Files.createTempFile(
                    parent, prefix, suffix, PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS));
            try {
                establishPosixPermissions(temporary, FILE_PERMISSIONS);
                return temporary;
            } catch (IOException | RuntimeException error) {
                deleteCreated(temporary, error);
                throw error;
            }
        }
        if (scheme == Scheme.WINDOWS_ACL) {
            OwnerOnlyAcl protection = ownerOnlyAcl(target);
            if (protection != null) {
                Path temporary = Files.createTempFile(parent, prefix, suffix, aclAttribute(protection.acl()));
                try {
                    verifyOwnerOnlyAcl(temporary, protection.owner(), protection.acl());
                    return temporary;
                } catch (IOException | RuntimeException error) {
                    deleteCreated(temporary, error);
                    throw error;
                }
            }
        }
        return Files.createTempFile(parent, prefix, suffix);
    }

    private static void createParentDirectories(Path parent, Scheme scheme) throws IOException {
        Deque<Path> missing = new ArrayDeque<>();
        Path current = parent;
        while (current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            missing.addFirst(current);
            current = current.getParent();
        }
        for (Path directory : missing) {
            try {
                if (scheme == Scheme.POSIX) {
                    Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS));
                    establishPosixPermissions(directory, DIRECTORY_PERMISSIONS);
                } else {
                    Files.createDirectory(directory);
                }
            } catch (FileAlreadyExistsException error) {
                if (!Files.isDirectory(directory)) throw error;
            }
        }
    }

    private static Path createPosixFile(Path file) throws IOException {
        boolean created = false;
        try {
            Files.createFile(file, PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS));
            created = true;
            establishPosixPermissions(file, FILE_PERMISSIONS);
            return file;
        } catch (IOException | RuntimeException error) {
            if (created) deleteCreated(file, error);
            throw error;
        }
    }

    private static CreatedFile createAclFileOrFallback(Path file) throws IOException {
        boolean created = false;
        try {
            UserPrincipal owner = currentWindowsUser(file);
            List<AclEntry> acl = ownerOnlyAcl(owner);
            Files.createFile(file, aclAttribute(acl));
            created = true;
            verifyOwnerOnlyAcl(file, owner, acl);
            return new CreatedFile(file, Protection.OWNER_ONLY);
        } catch (FileAlreadyExistsException error) {
            throw error;
        } catch (IOException | RuntimeException | LinkageError error) {
            if (created) {
                try {
                    Files.delete(file);
                } catch (IOException cleanupError) {
                    error.addSuppressed(cleanupError);
                    throw asIOException("Could not remove vault after ACL verification failed", error);
                }
            }
            return new CreatedFile(Files.createFile(file), Protection.UNSUPPORTED);
        }
    }

    private static void establishPosixPermissions(Path path, Set<PosixFilePermission> expected)
            throws PermissionException {
        try {
            if (!Files.getPosixFilePermissions(path).equals(expected)) {
                Files.setPosixFilePermissions(path, expected);
            }
            if (!Files.getPosixFilePermissions(path).equals(expected)) {
                throw new PermissionException("Could not establish owner-only POSIX permissions for " + path);
            }
        } catch (PermissionException error) {
            throw error;
        } catch (IOException | RuntimeException error) {
            throw new PermissionException("Could not establish owner-only POSIX permissions for " + path, error);
        }
    }

    private static UserPrincipal currentWindowsUser(Path path) throws IOException {
        String name = Secur32Util.getUserNameEx(Secur32.EXTENDED_NAME_FORMAT.NameSamCompatible);
        return path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(name);
    }

    private static List<AclEntry> ownerOnlyAcl(UserPrincipal owner) {
        return List.of(AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(ACL_PERMISSIONS)
                .build());
    }

    private static OwnerOnlyAcl ownerOnlyAcl(Path path) throws IOException {
        AclFileAttributeView view =
                Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) return null;
        UserPrincipal owner = view.getOwner();
        List<AclEntry> expected = ownerOnlyAcl(owner);
        return view.getAcl().equals(expected) ? new OwnerOnlyAcl(owner, expected) : null;
    }

    private static void verifyOwnerOnlyAcl(Path path, UserPrincipal owner, List<AclEntry> expected) throws IOException {
        AclFileAttributeView view =
                Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null || !view.getOwner().equals(owner) || !view.getAcl().equals(expected)) {
            throw new IOException("Could not establish an owner-only ACL for " + path);
        }
    }

    private static FileAttribute<List<AclEntry>> aclAttribute(List<AclEntry> acl) {
        return new FileAttribute<>() {
            @Override
            public String name() {
                return "acl:acl";
            }

            @Override
            public List<AclEntry> value() {
                return acl;
            }
        };
    }

    private static Scheme schemeFor(Path path) throws IOException {
        Path existing = path;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null) return Scheme.UNSUPPORTED;
        FileStore store = Files.getFileStore(existing);
        if (store.supportsFileAttributeView(PosixFileAttributeView.class)) return Scheme.POSIX;
        if (isWindows() && store.supportsFileAttributeView(AclFileAttributeView.class)) {
            return Scheme.WINDOWS_ACL;
        }
        return Scheme.UNSUPPORTED;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private static IOException asIOException(String message, Throwable cause) {
        return cause instanceof IOException io ? io : new IOException(message, cause);
    }

    private static void deleteCreated(Path path, Throwable error) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanupError) {
            error.addSuppressed(cleanupError);
        }
    }

    private record OwnerOnlyAcl(UserPrincipal owner, List<AclEntry> acl) {}
}
