package com.mmlinaric.remotemanager.ui.settings;

import java.util.Locale;

/** Supported application appearances and their persisted names. */
public enum Appearance {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    private final String displayName;

    Appearance(String displayName) {
        this.displayName = displayName;
    }

    public String persistedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Appearance fromPersistedName(String value) {
        if (value == null) return SYSTEM;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return SYSTEM;
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
