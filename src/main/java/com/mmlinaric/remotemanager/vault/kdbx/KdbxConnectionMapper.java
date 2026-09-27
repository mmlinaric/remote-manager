package com.mmlinaric.remotemanager.vault.kdbx;

import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.HOST;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.PREFIX;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.ROLE;

import com.mmlinaric.remotemanager.model.AuthenticationType;
import com.mmlinaric.remotemanager.model.Connection;
import java.util.UUID;
import org.linguafranca.pwdb.kdbx.jackson.JacksonEntry;

/** Maps Remote Manager host metadata between the domain model and a KeePass entry. */
final class KdbxConnectionMapper {
    private KdbxConnectionMapper() {}

    static Connection read(JacksonEntry entry, UUID parentId, UUID id) {
        return new Connection(
                id,
                entry.getTitle(),
                entry.getProperty(PREFIX + "Hostname"),
                Integer.parseInt(entry.getProperty(PREFIX + "Port")),
                entry.getUsername(),
                parentId,
                AuthenticationType.valueOf(entry.getProperty(PREFIX + "Auth")),
                optionalUuid(entry.getProperty(PREFIX + "CredentialId")),
                optionalUuid(entry.getProperty(PREFIX + "SudoId")),
                blankToNull(entry.getProperty(PREFIX + "KeyAttachment")),
                blankToNull(entry.getProperty(PREFIX + "KeyFile")),
                entry.getNotes(),
                number(entry.getProperty(PREFIX + "SortOrder")));
    }

    static void write(JacksonEntry entry, Connection connection) {
        entry.setTitle(connection.name());
        entry.setUsername(connection.username());
        entry.setNotes(connection.notes());
        entry.setProperty(ROLE, HOST);
        entry.setProperty(PREFIX + "Schema", "2");
        entry.setProperty(PREFIX + "Hostname", connection.hostname());
        entry.setProperty(PREFIX + "Port", Integer.toString(connection.port()));
        entry.setProperty(PREFIX + "Auth", connection.authenticationType().name());
        entry.setProperty(PREFIX + "CredentialId", string(connection.sshCredentialEntryId()));
        entry.setProperty(PREFIX + "SudoId", string(connection.sudoCredentialEntryId()));
        entry.setProperty(PREFIX + "KeyAttachment", string(connection.privateKeyAttachmentName()));
        entry.setProperty(PREFIX + "KeyFile", string(connection.privateKeyFilePath()));
        entry.setProperty(PREFIX + "SortOrder", Integer.toString(connection.sortOrder()));
    }

    private static UUID optionalUuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static int number(String value) {
        return value == null || value.isBlank() ? 0 : Integer.parseInt(value);
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }
}
