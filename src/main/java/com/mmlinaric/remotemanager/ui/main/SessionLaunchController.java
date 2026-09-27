package com.mmlinaric.remotemanager.ui.main;

import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.ui.connections.ConnectionTreePanel;
import com.mmlinaric.remotemanager.ui.terminal.SessionTabs;
import java.awt.Component;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

/** Opens saved hosts after resolving quick-connect input and missing credential decisions. */
final class SessionLaunchController {
    private final Component dialogOwner;
    private final ConnectionTreePanel connectionTree;
    private final SessionTabs tabs;
    private final JTextField quickConnect;
    private final BooleanSupplier vaultAvailable;
    private final Consumer<Connection> editHost;
    private final Consumer<String> showStatus;

    SessionLaunchController(
            Component dialogOwner,
            ConnectionTreePanel connectionTree,
            SessionTabs tabs,
            JTextField quickConnect,
            BooleanSupplier vaultAvailable,
            Consumer<Connection> editHost,
            Consumer<String> showStatus) {
        this.dialogOwner = dialogOwner;
        this.connectionTree = connectionTree;
        this.tabs = tabs;
        this.quickConnect = quickConnect;
        this.vaultAvailable = vaultAvailable;
        this.editHost = editHost;
        this.showStatus = showStatus;
    }

    void quickConnect() {
        if (!vaultAvailable.getAsBoolean()) return;
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
            showStatus.accept("Select a host or enter a saved host name.");
            return;
        }
        open(match);
    }

    void connectSelected() {
        if (connectionTree.selectedValue() instanceof Connection host) open(host);
    }

    void open(Connection host) {
        if (!vaultAvailable.getAsBoolean()) return;
        String issue = connectionTree.issue(host);
        if (issue == null) {
            tabs.open(host);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(
                dialogOwner,
                "This host has a missing credential: " + issue + ".\nEdit the host now?",
                "Host needs attention",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) editHost.accept(host);
    }
}
