package com.mmlinaric.remotemanager.ui.main;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.Test;

class ActionAvailabilityTest {
    @Test
    void enablesEachActionGroupOnlyWhenItsRequirementsAreMet() {
        ActionAvailability actions = new ActionAvailability();
        JButton vaultAction = actions.vaultButton("Vault", null, () -> {});
        JButton identityAction = actions.selectedIdentityButton("Identity", null, () -> {});
        JMenuItem hostAction = actions.selectedHostItem("Host", null, () -> {});
        JMenuItem sessionAction = actions.selectedSessionItem("Session", null, () -> {});

        actions.update(new ActionAvailability.State(false, true, true, true));

        assertFalse(vaultAction.isEnabled());
        assertFalse(identityAction.isEnabled());
        assertFalse(hostAction.isEnabled());
        assertTrue(sessionAction.isEnabled());

        actions.update(new ActionAvailability.State(true, false, false, false));

        assertTrue(vaultAction.isEnabled());
        assertFalse(identityAction.isEnabled());
        assertFalse(hostAction.isEnabled());
        assertFalse(sessionAction.isEnabled());
    }
}
