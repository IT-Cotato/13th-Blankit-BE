package com.cotato.blankit.domain.timetable.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalTime;

@Schema(description = "시간표 추가 요청")
public record TimetableCreateRequest(

        @Schema(description = "요일 (0=일, 1=월, 2=화, 3=수, 4=목, 5=금, 6=토)", example = "1")
        @NotNull(message = "요일은 필수입니다.")
        @Min(value = 0, message = "요일은 0(일) 이상이어야 합니다.")
        @Max(value = 6, message = "요일은 6(토) 이하여야 합니다.")
        Integer dayOfWeek,

        @Schema(description = "시작 시간", example = "09:00:00")
        @NotNull(message = "시작 시간은 필수입니다.")
        LocalTime startTime,

        @Schema(description = "종료 시간", example = "10:30:00")
        @NotNull(message = "종료 시간은 필수입니다.")
        LocalTime endTime,

        @Schema(description = "일정명", example = "알고리즘 강의")
        @NotBlank(message = "일정명은 필수입니다.")
        @Size(max = 100, message = "일정명은 100자 이하여야 합니다.")
        String title,

        @Schema(description = "장소 (선택)", example = "공학관 101호")
        @Size(max = 100, message = "장소는 100자 이하여야 합니다.")
        String place,

        @Schema(description = "블록 색상 (HEX)", example = "#7B5EA7")
        @NotBlank(message = "색상은 필수입니다.")
        @Size(max = 20, message = "색상은 20자 이하여야 합니다.")
        String color
) {
}
