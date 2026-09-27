package com.mmlinaric.remotemanager.ui.main;

import com.mmlinaric.remotemanager.ui.SilkIcons;
import com.mmlinaric.remotemanager.ui.connections.ConnectionTreePanel;
import com.mmlinaric.remotemanager.ui.terminal.SessionTabs;
import com.mmlinaric.remotemanager.vault.VaultEntry;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.util.function.Consumer;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;

/** Builds the unlocked workspace while leaving vault commands with the window controller. */
final class WorkspaceLayout {
    private final JPanel panel = new JPanel(new BorderLayout());
    private final JSplitPane workspace;
    private final JSplitPane hosts;
    private final JButton copySudoButton;

    WorkspaceLayout(
            ConnectionTreePanel connectionTree,
            JList<VaultEntry> identities,
            JLabel identityHint,
            JTextField quickConnect,
            SessionTabs tabs,
            ActionAvailability actionAvailability,
            Actions actions) {
        hosts = createHostsPane(connectionTree);
        JTabbedPane sidebar = createSidebar(hosts, identities, identityHint, actionAvailability, actions);
        SessionPane sessions = createSessionPane(tabs, actions);
        copySudoButton = sessions.copySudoButton();
        workspace = createWorkspacePane(sidebar, sessions.panel(), tabs);
        panel.add(createToolbar(quickConnect, actionAvailability, actions), BorderLayout.NORTH);
        panel.add(workspace, BorderLayout.CENTER);
    }

    JPanel panel() {
        return panel;
    }

    JSplitPane workspace() {
        return workspace;
    }

    JSplitPane hosts() {
        return hosts;
    }

    JButton copySudoButton() {
        return copySudoButton;
    }

    private static JToolBar createToolbar(
            JTextField quickConnect, ActionAvailability actionAvailability, Actions actions) {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(actionAvailability.vaultButton("New host", SilkIcons.NEW_CONNECTION, actions.newHost()));
        toolbar.add(actionAvailability.vaultButton("New identity", SilkIcons.NEW_IDENTITY, actions.newIdentity()));
        toolbar.addSeparator();
        toolbar.add(new JLabel("Host: "));
        toolbar.add(quickConnect);
        toolbar.add(actionAvailability.vaultButton("Connect", SilkIcons.CONNECT, actions.quickConnect()));
        toolbar.addSeparator();
        toolbar.add(actionAvailability.vaultButton("Lock", SilkIcons.LOCK, actions.lockVault()));
        return toolbar;
    }

    private static JSplitPane createHostsPane(ConnectionTreePanel connectionTree) {
        JSplitPane hosts = new JSplitPane(JSplitPane.VERTICAL_SPLIT, connectionTree, connectionTree.details());
        hosts.setContinuousLayout(true);
        hosts.setResizeWeight(1.0);
        return hosts;
    }

    private static JTabbedPane createSidebar(
            JSplitPane hosts,
            JList<VaultEntry> identities,
            JLabel identityHint,
            ActionAvailability actionAvailability,
            Actions actions) {
        JTabbedPane sidebar = new JTabbedPane();
        sidebar.setMinimumSize(new Dimension(190, 0));
        sidebar.addTab("Hosts", SilkIcons.CONNECTION, hosts);
        sidebar.addTab(
                "Identities",
                SilkIcons.VAULT,
                createIdentityPane(identities, identityHint, actionAvailability, actions));
        return sidebar;
    }

    private static JPanel createIdentityPane(
            JList<VaultEntry> identities, JLabel identityHint, ActionAvailability actionAvailability, Actions actions) {
        JPanel identityPanel = new JPanel(new BorderLayout());
        identities.setCellRenderer(new IdentityRenderer());
        identities.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                VaultEntry selected = identities.getSelectedValue();
                if (event.getClickCount() == 2 && selected != null)
                    actions.editIdentity().accept(selected);
            }
        });
        identityPanel.add(identityHint, BorderLayout.NORTH);
        identityPanel.add(new JScrollPane(identities), BorderLayout.CENTER);
        JPanel identityActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        identityActions.add(actionAvailability.vaultButton("New", SilkIcons.NEW_IDENTITY, actions.newIdentity()));
        identityActions.add(actionAvailability.selectedIdentityButton(
                "Edit", SilkIcons.EDIT, () -> actions.editIdentity().accept(identities.getSelectedValue())));
        identityActions.add(
                actionAvailability.selectedIdentityButton("Delete", SilkIcons.DELETE, actions.deleteIdentity()));
        identityPanel.add(identityActions, BorderLayout.SOUTH);
        return identityPanel;
    }

    private static SessionPane createSessionPane(SessionTabs tabs, Actions actions) {
        JPanel sessionPanel = new JPanel(new BorderLayout());
        JPanel sessionActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
        JButton copySudo = new JButton("Copy sudo password", SilkIcons.COPY_PASSWORD);
        copySudo.addActionListener(event -> actions.copySudoPassword().run());
        copySudo.setToolTipText("Copy the active session's sudo password");
        sessionActions.add(copySudo);
        sessionPanel.add(sessionActions, BorderLayout.NORTH);
        sessionPanel.add(tabs, BorderLayout.CENTER);
        return new SessionPane(sessionPanel, copySudo);
    }

    private static JSplitPane createWorkspacePane(JTabbedPane sidebar, JPanel sessionPanel, SessionTabs tabs) {
        JPanel right = new JPanel(new CardLayout());
        JPanel empty = new JPanel(new GridBagLayout());
        empty.add(new JLabel("Double-click a host to open an SSH session."));
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

    record Actions(
            Runnable newHost,
            Runnable newIdentity,
            Runnable quickConnect,
            Runnable lockVault,
            Consumer<VaultEntry> editIdentity,
            Runnable deleteIdentity,
            Runnable copySudoPassword) {}

    private record SessionPane(JPanel panel, JButton copySudoButton) {}

    private static final class IdentityRenderer extends DefaultListCellRenderer {
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
    }
}
