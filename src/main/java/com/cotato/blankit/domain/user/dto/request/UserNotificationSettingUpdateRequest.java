package com.cotato.blankit.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "사용자 알림 설정 수정 요청 (명세 4.8)")
public record UserNotificationSettingUpdateRequest(

        @Schema(description = "서비스 알림 활성화 여부. 기기 알림 권한을 허용한 경우에만 프론트엔드에서 true로 요청합니다.", example = "true")
        @NotNull(message = "서비스 알림 설정 값은 필수입니다.")
        Boolean isServiceAlarmEnabled,

        @Schema(description = "30분 Pack 알림 활성화 여부. 기기 알림 권한을 허용한 경우에만 프론트엔드에서 true로 요청합니다.", example = "false")
        @NotNull(message = "30분 Pack 알림 설정 값은 필수입니다.")
        Boolean is30minPackAlarmEnabled
) {
}
