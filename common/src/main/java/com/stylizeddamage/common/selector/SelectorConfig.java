package com.stylizeddamage.common.selector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Parsed selector configuration from {@code common.json}'s {@code selectors} block.
 *
 * <p>This class transforms the raw JSON structure into an immutable, queriable
 * tree. The tree has three levels:
 * <ol>
 *   <li><b>Interval</b> — damage-value range (e.g. {@code "[50,...]"}, {@code "common"})</li>
 *   <li><b>Branch</b> — target-type sub-path (e.g. {@code "player:yourTeam"},
 *       {@code "mob:hostile"}, or the top-level {@code "common"})</li>
 *   <li><b>MatchRule list</b> — ordered list of match-condition→style mappings</li>
 * </ol>
 *
 * <p>Parsing is lenient: if a branch value is a single JSON object (instead of
 * an array), it is automatically wrapped into a single-element list.
 *
 * <p>Instances are immutable — create via {@link #from(JsonObject)}.
 */
public final class SelectorConfig {

    /**
     * Maps each interval to its nested branch→rules map.
     * Maintains insertion order (from the JSON) to ensure deterministic matching.
     */
    private final Map<String, Map<String, List<MatchRule>>> intervalMap;

    /** Pre-parsed interval objects for fast lookup — built once during construction. */
    private final Map<String, IntervalParser.Interval> parsedIntervals;

    private SelectorConfig(Map<String, Map<String, List<MatchRule>>> intervalMap) {
        this.intervalMap = Collections.unmodifiableMap(intervalMap);
        // Pre-parse all interval keys so findInterval() avoids regex matching at runtime
        Map<String, IntervalParser.Interval> parsed = new LinkedHashMap<>();
        for (String key : intervalMap.keySet()) {
            if (!"common".equals(key)) {
                try {
                    parsed.put(key, IntervalParser.parse(key));
                } catch (IllegalArgumentException ignored) {
                    // Malformed interval keys are skipped — they can't match
                }
            }
        }
        this.parsedIntervals = Collections.unmodifiableMap(parsed);
    }

    // ── Public factory ────────────────────────────────────────────────

    /**
     * Parses the raw {@code selectors} JSON object (the value under the
     * {@code "selectors"} key in {@code common.json}) into a
     * {@link SelectorConfig}.
     *
     * <p>The input has the shape:
     * <pre>{@code
     * {
     *   "common": {                    // ← interval key
     *     "common": [ ... ],           // ← branch "common" (selector array)
     *     "player": {                  // ← optional player sub-branches
     *       "yourTeam": [ ... ],
     *       "otherTeam": [ ... ],
     *       "common": [ ... ]
     *     },
     *     "mob": {                     // ← optional mob sub-branches
     *       "hostile": [ ... ],
     *       "passive": [ ... ],
     *       "common": [ ... ]
     *     }
     *   },
     *   "[50,...]": { ... }
     * }
     * }</pre>
     *
     * @param selectorsJson the raw {@code selectors} JSON object, never {@code null}
     * @return a parsed, immutable {@link SelectorConfig}
     */
    public static SelectorConfig from(JsonObject selectorsJson) {
        Objects.requireNonNull(selectorsJson, "selectorsJson must not be null");

        Map<String, Map<String, List<MatchRule>>> result = new LinkedHashMap<>();

        for (Map.Entry<String, JsonElement> entry : selectorsJson.entrySet()) {
            String intervalKey = entry.getKey();
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject branchObj = entry.getValue().getAsJsonObject();
            Map<String, List<MatchRule>> branchMap = parseBranches(branchObj);
            if (!branchMap.isEmpty()) {
                result.put(intervalKey, branchMap);
            }
        }

        // Always ensure a "common" interval exists as fallback
        if (!result.containsKey("common")) {
            result.put("common", defaultBranchMap());
        }

        return new SelectorConfig(result);
    }

    /**
     * Convenience overload that accepts a {@link Map} (as returned by
     * {@code CommonConfig.selectors()}).
     *
     * @param selectorsMap a map of interval key → branch JSON object
     * @return a parsed, immutable {@link SelectorConfig}
     */
    public static SelectorConfig from(Map<String, JsonObject> selectorsMap) {
        Objects.requireNonNull(selectorsMap, "selectorsMap must not be null");
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, JsonObject> entry : selectorsMap.entrySet()) {
            obj.add(entry.getKey(), entry.getValue());
        }
        return from(obj);
    }

    /**
     * Expands {@code #tag_name} entries in all match rules to actual
     * damage type IDs using the built-in damage type tag table.
     */
    public void expandTags() {
        for (Map<String, List<MatchRule>> branchMap : intervalMap.values()) {
            for (List<MatchRule> rules : branchMap.values()) {
                for (int i = 0; i < rules.size(); i++) {
                    MatchRule rule = rules.get(i);
                    List<String> expanded = new ArrayList<>();
                    boolean changed = false;
                    for (String m : rule.match()) {
                        if (m.startsWith("#")) {
                            Set<String> ids = DamageTypeTags.resolveAny(m.substring(1));
                            expanded.addAll(ids);
                            changed = true;
                        } else {
                            expanded.add(m);
                        }
                    }
                    if (changed) {
                        rules.set(i, new MatchRule(Collections.unmodifiableList(expanded), rule.style()));
                    }
                }
            }
        }
    }

    /**
     * Creates an empty config with only the default fallback (matches
     * everything → {@code "default"} style).
     */
    public static SelectorConfig empty() {
        Map<String, Map<String, List<MatchRule>>> map = new LinkedHashMap<>();
        map.put("common", defaultBranchMap());
        return new SelectorConfig(map);
    }

    // ── Query interface ────────────────────────────────────────────────

    /**
     * Finds the interval that should apply for the given damage value.
     *
     * <p>Uses pre-parsed cached intervals for O(1) per-check amortized cost
     * (no regex matching at runtime). Non-{@code "common"} intervals are
     * tested in insertion order. When no specific interval matches,
     * {@code "common"} is returned.
     *
     * @param damage the damage amount
     * @return the matching interval key, never {@code null}
     */
    public String findInterval(double damage) {
        for (Map.Entry<String, IntervalParser.Interval> entry : parsedIntervals.entrySet()) {
            if (entry.getValue().contains(damage)) {
                return entry.getKey();
            }
        }
        return "common";
    }

    /**
     * Returns the branch→rules map for a given interval key.
     *
     * @param intervalKey the interval key (e.g. {@code "common"}, {@code "[50,...]"})
     * @return the branch map, or an empty map if the interval is unknown
     */
    public Map<String, List<MatchRule>> branchesFor(String intervalKey) {
        return intervalMap.getOrDefault(intervalKey, Collections.emptyMap());
    }

    /**
     * Returns the ordered list of {@link MatchRule}s for a branch within
     * an interval.
     *
     * @param intervalKey the interval key
     * @param branchKey   the branch key (e.g. {@code "common"},
     *                    {@code "player:yourTeam"}, {@code "mob:hostile"})
     * @return the rule list, or an empty list if not found
     */
    public List<MatchRule> rulesFor(String intervalKey, String branchKey) {
        Map<String, List<MatchRule>> branches = branchesFor(intervalKey);
        return branches.getOrDefault(branchKey, Collections.emptyList());
    }

    /**
     * Returns the total number of intervals (including {@code "common"}).
     */
    public int intervalCount() {
        return intervalMap.size();
    }

    @Override
    public String toString() {
        return "SelectorConfig{intervals=" + intervalMap.keySet() + "}";
    }

    // ── Internal: branch parsing ─────────────────────────────────────

    /**
     * Parses a branch object (the value under an interval key) into a
     * branch-key → rule-list map.
     *
     * <p>Top-level branch keys are {@code "common"}, {@code "player"}, and
     * {@code "mob"}. The {@code "player"} and {@code "mob"} objects contain
     * sub-branches which are flattened into dotted keys
     * (e.g. {@code "player:yourTeam"}).
     */
    private static Map<String, List<MatchRule>> parseBranches(JsonObject branchObj) {
        Map<String, List<MatchRule>> result = new LinkedHashMap<>();

        for (Map.Entry<String, JsonElement> entry : branchObj.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            if ("player".equals(key) || "mob".equals(key)) {
                if (value.isJsonObject()) {
                    JsonObject subObj = value.getAsJsonObject();
                    for (Map.Entry<String, JsonElement> subEntry : subObj.entrySet()) {
                        String subKey = subEntry.getKey();
                        String branchKey = key + ":" + subKey;
                        List<MatchRule> rules = parseSelectorList(subEntry.getValue());
                        if (!rules.isEmpty()) {
                            result.put(branchKey, rules);
                        }
                    }
                }
            } else {
                // Direct branch (e.g. "common")
                List<MatchRule> rules = parseSelectorList(value);
                if (!rules.isEmpty()) {
                    result.put(key, rules);
                }
            }
        }

        return result;
    }

    /**
     * Parses a selector list from either a JSON array or a single JSON object.
     * A single object is automatically wrapped into a one-element list for
     * backwards compatibility with simplified configurations.
     */
    private static List<MatchRule> parseSelectorList(JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            return StreamSupport.stream(arr.spliterator(), false)
                    .filter(JsonElement::isJsonObject)
                    .map(e -> parseMatchRule(e.getAsJsonObject()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        if (element.isJsonObject()) {
            MatchRule rule = parseMatchRule(element.getAsJsonObject());
            return rule != null ? List.of(rule) : Collections.emptyList();
        }
        return Collections.emptyList();
    }

    /**
     * Parses a single selector JSON object into a {@link MatchRule}.
     *
     * <p>The object should have {@code "style"} (required) and {@code "match"}
     * (string or array of strings).
     *
     * @return the parsed rule, or {@code null} if missing required fields
     */
    private static MatchRule parseMatchRule(JsonObject obj) {
        if (!obj.has("style")) {
            return null;
        }
        String style = obj.get("style").getAsString();
        if (style == null || style.isBlank()) {
            return null;
        }

        List<String> matchList;
        if (obj.has("match")) {
            matchList = parseMatchField(obj.get("match"));
        } else {
            matchList = List.of("*"); // default: match everything
        }

        if (matchList.isEmpty()) {
            return null;
        }

        return new MatchRule(matchList, style);
    }

    /**
     * Parses the {@code "match"} field which can be a single string
     * (e.g. {@code "*"}) or an array of strings.
     */
    private static List<String> parseMatchField(JsonElement matchElement) {
        if (matchElement.isJsonPrimitive()) {
            return List.of(matchElement.getAsString());
        }
        if (matchElement.isJsonArray()) {
            JsonArray arr = matchElement.getAsJsonArray();
            List<String> result = new ArrayList<>();
            for (JsonElement e : arr) {
                if (e.isJsonPrimitive()) {
                    result.add(e.getAsString());
                }
            }
            return result.isEmpty() ? List.of("*") : Collections.unmodifiableList(result);
        }
        return List.of("*");
    }

    // ── Defaults ───────────────────────────────────────────────────────

    private static Map<String, List<MatchRule>> defaultBranchMap() {
        Map<String, List<MatchRule>> map = new LinkedHashMap<>();
        map.put("common", List.of(MatchRule.of("*", "default")));
        return Collections.unmodifiableMap(map);
    }
}
