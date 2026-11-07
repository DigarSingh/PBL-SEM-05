package com.shopsmart.search;

import com.shopsmart.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "search_history")
public class SearchHistory extends BaseEntity {

    /** Null for anonymous searches. */
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String query;

    @Column(name = "normalized_query", nullable = false)
    private String normalizedQuery;

    @Enumerated(EnumType.STRING)
    @Column(name = "search_type", nullable = false, length = 20)
    private SearchType searchType;

    @Column(name = "result_count", nullable = false)
    private int resultCount;
}
