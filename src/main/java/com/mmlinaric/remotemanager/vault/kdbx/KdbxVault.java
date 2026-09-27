package com.mmlinaric.remotemanager.vault.kdbx;

import com.mmlinaric.remotemanager.model.AuthenticationType;
import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.model.ConnectionFolder;
import com.mmlinaric.remotemanager.vault.Vault;
import com.mmlinaric.remotemanager.vault.VaultConflictException;
import com.mmlinaric.remotemanager.vault.VaultEntry;
import com.mmlinaric.remotemanager.vault.VaultException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.linguafranca.pwdb.PropertyValue;
import org.linguafranca.pwdb.kdbx.KdbxCreds;
import org.linguafranca.pwdb.kdbx.KdbxHeader;
import org.linguafranca.pwdb.kdbx.KdbxStreamFormat;
import org.linguafranca.pwdb.kdbx.jackson.JacksonDatabase;
import org.linguafranca.pwdb.kdbx.jackson.JacksonEntry;
import org.linguafranca.pwdb.kdbx.jackson.JacksonGroup;

/** KeePassJava2 adapter for the app's KDBX4 workspace. */
public final class KdbxVault implements Vault {
  private static final String ROLE = "RemoteManager.Role";
  private static final String ID = "RemoteManager.Id";
  private static final String HOST_ROOT = "hosts-root";
  private static final String IDENTITY_ROOT = "identities-root";
  private static final String HOST_SECRET_ROOT = "host-secrets-root";
  private static final String MARKER = "group-marker";
  private static final String HOST = "host";
  public static final String HOST_SECRET = "host-secret";
  public static final String SECRET_OWNER = "RemoteManager.OwnerHostId";
  public static final String SECRET_PURPOSE = "RemoteManager.SecretPurpose";
  private static final String PREFIX = "RemoteManager.";

  private final Path path;
  private JacksonDatabase database;
  private byte[] loadedDigest;
  private char[] sessionPassword;

  public KdbxVault(Path path) {
    this.path = path.toAbsolutePath();
  }

  public Path path() {
    return path;
  }

  public static void create(Path path, char[] masterPassword) throws VaultException {
    if (Files.exists(path)) throw new VaultException("Vault file already exists");
    boolean created = false;
    try {
      Files.createDirectories(path.toAbsolutePath().getParent());
      JacksonDatabase fresh = new JacksonDatabase();
      try (OutputStream output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
        created = true;
        write(fresh, masterPassword, output);
      }
    } catch (Exception error) {
      if (created) try { Files.deleteIfExists(path); } catch (IOException ignored) { }
      throw new VaultException("Could not create KeePass vault", error);
    }
  }

  @Override
  public synchronized void unlock(char[] masterPassword) throws VaultException {
    try {
      JacksonDatabase loaded = read(path, masterPassword);
      byte[] digest = digest(path);
      database = loaded;
      loadedDigest = digest;
      if (sessionPassword != null) Arrays.fill(sessionPassword, '\0');
      sessionPassword = masterPassword.clone();
    } catch (Exception error) {
      lock();
      throw new VaultException("Could not unlock KeePass vault", error);
    }
  }

  @Override
  public synchronized void lock() {
    database = null;
    if (loadedDigest != null) Arrays.fill(loadedDigest, (byte) 0);
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
    return allEntries().stream()
        .filter(entry -> !isRole(entry, HOST) && !isRole(entry, HOST_SECRET)
            && !isRole(entry, MARKER))
        .map(this::summary)
        .sorted((a, b) -> a.toString().compareToIgnoreCase(b.toString()))
        .toList();
  }

  public synchronized List<VaultEntry> credentialEntries() throws VaultException {
    requireUnlocked();
    return allEntries().stream()
        .filter(entry -> !isRole(entry, HOST) && !isRole(entry, MARKER))
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
    return find(id).map(entry -> entry.getPropertyValue("Password"))
        .filter(value -> value != null)
        .map(PropertyValue::getValueAsChars)
        .filter(value -> value.length != 0);
  }

