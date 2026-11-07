package com.shopsmart.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class Slugs {

    public static final String PATTERN = "^[a-z0-9]+(?:-[a-z0-9]+)*$";
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private Slugs() {
    }

    public static String slugify(String input) {
        String normalized = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFD);
        String ascii = DIACRITICS.matcher(normalized).replaceAll("").toLowerCase(Locale.ROOT);
        String slug = NON_ALNUM.matcher(ascii).replaceAll("-").replaceAll("^-+|-+$", "");
        if (slug.length() > 200) {
            slug = slug.substring(0, 200).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "item" : slug;
    }

    /** Returns {@code base} or the first free {@code base-2}, {@code base-3}, ... */
    public static String unique(String base, Predicate<String> taken) {
        if (!taken.test(base)) {
            return base;
        }
        for (int suffix = 2; suffix < 10_000; suffix++) {
            String candidate = base + "-" + suffix;
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find a free slug for " + base);
    }
}
