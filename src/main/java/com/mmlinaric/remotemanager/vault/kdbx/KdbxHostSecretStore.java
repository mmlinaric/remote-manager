package com.mmlinaric.remotemanager.vault.kdbx;

import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.HOST_SECRET;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.HOST_SECRET_ROOT;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.ID;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.ROLE;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.SECRET_OWNER;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.SECRET_PURPOSE;

import com.mmlinaric.remotemanager.vault.VaultEntry;
import com.mmlinaric.remotemanager.vault.VaultException;
import java.util.UUID;
import org.linguafranca.pwdb.kdbx.jackson.JacksonDatabase;
import org.linguafranca.pwdb.kdbx.jackson.JacksonEntry;

/** Stores credentials that belong exclusively to one host instead of a reusable identity. */
final class KdbxHostSecretStore {
    private final JacksonDatabase database;
    private final KdbxEntries entries;
    private final KdbxManagedGroups groups;

    KdbxHostSecretStore(JacksonDatabase database) {
        this.database = database;
        entries = new KdbxEntries(database);
        groups = new KdbxManagedGroups(database);
    }

    static boolean belongsTo(VaultEntry entry, UUID ownerHostId, String purpose) {
        return entry != null
                && HOST_SECRET.equals(entry.fields().get(ROLE))
                && ownerHostId.toString().equals(entry.fields().get(SECRET_OWNER))
                && purpose.equals(entry.fields().get(SECRET_PURPOSE));
    }

    void put(UUID id, UUID ownerHostId, String purpose, String title, String username, char[] password)
            throws VaultException {
        validatePurpose(purpose);
        JacksonEntry entry = entries.find(id).orElse(null);
        if (entry == null) {
            create(id, ownerHostId, purpose, title, username, password);
            return;
        }
        verifyOwnership(entry, ownerHostId, purpose);
        entries.update(entry, title, username, password, null, null, null);
    }

    private void create(UUID id, UUID ownerHostId, String purpose, String title, String username, char[] password)
            throws VaultException {
        if (password == null || password.length == 0) throw new VaultException("Enter a password for this host");
        JacksonEntry entry = database.newEntry();
        entry.setProperty(ID, id.toString());
        entry.setProperty(ROLE, HOST_SECRET);
        entry.setProperty(SECRET_OWNER, ownerHostId.toString());
        entry.setProperty(SECRET_PURPOSE, purpose);
        entries.update(entry, title, username, password, null, null, null);
        groups.getOrCreateRoot(HOST_SECRET_ROOT, "Remote Manager Host Passwords")
                .addEntry(entry);
    }

    private static void validatePurpose(String purpose) throws VaultException {
        if (!"ssh".equals(purpose) && !"sudo".equals(purpose))
            throw new VaultException("Invalid host password purpose");
    }

    private static void verifyOwnership(JacksonEntry entry, UUID ownerHostId, String purpose) throws VaultException {
        if (!KdbxEntries.hasRole(entry, HOST_SECRET)
                || !ownerHostId.toString().equals(entry.getProperty(SECRET_OWNER))
                || !purpose.equals(entry.getProperty(SECRET_PURPOSE)))
            throw new VaultException("Host password belongs to a different host");
    }
}
