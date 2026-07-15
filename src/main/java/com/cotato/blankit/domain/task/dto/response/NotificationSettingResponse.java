package com.cotato.blankit.domain.task.dto.response;

import com.cotato.blankit.domain.task.entity.NotificationSetting;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "과업 알림 설정 응답")
public record NotificationSettingResponse(
        @Schema(description = "마감일 기준 몇 분 전 알림인지", example = "1440")
        Integer notifyBefore,
        @Schema(description = "알림 활성화 여부", example = "true")
        boolean enabled
) {

    public static NotificationSettingResponse from(NotificationSetting setting) {
        return new NotificationSettingResponse(setting.getNotifyBefore(), setting.isEnabled());
    }
}
