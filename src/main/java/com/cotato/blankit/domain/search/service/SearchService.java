package com.cotato.blankit.domain.search.service;

import com.cotato.blankit.domain.search.dto.response.SearchResultResponse;
import com.cotato.blankit.domain.search.entity.SearchHistory;
import com.cotato.blankit.domain.search.dto.response.SearchHistoryResponse;
import com.cotato.blankit.domain.search.repository.SearchHistoryRepository;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final int MAX_PAGE_SIZE = 100;

    private final TaskRepository taskRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public SearchResultResponse searchTasks(Long userId, String keyword, int page, int size) {
        String normalizedKeyword = normalizeKeyword(keyword);
        Page<Task> tasks = taskRepository.searchTaskCandidates(
                userId,
                null,
                null,
                null,
                escapeLikeKeyword(normalizedKeyword),
                createSearchPageable(page, size)
        );
        saveSearchHistory(userId, normalizedKeyword);
        return SearchResultResponse.from(tasks);
    }

    @Transactional(readOnly = true)
    public List<SearchHistoryResponse> getSearchHistories(Long userId) {
        return searchHistoryRepository.findAllByUserIdOrderByUpdatedAtDescSearchHistoryIdDesc(userId).stream()
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

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        String normalized = keyword.trim();
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private void saveSearchHistory(Long userId, String keyword) {
        searchHistoryRepository.findByUserIdAndKeyword(userId, keyword)
                .ifPresentOrElse(
                        searchHistory -> searchHistoryRepository.refreshSearchedAt(
                                searchHistory.getSearchHistoryId(),
                                LocalDateTime.now()
                        ),
                        () -> createSearchHistory(userId, keyword)
                );
    }

    private void createSearchHistory(Long userId, String keyword) {
        User user = userRepository.getReferenceById(userId);
        try {
            searchHistoryRepository.saveAndFlush(SearchHistory.create(user, keyword));
        } catch (DataIntegrityViolationException e) {
            searchHistoryRepository.findByUserIdAndKeyword(userId, keyword)
                    .ifPresentOrElse(
                            searchHistory -> searchHistoryRepository.refreshSearchedAt(
                                    searchHistory.getSearchHistoryId(),
                                    LocalDateTime.now()
                            ),
                            () -> {
                                throw e;
                            }
                    );
        }
    }

    private String escapeLikeKeyword(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
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
}
