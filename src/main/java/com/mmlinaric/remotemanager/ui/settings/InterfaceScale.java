package com.mmlinaric.remotemanager.ui.settings;

import java.util.Arrays;

/** Supported application interface scales and their stable persisted percentages. */
public enum InterfaceScale {
    PERCENT_100(100),
    PERCENT_110(110),
    PERCENT_125(125),
    PERCENT_150(150),
    PERCENT_175(175),
    PERCENT_200(200);

    private final int percent;

    InterfaceScale(int percent) {
        this.percent = percent;
    }

    public int percent() {
        return percent;
    }

    public float factor() {
        return percent / 100f;
    }

    public String persistedName() {
        return Integer.toString(percent);
    }

    public static InterfaceScale fromPersistedName(String value) {
        if (value == null) return PERCENT_100;
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(scale -> scale.persistedName().equals(normalized))
                .findFirst()
                .orElse(PERCENT_100);
    }

    @Override
    public String toString() {
        return percent + "%";
    }
}
