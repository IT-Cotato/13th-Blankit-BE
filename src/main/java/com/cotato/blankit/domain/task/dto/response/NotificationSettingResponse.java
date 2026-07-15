package com.cotato.blankit.domain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "과업 알림 설정 응답")
public record NotificationSettingResponse(

        @Schema(description = "알림 설정 ID", example = "1")
        Long notificationSettingId,

        @Schema(description = "알림 시점 (마감일 기준 분 전, 1440=1일 전)", example = "1440")
        int notifyBefore,

        @Schema(description = "알림 활성화 여부", example = "true")
        boolean isEnabled
) {
}
