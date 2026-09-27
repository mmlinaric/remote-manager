package com.mmlinaric.remotemanager.ui.connections;

import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.model.ConnectionFolder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Retains folder expansion and selection while the connection tree model is rebuilt. */
final class ConnectionTreeState {
    private final Set<UUID> expandedFolderIds = new HashSet<>();
    private String selectedId = "";
    private boolean loaded;
    private boolean restoring;

    void restoreOnNextLoad(String expandedIds, String selectedId) {
        expandedFolderIds.clear();
        for (String id : expandedIds.split(",")) {
            try {
                expandedFolderIds.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
                /* Ignore stale or malformed display state. */
            }
        }
        this.selectedId = selectedId;
        loaded = false;
    }

    void clear() {
        expandedFolderIds.clear();
        selectedId = "";
        loaded = false;
    }

    void clearExpandedFolders() {
        expandedFolderIds.clear();
    }

    void prepareReload(JTree tree) {
        if (loaded) selectedId = selectedId(tree);
    }

    void retainFolders(List<ConnectionFolder> folders) {
        expandedFolderIds.retainAll(
                folders.stream().map((ConnectionFolder folder) -> folder.id()).collect(Collectors.toSet()));
    }

    void restore(JTree tree) {
        if (!(tree.getModel().getRoot() instanceof DefaultMutableTreeNode root)) return;
        Map<UUID, TreePath> folderPaths = new HashMap<>();
        Map<String, TreePath> selectionPaths = new HashMap<>();
        var nodes = root.depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            Object value = node.getUserObject();
            if (value instanceof ConnectionFolder folder) {
                TreePath path = new TreePath(node.getPath());
                folderPaths.put(folder.id(), path);
                selectionPaths.put(folder.id().toString(), path);
            } else if (value instanceof Connection connection) {
                selectionPaths.put(connection.id().toString(), new TreePath(node.getPath()));
            }
        }
        folderPaths.entrySet().stream()
                .filter(entry -> expandedFolderIds.contains(entry.getKey()))
                .map((Map.Entry<UUID, TreePath> entry) -> entry.getValue())
                .sorted(java.util.Comparator.comparingInt((TreePath path) -> path.getPathCount()))
                .forEach(tree::expandPath);
        // Expanding a hidden child also opens its parent; close unsaved parents afterward.
        folderPaths.entrySet().stream()
                .filter(entry -> !expandedFolderIds.contains(entry.getKey()))
                .map((Map.Entry<UUID, TreePath> entry) -> entry.getValue())
                .sorted(java.util.Comparator.comparingInt((TreePath path) -> path.getPathCount())
                        .reversed())
                .filter(tree::isExpanded)
                .forEach(tree::collapsePath);
        TreePath selectedPath = selectionPaths.get(selectedId);
        if (selectedPath != null && isVisibleInExpandedFolders(selectedPath)) tree.setSelectionPath(selectedPath);
    }

    void reveal(JTree tree, UUID id) {
        if (!(tree.getModel().getRoot() instanceof DefaultMutableTreeNode root)) return;
        var nodes = root.depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            Object value = node.getUserObject();
            UUID nodeId = value instanceof ConnectionFolder folder
                    ? folder.id()
                    : value instanceof Connection connection ? connection.id() : null;
            if (id.equals(nodeId)) {
                TreePath path = new TreePath(node.getPath());
                tree.expandPath(path.getParentPath());
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
                return;
            }
        }
    }

    void recordExpansion(TreePath path, boolean expanded) {
        if (restoring) return;
        if (path.getLastPathComponent() instanceof DefaultMutableTreeNode node
                && node.getUserObject() instanceof ConnectionFolder folder) {
            if (expanded) expandedFolderIds.add(folder.id());
            else expandedFolderIds.remove(folder.id());
        }
    }

    void replaceModel(Runnable replacement) {
        restoring = true;
        try {
            replacement.run();
        } finally {
            restoring = false;
        }
    }

    void markLoaded() {
        loaded = true;
    }

    String expandedIds() {
        return expandedFolderIds.stream()
                .map((UUID id) -> id.toString())
                .sorted()
                .collect(Collectors.joining(","));
    }

    String selectedId(JTree tree) {
        if (tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node) {
            Object selected = node.getUserObject();
            if (selected instanceof Connection connection)
                return connection.id().toString();
            if (selected instanceof ConnectionFolder folder) return folder.id().toString();
        }
        return "";
    }

    private boolean isVisibleInExpandedFolders(TreePath path) {
        for (int index = 1; index < path.getPathCount() - 1; index++) {
            if (path.getPathComponent(index) instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof ConnectionFolder folder
                    && !expandedFolderIds.contains(folder.id())) return false;
        }
        return true;
    }
}
