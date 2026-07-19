package com.cotato.blankit.domain.task.entity;

import java.util.Arrays;
import java.util.List;

public enum NotifyBeforeOption {
    TEN_MINUTES(10),
    ONE_HOUR(60),
    ONE_DAY(1440),
    THREE_DAYS(4320),
    ONE_WEEK(10080);

    private final int minutes;

    NotifyBeforeOption(int minutes) {
        this.minutes = minutes;
    }

    public int getMinutes() {
        return minutes;
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
