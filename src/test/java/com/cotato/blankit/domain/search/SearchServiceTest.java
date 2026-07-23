package com.cotato.blankit.domain.search;

import com.cotato.blankit.domain.search.repository.SearchHistoryRepository;
import com.cotato.blankit.domain.search.service.SearchHistoryWriter;
import com.cotato.blankit.domain.search.service.SearchService;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchServiceTest {

    @Test
    void searchTasksReturnsResultEvenWhenSearchHistorySaveFails() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        SearchHistoryRepository searchHistoryRepository = mock(SearchHistoryRepository.class);
        SearchHistoryWriter searchHistoryWriter = mock(SearchHistoryWriter.class);
        SearchService searchService = new SearchService(taskRepository, searchHistoryRepository, searchHistoryWriter);

        when(taskRepository.searchTaskCandidates(
                eq(1L),
                eq((LocalDate) null),
                eq(null),
                eq(null),
                eq("수학"),
                any(Pageable.class)
        )).thenReturn(Page.empty());
        doThrow(new RuntimeException("history write failed"))
                .when(searchHistoryWriter).saveOrRefresh(1L, "수학");

        var response = searchService.searchTasks(1L, " 수학 ", 0, 20);

        assertThat(response.totalCount()).isZero();
        assertThat(response.tasks()).isEmpty();
        verify(searchHistoryWriter).saveOrRefresh(1L, "수학");
    }
}
