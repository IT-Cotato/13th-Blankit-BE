package com.cotato.blankit.domain.task.entity;

import java.util.Arrays;
import java.util.List;

public enum NotifyBeforeOption {
    ONE_DAY(1440, "1일"),
    THREE_DAYS(4320, "3일"),
    ONE_WEEK(10080, "1주일");

    private final int minutes;
    private final String label;

    NotifyBeforeOption(int minutes, String label) {
        this.minutes = minutes;
        this.label = label;
    }

    public int getMinutes() {
        return minutes;
    }

    public String getLabel() {
        return label;
    }

    public static String labelFor(int minutes) {
        return Arrays.stream(values())
                .filter(option -> option.minutes == minutes)
                .map(NotifyBeforeOption::getLabel)
                .findFirst()
                .orElse(minutes + "분");
    }

    public static boolean supports(int minutes) {
        return Arrays.stream(values())
                .anyMatch(option -> option.minutes == minutes);
    }

    public static List<Integer> minutesValues() {
        return Arrays.stream(values())
                .map(NotifyBeforeOption::getMinutes)
                .toList();
    }
}
