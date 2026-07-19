package com.cotato.blankit.domain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 설정 가능 범위")
public record ReminderRangeResponse(
        @Schema(description = "최소 알림 오프셋(분)", example = "10")
        int minimumMinutes,
        @Schema(description = "최대 알림 오프셋(분)", example = "10080")
        int maximumMinutes
) {
}
