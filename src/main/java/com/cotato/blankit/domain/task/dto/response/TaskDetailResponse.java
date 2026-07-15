package com.cotato.blankit.domain.task.dto.response;

import com.cotato.blankit.domain.task.entity.NotificationSetting;
import com.cotato.blankit.domain.task.entity.RepeatRule;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.entity.TaskPriority;
import com.cotato.blankit.domain.task.entity.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "과업 상세 응답")
public record TaskDetailResponse(
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
        @Schema(description = "예상 남은 시간(분). 계산 전에는 null입니다.", nullable = true)
        Integer estimatedTime,
        @Schema(description = "상태", example = "TODO")
        TaskStatus status,
        @Schema(description = "마감일", example = "2026-08-12")
        LocalDate deadline,
        @Schema(description = "알림 설정")
        NotificationSettingResponse notificationSetting,
        @Schema(description = "반복 규칙. 반복하지 않으면 null입니다.", nullable = true)
        RepeatRuleResponse repeatRule,
        @Schema(description = "생성일")
        LocalDateTime createdAt,
        @Schema(description = "수정일")
        LocalDateTime updatedAt,
        @Schema(description = "비슷한 이전 과업 ID", example = "12", nullable = true)
        Long similarTaskId,
        @Schema(description = "비슷한 이전 과업명", example = "자료구조 과제 제출", nullable = true)
        String similarTaskTitle,
        @Schema(description = "반복으로 생성된 과업의 원본 과업 ID. 원본 과업이면 null입니다.", example = "1", nullable = true)
        Long sourceTaskId,
        @Schema(description = "반복으로 생성된 과업의 원본 과업명. 원본 과업이면 null입니다.", example = "주간 회의", nullable = true)
        String sourceTaskTitle,
        @Schema(description = "총 수행 시간(초). task_session.elapsed_time 합계입니다.", example = "5400")
        Long totalElapsedTime
) {

    public static TaskDetailResponse from(
            Task task,
            NotificationSetting notificationSetting,
            RepeatRule repeatRule,
            long totalElapsedTime
    ) {
        Task similarTask = task.getSimilarTask();
        Task sourceTask = task.getSourceTask();
        return new TaskDetailResponse(
                task.getId(),
                task.getTitle(),
                CategoryResponse.from(task.getCategory()),
                task.getPriority(),
                task.isStarred(),
                task.getEstimatedTime(),
                task.getStatus(),
                task.getDeadline(),
                NotificationSettingResponse.from(notificationSetting),
                repeatRule == null ? null : RepeatRuleResponse.from(repeatRule),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                similarTask == null ? null : similarTask.getId(),
                similarTask == null ? null : similarTask.getTitle(),
                sourceTask == null ? null : sourceTask.getId(),
                sourceTask == null ? null : sourceTask.getTitle(),
                totalElapsedTime
        );
    }
}
