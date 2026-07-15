package com.cotato.blankit.domain.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "과업 알림 수정 요청")
public record NotificationSettingRequest(

        @Schema(description = "알림 시점 (마감일 기준 분 전, 최소 10분)", example = "1440")
        @NotNull(message = "알림 시점은 필수입니다.")
        @Min(value = 10, message = "알림 시점은 최소 10분 이상이어야 합니다.")
        Integer notifyBefore,

        @Schema(description = "알림 활성화 여부", example = "true")
        @NotNull(message = "알림 활성화 여부는 필수입니다.")
        Boolean isEnabled
) {
}
