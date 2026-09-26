package com.example.remotemanager.ui.connections;

import com.example.remotemanager.model.ConnectionFolder;
import com.example.remotemanager.ui.DialogEscape;
import com.example.remotemanager.ui.SilkIcons;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/** Selects an existing host folder, including the vault's host root. */
final class FolderPickerDialog extends JDialog {
  record Selection(UUID folderId) {}

  private final JTree tree;
  private Selection result;

  private FolderPickerDialog(Window owner, List<ConnectionFolder> folders, UUID currentId) {
    super(owner, "Choose host folder", ModalityType.APPLICATION_MODAL);
    setLayout(new BorderLayout(8, 8));
    ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    add(new JLabel("Choose where this host will be saved:"), BorderLayout.NORTH);

    tree = new JTree(buildTree(folders));
    tree.setRootVisible(true);
    tree.setShowsRootHandles(true);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setCellRenderer(new DefaultTreeCellRenderer() {
      @Override public Component getTreeCellRendererComponent(JTree tree, Object value,
          boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, false);
        Object item = ((DefaultMutableTreeNode) value).getUserObject();
        setIcon(item instanceof ConnectionFolder ? SilkIcons.FOLDER : SilkIcons.ROOT);
        return this;
      }
    });
    TreePath currentPath = pathFor(tree, currentId);
    tree.expandPath(currentPath.getParentPath());
    tree.setSelectionPath(currentPath);
    tree.scrollPathToVisible(currentPath);
    JScrollPane scroll = new JScrollPane(tree);
    scroll.setPreferredSize(new Dimension(400, 320));
    add(scroll, BorderLayout.CENTER);

    JButton choose = new JButton("Choose", SilkIcons.FOLDER);
    choose.addActionListener(event -> {
      if (tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node) {
        Object item = node.getUserObject();
        result = new Selection(item instanceof ConnectionFolder folder ? folder.id() : null);
        dispose();
      }
    });
    choose.setEnabled(tree.getSelectionPath() != null);
    tree.addTreeSelectionListener(event -> choose.setEnabled(tree.getSelectionPath() != null));
    JButton cancel = new JButton("Cancel", SilkIcons.CLOSE);
    cancel.addActionListener(event -> dispose());
    DialogEscape.bind(this, cancel);
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
    actions.add(choose);
    actions.add(cancel);
    add(actions, BorderLayout.SOUTH);
    getRootPane().setDefaultButton(choose);
    pack();
    setMinimumSize(new Dimension(360, 280));
    setLocationRelativeTo(owner);
  }

  static Selection choose(Window owner, List<ConnectionFolder> folders, UUID currentId) {
    FolderPickerDialog dialog = new FolderPickerDialog(owner, folders, currentId);
    dialog.setVisible(true);
    return dialog.result;
  }

  static DefaultTreeModel buildTree(List<ConnectionFolder> folders) {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("(root)");
    Map<UUID, DefaultMutableTreeNode> nodes = new HashMap<>();
    folders.forEach(folder -> nodes.put(folder.id(), new DefaultMutableTreeNode(folder)));
    folders.forEach(folder -> nodes.getOrDefault(folder.parentFolderId(), root)
        .add(nodes.get(folder.id())));
    return new DefaultTreeModel(root);
  }

  static TreePath pathFor(JTree tree, UUID id) {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
    if (id == null) return new TreePath(root.getPath());
    var nodes = root.depthFirstEnumeration();
    while (nodes.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
      if (node.getUserObject() instanceof ConnectionFolder folder && folder.id().equals(id))
        return new TreePath(node.getPath());
    }
    return new TreePath(root.getPath());
  }

  static String displayPath(List<ConnectionFolder> folders, ConnectionFolder selected) {
    if (selected == null) return "(root)";
    Map<UUID, ConnectionFolder> byId = new HashMap<>();
    folders.forEach(folder -> byId.put(folder.id(), folder));
    java.util.ArrayDeque<String> names = new java.util.ArrayDeque<>();
    for (ConnectionFolder folder = selected; folder != null; folder = byId.get(folder.parentFolderId()))
      names.addFirst(folder.name());
    return "(root) / " + String.join(" / ", names);
  }
}
