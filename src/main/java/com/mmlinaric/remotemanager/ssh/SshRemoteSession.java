package com.mmlinaric.remotemanager.ssh;

import com.hierynomus.sshj.userauth.agent.AgentProxy;
import com.hierynomus.sshj.userauth.agent.AuthAgent;
import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.TerminalPanel;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import com.mmlinaric.remotemanager.connection.RemoteSession;
import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.ui.terminal.SshTtyConnector;
import com.mmlinaric.remotemanager.ui.terminal.TerminalFonts;
import com.mmlinaric.remotemanager.vault.Vault;
import com.mmlinaric.remotemanager.vault.VaultException;
import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.userauth.password.PasswordFinder;
import net.schmizz.sshj.userauth.password.Resource;

public final class SshRemoteSession implements RemoteSession {
    public interface KeyPassphrasePrompt {
        char[] ask(String keyName);
    }

    private final Connection connection;
    private final Vault vault;
    private final Path knownHosts;
    private final KnownHostsVerifier.Prompt hostPrompt;
    private final KeyPassphrasePrompt passphrasePrompt;
    private final JediTermWidget terminal;
    private final AtomicReference<Font> terminalFont;
    private int fontSize;

    private volatile SSHClient client;
    private volatile Session channel;
    private volatile Session.Shell shell;

    public SshRemoteSession(
            Connection connection,
            Vault vault,
            Path knownHosts,
            KnownHostsVerifier.Prompt hostPrompt,
            KeyPassphrasePrompt passphrasePrompt,
            String fontName,
            int fontSize,
            int scrollback) {
        this.connection = connection;
        this.vault = vault;
        this.knownHosts = knownHosts;
        this.hostPrompt = hostPrompt;
        this.passphrasePrompt = passphrasePrompt;
        this.fontSize = fontSize;
        this.terminalFont = new AtomicReference<>(TerminalFonts.resolve(fontName, fontSize));
        this.terminal = new ResizableTerminalWidget(new DefaultSettingsProvider() {
            @Override
            public Font getTerminalFont() {
                return terminalFont.get();
            }

            @Override
            public int getBufferMaxLinesCount() {
                return scrollback;
            }
        });
    }

    public int fontSize() {
        return fontSize;
    }

    /** Called on the Swing event thread so the provider and panel change together. */
    public void setTerminalFont(String fontName, int size) {
        fontSize = size;
        terminalFont.set(TerminalFonts.resolve(fontName, size));
        ((ResizableTerminalWidget) terminal).refreshFont();
    }

    private static final class ResizableTerminalWidget extends JediTermWidget {
        private ResizableTerminalWidget(SettingsProvider settings) {
            super(settings);
        }

        @Override
        protected TerminalPanel createTerminalPanel(
                SettingsProvider settings, StyleState style, TerminalTextBuffer buffer) {
            return new ResizableTerminalPanel(settings, buffer, style);
        }

        private void refreshFont() {
            ((ResizableTerminalPanel) getTerminalPanel()).refreshFont();
        }
    }

    private static final class ResizableTerminalPanel extends TerminalPanel {
        private ResizableTerminalPanel(SettingsProvider settings, TerminalTextBuffer buffer, StyleState style) {
            super(settings, buffer, style);
        }

        private void refreshFont() {
            reinitFontAndResize();
            repaint();
        }
    }

    @Override
    public void connect() throws Exception {
        SSHClient ssh = new SSHClient();
        client = ssh;
        try {
            ssh.setConnectTimeout(10000);
            ssh.setTimeout(10000);
            ssh.addHostKeyVerifier(new KnownHostsVerifier(knownHosts, hostPrompt));
            ssh.connect(connection.hostname(), connection.port());
            authenticate(ssh);

            Session session = ssh.startSession();
            channel = session;
            session.allocatePTY("xterm-256color", 80, 24, 0, 0, java.util.Map.of());
            Session.Shell openedShell = session.startShell();
            shell = openedShell;

            SwingUtilities.invokeAndWait(() -> {
                terminal.setTtyConnector(new SshTtyConnector(openedShell));
                terminal.start();
            });
        } catch (Exception error) {
            disconnect();
            throw error;
        }
    }

