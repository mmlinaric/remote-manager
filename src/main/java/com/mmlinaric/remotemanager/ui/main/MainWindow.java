package com.mmlinaric.remotemanager.ui.main;

import com.mmlinaric.remotemanager.app.AppVersion;
import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.model.ConnectionFolder;
import com.mmlinaric.remotemanager.persistence.FolderExpansionPreferences;
import com.mmlinaric.remotemanager.persistence.SettingsRepository;
import com.mmlinaric.remotemanager.ui.DialogEscape;
import com.mmlinaric.remotemanager.ui.SilkIcons;
import com.mmlinaric.remotemanager.ui.connections.ConnectionEditor;
import com.mmlinaric.remotemanager.ui.connections.ConnectionTreePanel;
import com.mmlinaric.remotemanager.ui.settings.AppSettings;
import com.mmlinaric.remotemanager.ui.settings.SettingsDialog;
import com.mmlinaric.remotemanager.ui.terminal.SessionTabs;
import com.mmlinaric.remotemanager.ui.vault.IdentityEditor;
import com.mmlinaric.remotemanager.update.UpdateController;
import com.mmlinaric.remotemanager.vault.HostSecretDraft;
import com.mmlinaric.remotemanager.vault.IdentityDraft;
import com.mmlinaric.remotemanager.vault.VaultAttachment;
import com.mmlinaric.remotemanager.vault.VaultEntry;
import com.mmlinaric.remotemanager.vault.WorkspaceVault;
import com.mmlinaric.remotemanager.vault.kdbx.KdbxVault;
import com.mmlinaric.remotemanager.workspace.VaultWorkspace;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.LayoutFocusTraversalPolicy;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Vault-first desktop workspace. All host and identity data comes from the unlocked KDBX file. */
public final class MainWindow extends JFrame {
    private final SettingsRepository settings;
    private final WindowPreferences windowPreferences;
    private final UpdateController updates;
    private final FolderExpansionPreferences folderExpansion;
    private final JPanel cards = new JPanel(new CardLayout());
    private JLabel vaultPath;
    private final JLabel status = new JLabel("Open or create a vault to begin.");
    private final JLabel vaultState = new JLabel("Vault locked  |  0 sessions");
    private final JTextField quickConnect = new JTextField(20);
    private final JList<VaultEntry> identities = new JList<>();
    private final JLabel identityHint = new JLabel("  No identities yet. Create one here or while adding a host.");
    private final List<AbstractButton> vaultActions = new ArrayList<>();
    private final List<AbstractButton> hostActions = new ArrayList<>();
    private final List<AbstractButton> identityActions = new ArrayList<>();
    private final List<AbstractButton> sessionActions = new ArrayList<>();
    private JMenuItem unlockMenuItem;
    private JMenuItem connectSelectedItem;
    private JMenuItem copySudoSessionItem;
    private JButton copySudoButton;
    private JButton unlockWelcomeButton;
    private JButton openWelcomeButton;
    private JButton createWelcomeButton;
    private JMenuItem openVaultMenuItem;
    private JMenuItem createVaultMenuItem;
    private JMenuItem exitMenuItem;
    private JProgressBar creationProgress;
    private WorkspaceVault vault;
    private VaultWorkspace vaultWorkspace;
    private AppSettings preferences;
    private final SessionTabs tabs =
            new SessionTabs(() -> vault, this::sessionReady, this::showStatus, () -> preferences, this::currentHost);
    private final KeyEventDispatcher terminalFontKeys = this::dispatchTerminalFontKey;
    private final PropertyChangeListener buttonFocus = event -> {
        if (event.getNewValue() instanceof JButton button) button.setFocusPainted(false);
    };
    private final ConnectionTreePanel connectionTree = new ConnectionTreePanel(new TreeActions());
    private final JSplitPane workspace;
    private JSplitPane hosts;
    private final int initialSidebarWidth;
    private final int initialDetailsHeight;
    private int detailsHeight;
    private final AutoLockMonitor autoLock;
    private boolean locked = true;
    private boolean creatingVault;
    private boolean savingVault;
    private boolean sidebarWidthInitialized;
    private boolean detailsHeightInitialized;
    private boolean connectionsLoaded;
    private long operationVersion;

