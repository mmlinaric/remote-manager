package com.example.remotemanager.vault.kdbx;

import com.example.remotemanager.vault.Vault;
import com.example.remotemanager.vault.VaultConflictException;
import com.example.remotemanager.vault.VaultEntry;
import com.example.remotemanager.vault.VaultException;
import de.soderer.utilities.kdbx.KdbxDatabase;
import de.soderer.utilities.kdbx.KdbxReader;
import de.soderer.utilities.kdbx.KdbxWriter;
import de.soderer.utilities.kdbx.data.KdbxEntry;
import de.soderer.utilities.kdbx.data.KdbxEntryBinary;
import de.soderer.utilities.kdbx.data.KdbxUUID;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class KdbxVault implements Vault {
  private final Path path;
  private KdbxDatabase database;
  private byte[] loadedDigest;

  public KdbxVault(Path path) {
    this.path = path.toAbsolutePath();
  }

  public Path path() {
    return path;
  }

  public static void create(Path path, char[] masterPassword) throws VaultException {
    if (Files.exists(path)) {
      throw new VaultException("Vault file already exists");
    }
    try {
      Files.createDirectories(path.toAbsolutePath().getParent());
      try (OutputStream output = Files.newOutputStream(path);
          KdbxWriter writer = new KdbxWriter(output)) {
        writer.writeKdbxDatabase(new KdbxDatabase(), masterPassword);
      }
    } catch (Exception e) {
      throw new VaultException("Could not create KeePass vault", e);
    }
  }

  @Override
  public synchronized void unlock(char[] masterPassword) throws VaultException {
    try (InputStream input = Files.newInputStream(path);
        KdbxReader reader = new KdbxReader(input)) {
      KdbxDatabase loaded = reader.readKdbxDatabase(masterPassword);
      byte[] digest = digest(path);
      database = loaded;
      loadedDigest = digest;
    } catch (Exception e) {
      throw new VaultException("Could not unlock KeePass vault", e);
    }
  }

  @Override
  public synchronized void lock() {
    database = null;
    if (loadedDigest != null) {
      Arrays.fill(loadedDigest, (byte) 0);
    }
    loadedDigest = null;
  }

  @Override
  public synchronized boolean isUnlocked() {
    return database != null;
  }

  @Override
  public synchronized List<VaultEntry> entries() throws VaultException {
    requireUnlocked();
    return database.getAllEntries().stream()
        .map(this::summary)
        .sorted((a, b) -> a.toString().compareToIgnoreCase(b.toString()))
        .toList();
  }

  @Override
  public synchronized Optional<VaultEntry> getEntry(UUID id) throws VaultException {
    return find(id).map(this::summary);
  }

  @Override
  public synchronized Optional<char[]> getPassword(UUID id) throws VaultException {
    return find(id)
        .map(KdbxEntry::getPassword)
        .filter(value -> value != null && !value.isEmpty())
        .map(String::toCharArray);
  }

  @Override
  public synchronized Optional<byte[]> getAttachment(UUID id, String attachmentName)
      throws VaultException {
    for (KdbxEntryBinary binary :
        find(id).orElseThrow(() -> new VaultException("KeePass entry is missing")).getBinaries()) {
      if (binary.getKey().equals(attachmentName)) {
        try {
          return Optional.of(binary.getData());
        } catch (Exception e) {
          throw new VaultException("Could not read KeePass attachment", e);
        }
      }
    }
    return Optional.empty();
  }

  public synchronized UUID addEntry(
      String title,
      String username,
      char[] password,
      Map<String, String> fields,
      String attachmentName,
      byte[] attachment)
      throws VaultException {
    requireUnlocked();
    KdbxEntry entry = new KdbxEntry();
    updateValues(entry, title, username, password, fields, attachmentName, attachment);
    database.getEntries().add(entry);
    return toUuid(entry.getUuid());
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
    KdbxEntry entry = find(id).orElseThrow(() -> new VaultException("KeePass entry is missing"));
    updateValues(entry, title, username, password, fields, attachmentName, attachment);
  }

  private static void updateValues(
      KdbxEntry entry,
      String title,
      String username,
      char[] password,
      Map<String, String> fields,
      String attachmentName,
      byte[] attachment)
      throws VaultException {
    if (title == null || title.isBlank()) {
      throw new VaultException("Entry title is required");
    }
    entry.setTitle(title);
    entry.setUsername(username == null ? "" : username);
    if (password != null) {
      // The KDBX library accepts password fields as String values.
      entry.setPassword(new String(password));
    }
    if (fields != null) {
      fields.forEach(entry::setItem);
    }
    if (attachmentName != null && attachment != null) {
      try {
        entry.getBinaries().removeIf(binary -> binary.getKey().equals(attachmentName));
        entry.getBinaries().add(new KdbxEntryBinary().withKey(attachmentName).withData(attachment));
      } catch (Exception e) {
        throw new VaultException("Could not store attachment", e);
      }
    }
  }

  public synchronized void save(char[] masterPassword) throws VaultException {
    requireUnlocked();
    try {
      verifyUnchanged();
      Path temporary = Files.createTempFile(path.getParent(), ".remote-manager-", ".kdbx");
      try {
        try (OutputStream output = Files.newOutputStream(temporary);
            KdbxWriter writer = new KdbxWriter(output)) {
          writer.writeKdbxDatabase(database, masterPassword);
        }
        try (InputStream input = Files.newInputStream(temporary);
            KdbxReader reader = new KdbxReader(input)) {
          reader.readKdbxDatabase(masterPassword);
        }
        verifyUnchanged();
        Files.move(
            temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        loadedDigest = digest(path);
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (VaultConflictException e) {
      throw e;
    } catch (Exception e) {
      throw new VaultException("Could not save KeePass vault", e);
    }
  }

  private Optional<KdbxEntry> find(UUID id) throws VaultException {
    requireUnlocked();
    return Optional.ofNullable(
        database.getEntryByUUID(KdbxUUID.fromHex(id.toString().replace("-", ""))));
  }

  private VaultEntry summary(KdbxEntry entry) {
    UUID id = toUuid(entry.getUuid());
    Map<String, String> fields =
        entry.getItems().entrySet().stream()
            .filter(value -> !value.getKey().equals("Password"))
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, value -> String.valueOf(value.getValue())));
    return new VaultEntry(
        id,
        entry.getTitle(),
        entry.getUsername(),
        fields,
        entry.getBinaries().stream().map(KdbxEntryBinary::getKey).toList());
  }

  private void requireUnlocked() throws VaultException {
    if (database == null) {
      throw new VaultException("Vault is locked");
    }
  }

  private void verifyUnchanged() throws Exception {
    if (!MessageDigest.isEqual(loadedDigest, digest(path))) {
      throw new VaultConflictException();
    }
  }

  private static UUID toUuid(KdbxUUID id) {
    return UUID.fromString(
        id.toHex().replaceFirst("^(........)(....)(....)(....)(............)$", "$1-$2-$3-$4-$5"));
  }

  private static byte[] digest(Path path) throws IOException {
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

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (java.security.NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is unavailable", error);
    }
  }
}
