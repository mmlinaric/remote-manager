package com.mmlinaric.remotemanager.vault.kdbx;

import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.ID;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.PREFIX;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.ROLE;

import com.mmlinaric.remotemanager.vault.VaultEntry;
import com.mmlinaric.remotemanager.vault.VaultException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.linguafranca.pwdb.PropertyValue;
import org.linguafranca.pwdb.kdbx.jackson.JacksonDatabase;
import org.linguafranca.pwdb.kdbx.jackson.JacksonEntry;
import org.linguafranca.pwdb.kdbx.jackson.JacksonGroup;

/** Reads and updates ordinary KeePass entry values while preserving Remote Manager metadata. */
final class KdbxEntries {
    private final JacksonDatabase database;

    KdbxEntries(JacksonDatabase database) {
        this.database = database;
    }

    Optional<JacksonEntry> find(UUID id) throws VaultException {
        JacksonEntry result = null;
        for (JacksonEntry entry : all()) {
            if (!id.equals(logicalId(entry))) continue;
            if (result != null) throw new VaultException("Duplicate KeePass entry ID: " + id);
            result = entry;
        }
        return Optional.ofNullable(result);
    }

    List<JacksonEntry> all() {
        List<JacksonEntry> result = new ArrayList<>();
        collect(database.getRootGroup(), result);
        return result;
    }

    VaultEntry summary(JacksonEntry entry) {
        Map<String, String> fields = new HashMap<>();
        for (String name : entry.getPropertyNames()) {
            if (!"Password".equals(name)) fields.put(name, string(entry.getProperty(name)));
        }
        PropertyValue password = entry.getPropertyValue("Password");
        char[] chars = password == null ? null : password.getValueAsChars();
        boolean hasPassword = chars != null && chars.length != 0;
        if (chars != null) Arrays.fill(chars, '\0');
        return new VaultEntry(
                logicalId(entry),
                entry.getTitle(),
                entry.getUsername(),
                Map.copyOf(fields),
                entry.getBinaryPropertyNames(),
                hasPassword);
    }

    void update(
            JacksonEntry entry,
            String title,
            String username,
            char[] password,
            Map<String, String> fields,
            String attachmentName,
            byte[] attachment)
            throws VaultException {
        if (title == null || title.isBlank()) throw new VaultException("Entry title is required");
        entry.setTitle(title);
        entry.setUsername(username == null ? "" : username);
        if (password != null) {
            entry.setPropertyValue(
                    "Password",
                    database.getPropertyValueStrategy().newProtected().of(password));
        }
        if (fields != null) fields.forEach(entry::setProperty);
        if (attachmentName != null && attachment != null) {
            try {
                entry.setBinaryProperty(attachmentName, attachment);
            } catch (Exception error) {
                throw new VaultException("Could not store attachment", error);
            }
        }
    }

    static void rejectReservedFields(Map<String, String> fields) throws VaultException {
        if (fields == null) return;
        for (String key : fields.keySet()) {
            if (key == null
                    || key.equals("Title")
                    || key.equals("UserName")
                    || key.equals("Password")
                    || key.equals("URL")
                    || key.equals("Notes")
                    || key.startsWith(PREFIX)) {
                throw new VaultException("Reserved KeePass field cannot be custom: " + key);
            }
        }
    }

    static UUID logicalId(JacksonEntry entry) {
        String value = entry.getProperty(ID);
        return value == null || value.isBlank() ? entry.getUuid() : UUID.fromString(value);
    }

    static boolean hasRole(JacksonEntry entry, String role) {
        return role.equals(entry.getProperty(ROLE));
    }

    private static void collect(JacksonGroup group, List<JacksonEntry> result) {
        result.addAll(group.getEntries());
        for (JacksonGroup child : group.getGroups()) collect(child, result);
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }
}
