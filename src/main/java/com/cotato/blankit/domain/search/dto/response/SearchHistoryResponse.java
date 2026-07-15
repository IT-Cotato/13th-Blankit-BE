package com.cotato.blankit.domain.search.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "최근 검색어 항목")
public record SearchHistoryResponse(

        @Schema(description = "검색 기록 ID", example = "10")
        Long searchHistoryId,

        @Schema(description = "검색어", example = "기말고사")
        String keyword,

        @Schema(description = "검색 시각 (= createdAt)", example = "2026-07-13T18:30:00")
        LocalDateTime searchedAt
) {
}
