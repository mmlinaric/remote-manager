package com.mmlinaric.remotemanager.ui.main;

import com.mmlinaric.remotemanager.ui.connections.ConnectionTreePanel;
import com.mmlinaric.remotemanager.vault.VaultEntry;
import com.mmlinaric.remotemanager.workspace.WorkspaceSnapshot;
import java.util.List;
import java.util.UUID;
import javax.swing.JLabel;
import javax.swing.JList;

/** Renders an immutable workspace snapshot in the host tree and identity list. */
final class WorkspaceDataPresenter {
    private static final String EMPTY_IDENTITIES_MESSAGE =
            "  No identities yet. Create one here or while adding a host.";
    private static final String IDENTITIES_MESSAGE = "  Reusable credentials in this vault";

    private final ConnectionTreePanel connectionTree;
    private final JList<VaultEntry> identities;
    private final JLabel identityHint;

    WorkspaceDataPresenter(ConnectionTreePanel connectionTree, JList<VaultEntry> identities, JLabel identityHint) {
        this.connectionTree = connectionTree;
        this.identities = identities;
        this.identityHint = identityHint;
    }

    void show(WorkspaceSnapshot snapshot, UUID revealId) {
        connectionTree.setIdentities(snapshot.credentials());
        connectionTree.showConnections(snapshot.folders(), snapshot.connections());
        identities.setListData(snapshot.identities().toArray(VaultEntry[]::new));
        identityHint.setText(snapshot.identities().isEmpty() ? EMPTY_IDENTITIES_MESSAGE : IDENTITIES_MESSAGE);
        if (revealId != null) connectionTree.reveal(revealId);
    }

    void clear() {
        connectionTree.clear();
        connectionTree.setIdentities(List.of());
        identities.setListData(new VaultEntry[0]);
        identityHint.setText(EMPTY_IDENTITIES_MESSAGE);
    }
}
