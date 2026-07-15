package com.cotato.blankit.domain.task.dto.request;

import com.cotato.blankit.domain.task.entity.RecurrenceType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "반복 규칙 요청. 반복 안 함은 repeatRule을 전달하지 않거나 clearRepeatRule=true로 처리합니다.")
public record RepeatRuleRequest(
        @Schema(description = "반복 주기", example = "WEEKLY", allowableValues = {"WEEKLY", "MONTHLY", "YEARLY"})
        RecurrenceType frequency,

        @Schema(description = "WEEKLY 요일. 0=일요일, 6=토요일", example = "[1, 3, 5]")
        List<Integer> daysOfWeek,

        @Schema(description = "MONTHLY/YEARLY 날짜. 1~31. 마지막 날은 lastDayOfMonth=true로 전달합니다.", example = "[1, 15, 31]")
        List<Integer> daysOfMonth,

        @Schema(description = "MONTHLY/YEARLY 마지막 날 반복 여부. DB에는 days_of_month의 L로 저장됩니다.", example = "false")
        Boolean lastDayOfMonth,

        @Schema(description = "YEARLY 반복 월. 1~12", example = "5")
        Integer monthOfYear,

        @Schema(description = "반복 시작일", example = "2026-08-12")
        LocalDate startDate,

        @Schema(description = "반복 종료일. 생략하면 종료일 없는 반복입니다.", example = "2026-12-31", nullable = true)
        LocalDate endDate
) {
}
