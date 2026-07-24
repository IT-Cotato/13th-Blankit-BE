package com.cotato.blankit.domain.recommendation.dto.response;

import com.cotato.blankit.domain.task.entity.TaskPriority;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "오늘의 추천 응답 (홈 화면 권장 시간·우선순위 과업 3개)")
public record TodayRecommendationResponse(

        @Schema(description = "추천 날짜", example = "2026-07-13")
        LocalDate recommendedDate,

        @Schema(description = "오늘의 권장 시간 (분, logic-spec 5번 공식)", example = "120")
        int totalRecommendedMinutes,

        @Schema(description = "우선순위 추천 과업 (최대 3개, logic-spec 1번 점수 기준)")
        List<RecommendedTaskItem> topTasks
) {

    @Schema(description = "추천 과업 항목")
    public record RecommendedTaskItem(

            @Schema(description = "과업 ID", example = "1")
            Long taskId,

            @Schema(description = "과업명", example = "기말고사 준비")
            String title,

            @Schema(description = "우선순위", example = "HIGH")
            TaskPriority priority,

            @Schema(description = "카테고리 색상 (HEX)", example = "#FF5C5C")
            String categoryColor,

            @Schema(description = "카테고리 아이콘 식별 키", example = "book")
            String categoryIconKey,

            @Schema(description = "추천 순위", example = "1")
            int rankOrder,

            @Schema(description = "우선순위 점수 (기본순위×0.8 + 진행부족순위×0.2, 낮을수록 우선)", example = "1.60")
            BigDecimal score,

            @Schema(description = "오늘 권장 학습 시간 (분)", example = "90")
            Integer recommendedMinutes
    ) {
    }
}
