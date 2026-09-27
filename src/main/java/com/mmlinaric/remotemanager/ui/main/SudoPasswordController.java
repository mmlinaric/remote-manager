package com.mmlinaric.remotemanager.ui.main;

import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.ui.connections.ConnectionTreePanel;
import com.mmlinaric.remotemanager.ui.terminal.SessionTabs;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Chooses the host or active session whose sudo password should be copied. */
final class SudoPasswordController {
    private final ConnectionTreePanel connectionTree;
    private final SessionTabs tabs;
    private final BooleanSupplier vaultAvailable;
    private final Consumer<String> showStatus;

    SudoPasswordController(
            ConnectionTreePanel connectionTree,
            SessionTabs tabs,
            BooleanSupplier vaultAvailable,
            Consumer<String> showStatus) {
        this.connectionTree = connectionTree;
        this.tabs = tabs;
        this.vaultAvailable = vaultAvailable;
        this.showStatus = showStatus;
    }

    boolean canCopyForActiveSession() {
        return vaultAvailable.getAsBoolean() && connectionTree.hasSudoPassword(tabs.selectedConnection());
    }

    void copyForFocusedContext() {
        if (!vaultAvailable.getAsBoolean()) return;
        Connection selectedHost = connectionTree.selectedValue() instanceof Connection host ? host : null;
        Connection activeSession = tabs.selectedConnection();
        Connection target = connectionTree.isTreeFocused() ? selectedHost : activeSession;
        if (target == null) target = selectedHost;
        if (target == null) {
            showStatus.accept("Select a host or open a session to copy a sudo password");
            return;
        }
        if (!connectionTree.hasSudoPassword(target)) {
            showStatus.accept("No sudo password is available for " + target.name());
            return;
        }
        if (target == activeSession && !connectionTree.isTreeFocused()) tabs.copySudoPassword();
        else tabs.copySudoPassword(target);
    }

    void copyForHost(Connection host) {
        if (vaultAvailable.getAsBoolean() && connectionTree.hasSudoPassword(host)) {
            tabs.copySudoPassword(host);
        }
    }
}
