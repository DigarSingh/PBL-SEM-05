package com.shopsmart.product;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns free text into keyword term groups for the database search: every group must match
 * (AND) and a group matches if any of its alternatives does (OR). Alternatives are a plural-
 * stripped stem plus a few domain synonyms; matching is by substring on the search document,
 * so a stem such as "headphone" also finds "headphones".
 */
public final class KeywordQuery {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "for", "with", "without", "of", "in", "on", "to", "by", "at",
            "i", "me", "my", "we", "want", "need", "looking", "show", "find", "buy", "get", "some", "any",
            "is", "are", "be", "that", "this", "it", "its", "from", "please", "product", "products", "item", "items");

    private static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
            Map.entry("mice", List.of("mouse")),
            Map.entry("mouse", List.of("mice")),
            Map.entry("phone", List.of("smartphone", "mobile")),
            Map.entry("mobile", List.of("phone", "smartphone")),
            Map.entry("smartphone", List.of("phone")),
            Map.entry("headphone", List.of("headset", "earphone")),
            Map.entry("headset", List.of("headphone")),
            Map.entry("earphone", List.of("earbud", "headphone")),
            Map.entry("earbud", List.of("earphone", "tws")),
            Map.entry("laptop", List.of("notebook")),
            Map.entry("notebook", List.of("laptop")),
            Map.entry("watch", List.of("smartwatch")),
            Map.entry("smartwatch", List.of("smart watch", "watch")),
            Map.entry("tv", List.of("television")),
            Map.entry("television", List.of("tv")),
            Map.entry("powerbank", List.of("power bank")),
            Map.entry("charger", List.of("charging", "adapter")),
            Map.entry("cable", List.of("cord")),
            Map.entry("keyboard", List.of("keeb")),
            Map.entry("bluetooth", List.of("wireless")),
            Map.entry("monitor", List.of("display")),
            Map.entry("display", List.of("monitor")));

    private KeywordQuery() {
    }

    /** Normalised tokens without stop words (numbers such as "4k" or "65w" are kept). */
    public static List<String> tokens(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : raw.toLowerCase(Locale.ROOT).split("[^a-z0-9.+-]+")) {
            String token = part.replaceAll("^[.+-]+|[.+-]+$", "");
            if (token.length() < 2 || STOP_WORDS.contains(token)) {
                continue;
            }
            tokens.add(stem(token));
        }
        return List.copyOf(tokens);
    }

    public static List<List<String>> termGroups(String raw) {
        List<List<String>> groups = new ArrayList<>();
        for (String token : tokens(raw)) {
            Set<String> alternatives = new LinkedHashSet<>();
            alternatives.add(token);
            alternatives.addAll(SYNONYMS.getOrDefault(token, List.of()));
            groups.add(List.copyOf(alternatives));
        }
        return groups;
    }

    static String stem(String token) {
        if (token.length() <= 3 || !Character.isLetter(token.charAt(token.length() - 1))) {
            return token;
        }
        if (token.endsWith("ies") && token.length() > 4) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.endsWith("sses") || token.endsWith("shes") || token.endsWith("ches") || token.endsWith("xes")) {
            return token.substring(0, token.length() - 2);
        }
        if (token.endsWith("s") && !token.endsWith("ss") && !token.endsWith("us")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }
}
