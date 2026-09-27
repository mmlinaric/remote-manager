package com.mmlinaric.remotemanager.vault.kdbx;

import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.HOST;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.HOST_ROOT;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.HOST_SECRET;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.ID;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.SECRET_OWNER;

import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.model.ConnectionFolder;
import com.mmlinaric.remotemanager.vault.VaultException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.linguafranca.pwdb.kdbx.jackson.JacksonDatabase;
import org.linguafranca.pwdb.kdbx.jackson.JacksonEntry;
import org.linguafranca.pwdb.kdbx.jackson.JacksonGroup;

/** Maintains the managed host folder hierarchy and the connection entries stored inside it. */
final class KdbxConnectionStore {
    private final JacksonDatabase database;
    private final KdbxManagedGroups groups;
    private final KdbxEntries entries;

    KdbxConnectionStore(JacksonDatabase database) {
        this.database = database;
        this.groups = new KdbxManagedGroups(database);
        this.entries = new KdbxEntries(database);
    }

    List<ConnectionFolder> folders() {
        JacksonGroup root = groups.findRoot(HOST_ROOT);
        List<ConnectionFolder> result = new ArrayList<>();
        if (root != null) collectFolders(root, null, result);
        return result;
    }

    List<Connection> connections() throws VaultException {
        JacksonGroup root = groups.findRoot(HOST_ROOT);
        List<Connection> result = new ArrayList<>();
        if (root != null) collectConnections(root, null, result);
        return result;
    }

    UUID createFolder(UUID parentId, String name) throws VaultException {
        if (name == null || name.isBlank()) throw new VaultException("Folder name is required");
        JacksonGroup root = groups.getOrCreateRoot(HOST_ROOT, "Remote Manager Hosts");
        JacksonGroup parent = parentId == null ? root : groups.findGroup(root, parentId);
        if (parent == null) throw new VaultException("Parent folder is missing");
        JacksonGroup folder = database.newGroup(name);
        parent.addGroup(folder);
        return folder.getUuid();
    }

    void renameFolder(UUID id, String name) throws VaultException {
        if (name == null || name.isBlank()) throw new VaultException("Folder name is required");
        JacksonGroup root = groups.findRoot(HOST_ROOT);
        JacksonGroup folder = root == null ? null : groups.findGroup(root, id);
        if (folder == null || folder == root) throw new VaultException("Folder is missing");
        folder.setName(name);
    }

    void deleteFolder(UUID id) throws VaultException {
        JacksonGroup root = groups.findRoot(HOST_ROOT);
        JacksonGroup folder = root == null ? null : groups.findGroup(root, id);
        if (folder == null || folder == root) throw new VaultException("Folder is missing");
        if (!folder.getEntries().isEmpty() || !folder.getGroups().isEmpty()) {
            throw new VaultException("Move or delete this folder's contents first");
        }
        folder.getParent().removeGroup(folder);
    }

    void put(Connection connection) throws VaultException {
        JacksonGroup root = groups.getOrCreateRoot(HOST_ROOT, "Remote Manager Hosts");
        JacksonGroup parent =
                connection.parentFolderId() == null ? root : groups.findGroup(root, connection.parentFolderId());
        if (parent == null) throw new VaultException("Parent folder is missing");
        JacksonEntry entry = entries.find(connection.id()).orElse(null);
        if (entry != null && !KdbxEntries.hasRole(entry, HOST)) {
            throw new VaultException("Host ID conflicts with an identity");
        }
        if (entry == null) {
            entry = database.newEntry();
            entry.setProperty(ID, connection.id().toString());
        } else {
            entry.getParent().removeEntry(entry);
        }
        KdbxConnectionMapper.write(entry, connection);
        parent.addEntry(entry);
        pruneUnusedHostSecrets(connection.id(), connection);
    }

    void delete(UUID id) throws VaultException {
        JacksonEntry entry = entries.find(id).orElse(null);
        if (entry == null || !KdbxEntries.hasRole(entry, HOST)) throw new VaultException("Host is missing");
        entry.getParent().removeEntry(entry);
        pruneUnusedHostSecrets(id, null);
    }

    private void collectFolders(JacksonGroup parent, UUID parentId, List<ConnectionFolder> result) {
        for (JacksonGroup child : parent.getGroups()) {
            UUID id = child.getUuid();
            result.add(new ConnectionFolder(id, parentId, child.getName(), 0));
            collectFolders(child, id, result);
        }
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

    private void pruneUnusedHostSecrets(UUID ownerHostId, Connection connection) throws VaultException {
        for (JacksonEntry entry : entries.all()) {
            if (!KdbxEntries.hasRole(entry, HOST_SECRET)
                    || !ownerHostId.toString().equals(entry.getProperty(SECRET_OWNER))) continue;
            UUID id = KdbxEntries.logicalId(entry);
            if (connection == null
                    || (!id.equals(connection.sshCredentialEntryId())
                            && !id.equals(connection.sudoCredentialEntryId()))) {
                entry.getParent().removeEntry(entry);
            }
        }
    }
}