  @Override
  public synchronized Optional<byte[]> getAttachment(UUID id, String attachmentName)
      throws VaultException {
    JacksonEntry entry = find(id).orElseThrow(() -> new VaultException("KeePass entry is missing"));
    try {
      return Optional.ofNullable(entry.getBinaryProperty(attachmentName));
    } catch (Exception error) {
      throw new VaultException("Could not read KeePass attachment", error);
    }
  }

  public synchronized UUID addEntry(String title, String username, char[] password,
      Map<String, String> fields, String attachmentName, byte[] attachment) throws VaultException {
    return addEntry(UUID.randomUUID(), title, username, password, fields, attachmentName, attachment);
  }

  public synchronized UUID addEntry(UUID id, String title, String username, char[] password,
      Map<String, String> fields, String attachmentName, byte[] attachment) throws VaultException {
    requireUnlocked();
    if (find(id).isPresent()) throw new VaultException("Identity ID already exists");
    rejectReservedFields(fields);
    JacksonEntry entry = database.newEntry();
    entry.setProperty(ID, id.toString());
    updateValues(entry, title, username, password, fields, attachmentName, attachment);
    managedRoot(IDENTITY_ROOT, "Remote Manager Identities").addEntry(entry);
    return id;
  }

  public synchronized void deleteIdentity(UUID id) throws VaultException {
    JacksonEntry entry = find(id).orElseThrow(() -> new VaultException("Identity is missing"));
    if (isRole(entry, HOST) || isRole(entry, HOST_SECRET) || isRole(entry, MARKER))
      throw new VaultException("Identity is missing");
    for (Connection connection : connections()) {
      if (id.equals(connection.sshCredentialEntryId()) || id.equals(connection.sudoCredentialEntryId()))
        throw new VaultException("Identity is used by host: " + connection.name());
    }
    entry.getParent().removeEntry(entry);
  }

  public synchronized void putHostSecret(UUID id, UUID ownerHostId, String purpose,
      String title, String username, char[] password) throws VaultException {
    requireUnlocked();
    if (!"ssh".equals(purpose) && !"sudo".equals(purpose))
      throw new VaultException("Invalid host password purpose");
    JacksonEntry entry = find(id).orElse(null);
    if (entry == null) {
      if (password == null || password.length == 0)
        throw new VaultException("Enter a password for this host");
      entry = database.newEntry();
      entry.setProperty(ID, id.toString());
      entry.setProperty(ROLE, HOST_SECRET);
      entry.setProperty(SECRET_OWNER, ownerHostId.toString());
      entry.setProperty(SECRET_PURPOSE, purpose);
      updateValues(entry, title, username, password, null, null, null);
      managedRoot(HOST_SECRET_ROOT, "Remote Manager Host Passwords").addEntry(entry);
    } else {
      if (!isRole(entry, HOST_SECRET)
          || !ownerHostId.toString().equals(entry.getProperty(SECRET_OWNER))
          || !purpose.equals(entry.getProperty(SECRET_PURPOSE)))
        throw new VaultException("Host password belongs to a different host");
      updateValues(entry, title, username, password, null, null, null);
    }
  }

  private void pruneHostSecrets(UUID ownerHostId, Connection connection) throws VaultException {
    for (JacksonEntry entry : allEntries()) {
      if (!isRole(entry, HOST_SECRET)
          || !ownerHostId.toString().equals(entry.getProperty(SECRET_OWNER))) continue;
      UUID id = logicalId(entry);
      if (connection == null || (!id.equals(connection.sshCredentialEntryId())
          && !id.equals(connection.sudoCredentialEntryId()))) entry.getParent().removeEntry(entry);
    }
  }

  public synchronized List<ConnectionFolder> folders() throws VaultException {
    requireUnlocked();
    JacksonGroup root = findManagedRoot(HOST_ROOT);
    List<ConnectionFolder> result = new ArrayList<>();
    if (root != null) collectFolders(root, null, result);
    return result;
  }

  private void collectFolders(JacksonGroup parent, UUID parentId, List<ConnectionFolder> result) {
    for (JacksonGroup child : parent.getGroups()) {
      UUID id = child.getUuid();
      result.add(new ConnectionFolder(id, parentId, child.getName(), 0));
      collectFolders(child, id, result);
    }
  }

