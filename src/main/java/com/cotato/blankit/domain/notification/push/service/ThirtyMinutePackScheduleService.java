package com.cotato.blankit.domain.notification.push.service;

import com.cotato.blankit.domain.notification.repository.UserNotificationSettingRepository;
import com.cotato.blankit.domain.notification.push.entity.PushNotificationType;
import com.cotato.blankit.domain.timetable.entity.Timetable;
import com.cotato.blankit.domain.timetable.repository.TimetableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ThirtyMinutePackScheduleService {
    private static final int PACK_MINUTES = 30;
    private static final DateTimeFormatter DEDUPE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final TimetableRepository timetableRepository;
    private final UserNotificationSettingRepository settingRepository;
    private final PushNotificationJobService jobService;
    private final Clock clock;

    @Value("${blankit.push.thirty-minute-pack.horizon-days:14}")
    private int horizonDays;

    @Transactional
    public void synchronize(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        jobService.cancelFutureThirtyMinutePackJobs(userId, now);
        if (!isEnabled(userId)) return;

        Map<Byte, List<Timetable>> byDay = timetableRepository
                .findByUserIdOrderByDayOfWeekAscStartTimeAsc(userId).stream()
                .collect(Collectors.groupingBy(Timetable::getDayOfWeek));
        for (int offset = 0; offset < horizonDays; offset++) {
            LocalDate date = now.toLocalDate().plusDays(offset);
            byte dayOfWeek = (byte) (date.getDayOfWeek().getValue() % 7);
            scheduleGaps(userId, date, now, byDay.getOrDefault(dayOfWeek, List.of()));
        }
    }

    @Transactional
    public void synchronizeEnabledUsers() {
        settingRepository.findThirtyMinutePackNotificationRecipientUserIds().forEach(this::synchronize);
    }

    private boolean isEnabled(Long userId) {
        return settingRepository.findByUserId(userId)
                .map(setting -> setting.isThirtyMinPackAlarmEnabled())
                .orElse(false);
    }

    private void scheduleGaps(Long userId, LocalDate date, LocalDateTime now, List<Timetable> timetable) {
        List<Timetable> ordered = timetable.stream()
                .sorted(Comparator.comparing(Timetable::getStartTime).thenComparing(Timetable::getTimetableId))
                .toList();
        for (int i = 0; i + 1 < ordered.size(); i++) {
            Timetable before = ordered.get(i);
            Timetable after = ordered.get(i + 1);
            long gapMinutes = Duration.between(before.getEndTime(), after.getStartTime()).toMinutes();
            if (gapMinutes != PACK_MINUTES) continue;
            LocalDateTime scheduledAt = LocalDateTime.of(date, before.getEndTime());
            if (!scheduledAt.isAfter(now)) continue;

            String referenceId = before.getTimetableId() + ":" + after.getTimetableId() + ":" + date;
            String dedupeKey = "THIRTY_MIN_PACK:TIMETABLE_GAP:" + userId + ":"
                    + before.getTimetableId() + ":" + after.getTimetableId() + ":" + scheduledAt.format(DEDUPE_TIME);
            jobService.schedule(userId, PushNotificationType.THIRTY_MIN_PACK, "TIMETABLE_GAP", referenceId,
                    "지금 30분 Pack을 시작해 볼까요?",
                    gapMinutes + "분 동안 빠르게 진행할 수 있는 과업을 추천해 드려요.",
                    "/recommendations/pack30?availableMinutes=" + gapMinutes,
                    scheduledAt, dedupeKey);
        }
    }
}
