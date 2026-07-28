package com.cotato.blankit.domain.recommendation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "오늘의 추천 응답 (홈 화면 권장 시간·우선순위 과업 3개)")
public record TodayRecommendationResponse(

        @Schema(description = "추천 날짜", example = "2026-07-13")
        LocalDate recommendedDate,

        @Schema(description = "오늘의 권장 시간 (분, logic-spec 5번 공식)", example = "120")
        long totalRecommendedMinutes,

        @Schema(description = "우선순위 추천 과업 (최대 3개, logic-spec 1번 점수 기준)")
        List<RecommendedTaskItem> topTasks
) {
}
