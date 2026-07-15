package com.cotato.blankit.domain.timetable.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;

@Schema(description = "시간표 항목 응답")
public record TimetableResponse(

        @Schema(description = "시간표 ID", example = "1")
        Long timetableId,

        @Schema(description = "요일 (0=일, 1=월, 2=화, 3=수, 4=목, 5=금, 6=토)", example = "1")
        int dayOfWeek,

        @Schema(description = "시작 시간", example = "09:00:00")
        LocalTime startTime,

        @Schema(description = "종료 시간", example = "10:30:00")
        LocalTime endTime,

        @Schema(description = "일정명", example = "알고리즘 강의")
        String title,

        @Schema(description = "장소 (null 허용)", example = "공학관 101호")
        String place,

        @Schema(description = "블록 색상 (HEX)", example = "#7B5EA7")
        String color
) {
}
