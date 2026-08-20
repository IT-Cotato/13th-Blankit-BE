package com.cotato.blankit.domain.recommendation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "시간표 공백 동안 진행률을 빠르게 높일 수 있는 30분 Pack 추천")
public record ThirtyMinutePackRecommendationResponse(
        @Schema(description = "사용 가능한 공백 시간(분, 10~30)", example = "20")
        int availableMinutes,
        @Schema(description = "분당 예상 진행률 내림차순 상위 1~3개 과업")
        List<TaskItem> tasks
) {
    public record TaskItem(
            @Schema(description = "과업 ID", example = "1")
            Long taskId,
            @Schema(description = "과업명", example = "전공 기말 시험")
            String title,
            @Schema(description = "카테고리명", example = "학업")
            String categoryName,
            @Schema(description = "카테고리 색상 (HEX)", example = "#7B5EA7")
            String categoryColor,
            @Schema(description = "카테고리 아이콘 식별 키", example = "book")
            String categoryIconKey,
            @Schema(description = "현재 과업 진행률 (%)", example = "40")
            Integer currentProgressRate,
            @Schema(description = "남은 예상 시간(분)", example = "95")
            Integer remainingEstimatedMinutes,
            @Schema(description = "분당 예상 진행률", example = "1.2500") BigDecimal progressPerMinute,
            @Schema(description = "공백 시간 동안 예상 진행률 상승분", example = "25.00") BigDecimal expectedProgressIncrease,
            @Schema(description = "가장 최근 최종 제출한 피드백 메모. 없거나 빈 값이면 null", nullable = true,
                    example = "52p까지 진행")
            String memo
    ) {
    }
}
