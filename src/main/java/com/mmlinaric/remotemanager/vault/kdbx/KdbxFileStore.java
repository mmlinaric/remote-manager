package com.mmlinaric.remotemanager.vault.kdbx;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.linguafranca.pwdb.PropertyValue;
import org.linguafranca.pwdb.kdbx.KdbxCreds;
import org.linguafranca.pwdb.kdbx.KdbxHeader;
import org.linguafranca.pwdb.kdbx.KdbxStreamFormat;
import org.linguafranca.pwdb.kdbx.jackson.JacksonDatabase;
import org.linguafranca.pwdb.kdbx.jackson.JacksonEntry;
import org.linguafranca.pwdb.kdbx.jackson.JacksonGroup;

/** KDBX serialization and conflict-detection primitives. */
final class KdbxFileStore {
    private KdbxFileStore() {}

    static JacksonDatabase read(Path path, char[] password) throws Exception {
        KdbxCreds credentials = credentials(password);
        try (InputStream input = Files.newInputStream(path)) {
            return JacksonDatabase.load(credentials, input);
        } finally {
            Arrays.fill(credentials.getKey(), (byte) 0);
        }
    }

    static void write(JacksonDatabase database, char[] password, OutputStream output) throws Exception {
        KdbxCreds credentials = credentials(password);
        try {
            // Reusing a loaded header appends attachment binaries again on each save.
            database.save(new KdbxStreamFormat(new KdbxHeader(4)), credentials, output);
        } finally {
            Arrays.fill(credentials.getKey(), (byte) 0);
        }
    }

    static Map<String, String> snapshot(JacksonDatabase database) {
        Map<String, String> result = new HashMap<>();
        snapshotGroup(database.getRootGroup(), null, result);
        return result;
    }

    static byte[] digest(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest hash = sha256();
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                hash.update(buffer, 0, length);
            }
            Arrays.fill(buffer, (byte) 0);
            return hash.digest();
        }
    }

    private static KdbxCreds credentials(char[] password) {
        byte[] bytes = PropertyValue.charsToBytes(password);
        try {
            return new KdbxCreds(bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static void snapshotGroup(JacksonGroup group, UUID parent, Map<String, String> result) {
        String groupKey = "group/" + group.getUuid();
        result.put(groupKey, string(parent) + "\0" + string(group.getName()));
        for (JacksonEntry entry : group.getEntries()) {
            String key = "entry/" + entry.getUuid();
            result.put(key, string(group.getUuid()));
            for (String name : entry.getPropertyNames()) {
                PropertyValue value = entry.getPropertyValue(name);
                result.put(key + "/text/" + name, value == null ? "" : hash(value.getValueAsBytes()));
            }
            for (String name : entry.getBinaryPropertyNames()) {
                result.put(key + "/binary/" + name, hash(entry.getBinaryProperty(name)));
            }
        }
        for (JacksonGroup child : group.getGroups()) {
            snapshotGroup(child, group.getUuid(), result);
        }
    }

    private static String hash(byte[] value) {
        return Base64.getEncoder().encodeToString(sha256().digest(value));
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
