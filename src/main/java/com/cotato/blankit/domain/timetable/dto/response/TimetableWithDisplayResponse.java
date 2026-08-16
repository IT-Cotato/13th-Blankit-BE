package com.cotato.blankit.domain.timetable.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;

@Schema(description = "시간표 수정 응답")
public record TimetableWithDisplayResponse(

        @Schema(description = "수정된 시간표 블록")
        TimetableResponse timetable,

        @Schema(description = "현재 시간표 표시 시작 시간", example = "07:00:00")
        LocalTime displayStartTime,

        @Schema(description = "현재 시간표 표시 종료 시간", example = "23:00:00")
        LocalTime displayEndTime
) {
}
