package com.stylizeddamage.common.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stylizeddamage.common.selector.MatchRule;
import com.stylizeddamage.common.selector.SelectorConfig;
import com.stylizeddamage.common.selector.SelectorEngine;
import com.stylizeddamage.common.style.Style;
import com.stylizeddamage.common.style.StyleLoader;

import java.util.*;

/**
 * Singleton entry point for the StylizedDamage API.
 *
 * <p>Third-party mods obtain the API instance via the
 * {@link StylizedDamageRegisterEvent} during initialization and use it to
 * programmatically register custom styles, animations, and selector rules.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>The platform module initialises via {@link #initialize(StyleLoader, SelectorEngine)}.</li>
 *   <li>The platform module fires {@link StylizedDamageRegisterEvent} on its event bus.</li>
 *   <li>External mods subscribe and call {@link #createStyle(String)},
 *       {@link #createAnimation()}, and {@link #selectors()}.</li>
 *   <li>After registrations complete, the platform calls
 *       {@link #buildFinalSelectorConfig(Map)} to merge API rules with
 *       file selectors.</li>
 * </ol>
 *
 * <h3>Priority Rules</h3>
 * <ul>
 *   <li><b>Selectors:</b> API rules are inserted <em>before</em> config-file rules.</li>
 *   <li><b>Styles:</b> API-registered styles override same-named file styles
 *       (via {@link StyleLoader#register}).</li>
 * </ul>
 */
public final class StylizedDamageAPI {

    private static volatile StylizedDamageAPI instance;

    /**
     * Returns the singleton API instance.
     *
     * @throws IllegalStateException if not yet initialised
     */
    public static StylizedDamageAPI getInstance() {
        StylizedDamageAPI inst = instance;
        if (inst == null) {
            throw new IllegalStateException(
                    "StylizedDamageAPI not initialised — call initialize() first.");
        }
        return inst;
    }

    // ── Initialization ────────────────────────────────────────────────

    private StylizedDamageAPI() {}

    /**
     * Initialises the singleton. Must be called once by the platform module
     * before the register event. Subsequent calls are safe no-ops.
     */
    public static synchronized void initialize(StyleLoader styleLoader,
                                               SelectorEngine selectorEngine) {
        Objects.requireNonNull(styleLoader, "styleLoader");
        Objects.requireNonNull(selectorEngine, "selectorEngine");
        if (instance == null) {
            instance = new StylizedDamageAPI();
        }
        instance.styleLoader = styleLoader;
        instance.selectorEngine = selectorEngine;
    }

    public static boolean isInitialized() {
        return instance != null;
    }

    // ── Engine references ─────────────────────────────────────────────

    private volatile StyleLoader styleLoader;
    private volatile SelectorEngine selectorEngine;

    public StyleLoader getStyleLoader() {
        checkInit();
        return styleLoader;
    }

    public SelectorEngine getSelectorEngine() {
        checkInit();
        return selectorEngine;
    }

    private void checkInit() {
        if (styleLoader == null) {
            throw new IllegalStateException("API not fully initialised.");
        }
    }

    // ── Factory methods ───────────────────────────────────────────────

