package com.mmlinaric.remotemanager.ui.terminal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mmlinaric.remotemanager.ui.settings.AppSettings;
import com.mmlinaric.remotemanager.ui.settings.InterfaceScale;
import org.junit.jupiter.api.Test;

class SessionTabsSettingsTest {
    @Test
    void interfaceScaleDoesNotCountAsATerminalFontChange() {
        AppSettings current = AppSettings.defaults();

        assertFalse(SessionTabs.terminalFontChanged(
                current, current.withInterfaceScale(InterfaceScale.PERCENT_150)));
        assertTrue(SessionTabs.terminalFontChanged(
                current,
                new AppSettings(
                        current.appearance(),
                        current.interfaceScale(),
                        current.terminalFont(),
                        current.terminalFontSize() + 1,
                        current.scrollbackLines(),
                        current.clipboardSeconds(),
                        current.vaultAutoLockMinutes(),
                        current.knownHosts())));
    }
}
