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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.linguafranca.pwdb.kdbx.jackson.JacksonDatabase;
import org.linguafranca.pwdb.kdbx.jackson.JacksonEntry;
import org.linguafranca.pwdb.kdbx.jackson.JacksonGroup;

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
        KdbxEntries entryStore = entryStore();
        return entryStore.all().stream()
                .filter(entry -> !KdbxEntries.hasRole(entry, HOST)
                        && !KdbxEntries.hasRole(entry, HOST_SECRET)
                        && !KdbxEntries.hasRole(entry, MARKER))
                .map(entryStore::summary)
                .sorted((a, b) -> a.toString().compareToIgnoreCase(b.toString()))
                .toList();
    }

    public synchronized List<VaultEntry> credentialEntries() throws VaultException {
        KdbxEntries entryStore = entryStore();
        return entryStore.all().stream()
                .filter(entry -> !KdbxEntries.hasRole(entry, HOST) && !KdbxEntries.hasRole(entry, MARKER))
                .map(entryStore::summary)
                .toList();
    }

    public static boolean isHostSecret(VaultEntry entry, UUID ownerHostId, String purpose) {
        return entry != null
                && HOST_SECRET.equals(entry.fields().get(ROLE))
                && ownerHostId.toString().equals(entry.fields().get(SECRET_OWNER))
                && purpose.equals(entry.fields().get(SECRET_PURPOSE));
    }

    @Override
    public synchronized Optional<VaultEntry> getEntry(UUID id) throws VaultException {
        KdbxEntries entryStore = entryStore();
        return entryStore.find(id).map(entryStore::summary);
    }

    @Override
    public synchronized Optional<char[]> getPassword(UUID id) throws VaultException {
        return entryStore()
                .find(id)
                .map(entry -> entry.getPropertyValue("Password"))
                .filter(value -> value != null)
                .map(value -> value.getValueAsChars())
                .filter(value -> value.length != 0);
    }

    @Override
    public synchronized Optional<byte[]> getAttachment(UUID id, String attachmentName) throws VaultException {
        JacksonEntry entry = entryStore().find(id).orElseThrow(() -> new VaultException("KeePass entry is missing"));
        try {
            return Optional.ofNullable(entry.getBinaryProperty(attachmentName));
        } catch (Exception error) {
            throw new VaultException("Could not read KeePass attachment", error);
        }
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
        if (entryStore().find(id).isPresent()) throw new VaultException("Identity ID already exists");
        KdbxEntries.rejectReservedFields(fields);
        JacksonEntry entry = database.newEntry();
        entry.setProperty(ID, id.toString());
        entryStore().update(entry, title, username, password, fields, attachmentName, attachment);
        groups().getOrCreateRoot(IDENTITY_ROOT, "Remote Manager Identities").addEntry(entry);
        return id;
    }

    public synchronized void deleteIdentity(UUID id) throws VaultException {
        JacksonEntry entry = entryStore().find(id).orElseThrow(() -> new VaultException("Identity is missing"));
        if (KdbxEntries.hasRole(entry, HOST)
                || KdbxEntries.hasRole(entry, HOST_SECRET)
                || KdbxEntries.hasRole(entry, MARKER)) throw new VaultException("Identity is missing");
        for (Connection connection : connections()) {
            if (id.equals(connection.sshCredentialEntryId()) || id.equals(connection.sudoCredentialEntryId()))
                throw new VaultException("Identity is used by host: " + connection.name());
        }
        entry.getParent().removeEntry(entry);
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
        if (!"ssh".equals(purpose) && !"sudo".equals(purpose))
            throw new VaultException("Invalid host password purpose");
        JacksonEntry entry = entryStore().find(id).orElse(null);
        if (entry == null) {
            if (password == null || password.length == 0) throw new VaultException("Enter a password for this host");
            entry = database.newEntry();
            entry.setProperty(ID, id.toString());
            entry.setProperty(ROLE, HOST_SECRET);
            entry.setProperty(SECRET_OWNER, ownerHostId.toString());
            entry.setProperty(SECRET_PURPOSE, purpose);
            entryStore().update(entry, title, username, password, null, null, null);
            groups().getOrCreateRoot(HOST_SECRET_ROOT, "Remote Manager Host Passwords")
                    .addEntry(entry);
        } else {
            if (!KdbxEntries.hasRole(entry, HOST_SECRET)
                    || !ownerHostId.toString().equals(entry.getProperty(SECRET_OWNER))
                    || !purpose.equals(entry.getProperty(SECRET_PURPOSE)))
                throw new VaultException("Host password belongs to a different host");
            entryStore().update(entry, title, username, password, null, null, null);
        }
    }

    private void pruneHostSecrets(UUID ownerHostId, Connection connection) throws VaultException {
        for (JacksonEntry entry : entryStore().all()) {
            if (!KdbxEntries.hasRole(entry, HOST_SECRET)
                    || !ownerHostId.toString().equals(entry.getProperty(SECRET_OWNER))) continue;
            UUID id = KdbxEntries.logicalId(entry);
            if (connection == null
                    || (!id.equals(connection.sshCredentialEntryId())
                            && !id.equals(connection.sudoCredentialEntryId())))
                entry.getParent().removeEntry(entry);
        }
    }

    public synchronized List<ConnectionFolder> folders() throws VaultException {
        requireUnlocked();
        JacksonGroup root = groups().findRoot(HOST_ROOT);
        List<ConnectionFolder> result = new ArrayList<>();
        if (root != null) collectFolders(root, null, result);
        return result;
    }

    private void collectFolders(JacksonGroup parent, UUID parentId, List<ConnectionFolder> result) {
        for (JacksonGroup child : parent.getGroups()) {
            UUID id = child.getUuid();
            result.add(new ConnectionFolder(id, parentId, child.getName(), 0));
            collectFolders(child, id, result);
        }
    }

    public synchronized List<Connection> connections() throws VaultException {
        requireUnlocked();
        JacksonGroup root = groups().findRoot(HOST_ROOT);
        List<Connection> result = new ArrayList<>();
        if (root != null) collectConnections(root, null, result);
        return result;
    }

    private void collectConnections(JacksonGroup group, UUID parentId, List<Connection> result) throws VaultException {
        for (JacksonEntry entry : group.getEntries()) {
            if (!KdbxEntries.hasRole(entry, HOST)) continue;
            try {
                result.add(KdbxConnectionMapper.read(entry, parentId, KdbxEntries.logicalId(entry)));
            } catch (RuntimeException error) {
                throw new VaultException("Invalid saved host: " + entry.getTitle(), error);
            }
        }
        for (JacksonGroup child : group.getGroups()) collectConnections(child, child.getUuid(), result);
    }

    public synchronized UUID createFolder(UUID parentId, String name) throws VaultException {
        requireUnlocked();
        if (name == null || name.isBlank()) throw new VaultException("Folder name is required");
        JacksonGroup root = groups().getOrCreateRoot(HOST_ROOT, "Remote Manager Hosts");
        JacksonGroup parent = parentId == null ? root : groups().findGroup(root, parentId);
        if (parent == null) throw new VaultException("Parent folder is missing");
        JacksonGroup folder = database.newGroup(name);
        parent.addGroup(folder);
        return folder.getUuid();
    }

    public synchronized void renameFolder(UUID id, String name) throws VaultException {
        requireUnlocked();
        if (name == null || name.isBlank()) throw new VaultException("Folder name is required");
        JacksonGroup root = groups().findRoot(HOST_ROOT);
        JacksonGroup folder = root == null ? null : groups().findGroup(root, id);
        if (folder == null || folder == root) throw new VaultException("Folder is missing");
        folder.setName(name);
    }

    public synchronized void deleteFolder(UUID id) throws VaultException {
        requireUnlocked();
        JacksonGroup root = groups().findRoot(HOST_ROOT);
        JacksonGroup folder = root == null ? null : groups().findGroup(root, id);
        if (folder == null || folder == root) throw new VaultException("Folder is missing");
        if (!folder.getEntries().isEmpty() || !folder.getGroups().isEmpty())
            throw new VaultException("Move or delete this folder's contents first");
        folder.getParent().removeGroup(folder);
    }

    public synchronized void putConnection(Connection connection) throws VaultException {
        requireUnlocked();
        JacksonGroup root = groups().getOrCreateRoot(HOST_ROOT, "Remote Manager Hosts");
        JacksonGroup parent =
                connection.parentFolderId() == null ? root : groups().findGroup(root, connection.parentFolderId());
        if (parent == null) throw new VaultException("Parent folder is missing");
        JacksonEntry entry = entryStore().find(connection.id()).orElse(null);
        if (entry != null && !KdbxEntries.hasRole(entry, HOST))
            throw new VaultException("Host ID conflicts with an identity");
        if (entry == null) {
            entry = database.newEntry();
            entry.setProperty(ID, connection.id().toString());
        } else {
            entry.getParent().removeEntry(entry);
        }
        KdbxConnectionMapper.write(entry, connection);
        parent.addEntry(entry);
        pruneHostSecrets(connection.id(), connection);
    }

    public synchronized void deleteConnection(UUID id) throws VaultException {
        JacksonEntry entry = entryStore().find(id).orElse(null);
        if (entry == null || !KdbxEntries.hasRole(entry, HOST)) throw new VaultException("Host is missing");
        entry.getParent().removeEntry(entry);
        pruneHostSecrets(id, null);
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
        JacksonEntry entry = entryStore().find(id).orElseThrow(() -> new VaultException("KeePass entry is missing"));
        if (KdbxEntries.hasRole(entry, HOST)
                || KdbxEntries.hasRole(entry, HOST_SECRET)
                || KdbxEntries.hasRole(entry, MARKER)) throw new VaultException("Identity is missing");
        KdbxEntries.rejectReservedFields(fields);
        entryStore().update(entry, title, username, password, fields, attachmentName, attachment);
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
