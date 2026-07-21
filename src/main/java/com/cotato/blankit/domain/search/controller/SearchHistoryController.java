package com.cotato.blankit.domain.search.controller;

import com.cotato.blankit.domain.search.dto.response.SearchHistoryResponse;
import com.cotato.blankit.domain.search.service.SearchService;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import com.cotato.blankit.global.response.ApiResponse;
import com.cotato.blankit.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "검색", description = "최근 검색어 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/search-histories")
public class SearchHistoryController {

    private final SearchService searchService;

    @Operation(summary = "최근 검색어 목록 조회",
            description = "현재 로그인한 사용자의 최근 검색어를 최신 검색 순으로 반환합니다. 별도 최대 보관 개수 제한은 없습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping
    public ApiResponse<List<SearchHistoryResponse>> getSearchHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(searchService.getSearchHistories(userDetails.getUserId()));
    }

    @Operation(summary = "최근 검색어 단건 삭제",
            description = "현재 로그인한 사용자의 최근 검색어 중 searchHistoryId에 해당하는 항목을 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "최근 검색어 없음")
    })
    @DeleteMapping("/{searchHistoryId}")
    public ResponseEntity<Void> deleteSearchHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long searchHistoryId
    ) {
        if (userDetails == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        searchService.deleteSearchHistory(userDetails.getUserId(), searchHistoryId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "최근 검색어 전체 삭제",
            description = "현재 로그인한 사용자의 최근 검색어를 모두 삭제합니다. 다른 사용자의 최근 검색어에는 영향을 주지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @DeleteMapping
    public ResponseEntity<Void> deleteAllSearchHistories(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        searchService.deleteAllSearchHistories(userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }
}
