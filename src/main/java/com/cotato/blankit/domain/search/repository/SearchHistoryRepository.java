package com.cotato.blankit.domain.search.repository;

import com.cotato.blankit.domain.search.entity.SearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    Optional<SearchHistory> findByUserIdAndKeyword(Long userId, String keyword);

    List<SearchHistory> findAllByUserIdOrderByUpdatedAtDescSearchHistoryIdDesc(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update SearchHistory sh
            set sh.updatedAt = :searchedAt
            where sh.searchHistoryId = :searchHistoryId
            """)
    int refreshSearchedAt(
            @Param("searchHistoryId") Long searchHistoryId,
            @Param("searchedAt") LocalDateTime searchedAt
    );
}
