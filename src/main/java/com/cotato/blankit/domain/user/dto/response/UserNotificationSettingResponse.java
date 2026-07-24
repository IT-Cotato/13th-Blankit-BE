package com.cotato.blankit.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 알림 설정 응답 (명세 4.8, 최초 모두 OFF)")
public record UserNotificationSettingResponse(

        @Schema(description = "서비스 알림 활성화 여부 (최초 OFF)", example = "false")
        boolean isServiceAlarmEnabled,

        @Schema(description = "30분 Pack 알림 활성화 여부 (최초 OFF)", example = "false")
        boolean is30minPackAlarmEnabled
) {
}
