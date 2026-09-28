package com.mmlinaric.remotemanager.vault.kdbx;

import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.model.ConnectionFolder;
import com.mmlinaric.remotemanager.vault.HostSecretDraft;
import com.mmlinaric.remotemanager.vault.IdentityDraft;
import com.mmlinaric.remotemanager.vault.VaultAttachment;
import com.mmlinaric.remotemanager.vault.VaultEntry;
import com.mmlinaric.remotemanager.vault.VaultException;
import com.mmlinaric.remotemanager.vault.WorkspaceVault;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.linguafranca.pwdb.kdbx.jackson.JacksonDatabase;

/** KeePassJava2 adapter for the app's KDBX4 workspace. */
public final class KdbxVault implements WorkspaceVault {
    private final Path path;
    private JacksonDatabase database;
    private byte[] loadedDigest;
    private char[] sessionPassword;

    public KdbxVault(Path path) {
        this.path = path.toAbsolutePath();
    }

    public enum CreationProtection {
        OWNER_ONLY,
        UNSUPPORTED
    }

    @Override
    public Path path() {
        return path;
    }

    public static void create(Path path, char[] masterPassword) throws VaultException {
        createWithProtectionStatus(path, masterPassword);
    }

    public static CreationProtection createWithProtectionStatus(Path path, char[] masterPassword)
            throws VaultException {
        if (Files.exists(path)) throw new VaultException("Vault file already exists");
        boolean created = false;
        try {
            VaultFilePermissions.CreatedFile secured = VaultFilePermissions.createVaultFile(path);
            created = true;
            JacksonDatabase fresh = new JacksonDatabase();
            try (OutputStream output = Files.newOutputStream(secured.path(), StandardOpenOption.WRITE)) {
                KdbxFileStore.write(fresh, masterPassword, output);
            }
            return secured.protection() == VaultFilePermissions.Protection.OWNER_ONLY
                    ? CreationProtection.OWNER_ONLY
                    : CreationProtection.UNSUPPORTED;
        } catch (VaultFilePermissions.PermissionException error) {
            throw new VaultException(error.getMessage(), error);
        } catch (Exception error) {
            if (created)
                try {
                    Files.deleteIfExists(path.toAbsolutePath());
                } catch (IOException ignored) {
                }
            throw new VaultException("Could not create KeePass vault", error);
        }
    }

    @Override
    public synchronized void unlock(char[] masterPassword) throws VaultException {
        try {
            JacksonDatabase loaded = KdbxFileStore.read(path, masterPassword);
            byte[] digest = KdbxFileStore.digest(path);
            database = loaded;
            loadedDigest = digest;
            if (sessionPassword != null) Arrays.fill(sessionPassword, '\0');
            sessionPassword = masterPassword.clone();
        } catch (Exception error) {
            lock();
            throw new VaultException("Could not unlock KeePass vault", error);
        }
    }

    @Override
    public synchronized void lock() {
        database = null;
        if (loadedDigest != null) Arrays.fill(loadedDigest, (byte) 0);
        loadedDigest = null;
        if (sessionPassword != null) Arrays.fill(sessionPassword, '\0');
        sessionPassword = null;
    }

    @Override
    public synchronized boolean isUnlocked() {
        return database != null;
    }

    @Override
    public synchronized List<VaultEntry> entries() throws VaultException {
        requireUnlocked();
        return entryReader().identities();
    }

    public synchronized List<VaultEntry> credentialEntries() throws VaultException {
        requireUnlocked();
        return entryReader().credentials();
    }

    public static boolean isHostSecret(VaultEntry entry, UUID ownerHostId, String purpose) {
        return KdbxHostSecretStore.belongsTo(entry, ownerHostId, purpose);
    }

    @Override
    public synchronized Optional<VaultEntry> getEntry(UUID id) throws VaultException {
        requireUnlocked();
        return entryReader().entry(id);
    }

    @Override
    public synchronized Optional<char[]> getPassword(UUID id) throws VaultException {
        requireUnlocked();
        return entryReader().password(id);
    }

    @Override
    public synchronized Optional<byte[]> getAttachment(UUID id, String attachmentName) throws VaultException {
        requireUnlocked();
        return entryReader().attachment(id, attachmentName);
    }

    public synchronized UUID addEntry(
            String title,
            String username,
            char[] password,
            Map<String, String> fields,
            String attachmentName,
            byte[] attachment)
            throws VaultException {
        return addEntry(UUID.randomUUID(), title, username, password, fields, attachmentName, attachment);
    }

    @Override
    public synchronized UUID addIdentity(IdentityDraft draft) throws VaultException {
        return addIdentity(UUID.randomUUID(), draft);
    }

