package com.cotato.blankit.domain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "일별 통계 응답 (캘린더 통계 화면 3.4~3.6)")
public record TaskDailyStatsResponse(

        @Schema(description = "조회 날짜", example = "2026-07-13")
        LocalDate date,

        @Schema(description = "당일 실제 총 소요 시간 (초, 미래 날짜면 null)", example = "5400")
        Integer totalElapsedSeconds,

        @Schema(description = "당일 권장 시간 (분, logic-spec 5번 공식 기준)", example = "120")
        int totalRecommendedMinutes,

        @Schema(description = "피드백 완료 과업 목록 (draft 제외)")
        List<FeedbackTaskItem> feedbackTasks
) {

    @Schema(description = "피드백 완료 과업 항목")
    public record FeedbackTaskItem(

            @Schema(description = "과업 ID", example = "1")
            Long taskId,

            @Schema(description = "과업명", example = "기말고사 준비")
            String title,

            @Schema(description = "카테고리명", example = "학업")
            String categoryName,

            @Schema(description = "카테고리 색상 (HEX)", example = "#FF5C5C")
            String categoryColor,

            @Schema(description = "피드백 시점의 진척도 (0~100)", example = "40")
            int progressRate,

            @Schema(description = "100% 완료 여부", example = "false")
            boolean isCompleted
    ) {
    }
}
