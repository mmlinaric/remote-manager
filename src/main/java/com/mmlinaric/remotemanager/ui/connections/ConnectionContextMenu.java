package com.mmlinaric.remotemanager.ui.connections;

import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.model.ConnectionFolder;
import com.mmlinaric.remotemanager.ui.SilkIcons;
import java.util.function.Predicate;
import javax.swing.Icon;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.tree.DefaultMutableTreeNode;

/** Creates contextual commands for the host tree's root, folders, and connections. */
final class ConnectionContextMenu {
    private ConnectionContextMenu() {}

    static JPopupMenu create(
            DefaultMutableTreeNode node, ConnectionTreePanel.Actions actions, Predicate<Connection> hasSudoPassword) {
        Object value = node.getUserObject();
        JPopupMenu popup = new JPopupMenu();
        if (node.isRoot()) {
            popup.add(item("New folder", SilkIcons.NEW_FOLDER, () -> actions.newFolder(null)));
            popup.add(item("New connection", SilkIcons.NEW_CONNECTION, () -> actions.newConnection(null)));
        } else if (value instanceof ConnectionFolder folder) {
            popup.add(item("New subfolder", SilkIcons.NEW_FOLDER, () -> actions.newFolder(folder)));
            popup.add(item("New connection", SilkIcons.NEW_CONNECTION, () -> actions.newConnection(folder)));
            popup.addSeparator();
            popup.add(item("Rename", SilkIcons.EDIT, () -> actions.rename(folder)));
            popup.add(item("Delete", SilkIcons.DELETE, () -> actions.deleteFolder(folder)));
        } else if (value instanceof Connection connection) {
            popup.add(item("Open", SilkIcons.CONNECT, () -> actions.open(connection)));
            JMenuItem copySudo =
                    item("Copy sudo password", SilkIcons.COPY_PASSWORD, () -> actions.copySudoPassword(connection));
            copySudo.setEnabled(hasSudoPassword.test(connection));
            popup.add(copySudo);
            popup.add(item("Edit", SilkIcons.EDIT, () -> actions.edit(connection)));
            popup.addSeparator();
            popup.add(item("Delete", SilkIcons.DELETE, () -> actions.deleteConnection(connection)));
        } else {
            return null;
        }
        return popup;
    }

    private static JMenuItem item(String title, Icon icon, Runnable action) {
        JMenuItem item = new JMenuItem(title, icon);
        item.addActionListener(event -> action.run());
        return item;
    }
}
