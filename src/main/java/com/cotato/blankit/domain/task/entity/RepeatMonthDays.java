package com.cotato.blankit.domain.task.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record RepeatMonthDays(List<Integer> days, boolean lastDayOfMonth) {

    public RepeatMonthDays {
        days = days == null ? List.of() : days.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    public boolean isEmpty() {
        return days.isEmpty() && !lastDayOfMonth;
    }

    public static RepeatMonthDays none() {
        return new RepeatMonthDays(List.of(), false);
    }

    static RepeatMonthDays parse(String value) {
        if (value == null || value.isBlank()) {
            return none();
        }
        List<String> tokens = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .toList();
        boolean lastDay = tokens.contains("L");
        List<Integer> days = tokens.stream()
                .filter(token -> !"L".equals(token))
                .map(Integer::valueOf)
                .distinct()
                .sorted()
                .toList();
        return new RepeatMonthDays(days, lastDay);
    }

    String format() {
        if (isEmpty()) {
            return null;
        }
        String daysPart = days.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        if (!lastDayOfMonth) {
            return daysPart;
        }
        return daysPart.isBlank() ? "L" : daysPart + ",L";
    }
}
