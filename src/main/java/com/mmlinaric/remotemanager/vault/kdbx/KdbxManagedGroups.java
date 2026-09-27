package com.mmlinaric.remotemanager.vault.kdbx;

import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.MARKER;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.PREFIX;
import static com.mmlinaric.remotemanager.vault.kdbx.KdbxSchema.ROLE;

import java.util.UUID;
import org.linguafranca.pwdb.kdbx.jackson.JacksonDatabase;
import org.linguafranca.pwdb.kdbx.jackson.JacksonEntry;
import org.linguafranca.pwdb.kdbx.jackson.JacksonGroup;

/** Locates and creates the marked group roots owned by Remote Manager. */
final class KdbxManagedGroups {
    private final JacksonDatabase database;

    KdbxManagedGroups(JacksonDatabase database) {
        this.database = database;
    }

    JacksonGroup getOrCreateRoot(String role, String name) {
        JacksonGroup root = findRoot(role);
        if (root != null) {
            return root;
        }
        root = database.newGroup(name);
        database.getRootGroup().addGroup(root);
        JacksonEntry marker = database.newEntry();
        marker.setTitle("Remote Manager group marker");
        marker.setProperty(ROLE, MARKER);
        marker.setProperty(PREFIX + "GroupRole", role);
        root.addEntry(marker);
        return root;
    }

    JacksonGroup findRoot(String role) {
        return findRoot(database.getRootGroup(), role);
    }

    JacksonGroup findGroup(JacksonGroup root, UUID id) {
        if (id.equals(root.getUuid())) {
            return root;
        }
        for (JacksonGroup child : root.getGroups()) {
            JacksonGroup found = findGroup(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static JacksonGroup findRoot(JacksonGroup group, String role) {
        for (JacksonEntry entry : group.getEntries()) {
            if (MARKER.equals(entry.getProperty(ROLE)) && role.equals(entry.getProperty(PREFIX + "GroupRole"))) {
                return group;
            }
        }
        for (JacksonGroup child : group.getGroups()) {
            JacksonGroup found = findRoot(child, role);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
