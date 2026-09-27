package com.mmlinaric.remotemanager.ui.connections;

import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.model.ConnectionFolder;
import com.mmlinaric.remotemanager.ui.SilkIcons;
import java.awt.Component;
import java.util.function.Function;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

/** Renders host tree nodes with icons and diagnostic text derived from their current vault data. */
final class ConnectionTreeRenderer extends DefaultTreeCellRenderer {
    private final Function<Connection, String> issue;

    ConnectionTreeRenderer(Function<Connection, String> issue) {
        this.issue = issue;
    }

    @Override
    public Component getTreeCellRendererComponent(
            JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, false);
        Object item = value instanceof DefaultMutableTreeNode node ? node.getUserObject() : value;
        if (item instanceof Connection connection) {
            String connectionIssue = issue.apply(connection);
            setIcon(connectionIssue == null ? SilkIcons.CONNECTION : SilkIcons.FAILED);
            if (connectionIssue != null) setText(connection.name() + ": " + connectionIssue);
        } else if (item instanceof ConnectionFolder) {
            setIcon(SilkIcons.FOLDER);
        } else {
            setIcon(SilkIcons.ROOT);
        }
        return this;
    }
}
