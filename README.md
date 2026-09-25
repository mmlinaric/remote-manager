# Remote Manager

A classic Swing SSH connection manager with reusable identities in a normal KDBX4 KeePass vault. The interface uses FamFamFam Silk icons; see [third-party notices](src/main/resources/THIRD_PARTY_NOTICES.md).

## Build and run

Install Java 25, then run:

```sh
./mvnw test
./mvnw exec:exec
```

On Windows, use `mvnw.cmd`. The app uses the system Swing look and feel. Native installers are not included yet.

## First steps

1. Create a KeePass vault or open an existing `.kdbx` file. Enter its master password to unlock the workspace.
2. Click **New identity** to save a reusable SSH password, private key attachment, or sudo password. You can also create an identity while adding a host.
3. Click **New host**. Enter its address and SSH username, then choose an authentication method. Password and vault private-key methods need a suitable identity; SSH agent and local key-file methods do not.
4. Save the host and double-click it to connect. The host tree marks missing identities or key attachments and offers to edit the host before connecting.

The **Hosts** and **Identities** sidebar sections sit beside terminal tabs. The main workspace stays hidden until the vault is unlocked. Locking the vault disconnects sessions and clears the visible host and identity lists. Auto-lock is based on app activity, defaults to 30 minutes, and can be changed to 5, 15, 30 minutes, or Never in Settings.

## Data and KeePass compatibility

Hosts and folders are stored inside the selected KDBX file, together with identities. Hosts are KeePass entries in a marked “Remote Manager Hosts” group. Folders are nested KeePass groups. A host's name, address, username, authentication choice, identity references, key-file path, and notes are encrypted with the vault. New identities are saved in a separate “Remote Manager Identities” group. Existing non-host KeePass entries are available as identities. Entries are referenced by UUID, so renaming or moving an identity does not break its host reference.

The only separate app data is `settings.properties` under the OS-specific Remote Manager data directory. It stores the selected vault path, window geometry, terminal preferences, known-hosts path, and auto-lock setting. It contains no host details or secrets. SQLite and Flyway are no longer used. Old `connections.db` files are not read.

Vault writes check for changes made by another application and reject a conflicting save. Reload the vault to see external edits. The app writes a temporary KDBX file and replaces the original only after checking that the new file can be opened. Keep normal backups of your vault.

## Authentication and sessions

For password authentication, enter an SSH password directly in the host editor. You can also save a sudo password there. These host passwords live in the KeePass vault and do not appear as reusable identities. Leave a saved host password field blank while editing to keep its current value. Choose **Reusable identity** when several hosts should share a credential, or to use a private key attachment. A local private key file or SSH agent can be used without an identity. Copy a host's sudo password from the active session's **Copy sudo password** button or **Session** menu, or right-click the saved host in the tree to copy before connecting. **Ctrl+Shift+P** (Windows/Linux) or **Cmd+Shift+P** (macOS) copies from the selected host when the host tree has focus, otherwise from the active session; if no session is open, it uses the selected host. The password is copied for manual paste. The clipboard is cleared after the configured timeout only if it still contains the value copied by this app.

Key file buttons open the system file dialog in `~/.ssh` when that directory exists. You can also paste a full path into the private-key attachment field or the host's local key-file field.

The configured OpenSSH `known_hosts` file is checked on each connection. Unknown keys require explicit trust. Changed known keys are rejected. SSHJ handles transport and authentication; JediTerm displays terminal sessions. The Soderer KDBX library reads and writes the KeePass vault.

The master password is kept as a mutable character array only while the vault is unlocked so edits can be saved without another prompt. It is cleared on lock or exit. Java and third-party libraries can create temporary copies, so complete memory erasure cannot be guaranteed. The app does not log credentials.
