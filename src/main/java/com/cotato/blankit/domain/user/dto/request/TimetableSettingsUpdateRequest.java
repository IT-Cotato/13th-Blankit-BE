package com.cotato.blankit.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

@Schema(description = "시간표 표시 범위 수정 요청 (명세 4.6.1)")
public record TimetableSettingsUpdateRequest(

        @Schema(description = "시작 시간 (기본 08:00)", example = "09:00:00")
        @NotNull(message = "시작 시간은 필수입니다.")
        LocalTime startTime,

        @Schema(description = "종료 시간 (기본 24:00)", example = "23:00:00")
        @NotNull(message = "종료 시간은 필수입니다.")
        LocalTime endTime
) {
}
