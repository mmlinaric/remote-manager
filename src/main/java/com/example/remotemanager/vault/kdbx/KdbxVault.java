package com.example.remotemanager.vault.kdbx;

import com.example.remotemanager.vault.Vault;
import com.example.remotemanager.vault.VaultConflictException;
import com.example.remotemanager.vault.VaultEntry;
import com.example.remotemanager.vault.VaultException;
import com.example.remotemanager.model.AuthenticationType;
import com.example.remotemanager.model.Connection;
import com.example.remotemanager.model.ConnectionFolder;
import de.soderer.utilities.kdbx.KdbxDatabase;
import de.soderer.utilities.kdbx.KdbxReader;
import de.soderer.utilities.kdbx.KdbxWriter;
import de.soderer.utilities.kdbx.data.KdbxEntry;
import de.soderer.utilities.kdbx.data.KdbxEntryBinary;
import de.soderer.utilities.kdbx.data.KdbxCustomDataItem;
import de.soderer.utilities.kdbx.data.KdbxGroup;
import de.soderer.utilities.kdbx.data.KdbxUUID;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class KdbxVault implements Vault {
  private final Path path;
  private KdbxDatabase database;
  private byte[] loadedDigest;
  private char[] sessionPassword;
  private static final String ROLE = "RemoteManager.Role";
  private static final String HOST_ROOT = "hosts-root";
  private static final String IDENTITY_ROOT = "identities-root";
  private static final String HOST_SECRET_ROOT = "host-secrets-root";
  private static final String HOST = "host";
  public static final String HOST_SECRET = "host-secret";
  public static final String SECRET_OWNER = "RemoteManager.OwnerHostId";
  public static final String SECRET_PURPOSE = "RemoteManager.SecretPurpose";
  private static final String PREFIX = "RemoteManager.";

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
      if (sessionPassword != null) Arrays.fill(sessionPassword, '\0');
      sessionPassword = masterPassword.clone();
    } catch (Exception e) {
      lock();
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
    if (sessionPassword != null) Arrays.fill(sessionPassword, '\0');
    sessionPassword = null;
  }

  @Override
  public synchronized boolean isUnlocked() {
    return database != null;
  }

  @Override
  public synchronized List<VaultEntry> entries() throws VaultException {
    requireUnlocked();
    return database.getAllEntries().stream()
        .filter(entry -> !HOST.equals(entry.getItem(ROLE))
            && !HOST_SECRET.equals(entry.getItem(ROLE)))
        .map(this::summary)
        .sorted((a, b) -> a.toString().compareToIgnoreCase(b.toString()))
        .toList();
  }

  public synchronized List<VaultEntry> credentialEntries() throws VaultException {
    requireUnlocked();
    return database.getAllEntries().stream()
        .filter(entry -> !HOST.equals(entry.getItem(ROLE)))
        .map(this::summary).toList();
  }

  public static boolean isHostSecret(VaultEntry entry, UUID ownerHostId, String purpose) {
    return entry != null && HOST_SECRET.equals(entry.fields().get(ROLE))
        && ownerHostId.toString().equals(entry.fields().get(SECRET_OWNER))
        && purpose.equals(entry.fields().get(SECRET_PURPOSE));
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
          return Optional.ofNullable(binary.getData());
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
    return addEntry(UUID.randomUUID(), title, username, password, fields, attachmentName, attachment);
  }

  public synchronized UUID addEntry(
      UUID id, String title, String username, char[] password, Map<String, String> fields,
      String attachmentName, byte[] attachment) throws VaultException {
    requireUnlocked();
    if (database.getEntryByUUID(kdbxUuid(id)) != null) throw new VaultException("Identity ID already exists");
    KdbxEntry entry = new KdbxEntry().withUuid(kdbxUuid(id));
    entry.setAutoType(false, "0", "", "", "");
    updateValues(entry, title, username, password, fields, attachmentName, attachment);
    managedRoot(IDENTITY_ROOT, "Remote Manager Identities").getEntries().add(entry);
    return toUuid(entry.getUuid());
  }

  public synchronized void deleteIdentity(UUID id) throws VaultException {
    requireUnlocked();
    KdbxEntry entry = database.getEntryByUUID(kdbxUuid(id));
    if (entry == null || HOST.equals(entry.getItem(ROLE))
        || HOST_SECRET.equals(entry.getItem(ROLE))) throw new VaultException("Identity is missing");
    for (Connection connection : connections()) {
      if (id.equals(connection.sshCredentialEntryId()) || id.equals(connection.sudoCredentialEntryId()))
        throw new VaultException("Identity is used by host: " + connection.name());
    }
    for (KdbxGroup root : database.getGroups()) if (removeEntry(root, id)) {
      deleted(id);
      return;
    }
    throw new VaultException("Identity is missing");
  }

  public synchronized void putHostSecret(UUID id, UUID ownerHostId, String purpose,
      String title, String username, char[] password) throws VaultException {
    requireUnlocked();
    if (!"ssh".equals(purpose) && !"sudo".equals(purpose))
      throw new VaultException("Invalid host password purpose");
    KdbxEntry entry = database.getEntryByUUID(kdbxUuid(id));
    if (entry == null) {
      if (password == null || password.length == 0)
        throw new VaultException("Enter a password for this host");
      entry = new KdbxEntry().withUuid(kdbxUuid(id));
      entry.setAutoType(false, "0", "", "", "");
      updateValues(entry, title, username, password,
          Map.of(ROLE, HOST_SECRET, SECRET_OWNER, ownerHostId.toString(), SECRET_PURPOSE, purpose),
          null, null);
      managedRoot(HOST_SECRET_ROOT, "Remote Manager Host Passwords").getEntries().add(entry);
    } else {
      if (!HOST_SECRET.equals(entry.getItem(ROLE))
          || !ownerHostId.toString().equals(entry.getItem(SECRET_OWNER))
          || !purpose.equals(entry.getItem(SECRET_PURPOSE)))
        throw new VaultException("Host password belongs to a different host");
      updateValues(entry, title, username, password, null, null, null);
    }
  }

  private void pruneHostSecrets(UUID ownerHostId, Connection connection) {
    List<UUID> stale = database.getAllEntries().stream()
        .filter(entry -> HOST_SECRET.equals(entry.getItem(ROLE))
            && ownerHostId.toString().equals(entry.getItem(SECRET_OWNER)))
        .map(entry -> toUuid(entry.getUuid()))
        .filter(id -> connection == null || (!id.equals(connection.sshCredentialEntryId())
            && !id.equals(connection.sudoCredentialEntryId())))
        .toList();
    KdbxGroup root = findManagedRoot(HOST_SECRET_ROOT);
    if (root != null) for (UUID id : stale) {
      if (removeEntry(root, id)) deleted(id);
    }
  }

  public synchronized List<ConnectionFolder> folders() throws VaultException {
    requireUnlocked();
    KdbxGroup root = findManagedRoot(HOST_ROOT);
    List<ConnectionFolder> result = new ArrayList<>();
    if (root != null) collectFolders(root, null, result);
    return result;
  }

  private void collectFolders(KdbxGroup parent, UUID parentId, List<ConnectionFolder> result) {
    for (KdbxGroup child : parent.getGroups()) {
      UUID id = toUuid(child.getUuid());
      result.add(new ConnectionFolder(id, parentId, child.getName(), 0));
      collectFolders(child, id, result);
    }
  }

  public synchronized List<Connection> connections() throws VaultException {
    requireUnlocked();
    KdbxGroup root = findManagedRoot(HOST_ROOT);
    List<Connection> result = new ArrayList<>();
    if (root != null) collectConnections(root, null, result);
    return result;
  }

  private void collectConnections(KdbxGroup group, UUID parentId, List<Connection> result)
      throws VaultException {
    for (KdbxEntry entry : group.getEntries()) {
      if (!HOST.equals(entry.getItem(ROLE))) continue;
      try {
        result.add(new Connection(
            toUuid(entry.getUuid()), entry.getTitle(), entry.getItem(PREFIX + "Hostname"),
            Integer.parseInt(entry.getItem(PREFIX + "Port")), entry.getUsername(), parentId,
            AuthenticationType.valueOf(entry.getItem(PREFIX + "Auth")),
            optionalUuid(entry.getItem(PREFIX + "CredentialId")),
            optionalUuid(entry.getItem(PREFIX + "SudoId")),
            entry.getItem(PREFIX + "KeyAttachment"), entry.getItem(PREFIX + "KeyFile"),
            entry.getNotes(), number(entry.getItem(PREFIX + "SortOrder"))));
      } catch (RuntimeException error) {
        throw new VaultException("Invalid saved host: " + entry.getTitle(), error);
      }
    }
    for (KdbxGroup child : group.getGroups()) collectConnections(child, toUuid(child.getUuid()), result);
  }

  public synchronized void putFolder(ConnectionFolder folder) throws VaultException {
    requireUnlocked();
    KdbxGroup root = managedRoot(HOST_ROOT, "Remote Manager Hosts");
    KdbxGroup existing = findGroup(root, folder.id());
    if (existing == null) {
      KdbxGroup parent = folder.parentFolderId() == null ? root : findGroup(root, folder.parentFolderId());
      if (parent == null) throw new VaultException("Parent folder is missing");
      parent.getGroups().add(newGroup(folder.id(), folder.name()));
    } else {
      existing.setName(folder.name());
    }
  }

  public synchronized void deleteFolder(UUID id) throws VaultException {
    requireUnlocked();
    KdbxGroup root = findManagedRoot(HOST_ROOT);
    if (root == null || !removeFolder(root, id)) throw new VaultException("Folder is missing");
    deleted(id);
  }

  private boolean removeFolder(KdbxGroup parent, UUID id) throws VaultException {
    for (KdbxGroup child : parent.getGroups()) {
      if (id.equals(toUuid(child.getUuid()))) {
        if (!child.getEntries().isEmpty() || !child.getGroups().isEmpty())
          throw new VaultException("Move or delete this folder's contents first");
        return parent.getGroups().remove(child);
      }
      if (removeFolder(child, id)) return true;
    }
    return false;
  }

  public synchronized void putConnection(Connection connection) throws VaultException {
    requireUnlocked();
    KdbxGroup root = managedRoot(HOST_ROOT, "Remote Manager Hosts");
    KdbxGroup parent = connection.parentFolderId() == null ? root : findGroup(root, connection.parentFolderId());
    if (parent == null) throw new VaultException("Parent folder is missing");
    KdbxEntry entry = database.getEntryByUUID(kdbxUuid(connection.id()));
    if (entry != null && !HOST.equals(entry.getItem(ROLE)))
      throw new VaultException("Host ID conflicts with an identity");
    if (entry == null) entry = new KdbxEntry().withUuid(kdbxUuid(connection.id()));
    else removeEntry(root, connection.id());
    entry.setTitle(connection.name());
    entry.setUsername(connection.username());
    entry.setNotes(connection.notes());
    entry.setItem(ROLE, HOST);
    entry.setItem(PREFIX + "Schema", "1");
    entry.setItem(PREFIX + "Hostname", connection.hostname());
    entry.setItem(PREFIX + "Port", Integer.toString(connection.port()));
    entry.setItem(PREFIX + "Auth", connection.authenticationType().name());
    entry.setItem(PREFIX + "CredentialId", string(connection.sshCredentialEntryId()));
    entry.setItem(PREFIX + "SudoId", string(connection.sudoCredentialEntryId()));
    entry.setItem(PREFIX + "KeyAttachment", string(connection.privateKeyAttachmentName()));
    entry.setItem(PREFIX + "KeyFile", string(connection.privateKeyFilePath()));
    entry.setItem(PREFIX + "SortOrder", Integer.toString(connection.sortOrder()));
    parent.getEntries().add(entry);
    pruneHostSecrets(connection.id(), connection);
  }

  public synchronized void deleteConnection(UUID id) throws VaultException {
    requireUnlocked();
    KdbxGroup root = findManagedRoot(HOST_ROOT);
    if (root == null || !removeEntry(root, id)) throw new VaultException("Host is missing");
    deleted(id);
    pruneHostSecrets(id, null);
  }

  private boolean removeEntry(KdbxGroup group, UUID id) {
    if (group.getEntries().removeIf(entry -> id.equals(toUuid(entry.getUuid())))) return true;
    for (KdbxGroup child : group.getGroups()) if (removeEntry(child, id)) return true;
    return false;
  }

  private KdbxGroup managedRoot(String role, String name) throws VaultException {
    KdbxGroup root = findManagedRoot(role);
    if (root != null) return root;
    root = newGroup(UUID.randomUUID(), name);
    root.setCustomData(List.of(new KdbxCustomDataItem().withKey(ROLE).withValue(role)));
    if (database.getGroups().isEmpty())
      database.getGroups().add(newGroup(UUID.randomUUID(), "RemoteManager"));
    database.getGroups().getFirst().getGroups().add(root);
    return root;
  }

  private KdbxGroup findManagedRoot(String role) {
    for (KdbxGroup root : database.getGroups()) {
      KdbxGroup found = findManagedRoot(root, role);
      if (found != null) return found;
    }
    return null;
  }

  private KdbxGroup findManagedRoot(KdbxGroup group, String role) {
    if (group.getCustomData() != null && group.getCustomData().stream()
        .anyMatch(item -> ROLE.equals(item.getKey()) && role.equals(item.getValue()))) return group;
    for (KdbxGroup child : group.getGroups()) {
      KdbxGroup found = findManagedRoot(child, role);
      if (found != null) return found;
    }
    return null;
  }

  private KdbxGroup findGroup(KdbxGroup root, UUID id) {
    if (id.equals(toUuid(root.getUuid()))) return root;
    for (KdbxGroup child : root.getGroups()) {
      KdbxGroup found = findGroup(child, id);
      if (found != null) return found;
    }
    return null;
  }

  private static KdbxUUID kdbxUuid(UUID id) {
    return KdbxUUID.fromHex(id.toString().replace("-", ""));
  }

  private static KdbxGroup newGroup(UUID id, String name) {
    return new KdbxGroup().withUuid(kdbxUuid(id)).withName(name).withIconID(48)
        .withLastTopVisibleEntry(new KdbxUUID(new byte[16]));
  }

  private static UUID optionalUuid(String value) {
    return value == null || value.isBlank() ? null : UUID.fromString(value);
  }

  private static int number(String value) {
    return value == null || value.isBlank() ? 0 : Integer.parseInt(value);
  }

  private static String string(Object value) { return value == null ? "" : value.toString(); }

  private void deleted(UUID id) {
    database.getDeletedObjects().put(kdbxUuid(id), java.time.ZonedDateTime.now());
  }

  public synchronized void save() throws VaultException {
    if (sessionPassword == null) throw new VaultException("Vault is locked");
    save(sessionPassword);
  }

  public synchronized void recoverAfterFailedSave() {
    if (sessionPassword == null) return;
    char[] password = sessionPassword.clone();
    try { unlock(password); } catch (VaultException error) { lock(); }
    finally { Arrays.fill(password, '\0'); }
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
    if (fields != null) {
      for (String key : fields.keySet()) {
        if (List.of("Title", "UserName", "Password", "URL", "Notes").contains(key)) {
          throw new VaultException("Standard KeePass fields cannot be custom fields: " + key);
        }
      }
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
      verifyMasterPassword(masterPassword);
      Path temporary = Files.createTempFile(path.getParent(), ".remote-manager-", ".kdbx");
      try {
        try (OutputStream output = Files.newOutputStream(temporary);
            KdbxWriter writer = new KdbxWriter(output)) {
          writer.writeKdbxDatabase(database, masterPassword);
        }
        KdbxDatabase saved;
        try (InputStream input = Files.newInputStream(temporary);
            KdbxReader reader = new KdbxReader(input)) {
          saved = reader.readKdbxDatabase(masterPassword);
        }
        verifyUnchanged();
        Files.move(
            temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        database = saved;
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
        entry.getBinaries().stream().map(KdbxEntryBinary::getKey).toList(),
        entry.getPassword() != null && !entry.getPassword().isEmpty());
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

  private void verifyMasterPassword(char[] masterPassword) throws VaultException {
    try (InputStream input = Files.newInputStream(path);
        KdbxReader reader = new KdbxReader(input)) {
      reader.readKdbxDatabase(masterPassword);
    } catch (Exception error) {
      throw new VaultException("The vault master password is incorrect", error);
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
