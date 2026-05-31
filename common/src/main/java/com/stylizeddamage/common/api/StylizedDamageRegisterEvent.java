package com.stylizeddamage.common.api;

import java.util.Objects;

/**
 * Event fired by the StylizedDamage mod during its initialization phase,
 * allowing third-party mods to programmatically register custom styles and
 * selectors via the {@link StylizedDamageAPI}.
 *
 * <h3>Usage (platform-specific event bus)</h3>
 * <pre>{@code
 * // Forge / NeoForge example
 * @SubscribeEvent
 * public void onStylizedDamageRegister(StylizedDamageRegisterEvent event) {
 *     StylizedDamageAPI api = event.getAPI();
 *     api.createStyle("my_style")
 *         .color("#FF4444")
 *         .register();
 * }
 * }</pre>
 *
 * <p>This class is defined in the common module (zero platform dependencies)
 * and is fired by the platform-specific mod initializer. External mods
 * subscribe on their platform's event bus using the normal
 * {@code @SubscribeEvent} annotation.
 *
 * <p>All registrations made via the API during this event are collected and
 * applied before the selector engine begins matching. API-registered styles
 * override file-loaded styles with the same name; API-registered selectors
 * are inserted at the highest priority (before config selectors).
 */
public final class StylizedDamageRegisterEvent {

    private final StylizedDamageAPI api;

    /**
     * Creates a new event carrying the fully-initialized API instance.
     *
     * @param api the API singleton; must not be null
     * @throws NullPointerException if {@code api} is null
     */
    public StylizedDamageRegisterEvent(StylizedDamageAPI api) {
        this.api = Objects.requireNonNull(api, "api must not be null");
    }

    /**
     * Returns the {@link StylizedDamageAPI} instance through which external
     * mods can register styles, animations, and selectors.
     *
     * @return the API singleton, never null
     */
    public StylizedDamageAPI getAPI() {
        return api;
    }
}
