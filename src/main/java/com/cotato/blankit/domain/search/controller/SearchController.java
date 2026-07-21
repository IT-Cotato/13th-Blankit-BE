package com.cotato.blankit.domain.search.controller;

import com.cotato.blankit.domain.search.dto.response.SearchResultResponse;
import com.cotato.blankit.domain.search.service.SearchService;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import com.cotato.blankit.global.response.ApiResponse;
import com.cotato.blankit.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "검색", description = "과업 검색 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "과업 검색",
            description = "키워드로 과업명을 검색합니다. " +
                    "응답의 categoryId를 사용해 프론트에서 카테고리별 그룹핑이 가능합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "검색 성공 (결과 없으면 totalCount=0, tasks=[])"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "키워드 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping
    public ApiResponse<SearchResultResponse> search(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "검색어", example = "수학", required = true)
            @RequestParam(required = false) String keyword,
            @Parameter(description = "페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        if (userDetails == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(searchService.searchTasks(userDetails.getUserId(), keyword, page, size));
    }
}
