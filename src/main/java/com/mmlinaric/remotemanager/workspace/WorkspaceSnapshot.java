package com.mmlinaric.remotemanager.workspace;

import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.model.ConnectionFolder;
import com.mmlinaric.remotemanager.vault.VaultEntry;
import java.util.List;

/** The complete immutable data set needed to render an unlocked workspace. */
public record WorkspaceSnapshot(
        List<ConnectionFolder> folders,
        List<Connection> connections,
        List<VaultEntry> identities,
        List<VaultEntry> credentials) {
    public WorkspaceSnapshot {
        folders = List.copyOf(folders);
        connections = List.copyOf(connections);
        identities = List.copyOf(identities);
        credentials = List.copyOf(credentials);
    }
}
