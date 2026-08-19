package com.cotato.blankit.domain.task.service;

import com.cotato.blankit.domain.feedback.entity.DailyElapsedTime;
import com.cotato.blankit.domain.feedback.entity.Feedback;
import com.cotato.blankit.domain.feedback.repository.DailyElapsedTimeRepository;
import com.cotato.blankit.domain.feedback.repository.FeedbackRepository;
import com.cotato.blankit.domain.recommendation.repository.DailyRecommendationRepository;
import com.cotato.blankit.domain.task.dto.response.TaskCalendarResponse;
import com.cotato.blankit.domain.task.dto.response.TaskDailyStatsResponse;
import com.cotato.blankit.domain.task.dto.response.TaskMonthlyStatsResponse;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskStatsService {

    private final TaskRepository taskRepository;
    private final DailyElapsedTimeRepository dailyElapsedTimeRepository;
    private final FeedbackRepository feedbackRepository;
    private final DailyRecommendationRepository dailyRecommendationRepository;
    private final Clock clock;

    public List<TaskCalendarResponse> getMonthlyCalendar(Long userId, int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        List<Task> tasks = taskRepository.findTasksByUserIdAndDeadlineBetween(userId, startDate, endDate);

        Map<LocalDate, List<Task>> byDate = tasks.stream()
                .collect(Collectors.groupingBy(Task::getDeadline));

        return byDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new TaskCalendarResponse(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(t -> new TaskCalendarResponse.TaskCalendarItem(
                                        t.getId(),
                                        t.getTitle(),
                                        t.getCategory().getColor(),
                                        t.getCategory().getIconKey(),
                                        t.getStatus().name(),
                                        t.getEstimatedTime()
                                ))
                                .toList()
                ))
                .toList();
    }

    public TaskDailyStatsResponse getDailyStats(Long userId, LocalDate date) {
        LocalDate today = LocalDate.now(clock);

        Integer totalElapsedSeconds = null;
        if (!date.isAfter(today)) {
            totalElapsedSeconds = (int) dailyElapsedTimeRepository.sumElapsedSecondsByUserIdAndDate(userId, date);
        }

        int totalRecommendedMinutes;
        if (date.isAfter(today)) {
            List<Task> tasks = taskRepository.findNonDoneTasksWithEstimatedTime(userId, today);
            totalRecommendedMinutes = calcRecommendedMinutes(tasks, today, date);
        } else {
            totalRecommendedMinutes = dailyRecommendationRepository
                    .findByUser_IdAndRecommendedDateAndMode(userId, date, "TODAY")
                    .map(dr -> dr.getTotalRecommendedMinutes())
                    .orElseGet(() -> calcRecommendedMinutes(
                            taskRepository.findNonDoneTasksWithEstimatedTime(userId, date), date));
        }

        List<TaskDailyStatsResponse.FeedbackTaskItem> feedbackTasks = List.of();
        if (!date.isAfter(today)) {
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
            feedbackTasks = feedbackRepository
                    .findSubmittedByUserIdAndDateRange(userId, startOfDay, endOfDay)
                    .stream()
                    .collect(Collectors.toMap(
                            f -> f.getTask().getId(),
                            f -> f,
                            (a, b) -> {
                                int cmp = a.getSubmittedAt().compareTo(b.getSubmittedAt());
                                return cmp != 0 ? (cmp > 0 ? a : b) : (a.getFeedbackId() > b.getFeedbackId() ? a : b);
                            }
                    ))
                    .values()
                    .stream()
                    .sorted(Comparator.comparing(Feedback::getSubmittedAt))
                    .map(this::toFeedbackTaskItem)
                    .toList();
        }

        return new TaskDailyStatsResponse(date, totalElapsedSeconds, totalRecommendedMinutes, feedbackTasks);
    }

    public TaskMonthlyStatsResponse getMonthlyStats(Long userId, int year, int month) {
        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = LocalDate.of(year, month, 1);
        int daysInMonth = startDate.lengthOfMonth();

        LocalDate endDate = startDate.withDayOfMonth(daysInMonth);
        List<DailyElapsedTime> dailyRecords = dailyElapsedTimeRepository.findByUser_IdAndDateBetween(userId, startDate, endDate);

        Map<LocalDate, Long> elapsedByDate = dailyRecords.stream()
                .collect(Collectors.toMap(DailyElapsedTime::getDate, r -> (long) r.getElapsedSeconds(), Long::sum));

        List<Task> nonDoneTasks = taskRepository.findNonDoneTasksWithEstimatedTime(userId, startDate);

        int todayRecommendedMinutes = dailyRecommendationRepository
                .findByUser_IdAndRecommendedDateAndMode(userId, today, "TODAY")
                .map(dr -> dr.getTotalRecommendedMinutes())
                .orElseGet(() -> calcRecommendedMinutes(nonDoneTasks, today));

        LocalDate lastPastDate = endDate.isBefore(today) ? endDate : today.minusDays(1);
        Map<LocalDate, Integer> pastCacheMap = startDate.isAfter(lastPastDate)
                ? Map.of()
                : dailyRecommendationRepository
                        .findAllByUser_IdAndRecommendedDateBetweenAndMode(userId, startDate, lastPastDate, "TODAY")
                        .stream()
                        .collect(Collectors.toMap(
                                dr -> dr.getRecommendedDate(),
                                dr -> dr.getTotalRecommendedMinutes()
                        ));

        List<TaskMonthlyStatsResponse.DayStatsItem> dailyStats = new ArrayList<>();
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = LocalDate.of(year, month, day);

            Integer actualMinutes = null;
            if (!date.isAfter(today)) {
                long elapsedSeconds = elapsedByDate.getOrDefault(date, 0L);
                actualMinutes = (int) Math.round(elapsedSeconds / 60.0);
            }

            int recommendedMinutes;
            if (date.isBefore(today)) {
                recommendedMinutes = pastCacheMap.getOrDefault(date, calcRecommendedMinutes(nonDoneTasks, date));
            } else if (date.equals(today)) {
                recommendedMinutes = todayRecommendedMinutes;
            } else {
                recommendedMinutes = calcRecommendedMinutes(nonDoneTasks, today, date);
            }
            dailyStats.add(new TaskMonthlyStatsResponse.DayStatsItem(date, actualMinutes, recommendedMinutes));
        }

        return new TaskMonthlyStatsResponse(year, month, dailyStats);
    }

    private int calcRecommendedMinutes(List<Task> tasks, LocalDate date) {
        return calcRecommendedMinutes(tasks, date, date);
    }

    private int calcRecommendedMinutes(List<Task> tasks, LocalDate referenceDate, LocalDate filterDate) {
        return (int) Math.round(
                tasks.stream()
                        .mapToDouble(t -> {
                            long daysFromFilter    = ChronoUnit.DAYS.between(filterDate, t.getDeadline());
                            long daysFromReference = ChronoUnit.DAYS.between(referenceDate, t.getDeadline());
                            return (daysFromFilter > 0 && daysFromReference > 0)
                                    ? (double) t.getEstimatedTime() / daysFromReference
                                    : 0.0;
                        })
                        .sum()
        );
    }

    private TaskDailyStatsResponse.FeedbackTaskItem toFeedbackTaskItem(Feedback f) {
        return new TaskDailyStatsResponse.FeedbackTaskItem(
                f.getTask().getId(),
                f.getTask().getTitle(),
                f.getTask().getCategory().getName(),
                f.getTask().getCategory().getColor(),
                f.getTask().getCategory().getIconKey(),
                f.getProgressRate() == null ? 0 : f.getProgressRate(),
                f.isCompleted(),
                f.getMemo()
        );
    }
}
