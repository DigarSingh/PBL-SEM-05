package com.shopsmart.search;

import com.shopsmart.brand.Brand;
import com.shopsmart.category.Category;
import com.shopsmart.product.KeywordQuery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, dependency-free query understanding (the cheap first stage of hybrid search):
 * prices, ratings, stock, brand and category are extracted with rules; features and use cases
 * come from small lexicons. No model is called here.
 */
public final class SearchQueryParser {

    private static final String AMOUNT = "(?:₹|rs\\.?|inr)?\\s*(\\d{1,3}(?:,\\d{2,3})+|\\d+(?:\\.\\d+)?)\\s*(k|thousand|lakh|lac)?\\s*(?:rs\\.?|rupees|inr|/-)?";
    private static final Pattern BETWEEN = Pattern.compile("(?:between|from)\\s+" + AMOUNT + "\\s*(?:and|to|-)\\s*" + AMOUNT);
    private static final Pattern RANGE = Pattern.compile("\\b" + AMOUNT + "\\s*(?:-|to)\\s*" + AMOUNT);
    private static final Pattern MAX = Pattern.compile("(?:under|below|less than|lesser than|within|up ?to|max(?:imum)?|budget(?: of)?|cheaper than|<)\\s*" + AMOUNT);
    private static final Pattern MIN = Pattern.compile("(?:above|over|more than|greater than|min(?:imum)?|at least|>)\\s*" + AMOUNT);
    private static final Pattern AROUND = Pattern.compile("(?:around|about|approx(?:imately)?|near)\\s*" + AMOUNT);
    private static final Pattern RATING = Pattern.compile("(\\d(?:\\.\\d)?)\\s*(?:\\+|and above|or more)?\\s*(?:star|stars|rating|rated)|rated\\s*(?:above|over)?\\s*(\\d(?:\\.\\d)?)");
    private static final Pattern IN_STOCK = Pattern.compile("\\b(in stock|available now|available)\\b");

    private static final Map<String, List<String>> FEATURES = features();
    private static final Map<String, List<String>> USE_CASES = useCases();
    /** Generic words that describe a product type; mapped to terms likely to appear in category names. */
    private static final Map<String, List<String>> CATEGORY_HINTS = Map.ofEntries(
            Map.entry("headphone", List.of("audio", "headphone")), Map.entry("headset", List.of("audio", "headphone")),
            Map.entry("earphone", List.of("audio")), Map.entry("earbud", List.of("audio")),
            Map.entry("speaker", List.of("audio", "speaker")), Map.entry("soundbar", List.of("audio")),
            Map.entry("phone", List.of("smartphone")), Map.entry("mobile", List.of("smartphone")),
            Map.entry("smartphone", List.of("smartphone")), Map.entry("laptop", List.of("laptop")),
            Map.entry("notebook", List.of("laptop")), Map.entry("monitor", List.of("monitor")),
            Map.entry("display", List.of("monitor")), Map.entry("watch", List.of("wearable", "watch")),
            Map.entry("smartwatch", List.of("wearable", "watch")), Map.entry("band", List.of("wearable")),
            Map.entry("keyboard", List.of("keyboard")), Map.entry("mouse", List.of("mice", "mouse")),
            Map.entry("mice", List.of("mice", "mouse")), Map.entry("charger", List.of("power", "charging")),
            // Charging intent competes with the device noun ("keep my phone charged"), so such queries
            // imply no single category and are ranked purely on meaning.
            Map.entry("charged", List.of("power", "charging")), Map.entry("charging", List.of("power", "charging")),
            Map.entry("powerbank", List.of("power")), Map.entry("cable", List.of("accessor")),
            Map.entry("tablet", List.of("tablet")), Map.entry("camera", List.of("camera")));

    private SearchQueryParser() {
    }

    public static ParsedQuery parse(String raw, List<Category> categories, List<Brand> brands) {
        String original = raw == null ? "" : raw.trim();
        String text = " " + original.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") + " ";

        BigDecimal min = null;
        BigDecimal max = null;
        Matcher matcher;
        if ((matcher = BETWEEN.matcher(text)).find() || (matcher = RANGE.matcher(text)).find()) {
            BigDecimal first = amount(matcher.group(1), matcher.group(2));
            BigDecimal second = amount(matcher.group(3), matcher.group(4));
            if (first != null && second != null && looksLikePrice(first) && looksLikePrice(second)) {
                min = first.min(second);
                max = first.max(second);
                text = remove(text, matcher);
            }
        }
        if (max == null && (matcher = MAX.matcher(text)).find()) {
            max = amount(matcher.group(1), matcher.group(2));
            text = remove(text, matcher);
        }
        if (min == null && (matcher = MIN.matcher(text)).find()) {
            BigDecimal value = amount(matcher.group(1), matcher.group(2));
            if (value != null && looksLikePrice(value)) {
                min = value;
                text = remove(text, matcher);
            }
        }
        if (min == null && max == null && (matcher = AROUND.matcher(text)).find()) {
            BigDecimal value = amount(matcher.group(1), matcher.group(2));
            if (value != null) {
                min = value.multiply(new BigDecimal("0.8"));
                max = value.multiply(new BigDecimal("1.2"));
                text = remove(text, matcher);
            }
        }

        BigDecimal minRating = null;
        if ((matcher = RATING.matcher(text)).find()) {
            String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            BigDecimal rating = new BigDecimal(value);
            if (rating.compareTo(BigDecimal.ONE) >= 0 && rating.compareTo(BigDecimal.valueOf(5)) <= 0) {
                minRating = rating;
                text = remove(text, matcher);
            }
        }
        boolean inStock = false;
        if ((matcher = IN_STOCK.matcher(text)).find()) {
            inStock = true;
            text = remove(text, matcher);
        }

        List<String> tokens = KeywordQuery.tokens(text);
        List<String> features = matchLexicon(text, tokens, FEATURES);
        List<String> useCases = matchLexicon(text, tokens, USE_CASES);

        Brand brand = null;
        for (Brand candidate : brands) {
            String name = candidate.getName().toLowerCase(Locale.ROOT);
            if (Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(text).find()) {
                brand = candidate;
                break;
            }
        }

        CategoryMatch category = matchCategory(tokens, categories);
        String semantic = text.replaceAll("\\s+", " ").trim();
        return new ParsedQuery(original, semantic.isEmpty() ? original : semantic,
                category == null ? null : category.category().getId(),
                category == null ? null : category.category().getName(),
                category == null ? null : category.category().getSlug(),
                category != null && category.strong(),
                brand == null ? null : brand.getSlug(), brand == null ? null : brand.getName(),
                min, max, minRating, inStock, features, useCases);
    }

