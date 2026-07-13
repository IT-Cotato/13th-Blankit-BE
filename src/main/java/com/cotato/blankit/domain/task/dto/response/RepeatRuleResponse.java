package com.cotato.blankit.domain.task.dto.response;

import com.cotato.blankit.domain.task.entity.enums.RepeatFrequency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "반복 설정 응답")
public record RepeatRuleResponse(

        @Schema(description = "반복 규칙 ID", example = "1")
        Long repeatRuleId,

        @Schema(description = "반복 주기", example = "WEEKLY")
        RepeatFrequency frequency,

        @Schema(description = "WEEKLY 전용: 반복 요일 (0=일~6=토, 콤마 구분)", example = "1,3,5")
        String daysOfWeek,

        @Schema(description = "MONTHLY·YEARLY 전용: 반복 일자 (콤마 구분, 마지막날=L)", example = "1,15,L")
        String daysOfMonth,

        @Schema(description = "YEARLY 전용: 반복 월 (1~12)", example = "9")
        Integer monthOfYear,

        @Schema(description = "반복 시작일", example = "2026-07-14")
        LocalDate startDate,

        @Schema(description = "반복 종료일 (null = 무기한)", example = "2026-12-31")
        LocalDate endDate
) {
}
