package com.cotato.blankit.domain.recommendation.dto.response;

import com.cotato.blankit.domain.task.entity.TaskPriority;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "과업 조합 추천 모드 목록 응답 (logic-spec 4번)")
public record RecommendationModesResponse(

        @Schema(description = "추천 모드 목록")
        List<RecommendationModeItem> modes
) {

    @Schema(description = "추천 모드 항목")
    public record RecommendationModeItem(

            @Schema(description = "모드 코드", example = "FIRE")
            String mode,

            @Schema(description = "모드 표시명", example = "불끄기")
            String modeName,

            @Schema(description = "모드 설명", example = "오늘 최소 시간을 빨간색(상) 과업에 올인하는 조합")
            String description,

            @Schema(description = "해당 모드의 추천 과업 목록")
            List<ModeTaskItem> tasks
    ) {
    }

    @Schema(description = "모드 내 과업 항목")
    public record ModeTaskItem(

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

            @Schema(description = "이 모드에서 권장하는 학습 시간 (분)", example = "120")
            Integer recommendedMinutes
    ) {
    }
}
