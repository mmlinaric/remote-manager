package com.mmlinaric.remotemanager.ui.connections;

import com.mmlinaric.remotemanager.model.AuthenticationType;
import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.model.ConnectionFolder;
import com.mmlinaric.remotemanager.vault.VaultEntry;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/** Displays folders and connections while retaining selection across reloads. */
public final class ConnectionTreePanel extends JPanel {
    private final JTree tree = new JTree(new DefaultMutableTreeNode("Connections"));
    private final JLabel emptyHint = new JLabel("  No hosts yet. Use New host in the toolbar.");
    private final ConnectionDetailsPanel details = new ConnectionDetailsPanel();
    private List<ConnectionFolder> folders = List.of();
    private List<Connection> connections = List.of();
    private Map<UUID, VaultEntry> identities = Map.of();
    private final ConnectionTreeState state = new ConnectionTreeState();
    private Runnable selectionChanged = () -> {};

    public ConnectionTreePanel(Actions actions) {
        super(new BorderLayout());
        add(new JScrollPane(tree), BorderLayout.CENTER);
        add(emptyHint, BorderLayout.NORTH);
        tree.setCellRenderer(new ConnectionTreeRenderer(this::issue));
        tree.addTreeSelectionListener(event -> {
            if (selectedValue() instanceof Connection connection) details.showConnection(connection, issue(connection));
            else details.showConnection(null);
            selectionChanged.run();
        });
        tree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                recordExpansion(event.getPath(), true);
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                recordExpansion(event.getPath(), false);
            }
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (!tree.isEnabled()) return;
                if (SwingUtilities.isLeftMouseButton(event)
                        && tree.getPathForLocation(event.getX(), event.getY()) == null) {
                    TreePath path = pathAtRow(event.getY());
                    if (path != null) tree.setSelectionPath(path);
                }
                showPopup(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                showPopup(event);
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (!tree.isEnabled()) return;
                if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 2) {
                    TreePath path = pathAtRow(event.getY());
                    if (path != null
                            && ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject()
                                    instanceof Connection connection) {
                        actions.open(connection);
                    }
                }
            }

            private void showPopup(MouseEvent event) {
                if (!tree.isEnabled() || !event.isPopupTrigger()) {
                    return;
                }
                TreePath path = pathAtRow(event.getY());
                if (path == null) return;
                JPopupMenu popup = contextMenuFor((DefaultMutableTreeNode) path.getLastPathComponent(), actions);
                if (popup == null) return;
                tree.setSelectionPath(path);
                popup.show(tree, event.getX(), event.getY());
            }
        });
    }

    JPopupMenu contextMenuFor(DefaultMutableTreeNode node, Actions actions) {
        return ConnectionContextMenu.create(node, actions, this::hasSudoPassword);
    }

    private TreePath pathAtRow(int y) {
        TreePath path = tree.getClosestPathForLocation(0, y);
        Rectangle bounds = path == null ? null : tree.getPathBounds(path);
        return bounds != null && y >= bounds.y && y < bounds.y + bounds.height ? path : null;
    }

    public ConnectionDetailsPanel details() {
        return details;
    }

    public void onSelectionChanged(Runnable action) {
        selectionChanged = action;
    }

    public void setVaultBusy(boolean busy) {
        tree.setEnabled(!busy);
    }

    public void expandAll() {
        for (int row = 0; row < tree.getRowCount(); row++) {
            tree.expandRow(row);
        }
    }

    public void collapseAll() {
        List<TreePath> paths = new ArrayList<>();
        var nodes = ((DefaultMutableTreeNode) tree.getModel().getRoot()).depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            if (node.getUserObject() instanceof ConnectionFolder) paths.add(new TreePath(node.getPath()));
        }
        paths.sort(
                Comparator.comparingInt((TreePath path) -> path.getPathCount()).reversed());
        paths.forEach(tree::collapsePath);
        state.clearExpandedFolders();
    }

    public List<ConnectionFolder> folders() {
        return folders;
    }

    public List<Connection> connections() {
        return connections;
    }

    public void setIdentities(List<VaultEntry> entries) {
        identities = entries.stream().collect(java.util.stream.Collectors.toMap(entry -> entry.id(), entry -> entry));
        tree.repaint();
        if (selectedValue() instanceof Connection connection) details.showConnection(connection, issue(connection));
    }

    public String issue(Connection connection) {
        if (connection.sshCredentialEntryId() != null) {
            VaultEntry identity = identities.get(connection.sshCredentialEntryId());
            if (identity == null) return "identity missing";
            if (connection.authenticationType() == AuthenticationType.PASSWORD && !identity.hasPassword())
                return "SSH password missing";
            if (connection.authenticationType() == AuthenticationType.KDBX_PRIVATE_KEY
                    && !identity.attachments().contains(connection.privateKeyAttachmentName()))
                return "private key missing";
        }
        if (connection.sudoCredentialEntryId() != null) {
            VaultEntry sudo = identities.get(connection.sudoCredentialEntryId());
            if (sudo == null) return "sudo identity missing";
            if (!sudo.hasPassword()) return "sudo password missing";
        }
        return null;
    }

    public boolean hasSudoPassword(Connection connection) {
        if (connection == null || connection.sudoCredentialEntryId() == null) return false;
        VaultEntry identity = identities.get(connection.sudoCredentialEntryId());
        return identity != null && identity.hasPassword();
    }

    public boolean isTreeFocused() {
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return focus != null && SwingUtilities.isDescendingFrom(focus, tree);
    }

    public Object selectedValue() {
        if (tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node) {
            return node.getUserObject();
        }
        return null;
    }

    public void restoreOnNextLoad(String expandedIds, String selectedId) {
        state.restoreOnNextLoad(expandedIds, selectedId);
    }

    public void clear() {
        folders = List.of();
        connections = List.of();
        state.clear();
        emptyHint.setVisible(true);
        state.replaceModel(() -> tree.setModel(buildTree()));
        details.showConnection(null);
    }

    public void showConnections(List<ConnectionFolder> folders, List<Connection> connections) {
        state.prepareReload(tree);
        this.folders = folders;
        this.connections = connections;
        state.retainFolders(folders);
        emptyHint.setVisible(connections.isEmpty());
        state.replaceModel(() -> {
            tree.setModel(buildTree());
            state.restore(tree);
        });
        state.markLoaded();
    }

    public String expandedIds() {
        return state.expandedIds();
    }

    public String selectedId() {
        return state.selectedId(tree);
    }

    public void reveal(UUID id) {
        state.reveal(tree, id);
    }

    private DefaultTreeModel buildTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Connections");
        Map<UUID, DefaultMutableTreeNode> nodes = new HashMap<>();
        for (ConnectionFolder folder : folders) {
            nodes.put(folder.id(), new DefaultMutableTreeNode(folder));
        }
        for (ConnectionFolder folder : folders) {
            nodes.getOrDefault(folder.parentFolderId(), root).add(nodes.get(folder.id()));
        }
        for (Connection connection : connections) {
            nodes.getOrDefault(connection.parentFolderId(), root).add(new DefaultMutableTreeNode(connection));
        }
        return new DefaultTreeModel(root);
    }

    private void recordExpansion(TreePath path, boolean expanded) {
        state.recordExpansion(path, expanded);
    }

    public interface Actions {
        void open(Connection connection);

        void copySudoPassword(Connection connection);

        void edit(Connection connection);

        void newFolder(ConnectionFolder parent);

        void newConnection(ConnectionFolder parent);

        void rename(ConnectionFolder folder);

        void deleteFolder(ConnectionFolder folder);

        void deleteConnection(Connection connection);
    }
}
