package com.mmlinaric.remotemanager.ui.main;

import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.model.ConnectionFolder;
import com.mmlinaric.remotemanager.ui.connections.ConnectionEditor;
import com.mmlinaric.remotemanager.ui.connections.ConnectionTreePanel;
import com.mmlinaric.remotemanager.ui.vault.IdentityEditor;
import com.mmlinaric.remotemanager.vault.IdentityDraft;
import com.mmlinaric.remotemanager.vault.VaultAttachment;
import com.mmlinaric.remotemanager.vault.VaultEntry;
import com.mmlinaric.remotemanager.vault.WorkspaceVault;
import com.mmlinaric.remotemanager.workspace.HostSubmissionPersistence;
import com.mmlinaric.remotemanager.workspace.VaultWorkspace;
import java.awt.Window;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.JList;
import javax.swing.JOptionPane;

/** Coordinates editor dialogs with durable workspace mutations. */
final class WorkspaceEditorController {
    private final Window dialogOwner;
    private final ConnectionTreePanel connectionTree;
    private final JList<VaultEntry> identities;
    private final BooleanSupplier vaultAvailable;
    private final Supplier<WorkspaceVault> vault;
    private final MutationExecutor mutations;
    private final BiConsumer<String, Throwable> showError;
    private final BiConsumer<String, Throwable> showErrorLater;

    WorkspaceEditorController(
            Window dialogOwner,
            ConnectionTreePanel connectionTree,
            JList<VaultEntry> identities,
            BooleanSupplier vaultAvailable,
            Supplier<WorkspaceVault> vault,
            MutationExecutor mutations,
            BiConsumer<String, Throwable> showError,
            BiConsumer<String, Throwable> showErrorLater) {
        this.dialogOwner = dialogOwner;
        this.connectionTree = connectionTree;
        this.identities = identities;
        this.vaultAvailable = vaultAvailable;
        this.vault = vault;
        this.mutations = mutations;
        this.showError = showError;
        this.showErrorLater = showErrorLater;
    }

    void editHost(Connection current, UUID parentId) {
        if (!vaultAvailable.getAsBoolean()) return;
        if (current == null && parentId == null && connectionTree.selectedValue() instanceof ConnectionFolder folder) {
            parentId = folder.id();
        }
        try {
            WorkspaceVault currentVault = vault.get();
            if (currentVault == null) return;
            ConnectionEditor editor = new ConnectionEditor(
                    dialogOwner,
                    current,
                    parentId,
                    connectionTree.folders(),
                    currentVault.entries(),
                    currentVault.credentialEntries(),
                    this::saveHost);
            editor.setVisible(true);
        } catch (Exception error) {
            showError.accept("Could not open host editor", error);
        }
    }

    void editSelectedHost() {
        if (connectionTree.selectedValue() instanceof Connection host) editHost(host, host.parentFolderId());
        else if (connectionTree.selectedValue() instanceof ConnectionFolder folder) renameFolder(folder);
    }

    void deleteSelectedHost() {
        deleteItem(connectionTree.selectedValue());
    }

    void newFolder(ConnectionFolder parent) {
        if (!vaultAvailable.getAsBoolean()) return;
        String name = JOptionPane.showInputDialog(dialogOwner, "Folder name:");
        if (name == null || name.isBlank()) return;
        createFolder(parent, name.trim());
    }

    void createFolder(ConnectionFolder parent, String name) {
        if (!vaultAvailable.getAsBoolean()) return;
        mutations
                .execute(current -> current.createFolder(parent == null ? null : parent.id(), name))
                .exceptionally(error -> showFailure("Could not save folder", error));
    }

    void renameFolder(ConnectionFolder folder) {
        if (!vaultAvailable.getAsBoolean()) return;
        String name = JOptionPane.showInputDialog(dialogOwner, "Folder name:", folder.name());
        if (name == null || name.isBlank()) return;
        mutations
                .execute(current -> {
                    current.renameFolder(folder.id(), name.trim());
                    return folder.id();
                })
                .exceptionally(error -> showFailure("Could not rename folder", error));
    }

    void deleteItem(Object selected) {
        if (!vaultAvailable.getAsBoolean() || selected == null) return;
        if (JOptionPane.showConfirmDialog(
                        dialogOwner, "Delete " + selected + "?", "Confirm deletion", JOptionPane.YES_NO_OPTION)
                != JOptionPane.YES_OPTION) return;
        mutations
                .execute(current -> {
                    if (selected instanceof Connection host) current.deleteConnection(host.id());
                    else if (selected instanceof ConnectionFolder folder) current.deleteFolder(folder.id());
                    return null;
                })
                .exceptionally(error -> showFailure("Could not delete", error));
    }

    void editIdentity(VaultEntry current) {
        if (!vaultAvailable.getAsBoolean()) return;
        IdentityEditor.editAndSave(
                dialogOwner,
                current,
                change -> mutations.execute(workspace -> {
                    byte[] attachment =
                            change.attachmentPath() == null ? null : Files.readAllBytes(change.attachmentPath());
                    IdentityDraft draft = new IdentityDraft(
                            change.title(),
                            change.username(),
                            change.password(),
                            Map.of(),
                            new VaultAttachment(change.attachmentName(), attachment));
                    try {
                        if (current == null) workspace.addIdentity(draft);
                        else workspace.updateIdentity(current.id(), draft);
                        return null;
                    } finally {
                        draft.clearSecrets();
                        if (attachment != null) Arrays.fill(attachment, (byte) 0);
                    }
                }));
    }

    void deleteIdentity() {
        VaultEntry selected = identities.getSelectedValue();
        if (!vaultAvailable.getAsBoolean() || selected == null) return;
        boolean used = connectionTree.connections().stream()
                .anyMatch(host -> selected.id().equals(host.sshCredentialEntryId())
                        || selected.id().equals(host.sudoCredentialEntryId()));
        if (used) {
            JOptionPane.showMessageDialog(dialogOwner, "This identity is used by a host. Reassign that host first.");
            return;
        }
        if (JOptionPane.showConfirmDialog(
                        dialogOwner,
                        "Delete identity " + selected.title() + " from the vault?",
                        "Confirm deletion",
                        JOptionPane.YES_NO_OPTION)
                != JOptionPane.YES_OPTION) return;
        mutations
                .execute(current -> {
                    current.deleteIdentity(selected.id());
                    return null;
                })
                .exceptionally(error -> showFailure("Could not delete identity", error));
    }

    private CompletableFuture<Void> saveHost(ConnectionEditor.Submission submission) {
        HostSubmissionPersistence persistence = HostSubmissionPersistence.prepare(submission);
        return mutations
                .execute(current -> {
                    persistence.saveTo(current);
                    return persistence.connection().id();
                })
                .whenComplete((ignored, error) -> persistence.close());
    }

    private Void showFailure(String message, Throwable error) {
        showErrorLater.accept(message, error);
        return null;
    }

    @FunctionalInterface
    interface MutationExecutor {
        CompletableFuture<Void> execute(VaultWorkspace.Mutation mutation);
    }
}
