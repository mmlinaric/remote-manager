package com.mmlinaric.remotemanager.ui.main;

import com.mmlinaric.remotemanager.ui.terminal.SessionTabs;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import javax.swing.SwingUtilities;

/** Routes application font shortcuts to the terminal that currently owns keyboard focus. */
final class TerminalFontKeyDispatcher implements KeyEventDispatcher {
    private final SessionTabs tabs;

    TerminalFontKeyDispatcher(SessionTabs tabs) {
        this.tabs = tabs;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Component selected = tabs.getSelectedComponent();
        Component source = event.getComponent();
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        if (selected == null
                || source == null
                || !SwingUtilities.isDescendingFrom(source, selected)
                || (event.getModifiersEx() & shortcut) == 0) {
            return false;
        }

        int change =
                switch (event.getKeyCode()) {
                    case KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS, KeyEvent.VK_ADD -> 1;
                    case KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> -1;
                    case KeyEvent.VK_0 -> 0;
                    default -> Integer.MIN_VALUE;
                };
        if (event.getID() == KeyEvent.KEY_TYPED) {
            char character = event.getKeyChar();
            if (character != '+' && character != '=' && character != '-' && character != '0') return false;
        } else if (change == Integer.MIN_VALUE) {
            return false;
        }
        if (event.getID() == KeyEvent.KEY_PRESSED) {
            if (change == 0) tabs.resetFontSize();
            else tabs.changeFontSize(change);
        }
        event.consume();
        return true;
    }
}
