package com.cotato.blankit.domain.task.service;

import com.cotato.blankit.domain.task.entity.RecurrenceType;
import com.cotato.blankit.domain.task.entity.RepeatMonthDays;
import com.cotato.blankit.domain.task.entity.RepeatRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RepeatDeadlineCalculator {

    private static final int MAX_SEARCH_YEARS_WITHOUT_END_DATE = 5;

    public Optional<LocalDate> calculateInitialDeadline(RepeatRuleData rule, LocalDate today) {
        return calculateNextDeadline(rule, today.isAfter(rule.startDate()) ? today : rule.startDate());
    }

    public Optional<LocalDate> calculateNextDeadline(RepeatRule rule, LocalDate referenceDate) {
        return calculateNextDeadline(
                new RepeatRuleData(
                        rule.getFrequency(),
                        rule.getDaysOfWeek(),
                        rule.getDaysOfMonth(),
                        rule.getMonthOfYear(),
                        rule.getStartDate(),
                        rule.getEndDate()
                ),
                referenceDate
        );
    }

    public Optional<LocalDate> calculateNextDeadline(RepeatRuleData rule, LocalDate referenceDate) {
        LocalDate cursor = referenceDate.isAfter(rule.startDate()) ? referenceDate : rule.startDate();
        LocalDate searchLimit = rule.endDate() == null
                ? cursor.plusYears(MAX_SEARCH_YEARS_WITHOUT_END_DATE)
                : rule.endDate();
        while (!cursor.isAfter(searchLimit)) {
            if (matches(rule, cursor)) {
                return Optional.of(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return Optional.empty();
    }

    public boolean matches(RepeatRule rule, LocalDate targetDate) {
        return matches(
                new RepeatRuleData(
                        rule.getFrequency(),
                        rule.getDaysOfWeek(),
                        rule.getDaysOfMonth(),
                        rule.getMonthOfYear(),
                        rule.getStartDate(),
                        rule.getEndDate()
                ),
                targetDate
        );
    }

    public boolean matches(RepeatRuleData rule, LocalDate targetDate) {
        if (targetDate.isBefore(rule.startDate())
                || rule.endDate() != null && targetDate.isAfter(rule.endDate())) {
            return false;
        }
        return switch (rule.frequency()) {
            case WEEKLY -> rule.daysOfWeek().contains(toWeeklyNumber(targetDate));
            case MONTHLY -> matchesMonthDay(rule.daysOfMonth(), targetDate);
            case YEARLY -> rule.monthOfYear() != null
                    && rule.monthOfYear() == targetDate.getMonthValue()
                    && matchesMonthDay(rule.daysOfMonth(), targetDate);
        };
    }

    private boolean matchesMonthDay(RepeatMonthDays daysOfMonth, LocalDate targetDate) {
        return daysOfMonth.days().contains(targetDate.getDayOfMonth())
                || daysOfMonth.lastDayOfMonth()
                && targetDate.getDayOfMonth() == YearMonth.from(targetDate).lengthOfMonth();
    }

    private int toWeeklyNumber(LocalDate date) {
        return date.getDayOfWeek().getValue() % 7;
    }

    public record RepeatRuleData(
            RecurrenceType frequency,
            java.util.List<Integer> daysOfWeek,
            RepeatMonthDays daysOfMonth,
            Integer monthOfYear,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }
}
