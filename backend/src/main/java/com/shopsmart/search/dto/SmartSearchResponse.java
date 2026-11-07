package com.shopsmart.search.dto;

import java.util.List;

/** {@code engine} is "semantic" when the AI ranking was used, "keyword" for the database fallback. */
public record SmartSearchResponse(
        String query,
        SearchInterpretation interpretation,
        String engine,
        List<SearchResultItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
