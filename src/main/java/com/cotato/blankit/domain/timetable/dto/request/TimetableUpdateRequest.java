package com.cotato.blankit.domain.timetable.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

@Schema(description = "시간표 수정 요청. 변경하지 않을 필드는 null로 보내거나 요청 body에서 생략하세요. 빈 문자열(\"\")은 허용되지 않습니다.")
public record TimetableUpdateRequest(

        @Schema(description = "요일 (0=일~6=토)", example = "2")
        @Min(value = 0, message = "요일은 0(일) 이상이어야 합니다.")
        @Max(value = 6, message = "요일은 6(토) 이하여야 합니다.")
        Integer dayOfWeek,

        @Schema(description = "시작 시간", example = "10:00:00")
        LocalTime startTime,

        @Schema(description = "종료 시간", example = "11:30:00")
        LocalTime endTime,

        @Schema(description = "일정명", example = "자료구조 강의")
        @Size(max = 100, message = "일정명은 100자 이하여야 합니다.")
        @Pattern(regexp = "^(?!\\s+$).+$", message = "일정명은 공백만으로 이루어질 수 없습니다.")
        String title,

        @Schema(description = "장소", example = "공학관 202호")
        @Size(max = 100, message = "장소는 100자 이하여야 합니다.")
        String place,

        @Schema(description = "블록 색상 (HEX)", example = "#5C9EFF")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "색상은 HEX 형식(#RRGGBB)이어야 합니다.")
        String color
) {
}
