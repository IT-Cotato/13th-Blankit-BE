package com.cotato.blankit.domain.timetable.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;
import java.util.List;

@Schema(description = "시간표 추가 응답")
public record TimetablesWithDisplayResponse(

        @Schema(description = "추가된 시간표 블록 목록")
        List<TimetableResponse> timetables,

        @Schema(description = "현재 시간표 표시 시작 시간", example = "07:00:00")
        LocalTime displayStartTime,

        @Schema(description = "현재 시간표 표시 종료 시간", example = "23:00:00")
        LocalTime displayEndTime
) {
}
