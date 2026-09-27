package com.mmlinaric.remotemanager.ui.main;

import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JMenuItem;

/** Keeps controls enabled only when their required vault, selection, and session state is available. */
final class ActionAvailability {
    private final List<AbstractButton> vaultActions = new ArrayList<>();
    private final List<AbstractButton> hostActions = new ArrayList<>();
    private final List<AbstractButton> identityActions = new ArrayList<>();
    private final List<AbstractButton> sessionActions = new ArrayList<>();

    JButton vaultButton(String title, Icon icon, Runnable action) {
        JButton button = button(title, icon, action);
        vaultActions.add(button);
        return button;
    }

    JButton selectedIdentityButton(String title, Icon icon, Runnable action) {
        JButton button = button(title, icon, action);
        identityActions.add(button);
        return button;
    }

    JMenuItem vaultItem(String title, Icon icon, Runnable action) {
        JMenuItem item = item(title, icon, action);
        vaultActions.add(item);
        return item;
    }

    JMenuItem selectedHostItem(String title, Icon icon, Runnable action) {
        JMenuItem item = item(title, icon, action);
        hostActions.add(item);
        return item;
    }

    JMenuItem selectedSessionItem(String title, Icon icon, Runnable action) {
        JMenuItem item = item(title, icon, action);
        sessionActions.add(item);
        return item;
    }

    void update(State state) {
        vaultActions.forEach(action -> action.setEnabled(state.vaultAvailable()));
        hostActions.forEach(action -> action.setEnabled(state.vaultAvailable() && state.hasSelectedHost()));
        identityActions.forEach(action -> action.setEnabled(state.vaultAvailable() && state.hasSelectedIdentity()));
        sessionActions.forEach(action -> action.setEnabled(state.hasSelectedSession()));
    }

    private static JButton button(String title, Icon icon, Runnable action) {
        JButton button = new JButton(title, icon);
        button.addActionListener(event -> action.run());
        return button;
    }

    private static JMenuItem item(String title, Icon icon, Runnable action) {
        JMenuItem item = new JMenuItem(title, icon);
        item.addActionListener(event -> action.run());
        return item;
    }

    record State(
            boolean vaultAvailable, boolean hasSelectedHost, boolean hasSelectedIdentity, boolean hasSelectedSession) {}
}
