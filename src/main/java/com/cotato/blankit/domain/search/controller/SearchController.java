package com.cotato.blankit.domain.search.controller;

import com.cotato.blankit.domain.search.dto.response.SearchHistoryResponse;
import com.cotato.blankit.domain.search.dto.response.SearchResultResponse;
import com.cotato.blankit.domain.task.entity.TaskPriority;
import com.cotato.blankit.domain.task.entity.TaskStatus;
import com.cotato.blankit.global.config.swagger.NotImplementedYet;
import com.cotato.blankit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@NotImplementedYet
@Tag(name = "검색", description = "과업 검색 및 최근 검색어 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    @Operation(summary = "과업 검색",
            description = "키워드로 과업명을 검색합니다. " +
                    "검색 결과는 검색어 저장 후(최대 5개 표시는 앱에서 처리) 반환합니다. " +
                    "응답의 categoryId를 사용해 프론트에서 카테고리별 그룹핑이 가능합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "검색 성공 (결과 없으면 totalCount=0, tasks=[])"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "키워드 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping
    public ApiResponse<SearchResultResponse> search(
            @Parameter(description = "검색어", example = "수학", required = true)
            @RequestParam String keyword,
            @PageableDefault(size = 20, sort = "deadline", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.success(new SearchResultResponse(
                2,
                List.of(
                        new SearchResultResponse.SearchTaskItem(
                                1L, "수학 기말고사 준비", 1L, "학업", "#FF5C5C",
                                TaskPriority.HIGH, LocalDate.of(2026, 7, 20),
                                TaskStatus.IN_PROGRESS, 40),
                        new SearchResultResponse.SearchTaskItem(
                                9L, "수학 문제집 풀기", 1L, "학업", "#FF5C5C",
                                TaskPriority.MEDIUM, LocalDate.of(2026, 7, 28),
                                TaskStatus.TODO, 0)
                )
        ));
    }

    @Operation(summary = "최근 검색어 목록 조회",
            description = "최근 검색어를 최신 순으로 최대 5개 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/history")
    public ApiResponse<List<SearchHistoryResponse>> getSearchHistory() {
        return ApiResponse.success(List.of(
                new SearchHistoryResponse(10L, "기말고사", LocalDateTime.of(2026, 7, 13, 18, 30, 0)),
                new SearchHistoryResponse(9L, "영어", LocalDateTime.of(2026, 7, 13, 17, 0, 0)),
                new SearchHistoryResponse(8L, "수학", LocalDateTime.of(2026, 7, 12, 20, 0, 0))
        ));
    }

    @Operation(summary = "최근 검색어 단건 삭제",
            description = "특정 검색어를 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "검색어 없음")
    })
    @DeleteMapping("/history/{searchHistoryId}")
    public ApiResponse<Void> deleteSearchHistory(@PathVariable Long searchHistoryId) {
        return ApiResponse.success();
    }

    @Operation(summary = "최근 검색어 전체 삭제",
            description = "모든 최근 검색어를 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @DeleteMapping("/history")
    public ApiResponse<Void> deleteAllSearchHistory() {
        return ApiResponse.success();
    }
}
