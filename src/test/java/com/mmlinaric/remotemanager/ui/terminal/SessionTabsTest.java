package com.mmlinaric.remotemanager.ui.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jediterm.terminal.ui.JediTermWidget;
import com.mmlinaric.remotemanager.model.AuthenticationType;
import com.mmlinaric.remotemanager.model.Connection;
import com.mmlinaric.remotemanager.ssh.SshRemoteSession;
import com.mmlinaric.remotemanager.ui.settings.AppSettings;
import java.awt.Component;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SessionTabsTest {
    @Test
    void selectedSessionUsesEditedHostForSudoActions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            UUID id = UUID.randomUUID();
            Connection opened = host(id, null);
            Connection edited = host(id, UUID.randomUUID());
            AtomicReference<Connection> current = new AtomicReference<>(opened);
            SessionTabs tabs = new SessionTabs(
                    () -> null,
                    () -> CompletableFuture.completedFuture(true),
                    ignored -> {},
                    AppSettings::defaults,
                    hostId -> id.equals(hostId) ? current.get() : null);
            try {
                JPanel terminal = new JPanel();
                tabs.addTab("Server", terminal);
                tabs.setSelectedComponent(terminal);
                addOpenTab(tabs, terminal, opened);

                assertEquals(opened, tabs.selectedConnection());
                current.set(edited);
                assertEquals(edited, tabs.selectedConnection());
            } finally {
                clearOpenTabs(tabs);
                tabs.shutdown();
            }
        });
    }

    @Test
    void endedSessionIsShownAsDisconnectedAndKeepsItsTabOpen() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicReference<String> status = new AtomicReference<>();
            SessionTabs tabs = tabsReportingTo(status);
            SshRemoteSession session = session();
            try {
                Object tab = addSessionTab(tabs, session, new AtomicBoolean());

                invoke(tabs, "showSessionEnded", tab);

                assertEquals("Server (disconnected)", tabs.getTitleAt(0));
                assertEquals(1, tabs.getTabCount());
                assertEquals("Connection to server.example closed", status.get());
            } finally {
                ((JediTermWidget) session.component()).close();
                clearOpenTabs(tabs);
                tabs.shutdown();
            }
        });
    }

    @Test
    void sessionThatEndedBeforeConnectingFinishedIsNotShownAsConnected() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicReference<String> status = new AtomicReference<>();
            SessionTabs tabs = tabsReportingTo(status);
            SshRemoteSession session = session();
            try {
                Object tab = addSessionTab(tabs, session, new AtomicBoolean());

                invoke(tabs, "showConnected", tab);

                assertEquals("Server (disconnected)", tabs.getTitleAt(0));
                assertNull(status.get());
            } finally {
                ((JediTermWidget) session.component()).close();
                clearOpenTabs(tabs);
                tabs.shutdown();
            }
        });
    }

    @Test
    void closedTabIgnoresTheEndOfItsSession() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicReference<String> status = new AtomicReference<>();
            SessionTabs tabs = tabsReportingTo(status);
            SshRemoteSession session = session();
            try {
                Object tab = addSessionTab(tabs, session, new AtomicBoolean(true));

                invoke(tabs, "showSessionEnded", tab);

                assertEquals("Server (connecting)", tabs.getTitleAt(0));
                assertNull(status.get());
            } finally {
                ((JediTermWidget) session.component()).close();
                clearOpenTabs(tabs);
                tabs.shutdown();
            }
        });
    }

    private static SessionTabs tabsReportingTo(AtomicReference<String> status) {
        return new SessionTabs(
                () -> null,
                () -> CompletableFuture.completedFuture(true),
                status::set,
                AppSettings::defaults,
                hostId -> null);
    }

    private static SshRemoteSession session() {
        return new SshRemoteSession(null, null, Path.of("known_hosts"), null, null, "Monospaced", 13, 100, false);
    }

    private static Object addSessionTab(SessionTabs tabs, SshRemoteSession session, AtomicBoolean closed) {
        try {
            tabs.addTab("Server (connecting)", session.component());
            Class<?> type = Class.forName(SessionTabs.class.getName() + "$OpenTab");
            Constructor<?> constructor = type.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            Object tab = constructor.newInstance(host(UUID.randomUUID(), null), session, closed);
            openTabs(tabs).put(session.component(), tab);
            return tab;
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static void invoke(SessionTabs tabs, String method, Object tab) {
        try {
            Method target = SessionTabs.class.getDeclaredMethod(method, tab.getClass());
            target.setAccessible(true);
            target.invoke(tabs, tab);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static Connection host(UUID id, UUID sudoId) {
        return new Connection(
                id,
                "Server",
                "server.example",
                22,
                "admin",
                null,
                AuthenticationType.SSH_AGENT,
                null,
                sudoId,
                null,
                null,
                "",
                0);
    }

    private static void addOpenTab(SessionTabs tabs, Component component, Connection connection) {
        try {
            Class<?> type = Class.forName(SessionTabs.class.getName() + "$OpenTab");
            Constructor<?> constructor = type.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            openTabs(tabs).put(component, constructor.newInstance(connection, null, new AtomicBoolean()));
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static void clearOpenTabs(SessionTabs tabs) {
        openTabs(tabs).clear();
    }

    @SuppressWarnings("unchecked")
    private static Map<Component, Object> openTabs(SessionTabs tabs) {
        try {
            Field field = SessionTabs.class.getDeclaredField("openTabs");
            field.setAccessible(true);
            return (Map<Component, Object>) field.get(tabs);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
