package com.mmlinaric.remotemanager.vault.kdbx;

import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.*;

import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.model.ConnectionFolder;
import com.mmlinaric.remotemanager.vault.HostSecretDraft;
import com.mmlinaric.remotemanager.vault.IdentityDraft;
import com.mmlinaric.remotemanager.vault.VaultAttachment;
import com.mmlinaric.remotemanager.vault.VaultConflictException;
import com.mmlinaric.remotemanager.vault.VaultEntry;
import com.mmlinaric.remotemanager.vault.VaultException;
import com.mmlinaric.remotemanager.vault.WorkspaceVault;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    @Override
    public Path path() {
        return path;
    }

    public static void create(Path path, char[] masterPassword) throws VaultException {
        if (Files.exists(path)) throw new VaultException("Vault file already exists");
        boolean created = false;
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            JacksonDatabase fresh = new JacksonDatabase();
            try (OutputStream output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
                created = true;
                KdbxFileStore.write(fresh, masterPassword, output);
            }
        } catch (Exception error) {
            if (created)
                try {
                    Files.deleteIfExists(path);
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

    private KdbxManagedGroups groups() throws VaultException {
        requireUnlocked();
        return new KdbxManagedGroups(database);
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
        try {
            verifyUnchanged();
            verifyMasterPassword(masterPassword);
            Map<String, String> before = KdbxFileStore.snapshot(database);
            Path temporary = Files.createTempFile(path.getParent(), ".remote-manager-", ".kdbx");
            try {
                try (OutputStream output = Files.newOutputStream(temporary)) {
                    KdbxFileStore.write(database, masterPassword, output);
                }
                JacksonDatabase saved = KdbxFileStore.read(temporary, masterPassword);
                if (!before.equals(KdbxFileStore.snapshot(saved)))
                    throw new VaultException("KeePass vault changed during serialization");
                verifyUnchanged();
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                database = saved;
                loadedDigest = KdbxFileStore.digest(path);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (VaultConflictException error) {
            throw error;
        } catch (Exception error) {
            throw new VaultException("Could not save KeePass vault", error);
        }
    }

    private KdbxEntries entryStore() throws VaultException {
        requireUnlocked();
        return new KdbxEntries(database);
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

    private void verifyUnchanged() throws Exception {
        if (!java.security.MessageDigest.isEqual(loadedDigest, KdbxFileStore.digest(path)))
            throw new VaultConflictException();
    }

    private void verifyMasterPassword(char[] masterPassword) throws VaultException {
        try {
            KdbxFileStore.read(path, masterPassword);
        } catch (Exception error) {
            throw new VaultException("The vault master password is incorrect", error);
        }
    }
}
