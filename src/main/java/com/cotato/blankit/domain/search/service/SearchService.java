package com.cotato.blankit.domain.search.service;

import com.cotato.blankit.domain.search.dto.response.SearchResultResponse;
import com.cotato.blankit.domain.search.dto.response.SearchHistoryResponse;
import com.cotato.blankit.domain.search.repository.SearchHistoryRepository;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import com.cotato.blankit.global.util.LikeQueryUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TaskRepository taskRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final SearchHistoryWriter searchHistoryWriter;

    @Transactional(readOnly = true)
    public SearchResultResponse searchTasks(Long userId, String keyword, int page, int size) {
        String normalizedKeyword = LikeQueryUtils.normalizeRequiredKeyword(keyword);
        Page<Task> tasks = taskRepository.searchTaskCandidates(
                userId,
                null,
                null,
                null,
                LikeQueryUtils.escapeLikeKeyword(normalizedKeyword),
                createSearchPageable(page, size)
        );
        trySaveSearchHistory(userId, normalizedKeyword);
        return SearchResultResponse.from(tasks);
    }

    @Transactional(readOnly = true)
    public List<SearchHistoryResponse> getSearchHistories(Long userId, int page, int size) {
        return searchHistoryRepository.findAllByUserIdOrderByUpdatedAtDescSearchHistoryIdDesc(
                        userId,
                        createPageable(page, size)
                ).stream()
                .map(SearchHistoryResponse::from)
                .toList();
    }

    @Transactional
    public void deleteSearchHistory(Long userId, Long searchHistoryId) {
        int deletedCount = searchHistoryRepository.deleteBySearchHistoryIdAndUserId(searchHistoryId, userId);
        if (deletedCount == 0) {
            throw new CustomException(ErrorCode.SEARCH_HISTORY_NOT_FOUND);
        }
    }

    @Transactional
    public void deleteAllSearchHistories(Long userId) {
        searchHistoryRepository.deleteAllByUserId(userId);
    }

    private void trySaveSearchHistory(Long userId, String keyword) {
        try {
            searchHistoryWriter.saveOrRefresh(userId, keyword);
        } catch (RuntimeException ignored) {
            // 최근 검색어 저장 실패가 정상 검색 응답을 막지 않도록 분리한다.
        }
    }

    private Pageable createSearchPageable(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(
                        Sort.Order.asc("deadline"),
                        Sort.Order.asc("createdAt"),
                        Sort.Order.asc("id")
                )
        );
    }

    private Pageable createPageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