  public synchronized List<Connection> connections() throws VaultException {
    requireUnlocked();
    JacksonGroup root = findManagedRoot(HOST_ROOT);
    List<Connection> result = new ArrayList<>();
    if (root != null) collectConnections(root, null, result);
    return result;
  }

  private void collectConnections(JacksonGroup group, UUID parentId, List<Connection> result)
      throws VaultException {
    for (JacksonEntry entry : group.getEntries()) {
      if (!isRole(entry, HOST)) continue;
      try {
        result.add(new Connection(logicalId(entry), entry.getTitle(), entry.getProperty(PREFIX + "Hostname"),
            Integer.parseInt(entry.getProperty(PREFIX + "Port")), entry.getUsername(), parentId,
            AuthenticationType.valueOf(entry.getProperty(PREFIX + "Auth")),
            optionalUuid(entry.getProperty(PREFIX + "CredentialId")),
            optionalUuid(entry.getProperty(PREFIX + "SudoId")),
            blankToNull(entry.getProperty(PREFIX + "KeyAttachment")),
            blankToNull(entry.getProperty(PREFIX + "KeyFile")),
            entry.getNotes(), number(entry.getProperty(PREFIX + "SortOrder"))));
      } catch (RuntimeException error) {
        throw new VaultException("Invalid saved host: " + entry.getTitle(), error);
      }
    }
    for (JacksonGroup child : group.getGroups()) collectConnections(child, child.getUuid(), result);
  }

  public synchronized UUID createFolder(UUID parentId, String name) throws VaultException {
    requireUnlocked();
    if (name == null || name.isBlank()) throw new VaultException("Folder name is required");
    JacksonGroup root = managedRoot(HOST_ROOT, "Remote Manager Hosts");
    JacksonGroup parent = parentId == null ? root : findGroup(root, parentId);
    if (parent == null) throw new VaultException("Parent folder is missing");
    JacksonGroup folder = database.newGroup(name);
    parent.addGroup(folder);
    return folder.getUuid();
  }

  public synchronized void renameFolder(UUID id, String name) throws VaultException {
    requireUnlocked();
    if (name == null || name.isBlank()) throw new VaultException("Folder name is required");
    JacksonGroup root = findManagedRoot(HOST_ROOT);
    JacksonGroup folder = root == null ? null : findGroup(root, id);
    if (folder == null || folder == root) throw new VaultException("Folder is missing");
    folder.setName(name);
  }

  public synchronized void deleteFolder(UUID id) throws VaultException {
    requireUnlocked();
    JacksonGroup root = findManagedRoot(HOST_ROOT);
    JacksonGroup folder = root == null ? null : findGroup(root, id);
    if (folder == null || folder == root) throw new VaultException("Folder is missing");
    if (!folder.getEntries().isEmpty() || !folder.getGroups().isEmpty())
      throw new VaultException("Move or delete this folder's contents first");
    folder.getParent().removeGroup(folder);
  }

  public synchronized void putConnection(Connection connection) throws VaultException {
    requireUnlocked();
    JacksonGroup root = managedRoot(HOST_ROOT, "Remote Manager Hosts");
    JacksonGroup parent = connection.parentFolderId() == null
        ? root : findGroup(root, connection.parentFolderId());
    if (parent == null) throw new VaultException("Parent folder is missing");
    JacksonEntry entry = find(connection.id()).orElse(null);
    if (entry != null && !isRole(entry, HOST))
      throw new VaultException("Host ID conflicts with an identity");
    if (entry == null) {
      entry = database.newEntry();
      entry.setProperty(ID, connection.id().toString());
    } else {
      entry.getParent().removeEntry(entry);
    }
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
    parent.addEntry(entry);
    pruneHostSecrets(connection.id(), connection);
  }

  public synchronized void deleteConnection(UUID id) throws VaultException {
    JacksonEntry entry = find(id).orElse(null);
    if (entry == null || !isRole(entry, HOST)) throw new VaultException("Host is missing");
    entry.getParent().removeEntry(entry);
    pruneHostSecrets(id, null);
  }

