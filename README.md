# Remote Manager

A compact Java 25 desktop SSH connection manager built with Swing. It stores connection metadata in SQLite and credentials in a normal KDBX4 KeePass database.

## Build and run

Install Java 25. The Maven Wrapper downloads Maven if it is not installed locally.

```sh
./mvnw test
./mvnw exec:exec
```

On Windows, use `mvnw.cmd`. The app uses the system Swing look and feel. Java and its desktop runtime are needed on Windows, Linux, and macOS. Native installers are not included yet; the project can later be packaged with `jpackage`.

## Design

`app` starts the program and selects OS-specific data paths. `ui` contains the Swing window, connection editor, terminal tabs, vault browser, and settings dialog. `model` defines connection records. `persistence` contains SQLite access and Flyway migrations. `vault` exposes the credential interface and the `kdbx` package implements it. `ssh` contains SSHJ transport and host-key checks. `connection.RemoteSession` is the small protocol-independent session interface.

The SQLite database holds folders, connection names, hosts, usernames, authentication choices, KeePass entry UUID references, and UI settings. It contains no passwords or private keys. Its schema is created through versioned Flyway migrations in `src/main/resources/db/migration`.

The selected `.kdbx` file holds credentials. It remains a normal KeePass/KeePassXC database that you can open independently. Entries are referenced by their KeePass UUIDs, so you can rename or move them without changing the connection reference. The vault starts locked and prompts for its master password when a credential is needed. The master password is not stored by the app. You can lock or reload the vault from the File menu. Settings offer automatic locking after 5, 15, or 30 minutes, or never.

For a KeePass private key connection, choose a credential entry and the attachment that contains its OpenSSH private key. The entry password may hold the key passphrase. The key is parsed from memory for authentication. Password authentication also uses a KeePass entry. A private key file may be selected instead. SSH agent authentication uses the agent socket on Unix systems or the OpenSSH named pipe on Windows. The agent supplies signatures, so its private key is not copied into the app.

An example KeePassXC layout:

```text
RemoteManager
├── Shared
│   └── Linux sudo
│       Password: ...
└── SSH
    └── Homelab SSH
        Username: mario
        Password: optional private-key passphrase
        Attachment: id_ed25519
```

Several connections can reference the same SSH or sudo entry UUID. In an SSH tab, use Session > Copy sudo password to copy the assigned sudo password. The user pastes it into the terminal manually. The default clipboard timeout is 30 seconds. Cleanup only clears the clipboard if its text still equals the value copied by this app.

The configured OpenSSH `known_hosts` file is checked on each connection. An unknown key requires explicit trust. A changed known key displays both fingerprints and is rejected. There is no setting to disable host-key checking.

## Libraries

SSHJ provides SSH transport, key parsing, authentication, and known-host support. JediTerm provides the Swing VT terminal. The Soderer KDBX library reads and writes KDBX4 entries and attachments. SQLite JDBC stores non-secret metadata, and Flyway applies explicit schema migrations. SLF4J and Logback handle application logging. JNA is used only for the Windows OpenSSH agent named pipe. No application framework or custom cryptography is used.

These libraries are portable Java dependencies except for JNA's platform access to the Windows agent pipe. SSH agent availability depends on the operating system configuration. The app currently accepts OpenSSH-format attached private keys. External private key file formats are handled by SSHJ.

## Security and current limits

The vault is checked for external file changes before each save. A conflicting change is rejected rather than overwritten. Reload the vault to see external edits. The app does not merge simultaneous changes. Decrypted passwords and key bytes are cleared from mutable buffers where practical, but Java cannot guarantee complete memory erasure. Clipboard managers and terminal applications may retain pasted text. Logs must never include credentials.

Terminal tabs support independent connections and PTY resizing. Fedora and other Linux systems use the usual Unix agent socket; Windows uses the OpenSSH agent named pipe. A real SSH server and all full-screen terminal programs have not been covered by automated integration tests yet. KeePassXC 2.7.12 CLI was used to verify that a vault created by this app and an entry with a binary attachment written to a KeePassXC-created vault can be opened there.