    public MainWindow(SettingsRepository settings) {
        super("Remote Manager");
        this.settings = settings;
        this.windowPreferences = new WindowPreferences(settings);
        this.updates = new UpdateController(this, settings);
        this.folderExpansion = new FolderExpansionPreferences(settings);
        this.preferences = AppSettings.load(settings);
        this.initialSidebarWidth = windowPreferences.sidebarWidth();
        this.workspace = createWorkspace();
        int savedDetailsHeight = windowPreferences.detailsHeight();
        this.initialDetailsHeight =
                Math.max(savedDetailsHeight, connectionTree.details().getPreferredSize().height);
        workspace.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                initializeSidebarWidth();
            }
        });
        connectionTree.onSelectionChanged(this::updateActions);
        identities.addListSelectionListener(event -> updateActions());
        tabs.addChangeListener(event -> {
            updateActions();
            updateVaultState();
        });
        settings.get("vault.path").ifPresent(path -> selectVault(new KdbxVault(Path.of(path))));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(800, 520));
        windowPreferences.restoreFrame(this);
        setJMenuBar(menu());
        installSudoShortcut();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(terminalFontKeys);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", buttonFocus);
        VaultWelcomePanel welcome = new VaultWelcomePanel(this::unlockVault, this::openVault, this::createVault);
        vaultPath = welcome.vaultPath();
        unlockWelcomeButton = welcome.unlockButton();
        openWelcomeButton = welcome.openButton();
        createWelcomeButton = welcome.createButton();
        creationProgress = welcome.creationProgress();
        cards.add(welcome, "locked");
        cards.add(workspacePanel(), "workspace");
        add(cards, BorderLayout.CENTER);
        JPanel statusBar = new JPanel(new BorderLayout(12, 0));
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        statusBar.add(status, BorderLayout.CENTER);
        statusBar.add(vaultState, BorderLayout.EAST);
        JPanel south = new JPanel(new BorderLayout());
        south.add(updates.banner(), BorderLayout.NORTH);
        south.add(statusBar, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
        this.autoLock =
                new AutoLockMonitor(this, () -> locked, () -> preferences.vaultAutoLockMinutes(), this::lockVault);
        showLocked();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeWindow();
            }
        });
    }

    private JPanel workspacePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(vaultButton("New host", SilkIcons.NEW_CONNECTION, () -> editHost(null, null)));
        toolbar.add(vaultButton("New identity", SilkIcons.NEW_IDENTITY, () -> editIdentity(null)));
        toolbar.addSeparator();
        toolbar.add(new JLabel("Host: "));
        toolbar.add(quickConnect);
        toolbar.add(vaultButton("Connect", SilkIcons.CONNECT, this::quickConnect));
        toolbar.addSeparator();
        toolbar.add(vaultButton("Lock", SilkIcons.LOCK, this::lockVault));
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(workspace, BorderLayout.CENTER);
        return panel;
    }

    private JSplitPane createWorkspace() {
        JTabbedPane sidebar = new JTabbedPane();
        sidebar.setMinimumSize(new Dimension(190, 0));
        hosts = new JSplitPane(JSplitPane.VERTICAL_SPLIT, connectionTree, connectionTree.details());
        hosts.setContinuousLayout(true);
        hosts.setResizeWeight(1.0);
        hosts.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> {
            if (detailsHeightInitialized && hosts.isShowing() && hosts.getHeight() > 0)
                detailsHeight = currentDetailsHeight();
        });
        sidebar.addTab("Hosts", SilkIcons.CONNECTION, hosts);
        JPanel identityPanel = new JPanel(new BorderLayout());
        identities.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean selected, boolean focused) {
                VaultEntry entry = (VaultEntry) value;
                String text = entry == null
                        ? ""
                        : entry.title()
                                + (entry.username() == null || entry.username().isBlank()
                                        ? ""
                                        : "  (" + entry.username() + ")")
                                + (entry.hasPassword() ? "  [password]" : "")
                                + (entry.attachments().isEmpty() ? "" : "  [key/file]");
                super.getListCellRendererComponent(list, text, index, selected, focused);
                setIcon(SilkIcons.VAULT);
                return this;
            }
        });
        identities.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (!savingVault && event.getClickCount() == 2 && identities.getSelectedValue() != null)
                    editIdentity(identities.getSelectedValue());
            }
        });
        identityPanel.add(identityHint, BorderLayout.NORTH);
        identityPanel.add(new JScrollPane(identities), BorderLayout.CENTER);
        JPanel identityActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        identityActions.add(vaultButton("New", SilkIcons.NEW_IDENTITY, () -> editIdentity(null)));
        identityActions.add(
                selectedIdentityButton("Edit", SilkIcons.EDIT, () -> editIdentity(identities.getSelectedValue())));
        identityActions.add(selectedIdentityButton("Delete", SilkIcons.DELETE, this::deleteIdentity));
        identityPanel.add(identityActions, BorderLayout.SOUTH);
        sidebar.addTab("Identities", SilkIcons.VAULT, identityPanel);
        JPanel right = new JPanel(new CardLayout());
        JPanel empty = new JPanel(new GridBagLayout());
        empty.add(new JLabel("Double-click a host to open an SSH session."));
        JPanel sessionPanel = new JPanel(new BorderLayout());
        JPanel sessionActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
        copySudoButton = button("Copy sudo password", SilkIcons.COPY_PASSWORD, tabs::copySudoPassword);
        copySudoButton.setToolTipText("Copy the active session's sudo password");
        sessionActions.add(copySudoButton);
        sessionPanel.add(sessionActions, BorderLayout.NORTH);
        sessionPanel.add(tabs, BorderLayout.CENTER);
        right.add(empty, "empty");
        right.add(sessionPanel, "tabs");
        tabs.addChangeListener(
                event -> ((CardLayout) right.getLayout()).show(right, tabs.getTabCount() == 0 ? "empty" : "tabs"));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, right);
        right.setMinimumSize(new Dimension(300, 0));
        split.setContinuousLayout(true);
        split.setResizeWeight(0);
        return split;
    }

    private JMenuBar menu() {
        JMenuBar bar = new JMenuBar();
        JMenu file = topMenu("File");
        file.add(vaultItem("New host", SilkIcons.NEW_CONNECTION, () -> editHost(null, null)));
        file.add(vaultItem("New folder", SilkIcons.NEW_FOLDER, () -> newFolder(null)));
        file.add(vaultItem("New identity", SilkIcons.NEW_IDENTITY, () -> editIdentity(null)));
        file.addSeparator();
        openVaultMenuItem = item("Open vault...", SilkIcons.OPEN_VAULT, this::openVault);
        file.add(openVaultMenuItem);
        createVaultMenuItem = item("Create vault...", SilkIcons.CREATE_VAULT, this::createVault);
        file.add(createVaultMenuItem);
        unlockMenuItem = item("Unlock vault", SilkIcons.UNLOCK, this::unlockVault);
        file.add(unlockMenuItem);
        file.add(vaultItem("Lock vault", SilkIcons.LOCK, this::lockVault));
        file.add(vaultItem("Reload vault", SilkIcons.RECONNECT, this::reloadVault));
        file.addSeparator();
        exitMenuItem = item("Exit", SilkIcons.EXIT, this::closeWindow);
        file.add(exitMenuItem);
        bar.add(file);
        JMenu host = topMenu("Host");
        connectSelectedItem = selectedHostItem("Connect selected", SilkIcons.CONNECT, this::connectSelected);
        host.add(connectSelectedItem);
        host.add(selectedHostItem("Edit selected", SilkIcons.EDIT, this::editSelectedHost));
        host.add(selectedHostItem("Delete selected", SilkIcons.DELETE, this::deleteSelectedHost));
        bar.add(host);
        JMenu session = topMenu("Session");
        session.add(selectedSessionItem("Disconnect", SilkIcons.DISCONNECT, tabs::disconnectSelected));
        session.add(selectedSessionItem("Reconnect", SilkIcons.RECONNECT, tabs::reconnectSelected));
        session.add(selectedSessionItem("Close tab", SilkIcons.CLOSE, tabs::closeSelected));
        session.addSeparator();
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenuItem increaseFont =
                selectedSessionItem("Increase font size", SilkIcons.FONT_INCREASE, () -> tabs.changeFontSize(1));
        increaseFont.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, shortcut));
        session.add(increaseFont);
        JMenuItem decreaseFont =
                selectedSessionItem("Decrease font size", SilkIcons.FONT_DECREASE, () -> tabs.changeFontSize(-1));
        decreaseFont.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, shortcut));
        session.add(decreaseFont);
        JMenuItem resetFont = selectedSessionItem("Reset font size", SilkIcons.FONT, tabs::resetFontSize);
        resetFont.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, shortcut));
        session.add(resetFont);
        session.addSeparator();
        copySudoSessionItem =
                selectedSessionItem("Copy sudo password", SilkIcons.COPY_PASSWORD, tabs::copySudoPassword);
        session.add(copySudoSessionItem);
        bar.add(session);
        JMenu tools = topMenu("Tools");
        tools.add(item("Settings", SilkIcons.SETTINGS, this::editSettings));
        bar.add(tools);
        JMenu help = topMenu("Help");
        help.add(item("Check for updates...", SilkIcons.RECONNECT, updates::checkManually));
        help.addSeparator();
        help.add(item(
                "About",
                SilkIcons.ABOUT,
                () -> JOptionPane.showMessageDialog(
                        this,
                        "Remote Manager " + AppVersion.display()
                                + "\nSSH hosts and identities in a KeePass vault."
                                + "\nIcons: FamFamFam Silk by Mark James (CC BY 2.5).",
                        "About Remote Manager",
                        JOptionPane.INFORMATION_MESSAGE)));
        bar.add(help);
        return bar;
    }

    private static JButton button(String title, javax.swing.Icon icon, Runnable action) {
        JButton button = new JButton(title, icon);
        button.addActionListener(event -> action.run());
        return button;
    }

    private static JMenu topMenu(String title) {
        JMenu menu = new JMenu(title);
        int horizontalPadding = com.sun.jna.Platform.isLinux() ? 10 : 4;
        menu.setBorder(BorderFactory.createEmptyBorder(0, horizontalPadding, 0, horizontalPadding));
        return menu;
    }

    private static JMenuItem item(String title, javax.swing.Icon icon, Runnable action) {
        JMenuItem item = new JMenuItem(title, icon);
        item.addActionListener(event -> action.run());
        return item;
    }

    private JButton vaultButton(String title, javax.swing.Icon icon, Runnable action) {
        JButton button = button(title, icon, action);
        vaultActions.add(button);
        return button;
    }

    private JButton selectedIdentityButton(String title, javax.swing.Icon icon, Runnable action) {
        JButton button = button(title, icon, action);
        identityActions.add(button);
        return button;
    }

    private JMenuItem vaultItem(String title, javax.swing.Icon icon, Runnable action) {
        JMenuItem item = item(title, icon, action);
        vaultActions.add(item);
        return item;
    }

    private JMenuItem selectedHostItem(String title, javax.swing.Icon icon, Runnable action) {
        JMenuItem item = item(title, icon, action);
        hostActions.add(item);
        return item;
    }

    private JMenuItem selectedSessionItem(String title, javax.swing.Icon icon, Runnable action) {
        JMenuItem item = item(title, icon, action);
        sessionActions.add(item);
        return item;
    }

    private void updateActions() {
        boolean vaultAvailable = !locked && !savingVault;
        connectionTree.setVaultBusy(!vaultAvailable);
        identities.setEnabled(vaultAvailable);
        quickConnect.setEnabled(vaultAvailable);
        vaultActions.forEach(action -> action.setEnabled(vaultAvailable));
        hostActions.forEach(action -> action.setEnabled(vaultAvailable && connectionTree.selectedValue() != null));
        if (connectSelectedItem != null)
            connectSelectedItem.setEnabled(vaultAvailable && connectionTree.selectedValue() instanceof Connection);
        identityActions.forEach(action -> action.setEnabled(vaultAvailable && identities.getSelectedValue() != null));
        sessionActions.forEach(action -> action.setEnabled(!locked && tabs.getSelectedIndex() >= 0));
        boolean canCopySudo = vaultAvailable && connectionTree.hasSudoPassword(tabs.selectedConnection());
        if (copySudoSessionItem != null) copySudoSessionItem.setEnabled(canCopySudo);
        if (copySudoButton != null) copySudoButton.setEnabled(canCopySudo);
        if (unlockMenuItem != null) unlockMenuItem.setEnabled(!creatingVault && locked && vault != null);
        if (unlockWelcomeButton != null) unlockWelcomeButton.setEnabled(!creatingVault && locked && vault != null);
        if (openVaultMenuItem != null) openVaultMenuItem.setEnabled(!creatingVault && !savingVault);
        if (createVaultMenuItem != null) createVaultMenuItem.setEnabled(!creatingVault && !savingVault);
        if (exitMenuItem != null) exitMenuItem.setEnabled(!creatingVault);
        if (openWelcomeButton != null) openWelcomeButton.setEnabled(!creatingVault);
        if (createWelcomeButton != null) createWelcomeButton.setEnabled(!creatingVault);
    }

    private Connection currentHost(UUID id) {
        return connectionTree.connections().stream()
                .filter(host -> host.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    private void setCreatingVault(boolean creating) {
        creatingVault = creating;
        creationProgress.setVisible(creating);
        updateActions();
    }

    private void setSavingVault(boolean saving) {
        savingVault = saving;
        if (saving) status.setText("Saving vault...");
        updateVaultState();
        updateActions();
    }

    private void showLocked() {
        locked = true;
        vaultPath.setText(vault == null ? "No vault selected" : vault.path().toString());
        ((CardLayout) cards.getLayout()).show(cards, "locked");
        status.setText(vault == null ? "Create or open a KeePass vault to begin." : "Vault locked");
        updateVaultState();
        updateActions();
    }

    private void showWorkspace() {
        workspace();
        locked = false;
        autoLock.reset();
        connectionsLoaded = false;
        connectionTree.restoreOnNextLoad(folderExpansion.load(vault.path()), "");
        ((CardLayout) cards.getLayout()).show(cards, "workspace");
        if (!sidebarWidthInitialized) SwingUtilities.invokeLater(this::initializeSidebarWidth);
        if (!detailsHeightInitialized) SwingUtilities.invokeLater(this::initializeDetailsHeight);
        status.setText("Vault unlocked: " + vault.path());
        updateVaultState();
        updateActions();
        refreshData(null);
    }

    private void initializeSidebarWidth() {
        if (sidebarWidthInitialized || !workspace.isShowing() || workspace.getWidth() <= 0) return;
        int maxWidth = workspace.getWidth()
                - workspace.getDividerSize()
                - workspace.getRightComponent().getMinimumSize().width;
        workspace.setDividerLocation(Math.min(initialSidebarWidth, Math.max(190, maxWidth)));
        sidebarWidthInitialized = true;
    }

    private void initializeDetailsHeight() {
        if (detailsHeightInitialized || !hosts.isShowing() || hosts.getHeight() <= hosts.getDividerSize()) return;
        int available = hosts.getHeight() - hosts.getDividerSize();
        int maxDetailsHeight = Math.max(0, available - connectionTree.getMinimumSize().height);
        hosts.setDividerLocation(available - Math.min(initialDetailsHeight, maxDetailsHeight));
        detailsHeight = currentDetailsHeight();
        detailsHeightInitialized = true;
    }

    private int currentDetailsHeight() {
        return Math.max(0, hosts.getHeight() - hosts.getDividerSize() - hosts.getDividerLocation());
    }

    private void openVault() {
        if (creatingVault || savingVault) return;
        Path path = chooseVaultFile(FileDialog.LOAD);
        if (path == null) return;
        lockVault();
        selectVault(new KdbxVault(path));
        saveSetting("vault.path", path.toAbsolutePath().toString());
        showLocked();
        unlockVault();
    }

    private void createVault() {
        if (creatingVault || savingVault) return;
        Path path = chooseVaultFile(FileDialog.SAVE);
        if (path == null) return;
        char[] password = askNewVaultPassword();
        if (password == null) return;
        lockVault();
        long version = operationVersion;
        creationProgress.setString("Creating " + path.getFileName() + "...");
        setCreatingVault(true);
        status.setText("Creating vault: " + path.getFileName() + "...");
        VaultWorkspace.create(path, password)
                .whenComplete((created, error) -> SwingUtilities.invokeLater(() -> {
                    if (version != operationVersion || !isDisplayable()) {
                        if (created != null) created.close();
                        if (isDisplayable()) setCreatingVault(false);
                        return;
                    }
                    try {
                        if (error != null) {
                            showError("Could not create vault", error);
                            showLocked();
                            return;
                        }
                        selectWorkspace(created);
                        saveSetting("vault.path", path.toAbsolutePath().toString());
                        showWorkspace();
                    } finally {
                        setCreatingVault(false);
                    }
                }));
    }

    private char[] askNewVaultPassword() {
        return VaultDialogs.askNewVaultPassword(this);
    }

    private Path chooseVaultFile(int mode) {
        return VaultDialogs.chooseVaultFile(this, mode);
    }

    private void unlockVault() {
        if (creatingVault) return;
        WorkspaceVault selected = vault;
        if (selected == null) {
            openVault();
            return;
        }
        if (!locked) return;
        showUnlockDialog(selected);
    }

    private void showUnlockDialog(WorkspaceVault selected) {
        JPasswordField field = new JPasswordField(24);
        JDialog dialog = new JDialog(this, "Unlock vault", Dialog.ModalityType.APPLICATION_MODAL);
        JButton unlock = new JButton("Unlock", SilkIcons.UNLOCK);
        JButton cancel = new JButton("Cancel", SilkIcons.CLOSE);
        JLabel feedback = new JLabel();
        feedback.setVisible(false);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        unlock.addActionListener(event -> {
            char[] password = field.getPassword();
            if (password.length == 0) {
                feedback.setForeground(new Color(0xB0, 0x20, 0x20));
                feedback.setText("Enter your vault password.");
                feedback.setVisible(true);
                dialog.pack();
                field.requestFocusInWindow();
                return;
            }
            field.setText("");
            field.setEnabled(false);
            unlock.setEnabled(false);
            cancel.setEnabled(false);
            feedback.setForeground(UIManager.getColor("Label.foreground"));
            feedback.setText("Unlocking vault...");
            feedback.setVisible(true);
            dialog.pack();
            long version = ++operationVersion;
            status.setText("Unlocking vault...");
            vaultWorkspace
                    .unlock(password)
                    .whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
                        if (version != operationVersion || vault != selected || !dialog.isDisplayable()) {
                            selected.lock();
                            return;
                        }
                        if (error == null) {
                            dialog.dispose();
                            showWorkspace();
                            return;
                        }
                        showLocked();
                        feedback.setForeground(new Color(0xB0, 0x20, 0x20));
                        feedback.setText(unlockFailureMessage(error));
                        dialog.pack();
                        field.setEnabled(true);
                        unlock.setEnabled(true);
                        cancel.setEnabled(true);
                        field.requestFocusInWindow();
                    }));
        });
        cancel.addActionListener(event -> dialog.dispose());
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (cancel.isEnabled()) cancel.doClick();
            }
        });
        DialogEscape.bind(dialog, cancel);
        field.addActionListener(event -> unlock.doClick());
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));
        JPanel passwordRow = new JPanel(new BorderLayout(0, 6));
        passwordRow.add(new JLabel("Vault password:"), BorderLayout.NORTH);
        passwordRow.add(field, BorderLayout.CENTER);
        passwordRow.add(feedback, BorderLayout.SOUTH);
        content.add(passwordRow, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(unlock);
        buttons.add(cancel);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(unlock);
        dialog.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy() {
            @Override
            public Component getDefaultComponent(Container container) {
                return field;
            }
        });
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        try {
            dialog.setVisible(true);
        } finally {
            field.setText("");
            dialog.dispose();
        }
    }

    private static String unlockFailureMessage(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if ("Header HMAC does not match".equals(cause.getMessage())) return "Incorrect password. Try again.";
        }
        return "Could not unlock vault. Check the vault file and try again.";
    }

    private CompletableFuture<Boolean> sessionReady() {
        return CompletableFuture.completedFuture(!locked && vault != null && vault.isUnlocked());
    }

    private void lockVault() {
        operationVersion++;
        if (!locked && connectionsLoaded && vault != null) {
            try {
                folderExpansion.save(vault.path(), connectionTree.expandedIds());
            } catch (Exception error) {
                showError("Could not save folder layout", error);
            }
        }
        connectionsLoaded = false;
        for (Window child : getOwnedWindows()) if (child.isVisible()) child.dispose();
        tabs.closeAll();
        connectionTree.clear();
        connectionTree.setIdentities(List.of());
        quickConnect.setText("");
        identities.setListData(new VaultEntry[0]);
        identityHint.setText("  No identities yet. Create one here or while adding a host.");
        showLocked();
        setSavingVault(false);
        VaultWorkspace currentWorkspace = workspace();
        if (currentWorkspace != null) currentWorkspace.lock();
    }

    private void reloadVault() {
        if (vault == null || savingVault) return;
        lockVault();
        unlockVault();
    }

    private void refreshData(UUID revealId) {
        refreshData(revealId, null);
    }

    private void refreshData(UUID revealId, String saveResult) {
        if (locked || vault == null) return;
        WorkspaceVault selected = vault;
        long version = operationVersion;
        VaultWorkspace currentWorkspace = workspace();
        if (currentWorkspace == null) return;
        currentWorkspace
                .refresh()
                .whenComplete((data, error) -> SwingUtilities.invokeLater(() -> {
                    if (locked || version != operationVersion || vault != selected) return;
                    if (error != null) {
                        if (saveResult != null) {
                            status.setText("Could not refresh vault");
                            setSavingVault(false);
                        }
                        showError("Could not read vault", error);
                        return;
                    }
                    connectionTree.setIdentities(data.credentials());
                    connectionTree.showConnections(data.folders(), data.connections());
                    connectionsLoaded = true;
                    identities.setListData(data.identities().toArray(VaultEntry[]::new));
                    identityHint.setText(
                            data.identities().isEmpty()
                                    ? "  No identities yet. Create one here or while adding a host."
                                    : "  Reusable credentials in this vault");
                    if (revealId != null) connectionTree.reveal(revealId);
                    if (saveResult != null) {
                        status.setText(saveResult);
                        setSavingVault(false);
                    } else updateActions();
                }));
    }

    private CompletableFuture<Void> mutate(VaultAction action, UUID revealId) {
        return mutateAndReveal(selected -> {
            action.run(selected);
            return revealId;
        });
    }

    private CompletableFuture<Void> mutateAndReveal(VaultMutation action) {
        if (locked || vault == null)
            return CompletableFuture.failedFuture(new IllegalStateException("Vault is locked"));
        if (savingVault) return CompletableFuture.failedFuture(new IllegalStateException("Vault is being saved"));
        WorkspaceVault selected = vault;
        long version = operationVersion;
        setSavingVault(true);
        VaultWorkspace currentWorkspace = workspace();
        if (currentWorkspace == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Vault workspace is unavailable"));
        }
        return currentWorkspace
                .mutateAndSaveAsync(action::run)
                .whenComplete((revealId, error) -> SwingUtilities.invokeLater(() -> {
                    if (version != operationVersion || vault != selected || locked) return;
                    if (!selected.isUnlocked()) {
                        lockVault();
                        return;
                    }
                    refreshData(error == null ? revealId : null, error == null ? "Vault saved" : "Vault save failed");
                }))
                .thenApply(ignored -> null);
    }

    private void editHost(Connection current, UUID parent) {
        if (locked || savingVault) return;
        if (current == null && parent == null && connectionTree.selectedValue() instanceof ConnectionFolder folder)
            parent = folder.id();
        try {
            ConnectionEditor editor = new ConnectionEditor(
                    this,
                    current,
                    parent,
                    connectionTree.folders(),
                    vault.entries(),
                    vault.credentialEntries(),
                    submission -> saveHost(submission));
            editor.setVisible(true);
        } catch (Exception error) {
            showError("Could not open host editor", error);
        }
    }

    private CompletableFuture<Void> saveHost(ConnectionEditor.Submission submission) {
        Connection host = submission.connection();
        Map<UUID, IdentityEditor.Change> drafts = new LinkedHashMap<>();
        submission
                .newIdentities()
                .forEach((id, change) -> drafts.put(
                        id,
                        new IdentityEditor.Change(
                                change.title(),
                                change.username(),
                                change.password() == null
                                        ? null
                                        : change.password().clone(),
                                change.attachmentPath())));
        Map<UUID, ConnectionEditor.HostPassword> hostPasswords = new LinkedHashMap<>();
        submission
                .hostPasswords()
                .forEach((id, change) -> hostPasswords.put(
                        id,
                        new ConnectionEditor.HostPassword(
                                change.purpose(),
                                change.password() == null
                                        ? null
                                        : change.password().clone())));
        return mutate(
                        selected -> {
                            for (Map.Entry<UUID, IdentityEditor.Change> item : drafts.entrySet()) {
                                IdentityEditor.Change draft = item.getValue();
                                byte[] attachment = draft.attachmentPath() == null
                                        ? null
                                        : Files.readAllBytes(draft.attachmentPath());
                                IdentityDraft identity = new IdentityDraft(
                                        draft.title(),
                                        draft.username(),
                                        draft.password(),
                                        Map.of(),
                                        new VaultAttachment(draft.attachmentName(), attachment));
                                try {
                                    selected.addIdentity(item.getKey(), identity);
                                } finally {
                                    identity.clearSecrets();
                                    if (attachment != null) Arrays.fill(attachment, (byte) 0);
                                }
                            }
                            for (Map.Entry<UUID, ConnectionEditor.HostPassword> item : hostPasswords.entrySet()) {
                                ConnectionEditor.HostPassword change = item.getValue();
                                HostSecretDraft secret = new HostSecretDraft(
                                        item.getKey(),
                                        host.id(),
                                        change.purpose(),
                                        host.name()
                                                + ("ssh".equals(change.purpose()) ? " SSH password" : " sudo password"),
                                        host.username(),
                                        change.password());
                                try {
                                    selected.putHostSecret(secret);
                                } finally {
                                    secret.clearSecrets();
                                }
                            }
                            selected.putConnection(host);
                        },
                        host.id())
                .whenComplete((ignored, error) -> {
                    drafts.values().forEach(change -> change.clear());
                    hostPasswords.values().forEach(change -> change.clear());
                });
    }

    private void editSelectedHost() {
        if (connectionTree.selectedValue() instanceof Connection host) editHost(host, host.parentFolderId());
        else if (connectionTree.selectedValue() instanceof ConnectionFolder folder) renameFolder(folder);
    }

    private void deleteSelectedHost() {
        deleteItem(connectionTree.selectedValue());
    }

    private void newFolder(ConnectionFolder parent) {
        if (locked || savingVault) return;
        String name = JOptionPane.showInputDialog(this, "Folder name:");
        if (name == null || name.isBlank()) return;
        createFolder(parent, name.trim());
    }

    private void createFolder(ConnectionFolder parent, String name) {
        if (locked || savingVault) return;
        mutateAndReveal(v -> v.createFolder(parent == null ? null : parent.id(), name))
                .exceptionally(error -> {
                    showErrorLater("Could not save folder", error);
                    return null;
                });
    }

    private void renameFolder(ConnectionFolder folder) {
        if (locked || savingVault) return;
        String name = JOptionPane.showInputDialog(this, "Folder name:", folder.name());
        if (name == null || name.isBlank()) return;
        mutate(v -> v.renameFolder(folder.id(), name.trim()), folder.id()).exceptionally(error -> {
            showErrorLater("Could not rename folder", error);
            return null;
        });
    }

    private void deleteItem(Object selected) {
        if (locked || savingVault || selected == null) return;
        if (JOptionPane.showConfirmDialog(
                        this, "Delete " + selected + "?", "Confirm deletion", JOptionPane.YES_NO_OPTION)
                != JOptionPane.YES_OPTION) return;
        mutate(
                        v -> {
                            if (selected instanceof Connection host) v.deleteConnection(host.id());
                            else if (selected instanceof ConnectionFolder folder) v.deleteFolder(folder.id());
                        },
                        null)
                .exceptionally(error -> {
                    showErrorLater("Could not delete", error);
                    return null;
                });
    }

    private void editIdentity(VaultEntry current) {
        if (locked || savingVault) return;
        IdentityEditor.editAndSave(
                this,
                current,
                change -> mutate(
                        v -> {
                            byte[] attachment = change.attachmentPath() == null
                                    ? null
                                    : Files.readAllBytes(change.attachmentPath());
                            IdentityDraft draft = new IdentityDraft(
                                    change.title(),
                                    change.username(),
                                    change.password(),
                                    Map.of(),
                                    new VaultAttachment(change.attachmentName(), attachment));
                            try {
                                if (current == null) v.addIdentity(draft);
                                else v.updateIdentity(current.id(), draft);
                            } finally {
                                draft.clearSecrets();
                                if (attachment != null) Arrays.fill(attachment, (byte) 0);
                            }
                        },
                        null));
    }

    private void deleteIdentity() {
        VaultEntry selected = identities.getSelectedValue();
        if (locked || savingVault || selected == null) return;
        boolean used = connectionTree.connections().stream()
                .anyMatch(host -> selected.id().equals(host.sshCredentialEntryId())
                        || selected.id().equals(host.sudoCredentialEntryId()));
        if (used) {
            JOptionPane.showMessageDialog(this, "This identity is used by a host. Reassign that host first.");
            return;
        }
        if (JOptionPane.showConfirmDialog(
                        this,
                        "Delete identity " + selected.title() + " from the vault?",
                        "Confirm deletion",
                        JOptionPane.YES_NO_OPTION)
                != JOptionPane.YES_OPTION) return;
        mutate(v -> v.deleteIdentity(selected.id()), null).exceptionally(error -> {
            showErrorLater("Could not delete identity", error);
            return null;
        });
    }

    private void quickConnect() {
        if (locked || savingVault) return;
        String target = quickConnect.getText().trim();
        Connection selected = connectionTree.selectedValue() instanceof Connection host ? host : null;
        Connection match = target.isBlank()
                ? selected
                : connectionTree.connections().stream()
                        .filter(host -> host.name().equalsIgnoreCase(target)
                                || host.hostname().equalsIgnoreCase(target))
                        .findFirst()
                        .orElse(null);
        if (match == null) {
            showStatus("Select a host or enter a saved host name.");
            return;
        }
        openHost(match);
    }

    private void connectSelected() {
        if (!locked && !savingVault && connectionTree.selectedValue() instanceof Connection host) openHost(host);
    }

    private void installSudoShortcut() {
        int modifiers = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK;
        getRootPane()
                .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_P, modifiers), "copySudoPassword");
        getRootPane().getActionMap().put("copySudoPassword", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                copySudoForFocusedContext();
            }
        });
    }

    private boolean dispatchTerminalFontKey(KeyEvent event) {
        Component selected = tabs.getSelectedComponent();
        Component source = event.getComponent();
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        if (selected == null
                || source == null
                || !SwingUtilities.isDescendingFrom(source, selected)
                || (event.getModifiersEx() & shortcut) == 0) return false;

        int change =
                switch (event.getKeyCode()) {
                    case KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS, KeyEvent.VK_ADD -> 1;
                    case KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> -1;
                    case KeyEvent.VK_0 -> 0;
                    default -> Integer.MIN_VALUE;
                };
        if (event.getID() == KeyEvent.KEY_TYPED) {
            char character = event.getKeyChar();
            if (character != '+' && character != '=' && character != '-' && character != '0') return false;
        } else if (change == Integer.MIN_VALUE) {
            return false;
        }
        if (event.getID() == KeyEvent.KEY_PRESSED) {
            if (change == 0) tabs.resetFontSize();
            else tabs.changeFontSize(change);
        }
        event.consume();
        return true;
    }

    private void copySudoForFocusedContext() {
        if (locked || savingVault) return;
        Connection selectedHost = connectionTree.selectedValue() instanceof Connection host ? host : null;
        boolean useTree = connectionTree.isTreeFocused();
        Connection activeSession = tabs.selectedConnection();
        Connection target = useTree ? selectedHost : activeSession;
        if (target == null) target = selectedHost;
        if (target == null) {
            showStatus("Select a host or open a session to copy a sudo password");
            return;
        }
        if (connectionTree.hasSudoPassword(target)) {
            if (!useTree && activeSession != null) tabs.copySudoPassword();
            else tabs.copySudoPassword(target);
        } else showStatus("No sudo password is available for " + target.name());
    }

    private void openHost(Connection host) {
        if (locked || savingVault) return;
        String issue = connectionTree.issue(host);
        if (issue != null) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "This host has a missing credential: " + issue + ".\nEdit the host now?",
                    "Host needs attention",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) editHost(host, host.parentFolderId());
            return;
        }
        tabs.open(host);
    }

    private void editSettings() {
        AppSettings changed = SettingsDialog.edit(this, preferences);
        if (changed == null) return;
        preferences = changed;
        tabs.applyFontSettings(changed);
        try {
            changed.save(settings);
            showStatus("Settings saved");
        } catch (Exception error) {
            showError("Could not save settings", error);
        }
    }

    private void closeWindow() {
        if (creatingVault) return;
        if (detailsHeightInitialized) {
            if (hosts.isShowing()) detailsHeight = currentDetailsHeight();
        }
        try {
            windowPreferences.saveFrame(
                    this, workspace, sidebarWidthInitialized, detailsHeightInitialized, detailsHeight);
        } catch (Exception error) {
            showError("Could not save preference", error);
        }
        lockVault();
        dispose();
    }

    public void checkForUpdatesAutomatically() {
        updates.checkAutomatically();
    }

    @Override
    public void dispose() {
        operationVersion++;
        autoLock.close();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(terminalFontKeys);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusOwner", buttonFocus);
        tabs.shutdown();
        updates.close();
        VaultWorkspace currentWorkspace = workspace();
        if (currentWorkspace != null) currentWorkspace.close();
        super.dispose();
    }

    private void selectVault(WorkspaceVault selected) {
        selectWorkspace(new VaultWorkspace(selected));
    }

    private void selectWorkspace(VaultWorkspace selected) {
        if (vaultWorkspace != null) {
            vaultWorkspace.close();
        }
        vaultWorkspace = selected;
        vault = selected.vault();
    }

    private VaultWorkspace workspace() {
        if (vault == null) {
            return null;
        }
        if (vaultWorkspace == null || vaultWorkspace.vault() != vault) {
            if (vaultWorkspace != null) {
                vaultWorkspace.close();
            }
            vaultWorkspace = new VaultWorkspace(vault);
        }
        return vaultWorkspace;
    }

    private void saveSetting(String key, String value) {
        try {
            windowPreferences.put(key, value);
        } catch (Exception error) {
            showError("Could not save preference", error);
        }
    }

    private void showStatus(String text) {
        status.setText(text);
    }

    private void updateVaultState() {
        vaultState.setText((locked ? "Locked" : "Unlocked: " + vault.path().getFileName())
                + "  |  " + tabs.getTabCount() + " session" + (tabs.getTabCount() == 1 ? "" : "s")
                + (savingVault ? "  |  Saving..." : ""));
    }

    private void showErrorLater(String message, Throwable error) {
        SwingUtilities.invokeLater(() -> {
            if (isShowing()) showError(message, error);
        });
    }

    private void showError(String message, Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        JOptionPane.showMessageDialog(
                this, message + ": " + cause.getMessage(), "Remote Manager", JOptionPane.ERROR_MESSAGE);
    }

    @FunctionalInterface
    private interface VaultAction {
        void run(WorkspaceVault vault) throws Exception;
    }

    @FunctionalInterface
    private interface VaultMutation {
        UUID run(WorkspaceVault vault) throws Exception;
    }

    private final class TreeActions implements ConnectionTreePanel.Actions {
        @Override
        public void open(Connection host) {
            if (!locked && !savingVault) openHost(host);
        }

        @Override
        public void copySudoPassword(Connection host) {
            if (!locked && !savingVault && connectionTree.hasSudoPassword(host)) tabs.copySudoPassword(host);
        }

        @Override
        public void edit(Connection host) {
            editHost(host, host.parentFolderId());
        }

        @Override
        public void newFolder(ConnectionFolder parent) {
            MainWindow.this.newFolder(parent);
        }

        @Override
        public void newConnection(ConnectionFolder parent) {
            editHost(null, parent == null ? null : parent.id());
        }

        @Override
        public void rename(ConnectionFolder folder) {
            MainWindow.this.renameFolder(folder);
        }

        @Override
        public void deleteFolder(ConnectionFolder folder) {
            deleteItem(folder);
        }

        @Override
        public void deleteConnection(Connection host) {
            deleteItem(host);
        }
    }
}