  private JacksonGroup managedRoot(String role, String name) throws VaultException {
    JacksonGroup root = findManagedRoot(role);
    if (root != null) return root;
    root = database.newGroup(name);
    database.getRootGroup().addGroup(root);
    JacksonEntry marker = database.newEntry();
    marker.setTitle("Remote Manager group marker");
    marker.setProperty(ROLE, MARKER);
    marker.setProperty(PREFIX + "GroupRole", role);
    root.addEntry(marker);
    return root;
  }

  private JacksonGroup findManagedRoot(String role) {
    return database == null ? null : findManagedRoot(database.getRootGroup(), role);
  }

  private JacksonGroup findManagedRoot(JacksonGroup group, String role) {
    for (JacksonEntry entry : group.getEntries()) {
      if (isRole(entry, MARKER) && role.equals(entry.getProperty(PREFIX + "GroupRole"))) return group;
    }
    for (JacksonGroup child : group.getGroups()) {
      JacksonGroup found = findManagedRoot(child, role);
      if (found != null) return found;
    }
    return null;
  }

  private static JacksonGroup findGroup(JacksonGroup root, UUID id) {
    if (id.equals(root.getUuid())) return root;
    for (JacksonGroup child : root.getGroups()) {
      JacksonGroup found = findGroup(child, id);
      if (found != null) return found;
    }
    return null;
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

  public synchronized void updateEntry(UUID id, String title, String username, char[] password,
      Map<String, String> fields, String attachmentName, byte[] attachment) throws VaultException {
    JacksonEntry entry = find(id).orElseThrow(() -> new VaultException("KeePass entry is missing"));
    if (isRole(entry, HOST) || isRole(entry, HOST_SECRET) || isRole(entry, MARKER))
      throw new VaultException("Identity is missing");
    rejectReservedFields(fields);
    updateValues(entry, title, username, password, fields, attachmentName, attachment);
  }

  private static void rejectReservedFields(Map<String, String> fields) throws VaultException {
    if (fields == null) return;
    for (String key : fields.keySet()) {
      if (key == null || key.equals("Title") || key.equals("UserName") || key.equals("Password")
          || key.equals("URL") || key.equals("Notes") || key.startsWith(PREFIX))
        throw new VaultException("Reserved KeePass field cannot be custom: " + key);
    }
  }

  private void updateValues(JacksonEntry entry, String title, String username, char[] password,
      Map<String, String> fields, String attachmentName, byte[] attachment) throws VaultException {
    if (title == null || title.isBlank()) throw new VaultException("Entry title is required");
    entry.setTitle(title);
    entry.setUsername(username == null ? "" : username);
    if (password != null) entry.setPropertyValue("Password",
        database.getPropertyValueStrategy().newProtected().of(password));
    if (fields != null) fields.forEach(entry::setProperty);
    if (attachmentName != null && attachment != null) {
      try { entry.setBinaryProperty(attachmentName, attachment); }
      catch (Exception error) { throw new VaultException("Could not store attachment", error); }
    }
  }

  public synchronized void save(char[] masterPassword) throws VaultException {
    requireUnlocked();
    try {
      verifyUnchanged();
      verifyMasterPassword(masterPassword);
      Map<String, String> before = snapshot(database);
      Path temporary = Files.createTempFile(path.getParent(), ".remote-manager-", ".kdbx");
      try {
        try (OutputStream output = Files.newOutputStream(temporary)) {
          write(database, masterPassword, output);
        }
        JacksonDatabase saved = read(temporary, masterPassword);
        if (!before.equals(snapshot(saved)))
          throw new VaultException("KeePass vault changed during serialization");
        verifyUnchanged();
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
        database = saved;
        loadedDigest = digest(path);
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (VaultConflictException error) {
      throw error;
    } catch (Exception error) {
      throw new VaultException("Could not save KeePass vault", error);
    }
  }

  private Optional<JacksonEntry> find(UUID id) throws VaultException {
    requireUnlocked();
    JacksonEntry result = null;
    for (JacksonEntry entry : allEntries()) {
      if (!id.equals(logicalId(entry))) continue;
      if (result != null) throw new VaultException("Duplicate KeePass entry ID: " + id);
      result = entry;
    }
    return Optional.ofNullable(result);
  }

  private static UUID logicalId(JacksonEntry entry) {
    String value = entry.getProperty(ID);
    return value == null || value.isBlank() ? entry.getUuid() : UUID.fromString(value);
  }

  private List<JacksonEntry> allEntries() {
    List<JacksonEntry> result = new ArrayList<>();
    collectEntries(database.getRootGroup(), result);
    return result;
  }

  private static void collectEntries(JacksonGroup group, List<JacksonEntry> result) {
    result.addAll(group.getEntries());
    for (JacksonGroup child : group.getGroups()) collectEntries(child, result);
  }

  private VaultEntry summary(JacksonEntry entry) {
    Map<String, String> fields = new HashMap<>();
    for (String name : entry.getPropertyNames()) {
      if (!"Password".equals(name)) fields.put(name, string(entry.getProperty(name)));
    }
    PropertyValue password = entry.getPropertyValue("Password");
    char[] chars = password == null ? null : password.getValueAsChars();
    boolean hasPassword = chars != null && chars.length != 0;
    if (chars != null) Arrays.fill(chars, '\0');
    return new VaultEntry(logicalId(entry), entry.getTitle(), entry.getUsername(),
        Map.copyOf(fields), entry.getBinaryPropertyNames(), hasPassword);
  }

  private void requireUnlocked() throws VaultException {
    if (database == null) throw new VaultException("Vault is locked");
  }

  private void verifyUnchanged() throws Exception {
    if (!MessageDigest.isEqual(loadedDigest, digest(path))) throw new VaultConflictException();
  }

  private void verifyMasterPassword(char[] masterPassword) throws VaultException {
    try { read(path, masterPassword); }
    catch (Exception error) { throw new VaultException("The vault master password is incorrect", error); }
  }

  private static JacksonDatabase read(Path path, char[] password) throws Exception {
    KdbxCreds credentials = credentials(password);
    try (InputStream input = Files.newInputStream(path)) {
      return JacksonDatabase.load(credentials, input);
    } finally {
      Arrays.fill(credentials.getKey(), (byte) 0);
    }
  }

  private static void write(JacksonDatabase database, char[] password, OutputStream output)
      throws Exception {
    KdbxCreds credentials = credentials(password);
    try {
      // Reusing a loaded header appends its attachment binaries again on each save.
      database.save(new KdbxStreamFormat(new KdbxHeader(4)), credentials, output);
    } finally {
      Arrays.fill(credentials.getKey(), (byte) 0);
    }
  }

  private static KdbxCreds credentials(char[] password) {
    byte[] bytes = PropertyValue.charsToBytes(password);
    try { return new KdbxCreds(bytes); }
    finally { Arrays.fill(bytes, (byte) 0); }
  }

  private static Map<String, String> snapshot(JacksonDatabase database) {
    Map<String, String> result = new HashMap<>();
    snapshotGroup(database.getRootGroup(), null, result);
    return result;
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
      for (String name : entry.getBinaryPropertyNames())
        result.put(key + "/binary/" + name, hash(entry.getBinaryProperty(name)));
    }
    for (JacksonGroup child : group.getGroups()) snapshotGroup(child, group.getUuid(), result);
  }

  private static String hash(byte[] value) {
    return Base64.getEncoder().encodeToString(sha256().digest(value));
  }

  private static boolean isRole(JacksonEntry entry, String role) {
    return role.equals(entry.getProperty(ROLE));
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

  private static byte[] digest(Path path) throws IOException {
    try (InputStream input = Files.newInputStream(path)) {
      MessageDigest hash = sha256();
      byte[] buffer = new byte[8192];
      int length;
      while ((length = input.read(buffer)) != -1) hash.update(buffer, 0, length);
      Arrays.fill(buffer, (byte) 0);
      return hash.digest();
    }
  }

  private static MessageDigest sha256() {
    try { return MessageDigest.getInstance("SHA-256"); }
    catch (java.security.NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is unavailable", error);
    }
  }
}
