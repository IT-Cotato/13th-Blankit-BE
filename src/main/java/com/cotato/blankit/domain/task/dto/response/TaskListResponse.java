package com.cotato.blankit.domain.task.dto.response;

import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.entity.TaskPriority;
import com.cotato.blankit.domain.task.entity.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "과업 목록 응답")
public record TaskListResponse(
        @Schema(description = "과업 ID", example = "1")
        Long taskId,
        @Schema(description = "과업명", example = "알고리즘 과제 제출")
        String title,
        @Schema(description = "카테고리")
        CategoryResponse category,
        @Schema(description = "추천 우선순위. 추천 계산 전에는 null입니다.", nullable = true)
        TaskPriority priority,
        @Schema(description = "중요 표시", example = "false")
        boolean starred,
        @Schema(description = "예상 남은 시간(분)", nullable = true)
        Integer estimatedTime,
        @Schema(description = "상태", example = "TODO")
        TaskStatus status,
        @Schema(description = "마감일", example = "2026-08-12")
        LocalDate deadline,
        @Schema(description = "비슷한 과업 연결 여부", example = "true")
        boolean hasSimilarTask,
        @Schema(description = "비슷한 이전 과업 ID", example = "12", nullable = true)
        Long similarTaskId,
        @Schema(description = "반복 생성 원본 과업 ID. 원본 과업이면 null입니다.", example = "1", nullable = true)
        Long sourceTaskId,
        @Schema(description = "생성일")
        LocalDateTime createdAt,
        @Schema(description = "수정일")
        LocalDateTime updatedAt
) {

    public static TaskListResponse from(Task task) {
        Long similarTaskId = task.getSimilarTask() == null ? null : task.getSimilarTask().getId();
        Long sourceTaskId = task.getSourceTask() == null ? null : task.getSourceTask().getId();
        return new TaskListResponse(
                task.getId(),
                task.getTitle(),
                CategoryResponse.from(task.getCategory()),
                task.getPriority(),
                task.isStarred(),
                task.getEstimatedTime(),
                task.getStatus(),
                task.getDeadline(),
                similarTaskId != null,
                similarTaskId,
                sourceTaskId,
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
