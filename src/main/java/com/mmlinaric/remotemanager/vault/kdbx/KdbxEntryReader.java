package com.mmlinaric.remotemanager.vault.kdbx;

import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.HOST;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.HOST_SECRET;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.MARKER;

import com.mmlinaric.remotemanager.vault.VaultEntry;
import com.mmlinaric.remotemanager.vault.VaultException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.linguafranca.pwdb.kdbx.jackson.JacksonDatabase;
import org.linguafranca.pwdb.kdbx.jackson.JacksonEntry;

/** Provides the application-facing read view of entries in an unlocked KDBX database. */
final class KdbxEntryReader {
    private final KdbxEntries entries;

    KdbxEntryReader(JacksonDatabase database) {
        entries = new KdbxEntries(database);
    }

    List<VaultEntry> identities() {
        return entries.all().stream()
                .filter(entry -> !KdbxEntries.hasRole(entry, HOST)
                        && !KdbxEntries.hasRole(entry, HOST_SECRET)
                        && !KdbxEntries.hasRole(entry, MARKER))
                .map(entries::summary)
                .sorted((first, second) -> first.toString().compareToIgnoreCase(second.toString()))
                .toList();
    }

    List<VaultEntry> credentials() {
        return entries.all().stream()
                .filter(entry -> !KdbxEntries.hasRole(entry, HOST) && !KdbxEntries.hasRole(entry, MARKER))
                .map(entries::summary)
                .toList();
    }

    Optional<VaultEntry> entry(UUID id) throws VaultException {
        return entries.find(id).map(entries::summary);
    }

    Optional<char[]> password(UUID id) throws VaultException {
        return entries.find(id)
                .map(entry -> entry.getPropertyValue("Password"))
                .filter(value -> value != null)
                .map(value -> value.getValueAsChars())
                .filter(value -> value.length != 0);
    }

    Optional<byte[]> attachment(UUID id, String attachmentName) throws VaultException {
        JacksonEntry entry = entries.find(id).orElseThrow(() -> new VaultException("KeePass entry is missing"));
        try {
            return Optional.ofNullable(entry.getBinaryProperty(attachmentName));
        } catch (Exception error) {
            throw new VaultException("Could not read KeePass attachment", error);
        }
    }
}