    private void authenticate(SSHClient ssh) throws Exception {
        switch (connection.authenticationType()) {
            case KDBX_PRIVATE_KEY -> authenticateVaultKey(ssh);
            case SSH_AGENT -> authenticateAgent(ssh);
            case PRIVATE_KEY_FILE -> authenticateFileKey(ssh);
            case PASSWORD -> authenticatePassword(ssh);
        }
    }

    private void authenticatePassword(SSHClient ssh) throws Exception {
        char[] password = vault.getPassword(connection.sshCredentialEntryId())
                .orElseThrow(() -> new VaultException("SSH password is missing in KeePass"));
        try {
            ssh.authPassword(connection.username(), password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void authenticateVaultKey(SSHClient ssh) throws Exception {
        byte[] key = vault.getAttachment(connection.sshCredentialEntryId(), connection.privateKeyAttachmentName())
                .orElseThrow(() -> new VaultException("SSH key attachment is missing in KeePass"));
        char[] passphrase = vault.getPassword(connection.sshCredentialEntryId()).orElse(null);
        try {
            OpenSSHKeyV1KeyFile provider = new OpenSSHKeyV1KeyFile();
            try (InputStreamReader reader =
                    new InputStreamReader(new ByteArrayInputStream(key), StandardCharsets.UTF_8)) {
                provider.init(reader, fixedPassword(passphrase));
                ssh.authPublickey(connection.username(), provider);
            }
        } finally {
            Arrays.fill(key, (byte) 0);
            if (passphrase != null) {
                Arrays.fill(passphrase, '\0');
            }
        }
    }

    private void authenticateFileKey(SSHClient ssh) throws Exception {
        String configured = connection.privateKeyFilePath();
        Path path = expandHome(configured);
        AtomicReference<char[]> passphrase = new AtomicReference<>();
        try {
            KeyProvider key = ssh.loadKeys(
                    path.toString(), promptingPassword(path.getFileName().toString(), passphrase));
            ssh.authPublickey(connection.username(), key);
        } finally {
            char[] entered = passphrase.get();
            if (entered != null) {
                Arrays.fill(entered, '\0');
            }
        }
    }

    private void authenticateAgent(SSHClient ssh) throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        try (AgentProxy agent =
                windows ? new AgentProxy(new WindowsNamedPipeAgentConnection()) : AgentProxy.fromEnvironment()) {
            var methods = AuthAgent.fromIdentities(agent);
            if (methods.isEmpty()) {
                throw new IOException("SSH agent has no available identities");
            }
            ssh.auth(connection.username(), methods);
        }
    }

    private PasswordFinder fixedPassword(char[] password) {
        return new PasswordFinder() {
            @Override
            public char[] reqPassword(Resource<?> resource) {
                return password;
            }

            @Override
            public boolean shouldRetry(Resource<?> resource) {
                return false;
            }
        };
    }

    private PasswordFinder promptingPassword(String name, AtomicReference<char[]> passphrase) {
        return new PasswordFinder() {
            @Override
            public char[] reqPassword(Resource<?> resource) {
                char[] entered = passphrasePrompt.ask(name);
                passphrase.set(entered);
                return entered;
            }

            @Override
            public boolean shouldRetry(Resource<?> resource) {
                return false;
            }
        };
    }

    private static Path expandHome(String configured) {
        if (configured.startsWith("~/") || configured.startsWith("~\\")) {
            return Path.of(System.getProperty("user.home"), configured.substring(2));
        }
        return Path.of(configured);
    }

    @Override
    public void disconnect() {
        Session currentChannel = channel;
        SSHClient currentClient = client;
        channel = null;
        shell = null;
        client = null;
        if (currentChannel != null) {
            try {
                currentChannel.close();
            } catch (IOException ignored) {
                // Continue closing the client even if the channel has closed remotely.
            }
        }
        if (currentClient != null) {
            try {
                currentClient.close();
            } catch (IOException ignored) {
                // There is no further network resource to release.
            }
        }
        SwingUtilities.invokeLater(terminal::close);
    }

    @Override
    public boolean isConnected() {
        return client != null && client.isConnected() && shell != null && shell.isOpen();
    }

    @Override
    public JComponent component() {
        return terminal;
    }
}
