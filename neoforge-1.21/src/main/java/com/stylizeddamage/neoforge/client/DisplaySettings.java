package com.stylizeddamage.neoforge.client;

/**
 * Client-side display toggle — checked by both renderers before rendering.
 */
public final class DisplaySettings {

    private static volatile boolean displayEnabled = true;

    private DisplaySettings() {}

    public static boolean isDisplayEnabled() { return displayEnabled; }

    public static void setDisplayEnabled(final boolean enabled) { displayEnabled = enabled; }

    public static boolean toggle() {
        displayEnabled = !displayEnabled;
        return displayEnabled;
    }
}
