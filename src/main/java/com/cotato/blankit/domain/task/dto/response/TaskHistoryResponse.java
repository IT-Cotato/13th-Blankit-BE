package com.cotato.blankit.domain.task.dto.response;

import com.cotato.blankit.domain.task.entity.Task;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "비슷한 이전 과업 검색 응답")
public record TaskHistoryResponse(
        @Schema(description = "과업 ID", example = "1")
        Long taskId,
        @Schema(description = "과업명", example = "알고리즘 과제 제출")
        String title,
        @Schema(description = "카테고리 ID", example = "1")
        Long categoryId,
        @Schema(description = "카테고리명", example = "학교")
        String categoryName,
        @Schema(description = "카테고리 색상값", example = "#FFB85C")
        String categoryColor,
        @Schema(description = "카테고리 아이콘 식별 키", example = "book")
        String categoryIconKey,
        @Schema(description = "과업 카드 완료 일자. 실제 완료 시각이 아니라 task.deadline입니다.", example = "2026-08-12")
        LocalDate deadline,
        @Schema(description = "총 수행 시간(초). task_session.elapsed_time 합계입니다.", example = "5400")
        Long totalElapsedTime
) {

    public static TaskHistoryResponse from(Task task, long totalElapsedTime) {
        return new TaskHistoryResponse(
                task.getId(),
                task.getTitle(),
                task.getCategory().getId(),
                task.getCategory().getName(),
                task.getCategory().getColor(),
                task.getCategory().getIconKey(),
                task.getDeadline(),
                totalElapsedTime
        );
    }
}
