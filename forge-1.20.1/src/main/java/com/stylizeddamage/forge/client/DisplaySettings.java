package com.stylizeddamage.forge.client;

/**
 * Client-side display settings for the StylizedDamage mod.
 *
 * <p>Holds the global toggle flag that controls whether damage numbers are
 * rendered on the HUD. Both {@link DamageNumberRenderer} and
 * {@link TotalDamageHudRenderer} check this flag before rendering.
 *
 * <p>The toggle command ({@code /stylizeddamage toggle}) flips this flag.
 * In a single-player/integrated-server environment, the server and client
 * share the same JVM, so the toggle takes effect immediately.
 * For dedicated servers, this flag only affects the local client.
 */
public final class DisplaySettings {

    private static volatile boolean displayEnabled = true;

    private DisplaySettings() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    /** Returns whether damage number display is currently enabled. */
    public static boolean isDisplayEnabled() {
        return displayEnabled;
    }

    /** Sets the display enabled state. */
    public static void setDisplayEnabled(final boolean enabled) {
        displayEnabled = enabled;
    }

    /** Toggles the display enabled state and returns the new value. */
    public static boolean toggle() {
        displayEnabled = !displayEnabled;
        return displayEnabled;
    }
}
