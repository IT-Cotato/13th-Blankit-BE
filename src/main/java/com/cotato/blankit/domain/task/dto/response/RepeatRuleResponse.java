package com.cotato.blankit.domain.task.dto.response;

import com.cotato.blankit.domain.task.entity.RecurrenceType;
import com.cotato.blankit.domain.task.entity.RepeatRule;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "반복 규칙 응답. 반복하지 않는 과업은 null입니다.")
public record RepeatRuleResponse(
        @Schema(description = "반복 주기", example = "WEEKLY")
        RecurrenceType frequency,
        @Schema(description = "WEEKLY 요일. 0=일요일, 6=토요일")
        List<Integer> daysOfWeek,
        @Schema(description = "MONTHLY/YEARLY 날짜")
        List<Integer> daysOfMonth,
        @Schema(description = "MONTHLY 마지막 날 반복 여부. DB에는 L로 저장됩니다.")
        boolean lastDayOfMonth,
        @Schema(description = "YEARLY 반복 월", nullable = true)
        Integer monthOfYear,
        @Schema(description = "반복 시작일")
        LocalDate startDate,
        @Schema(description = "반복 종료일")
        LocalDate endDate
) {

    public static RepeatRuleResponse from(RepeatRule repeatRule) {
        return new RepeatRuleResponse(
                repeatRule.getFrequency(),
                repeatRule.getDaysOfWeek(),
                repeatRule.getDaysOfMonth().days(),
                repeatRule.getDaysOfMonth().lastDayOfMonth(),
                repeatRule.getMonthOfYear(),
                repeatRule.getStartDate(),
                repeatRule.getEndDate()
        );
    }
}
