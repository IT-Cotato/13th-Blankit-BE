package com.cotato.blankit.domain.search.service;

import com.cotato.blankit.domain.search.dto.response.SearchResultResponse;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final int MAX_PAGE_SIZE = 100;

    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public SearchResultResponse searchTasks(Long userId, String keyword, int page, int size) {
        Page<Task> tasks = taskRepository.searchTaskCandidates(
                userId,
                null,
                null,
                null,
                normalizeKeyword(keyword),
                createSearchPageable(page, size)
        );
        return SearchResultResponse.from(tasks);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        String normalized = keyword.trim();
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return escapeLikeKeyword(normalized);
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
