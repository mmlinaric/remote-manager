package com.mmlinaric.remotemanager.ui.main;

import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.model.ConnectionFolder;
import com.mmlinaric.remotemanager.persistence.FolderExpansionPreferences;
import com.mmlinaric.remotemanager.persistence.SettingsRepository;
import com.mmlinaric.remotemanager.ui.connections.ConnectionEditor;
import com.mmlinaric.remotemanager.ui.connections.ConnectionTreePanel;
import com.mmlinaric.remotemanager.ui.settings.AppSettings;
import com.mmlinaric.remotemanager.ui.settings.SettingsDialog;
import com.mmlinaric.remotemanager.ui.terminal.SessionTabs;
import com.mmlinaric.remotemanager.ui.vault.IdentityEditor;
import com.mmlinaric.remotemanager.update.UpdateController;
import com.mmlinaric.remotemanager.vault.IdentityDraft;
import com.mmlinaric.remotemanager.vault.VaultAttachment;
import com.mmlinaric.remotemanager.vault.VaultEntry;
import com.mmlinaric.remotemanager.vault.WorkspaceVault;
import com.mmlinaric.remotemanager.vault.kdbx.KdbxVault;
import com.mmlinaric.remotemanager.workspace.HostSubmissionPersistence;
import com.mmlinaric.remotemanager.workspace.VaultWorkspace;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FileDialog;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

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
    private final ActionAvailability actionAvailability = new ActionAvailability();
    private JButton copySudoButton;
    private JButton unlockWelcomeButton;
    private JButton openWelcomeButton;
    private JButton createWelcomeButton;
    private final MainMenu.Controls menuControls;
    private JProgressBar creationProgress;
    private WorkspaceVault vault;
    private VaultWorkspace vaultWorkspace;
    private AppSettings preferences;
    private final SessionTabs tabs =
            new SessionTabs(() -> vault, this::sessionReady, this::showStatus, () -> preferences, this::currentHost);
    private final TerminalFontKeyDispatcher terminalFontKeys = new TerminalFontKeyDispatcher(tabs);
    private final PropertyChangeListener buttonFocus = event -> {
        if (event.getNewValue() instanceof JButton button) button.setFocusPainted(false);
    };
    private final ConnectionTreePanel connectionTree = new ConnectionTreePanel(new TreeActions());
    private final WorkspaceLayout workspaceLayout;
    private final JSplitPane workspace;
    private final JSplitPane hosts;
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
        this.workspaceLayout = new WorkspaceLayout(
                connectionTree,
                identities,
                identityHint,
                quickConnect,
                tabs,
                actionAvailability,
                new WorkspaceLayout.Actions(
                        () -> editHost(null, null),
                        () -> editIdentity(null),
                        this::quickConnect,
                        this::lockVault,
                        this::editIdentity,
                        this::deleteIdentity,
                        tabs::copySudoPassword));
        this.workspace = workspaceLayout.workspace();
        this.hosts = workspaceLayout.hosts();
        this.copySudoButton = workspaceLayout.copySudoButton();
        hosts.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> {
            if (detailsHeightInitialized && hosts.isShowing() && hosts.getHeight() > 0) {
                detailsHeight = currentDetailsHeight();
            }
        });
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
        this.menuControls = MainMenu.create(this, actionAvailability, menuActions());
        setJMenuBar(menuControls.bar());
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
        cards.add(workspaceLayout.panel(), "workspace");
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

    private MainMenu.Actions menuActions() {
        return new MainMenu.Actions(
                new MainMenu.FileActions(
                        () -> editHost(null, null),
                        () -> newFolder(null),
                        () -> editIdentity(null),
                        this::openVault,
                        this::createVault,
                        this::unlockVault,
                        this::lockVault,
                        this::reloadVault,
                        this::closeWindow),
                new MainMenu.HostActions(this::connectSelected, this::editSelectedHost, this::deleteSelectedHost),
                new MainMenu.SessionActions(
                        tabs::disconnectSelected,
                        tabs::reconnectSelected,
                        tabs::closeSelected,
                        () -> tabs.changeFontSize(1),
                        () -> tabs.changeFontSize(-1),
                        tabs::resetFontSize,
                        tabs::copySudoPassword),
                new MainMenu.ToolActions(this::editSettings, updates::checkManually));
    }

    private void updateActions() {
        boolean vaultAvailable = !locked && !savingVault;
        connectionTree.setVaultBusy(!vaultAvailable);
        identities.setEnabled(vaultAvailable);
        quickConnect.setEnabled(vaultAvailable);
        actionAvailability.update(new ActionAvailability.State(
                vaultAvailable,
                connectionTree.selectedValue() != null,
                identities.getSelectedValue() != null,
                !locked && tabs.getSelectedIndex() >= 0));
        menuControls
                .connectSelected()
                .setEnabled(vaultAvailable && connectionTree.selectedValue() instanceof Connection);
        boolean canCopySudo = vaultAvailable && connectionTree.hasSudoPassword(tabs.selectedConnection());
        menuControls.copySudoPassword().setEnabled(canCopySudo);
        if (copySudoButton != null) copySudoButton.setEnabled(canCopySudo);
        menuControls.unlockVault().setEnabled(!creatingVault && locked && vault != null);
        if (unlockWelcomeButton != null) unlockWelcomeButton.setEnabled(!creatingVault && locked && vault != null);
        menuControls.openVault().setEnabled(!creatingVault && !savingVault);
        menuControls.createVault().setEnabled(!creatingVault && !savingVault);
        menuControls.exit().setEnabled(!creatingVault);
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
        VaultUnlockDialog dialog = new VaultUnlockDialog(this);
        dialog.setUnlockHandler(password -> {
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
                        dialog.showFailure(unlockFailureMessage(error));
                    }));
        });
        try {
            dialog.setVisible(true);
        } finally {
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
        HostSubmissionPersistence persistence = HostSubmissionPersistence.prepare(submission);
        return mutate(persistence::saveTo, persistence.connection().id())
                .whenComplete((ignored, error) -> persistence.close());
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
