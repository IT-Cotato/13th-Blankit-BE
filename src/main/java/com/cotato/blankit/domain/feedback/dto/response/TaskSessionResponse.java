package com.cotato.blankit.domain.feedback.dto.response;

import com.cotato.blankit.domain.feedback.entity.TaskSession;
import com.cotato.blankit.domain.feedback.entity.enums.TaskSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "세션 응답")
public record TaskSessionResponse(

        @Schema(description = "세션 ID", example = "1")
        Long taskSessionId,

        @Schema(description = "과업 ID", example = "1")
        Long taskId,

        @Schema(description = "세션 시작 시각", example = "2026-07-13T14:00:00")
        LocalDateTime startedAt,

        @Schema(description = "세션 종료 시각 (진행 중이면 null)", example = "2026-07-13T15:30:00")
        LocalDateTime endedAt,

        @Schema(description = "누적 소요 시간 (초)", example = "0")
        int elapsedTime,

        @Schema(description = "세션 상태 (최초 진입 시 PAUSED)", example = "PAUSED")
        TaskSessionStatus status
) {
    public static TaskSessionResponse from(TaskSession session) {
        return new TaskSessionResponse(
                session.getTaskSessionId(),
                session.getTask().getId(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getElapsedTime(),
                session.getStatus()
        );
    }
}
