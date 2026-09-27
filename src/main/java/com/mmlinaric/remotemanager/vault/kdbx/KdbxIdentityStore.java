package com.mmlinaric.remotemanager.vault.kdbx;

import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.HOST;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.HOST_SECRET;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.ID;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.IDENTITY_ROOT;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.MARKER;

import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.vault.VaultException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.linguafranca.pwdb.kdbx.jackson.JacksonDatabase;
import org.linguafranca.pwdb.kdbx.jackson.JacksonEntry;

/** Maintains reusable identity entries and prevents changes that would conflict with host references. */
final class KdbxIdentityStore {
    private final JacksonDatabase database;
    private final KdbxEntries entries;
    private final KdbxManagedGroups groups;
    private final ConnectionLookup connections;

    KdbxIdentityStore(JacksonDatabase database, ConnectionLookup connections) {
        this.database = database;
        this.entries = new KdbxEntries(database);
        this.groups = new KdbxManagedGroups(database);
        this.connections = connections;
    }

    UUID add(
            UUID id,
            String title,
            String username,
            char[] password,
            Map<String, String> fields,
            String attachmentName,
            byte[] attachment)
            throws VaultException {
        if (entries.find(id).isPresent()) throw new VaultException("Identity ID already exists");
        KdbxEntries.rejectReservedFields(fields);
        JacksonEntry entry = database.newEntry();
        entry.setProperty(ID, id.toString());
        entries.update(entry, title, username, password, fields, attachmentName, attachment);
        groups.getOrCreateRoot(IDENTITY_ROOT, "Remote Manager Identities").addEntry(entry);
        return id;
    }

    void delete(UUID id) throws VaultException {
        JacksonEntry entry = entries.find(id).orElseThrow(() -> new VaultException("Identity is missing"));
        if (KdbxEntries.hasRole(entry, HOST)
                || KdbxEntries.hasRole(entry, HOST_SECRET)
                || KdbxEntries.hasRole(entry, MARKER)) {
            throw new VaultException("Identity is missing");
        }
        for (Connection connection : connections.get()) {
            if (id.equals(connection.sshCredentialEntryId()) || id.equals(connection.sudoCredentialEntryId())) {
                throw new VaultException("Identity is used by host: " + connection.name());
            }
        }
        entry.getParent().removeEntry(entry);
    }

    void update(
            UUID id,
            String title,
            String username,
            char[] password,
            Map<String, String> fields,
            String attachmentName,
            byte[] attachment)
            throws VaultException {
        JacksonEntry entry = entries.find(id).orElseThrow(() -> new VaultException("KeePass entry is missing"));
        if (KdbxEntries.hasRole(entry, HOST)
                || KdbxEntries.hasRole(entry, HOST_SECRET)
                || KdbxEntries.hasRole(entry, MARKER)) {
            throw new VaultException("Identity is missing");
        }
        KdbxEntries.rejectReservedFields(fields);
        entries.update(entry, title, username, password, fields, attachmentName, attachment);
    }

    @FunctionalInterface
    interface ConnectionLookup {
        List<Connection> get() throws VaultException;
    }
}
