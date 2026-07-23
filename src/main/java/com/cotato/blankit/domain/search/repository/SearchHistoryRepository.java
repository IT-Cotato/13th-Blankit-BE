package com.cotato.blankit.domain.search.repository;

import com.cotato.blankit.domain.search.entity.SearchHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    Optional<SearchHistory> findByUserIdAndKeyword(Long userId, String keyword);

    Optional<SearchHistory> findBySearchHistoryIdAndUserId(Long searchHistoryId, Long userId);

    List<SearchHistory> findAllByUserIdOrderByUpdatedAtDescSearchHistoryIdDesc(Long userId);

    Page<SearchHistory> findAllByUserIdOrderByUpdatedAtDescSearchHistoryIdDesc(Long userId, Pageable pageable);

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

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update SearchHistory sh
            set sh.updatedAt = :searchedAt
            where sh.user.id = :userId
              and sh.keyword = :keyword
            """)
    int refreshSearchedAtByUserIdAndKeyword(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("searchedAt") LocalDateTime searchedAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from SearchHistory sh
            where sh.searchHistoryId = :searchHistoryId
              and sh.user.id = :userId
            """)
    int deleteBySearchHistoryIdAndUserId(
            @Param("searchHistoryId") Long searchHistoryId,
            @Param("userId") Long userId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from SearchHistory sh
            where sh.user.id = :userId
            """)
    int deleteAllByUserId(@Param("userId") Long userId);
}
