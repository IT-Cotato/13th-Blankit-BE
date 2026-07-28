package com.cotato.blankit.domain.recommendation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "우선순위 전체 과업 조회 응답")
public record AllRecommendationResponse(

        @Schema(description = "추천 날짜", example = "2026-07-27")
        LocalDate recommendedDate,

        @Schema(description = "우선순위 순으로 정렬된 전체 과업 목록")
        List<RecommendedTaskItem> tasks
) {
}