    /**
     * Picks the category whose name best matches the query. A match on the category name is
     * "strong" (used as a filter); matches only via hints/description are used for ranking.
     */
    private static CategoryMatch matchCategory(List<String> tokens, List<Category> categories) {
        Map<Category, Integer> scores = new LinkedHashMap<>();
        Map<Category, Boolean> strong = new HashMap<>();
        for (Category category : categories) {
            Set<String> nameTokens = new LinkedHashSet<>(KeywordQuery.tokens(category.getName() + " " + category.getSlug()));
            String description = category.getDescription() == null ? "" : category.getDescription().toLowerCase(Locale.ROOT);
            int score = 0;
            boolean nameHit = false;
            for (String token : tokens) {
                if (nameTokens.contains(token)) {
                    score += 3;
                    nameHit = true;
                } else if (CATEGORY_HINTS.getOrDefault(token, List.of()).stream()
                        .anyMatch(hint -> nameTokens.stream().anyMatch(name -> name.startsWith(hint)))) {
                    // Hint matches ("phone" -> Smartphones) guide ranking only: "keep my phone charged"
                    // is about power banks, so they must not become a hard filter.
                    score += 2;
                } else if (token.length() > 3 && description.contains(token)) {
                    score += 1;
                }
            }
            if (score > 0) {
                scores.put(category, score);
                strong.put(category, nameHit);
            }
        }
        if (scores.isEmpty()) {
            return null;
        }
        int best = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Category> winners = scores.entrySet().stream().filter(e -> e.getValue() == best).map(Map.Entry::getKey).toList();
        if (winners.size() != 1) {
            return null;
        }
        Category winner = winners.getFirst();
        return new CategoryMatch(winner, strong.get(winner));
    }

    private static List<String> matchLexicon(String text, List<String> tokens, Map<String, List<String>> lexicon) {
        List<String> matches = new ArrayList<>();
        lexicon.forEach((canonical, variants) -> {
            for (String variant : variants) {
                boolean hit = variant.contains(" ") ? text.contains(" " + variant + " ") : tokens.contains(variant);
                if (hit) {
                    matches.add(canonical);
                    return;
                }
            }
        });
        return matches;
    }

    static BigDecimal amount(String digits, String unit) {
        if (digits == null) {
            return null;
        }
        BigDecimal value = new BigDecimal(digits.replace(",", ""));
        if (unit != null) {
            switch (unit) {
                case "k", "thousand" -> value = value.multiply(BigDecimal.valueOf(1_000));
                case "lakh", "lac" -> value = value.multiply(BigDecimal.valueOf(100_000));
                default -> {
                }
            }
        }
        return value;
    }

    /** Filters numbers that are clearly not prices (e.g. "5 star", "2 pack"). */
    private static boolean looksLikePrice(BigDecimal value) {
        return value.compareTo(BigDecimal.valueOf(50)) >= 0;
    }

    private static String remove(String text, Matcher matcher) {
        return (text.substring(0, matcher.start()) + " " + text.substring(matcher.end())).replaceAll("\\s+", " ");
    }

    private static Map<String, List<String>> features() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("wireless", List.of("wireless", "bluetooth", "cordless", "tws"));
        map.put("noise cancelling", List.of("noise cancelling", "noise canceling", "anc", "noise-cancelling"));
        map.put("rgb", List.of("rgb", "backlit"));
        map.put("mechanical", List.of("mechanical", "hot-swappable"));
        map.put("4k", List.of("4k", "uhd", "2160p"));
        map.put("high refresh rate", List.of("144hz", "165hz", "240hz", "high refresh"));
        map.put("fast charging", List.of("fast charging", "fast charge", "quick charge", "65w", "100w", "gan"));
        map.put("usb-c", List.of("usb-c", "type-c", "type c"));
        map.put("waterproof", List.of("waterproof", "water resistant", "ip68", "ip67"));
        map.put("amoled", List.of("amoled", "oled"));
        map.put("5g", List.of("5g"));
        map.put("lightweight", List.of("lightweight", "light weight", "portable", "compact"));
        map.put("long battery", List.of("long battery", "battery life", "all-day battery"));
        return map;
    }

    private static Map<String, List<String>> useCases() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("gaming", List.of("gaming", "gamer", "esports", "fps"));
        map.put("office", List.of("office", "work", "productivity", "business"));
        map.put("study", List.of("student", "study", "college", "school"));
        map.put("travel", List.of("travel", "commute", "trip"));
        map.put("fitness", List.of("fitness", "running", "gym", "workout", "sport"));
        map.put("photography", List.of("photography", "camera", "vlogging"));
        map.put("music", List.of("music", "audiophile", "bass"));
        map.put("creator", List.of("editing", "creator", "design", "streaming"));
        return map;
    }

    private record CategoryMatch(Category category, boolean strong) {
    }
}
