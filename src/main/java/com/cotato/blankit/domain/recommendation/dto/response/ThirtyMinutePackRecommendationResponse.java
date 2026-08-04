package com.cotato.blankit.domain.recommendation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "시간표 공백 동안 진행률을 빠르게 높일 수 있는 30분 Pack 추천")
public record ThirtyMinutePackRecommendationResponse(
        @Schema(description = "사용 가능한 공백 시간(분)", example = "20")
        int availableMinutes,
        @Schema(description = "분당 예상 진행률 내림차순 상위 1~3개 과업")
        List<TaskItem> tasks
) {
    public record TaskItem(
            Long taskId,
            String title,
            String categoryColor,
            String categoryIconKey,
            Integer currentProgressRate,
            Integer remainingEstimatedMinutes,
            @Schema(example = "1.2500") BigDecimal progressPerMinute,
            @Schema(example = "25.00") BigDecimal expectedProgressIncrease
    ) {
    }
}
