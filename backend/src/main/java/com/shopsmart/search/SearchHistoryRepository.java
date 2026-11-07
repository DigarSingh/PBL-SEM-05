package com.shopsmart.search;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    @Query("""
            select s.normalizedQuery from SearchHistory s where s.userId = :userId
            group by s.normalizedQuery order by max(s.createdAt) desc
            """)
    List<String> recentQueries(@Param("userId") Long userId, Pageable pageable);

    @Modifying
    @Query("delete from SearchHistory s where s.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
