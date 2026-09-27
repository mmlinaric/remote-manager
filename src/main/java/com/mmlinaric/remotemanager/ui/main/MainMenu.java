package com.mmlinaric.remotemanager.ui.main;

import com.mmlinaric.remotemanager.app.AppVersion;
import com.mmlinaric.remotemanager.ui.SilkIcons;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

/** Builds the desktop menu bar and reports the controls whose availability changes with vault state. */
final class MainMenu {
    private MainMenu() {}

    static Controls create(Component owner, ActionAvailability availability, Actions actions) {
        JMenuBar bar = new JMenuBar();
        Controls controls = new Controls();
        bar.add(fileMenu(availability, actions.file(), controls));
        bar.add(hostMenu(availability, actions.host(), controls));
        bar.add(sessionMenu(availability, actions.session(), controls));
        bar.add(toolsMenu(actions.tools()));
        bar.add(helpMenu(owner, actions.tools()));
        return controls.withBar(bar);
    }

    private static JMenu fileMenu(ActionAvailability availability, FileActions actions, Controls controls) {
        JMenu file = topMenu("File");
        file.add(availability.vaultItem("New host", SilkIcons.NEW_CONNECTION, actions.newHost()));
        file.add(availability.vaultItem("New folder", SilkIcons.NEW_FOLDER, actions.newFolder()));
        file.add(availability.vaultItem("New identity", SilkIcons.NEW_IDENTITY, actions.newIdentity()));
        file.addSeparator();
        controls.openVault = item("Open vault...", SilkIcons.OPEN_VAULT, actions.openVault());
        file.add(controls.openVault);
        controls.createVault = item("Create vault...", SilkIcons.CREATE_VAULT, actions.createVault());
        file.add(controls.createVault);
        controls.unlockVault = item("Unlock vault", SilkIcons.UNLOCK, actions.unlockVault());
        file.add(controls.unlockVault);
        file.add(availability.vaultItem("Lock vault", SilkIcons.LOCK, actions.lockVault()));
        file.add(availability.vaultItem("Reload vault", SilkIcons.RECONNECT, actions.reloadVault()));
        file.addSeparator();
        controls.exit = item("Exit", SilkIcons.EXIT, actions.exit());
        file.add(controls.exit);
        return file;
    }

    private static JMenu hostMenu(ActionAvailability availability, HostActions actions, Controls controls) {
        JMenu host = topMenu("Host");
        controls.connectSelected =
                availability.selectedHostItem("Connect selected", SilkIcons.CONNECT, actions.connectSelected());
        host.add(controls.connectSelected);
        host.add(availability.selectedHostItem("Edit selected", SilkIcons.EDIT, actions.editSelected()));
        host.add(availability.selectedHostItem("Delete selected", SilkIcons.DELETE, actions.deleteSelected()));
        return host;
    }

    private static JMenu sessionMenu(ActionAvailability availability, SessionActions actions, Controls controls) {
        JMenu session = topMenu("Session");
        session.add(availability.selectedSessionItem("Disconnect", SilkIcons.DISCONNECT, actions.disconnect()));
        session.add(availability.selectedSessionItem("Reconnect", SilkIcons.RECONNECT, actions.reconnect()));
        session.add(availability.selectedSessionItem("Close tab", SilkIcons.CLOSE, actions.closeTab()));
        session.addSeparator();
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenuItem increase =
                availability.selectedSessionItem("Increase font size", SilkIcons.FONT_INCREASE, actions.increaseFont());
        increase.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, shortcut));
        session.add(increase);
        JMenuItem decrease =
                availability.selectedSessionItem("Decrease font size", SilkIcons.FONT_DECREASE, actions.decreaseFont());
        decrease.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, shortcut));
        session.add(decrease);
        JMenuItem reset = availability.selectedSessionItem("Reset font size", SilkIcons.FONT, actions.resetFont());
        reset.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, shortcut));
        session.add(reset);
        session.addSeparator();
        controls.copySudoPassword = availability.selectedSessionItem(
                "Copy sudo password", SilkIcons.COPY_PASSWORD, actions.copySudoPassword());
        session.add(controls.copySudoPassword);
        return session;
    }

    private static JMenu toolsMenu(ToolActions actions) {
        JMenu tools = topMenu("Tools");
        tools.add(item("Settings", SilkIcons.SETTINGS, actions.editSettings()));
        return tools;
    }

    private static JMenu helpMenu(Component owner, ToolActions actions) {
        JMenu help = topMenu("Help");
        help.add(item("Check for updates...", SilkIcons.RECONNECT, actions.checkForUpdates()));
        help.addSeparator();
        help.add(item("About", SilkIcons.ABOUT, () -> showAbout(owner)));
        return help;
    }

    private static void showAbout(Component owner) {
        JOptionPane.showMessageDialog(
                owner,
                "Remote Manager " + AppVersion.display()
                        + "\nSSH hosts and identities in a KeePass vault."
                        + "\nIcons: FamFamFam Silk by Mark James (CC BY 2.5).",
                "About Remote Manager",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static JMenu topMenu(String title) {
        JMenu menu = new JMenu(title);
        int horizontalPadding = com.sun.jna.Platform.isLinux() ? 10 : 4;
        menu.setBorder(BorderFactory.createEmptyBorder(0, horizontalPadding, 0, horizontalPadding));
        return menu;
    }

    private static JMenuItem item(String title, Icon icon, Runnable action) {
        JMenuItem item = new JMenuItem(title, icon);
        item.addActionListener(event -> action.run());
        return item;
    }

    record Actions(FileActions file, HostActions host, SessionActions session, ToolActions tools) {}

    record FileActions(
            Runnable newHost,
            Runnable newFolder,
            Runnable newIdentity,
            Runnable openVault,
            Runnable createVault,
            Runnable unlockVault,
            Runnable lockVault,
            Runnable reloadVault,
            Runnable exit) {}

    record HostActions(Runnable connectSelected, Runnable editSelected, Runnable deleteSelected) {}

    record SessionActions(
            Runnable disconnect,
            Runnable reconnect,
            Runnable closeTab,
            Runnable increaseFont,
            Runnable decreaseFont,
            Runnable resetFont,
            Runnable copySudoPassword) {}

    record ToolActions(Runnable editSettings, Runnable checkForUpdates) {}

    static final class Controls {
        private JMenuBar bar;
        private JMenuItem unlockVault;
        private JMenuItem connectSelected;
        private JMenuItem copySudoPassword;
        private JMenuItem openVault;
        private JMenuItem createVault;
        private JMenuItem exit;

        JMenuBar bar() {
            return bar;
        }

        JMenuItem unlockVault() {
            return unlockVault;
        }

        JMenuItem connectSelected() {
            return connectSelected;
        }

        JMenuItem copySudoPassword() {
            return copySudoPassword;
        }

        JMenuItem openVault() {
            return openVault;
        }

        JMenuItem createVault() {
            return createVault;
        }

        JMenuItem exit() {
            return exit;
        }

        private Controls withBar(JMenuBar value) {
            bar = value;
            return this;
        }
    }
}
