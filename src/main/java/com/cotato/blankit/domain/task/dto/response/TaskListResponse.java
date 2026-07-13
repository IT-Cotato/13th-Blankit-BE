package com.cotato.blankit.domain.task.dto.response;

import com.cotato.blankit.domain.task.entity.enums.TaskPriority;
import com.cotato.blankit.domain.task.entity.enums.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "과업 목록 항목")
public record TaskListResponse(

        @Schema(description = "과업 ID", example = "1")
        Long taskId,

        @Schema(description = "과업명", example = "기말고사 준비")
        String title,

        @Schema(description = "카테고리 ID", example = "1")
        Long categoryId,

        @Schema(description = "카테고리명", example = "학업")
        String categoryName,

        @Schema(description = "카테고리 색상 (HEX)", example = "#FF5C5C")
        String categoryColor,

        @Schema(description = "우선순위 (추천 로직 계산 전 null)", example = "HIGH")
        TaskPriority priority,

        @Schema(description = "중요 표시 여부", example = "true")
        boolean isStarred,

        @Schema(description = "마감일", example = "2026-07-25")
        LocalDate deadline,

        @Schema(description = "남은 예상 시간 (분, 보정 로직 갱신 전 null)", example = "180")
        Integer estimatedTime,

        @Schema(description = "과업 상태", example = "TODO")
        TaskStatus status,

        @Schema(description = "전체 진척도 (0~100, 세부 단계 가중 평균)", example = "40")
        int progressRate,

        @Schema(description = "마지막 피드백 메모 (없으면 null)", example = "2단원까지 정리 완료")
        String lastMemo
) {
}
