package com.shopsmart.search.dto;

import java.util.List;

public record SearchSuggestions(List<Suggestion> products, List<Suggestion> categories, List<Suggestion> brands,
                                List<String> recentSearches) {

    public record Suggestion(Long id, String name, String slug, String imageUrl) {
    }
}