    /** Stores a draft under a caller-supplied ID when one is needed before persistence. */
    public synchronized UUID addIdentity(UUID id, IdentityDraft draft) throws VaultException {
        VaultAttachment attachment = draft.attachment();
        char[] password = draft.password();
        byte[] bytes = attachment == null ? null : attachment.bytes();
        try {
            return addEntry(
                    id,
                    draft.title(),
                    draft.username(),
                    password,
                    draft.fields(),
                    attachment == null ? null : attachment.name(),
                    bytes);
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (bytes != null) Arrays.fill(bytes, (byte) 0);
        }
    }

    public synchronized UUID addEntry(
            UUID id,
            String title,
            String username,
            char[] password,
            Map<String, String> fields,
            String attachmentName,
            byte[] attachment)
            throws VaultException {
        requireUnlocked();
        return identityStore().add(id, title, username, password, fields, attachmentName, attachment);
    }

    public synchronized void deleteIdentity(UUID id) throws VaultException {
        requireUnlocked();
        identityStore().delete(id);
    }

    @Override
    public synchronized void putHostSecret(HostSecretDraft draft) throws VaultException {
        char[] password = draft.password();
        try {
            putHostSecret(draft.id(), draft.ownerHostId(), draft.purpose(), draft.title(), draft.username(), password);
        } finally {
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    public synchronized void putHostSecret(
            UUID id, UUID ownerHostId, String purpose, String title, String username, char[] password)
            throws VaultException {
        requireUnlocked();
        hostSecretStore().put(id, ownerHostId, purpose, title, username, password);
    }

    public synchronized List<ConnectionFolder> folders() throws VaultException {
        requireUnlocked();
        return connectionStore().folders();
    }

    public synchronized List<Connection> connections() throws VaultException {
        requireUnlocked();
        return connectionStore().connections();
    }

    public synchronized UUID createFolder(UUID parentId, String name) throws VaultException {
        requireUnlocked();
        return connectionStore().createFolder(parentId, name);
    }

    public synchronized void renameFolder(UUID id, String name) throws VaultException {
        requireUnlocked();
        connectionStore().renameFolder(id, name);
    }

    public synchronized void deleteFolder(UUID id) throws VaultException {
        requireUnlocked();
        connectionStore().deleteFolder(id);
    }

    public synchronized void putConnection(Connection connection) throws VaultException {
        requireUnlocked();
        connectionStore().put(connection);
    }

    public synchronized void deleteConnection(UUID id) throws VaultException {
        requireUnlocked();
        connectionStore().delete(id);
    }

    public synchronized void save() throws VaultException {
        if (sessionPassword == null) throw new VaultException("Vault is locked");
        save(sessionPassword);
    }

    public synchronized void recoverAfterFailedSave() {
        if (sessionPassword == null) return;
        char[] password = sessionPassword.clone();
        try {
            unlock(password);
        } catch (VaultException error) {
            lock();
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public synchronized void updateEntry(
            UUID id,
            String title,
            String username,
            char[] password,
            Map<String, String> fields,
            String attachmentName,
            byte[] attachment)
            throws VaultException {
        requireUnlocked();
        identityStore().update(id, title, username, password, fields, attachmentName, attachment);
    }

    @Override
    public synchronized void updateIdentity(UUID id, IdentityDraft draft) throws VaultException {
        VaultAttachment attachment = draft.attachment();
        char[] password = draft.password();
        byte[] bytes = attachment == null ? null : attachment.bytes();
        try {
            updateEntry(
                    id,
                    draft.title(),
                    draft.username(),
                    password,
                    draft.fields(),
                    attachment == null ? null : attachment.name(),
                    bytes);
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (bytes != null) Arrays.fill(bytes, (byte) 0);
        }
    }

    public synchronized void save(char[] masterPassword) throws VaultException {
        requireUnlocked();
        KdbxVaultPersistence.SavedDatabase saved =
                new KdbxVaultPersistence(path).save(database, masterPassword, loadedDigest);
        database = saved.database();
        loadedDigest = saved.digest();
    }

    private KdbxEntryReader entryReader() throws VaultException {
        requireUnlocked();
        return new KdbxEntryReader(database);
    }

    private KdbxConnectionStore connectionStore() throws VaultException {
        requireUnlocked();
        return new KdbxConnectionStore(database);
    }

    private KdbxIdentityStore identityStore() throws VaultException {
        requireUnlocked();
        return new KdbxIdentityStore(database, this::connections);
    }

    private KdbxHostSecretStore hostSecretStore() throws VaultException {
        requireUnlocked();
        return new KdbxHostSecretStore(database);
    }

    private void requireUnlocked() throws VaultException {
        if (database == null) throw new VaultException("Vault is locked");
    }
}