    /** Creates a {@link StyleBuilder} for the given style name. */
    public StyleBuilder createStyle(String name) {
        checkInit();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Style name must not be blank");
        }
        return new StyleBuilder(name, styleLoader);
    }

    /** Creates an {@link AnimationBuilder}. */
    public AnimationBuilder createAnimation() {
        return new AnimationBuilder();
    }

    /** Creates a {@link SelectorBuilder} for API-level selector rules. */
    public SelectorBuilder selectors() {
        checkInit();
        return new SelectorBuilder(this);
    }

    // ── Direct style registration ─────────────────────────────────────

    /** Registers a fully-constructed {@link Style}. */
    public void registerStyle(String name, Style style) {
        checkInit();
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(style, "style");
        styleLoader.register(name, style);
    }

    // ── API selector registry (internal) ──────────────────────────────
    //
    // API selectors are stored separately and merged with file selectors
    // via buildFinalSelectorConfig(). The storage shape mirrors
    // CommonConfig.selectors(): interval → JsonObject of branches.

    private final Map<String, JsonObject> apiSelectors = new LinkedHashMap<>();

    /**
     * Registers a set of rules for an interval + branch.
     * Called by {@link SelectorBuilder#register()}.
     */
    void registerSelectorRules(String interval, String branch, List<MatchRule> rules) {
        synchronized (apiSelectors) {
            JsonObject branchObj = apiSelectors.computeIfAbsent(interval, k -> new JsonObject());
            writeBranch(branchObj, branch, rules);
        }
    }

    /**
     * Merges API-registered selector rules with configuration-file
     * selectors, producing a final {@link SelectorConfig} where API rules
     * are inserted before file rules (highest priority).
     *
     * @param fileSelectors the raw selectors map from {@code CommonConfig.selectors()}
     * @return a merged {@link SelectorConfig}
     */
    public SelectorConfig buildFinalSelectorConfig(Map<String, JsonObject> fileSelectors) {
        Objects.requireNonNull(fileSelectors, "fileSelectors");

        Map<String, JsonObject> merged = new LinkedHashMap<>();

        synchronized (apiSelectors) {
            // Gather all interval keys (API comes first for ordering)
            Set<String> intervals = new LinkedHashSet<>(apiSelectors.keySet());
            intervals.addAll(fileSelectors.keySet());

            for (String interval : intervals) {
                JsonObject apiBranchObj = apiSelectors.get(interval);
                JsonObject fileBranchObj = fileSelectors.get(interval);

                if (apiBranchObj == null) {
                    // File-only interval
                    merged.put(interval, deepCopyJson(fileBranchObj));
                } else if (fileBranchObj == null) {
                    // API-only interval
                    merged.put(interval, deepCopyJson(apiBranchObj));
                } else {
                    // Both — merge per branch: API rules before file rules
                    merged.put(interval, mergeBranchObjects(apiBranchObj, fileBranchObj));
                }
            }

            // Ensure "common" interval always exists as fallback
            if (!merged.containsKey("common")) {
                JsonObject commonBranch = new JsonObject();
                JsonArray defaultRules = new JsonArray();
                JsonObject defaultRule = new JsonObject();
                JsonArray matchArr = new JsonArray();
                matchArr.add("*");
                defaultRule.add("match", matchArr);
                defaultRule.addProperty("style", "default");
                defaultRules.add(defaultRule);
                commonBranch.add("common", defaultRules);
                merged.put("common", commonBranch);
            }
        }

        return SelectorConfig.from(merged);
    }

    /** Clears all API-registered selectors (for hot-reload). */
    public void clearApiSelectors() {
        synchronized (apiSelectors) {
            apiSelectors.clear();
        }
    }

    // ── Internal merge helpers ────────────────────────────────────────

    /**
     * Writes rules into the branch JSON object, supporting dotted keys
     * like {@code "player:yourTeam"} → nested player.yourTeam.
     */
    private static void writeBranch(JsonObject branchObj, String branch, List<MatchRule> rules) {
        JsonArray rulesJson = rulesToJsonArray(rules);

        if (branch.contains(":")) {
            String[] parts = branch.split(":", 2);
            if (!branchObj.has(parts[0]) || !branchObj.get(parts[0]).isJsonObject()) {
                branchObj.add(parts[0], new JsonObject());
            }
            JsonObject parent = branchObj.getAsJsonObject(parts[0]);

            if (parent.has(parts[1])) {
                // Prepend to existing rules
                JsonArray existing = parent.getAsJsonArray(parts[1]);
                JsonArray combined = new JsonArray();
                combined.addAll(rulesJson);
                combined.addAll(existing);
                parent.add(parts[1], combined);
            } else {
                parent.add(parts[1], rulesJson);
            }
        } else {
            if (branchObj.has(branch)) {
                JsonArray existing = branchObj.getAsJsonArray(branch);
                JsonArray combined = new JsonArray();
                combined.addAll(rulesJson);
                combined.addAll(existing);
                branchObj.add(branch, combined);
            } else {
                branchObj.add(branch, rulesJson);
            }
        }
    }

    /**
     * Merges two branch JSON objects: API branches get their rules at the
     * front of file rules. File-only branches are appended after API branches.
     */
    private static JsonObject mergeBranchObjects(JsonObject apiBranches,
                                                  JsonObject fileBranches) {
        JsonObject result = new JsonObject();
        Set<String> done = new HashSet<>();

        // Process API branches first (higher priority)
        for (Map.Entry<String, com.google.gson.JsonElement> entry : apiBranches.entrySet()) {
            String key = entry.getKey();
            if ("player".equals(key) || "mob".equals(key)) {
                // Nested sub-branches
                JsonObject apiSub = entry.getValue().getAsJsonObject();
                JsonObject mergedSub = new JsonObject();

                JsonObject fileSub = fileBranches.has(key)
                        && fileBranches.get(key).isJsonObject()
                        ? fileBranches.getAsJsonObject(key) : new JsonObject();

                Set<String> subKeys = new LinkedHashSet<>(apiSub.keySet());
                subKeys.addAll(fileSub.keySet());

                for (String subKey : subKeys) {
                    if (apiSub.has(subKey) && fileSub.has(subKey)) {
                        // Prepend API rules
                        JsonArray combined = new JsonArray();
                        combined.addAll(apiSub.getAsJsonArray(subKey));
                        combined.addAll(fileSub.getAsJsonArray(subKey));
                        mergedSub.add(subKey, combined);
                    } else if (apiSub.has(subKey)) {
                        mergedSub.add(subKey, deepCopyArray(apiSub.getAsJsonArray(subKey)));
                    } else {
                        mergedSub.add(subKey, deepCopyArray(fileSub.getAsJsonArray(subKey)));
                    }
                }
                result.add(key, mergedSub);
            } else {
                // Flat branch (e.g. "common")
                if (fileBranches.has(key)) {
                    JsonArray combined = new JsonArray();
                    combined.addAll(entry.getValue().getAsJsonArray());
                    combined.addAll(fileBranches.getAsJsonArray(key));
                    result.add(key, combined);
                } else {
                    result.add(key, deepCopyArray(entry.getValue().getAsJsonArray()));
                }
            }
            done.add(key);
        }

        // File-only branches (not in API) — append after API branches
        for (Map.Entry<String, com.google.gson.JsonElement> entry : fileBranches.entrySet()) {
            if (!done.contains(entry.getKey())) {
                result.add(entry.getKey(), deepCopyJson(entry.getValue()));
            }
        }

        return result;
    }

    // ── JSON conversion helpers ───────────────────────────────────────

    private static JsonObject buildJsonFromMap(Map<String, JsonObject> map) {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, JsonObject> entry : map.entrySet()) {
            root.add(entry.getKey(), entry.getValue());
        }
        return root;
    }

    private static JsonArray rulesToJsonArray(List<MatchRule> rules) {
        JsonArray arr = new JsonArray();
        for (MatchRule rule : rules) {
            JsonObject obj = new JsonObject();
            JsonArray matchArr = new JsonArray();
            for (String cond : rule.match()) {
                matchArr.add(cond);
            }
            obj.add("match", matchArr);
            obj.addProperty("style", rule.style());
            arr.add(obj);
        }
        return arr;
    }

    private static JsonArray deepCopyArray(JsonArray src) {
        JsonArray copy = new JsonArray();
        for (com.google.gson.JsonElement e : src) {
            if (e.isJsonObject()) {
                copy.add(deepCopyJson(e.getAsJsonObject()));
            } else if (e.isJsonArray()) {
                copy.add(deepCopyArray(e.getAsJsonArray()));
            } else {
                copy.add(e.deepCopy()); // primitives, null
            }
        }
        return copy;
    }

    private static JsonObject deepCopyJson(JsonObject src) {
        JsonObject copy = new JsonObject();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : src.entrySet()) {
            com.google.gson.JsonElement value = entry.getValue();
            if (value.isJsonObject()) {
                copy.add(entry.getKey(), deepCopyJson(value.getAsJsonObject()));
            } else if (value.isJsonArray()) {
                copy.add(entry.getKey(), deepCopyArray(value.getAsJsonArray()));
            } else {
                copy.add(entry.getKey(), value.deepCopy());
            }
        }
        return copy;
    }

    private static JsonObject deepCopyJson(com.google.gson.JsonElement src) {
        return deepCopyJson(src.getAsJsonObject());
    }
}
