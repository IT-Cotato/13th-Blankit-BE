package com.cotato.blankit.domain.search.controller;

import com.cotato.blankit.domain.search.dto.response.SearchHistoryResponse;
import com.cotato.blankit.global.config.swagger.NotImplementedYet;
import com.cotato.blankit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@NotImplementedYet
@Tag(name = "검색", description = "최근 검색어 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/search/history")
public class SearchHistoryController {

    @Operation(summary = "최근 검색어 목록 조회",
            description = "최근 검색어를 최신 순으로 최대 5개 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping
    public ApiResponse<List<SearchHistoryResponse>> getSearchHistory() {
        return ApiResponse.success(List.of(
                new SearchHistoryResponse(10L, "기말고사", LocalDateTime.of(2026, 7, 13, 18, 30, 0)),
                new SearchHistoryResponse(9L, "영어", LocalDateTime.of(2026, 7, 13, 17, 0, 0)),
                new SearchHistoryResponse(8L, "수학", LocalDateTime.of(2026, 7, 12, 20, 0))
        ));
    }

    @Operation(summary = "최근 검색어 단건 삭제",
            description = "특정 검색어를 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "검색어 없음")
    })
    @DeleteMapping("/{searchHistoryId}")
    public ApiResponse<Void> deleteSearchHistory(@PathVariable Long searchHistoryId) {
        return ApiResponse.success();
    }

    @Operation(summary = "최근 검색어 전체 삭제",
            description = "모든 최근 검색어를 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @DeleteMapping
    public ApiResponse<Void> deleteAllSearchHistory() {
        return ApiResponse.success();
    }
}
