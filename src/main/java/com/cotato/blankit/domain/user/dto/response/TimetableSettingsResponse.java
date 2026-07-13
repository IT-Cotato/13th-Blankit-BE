package com.cotato.blankit.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;

@Schema(description = "시간표 표시 범위 응답")
public record TimetableSettingsResponse(

        @Schema(description = "시간표 표시 시작 시간", example = "09:00:00")
        LocalTime timetableStartTime,

        @Schema(description = "시간표 표시 종료 시간", example = "23:00:00")
        LocalTime timetableEndTime
) {
}
