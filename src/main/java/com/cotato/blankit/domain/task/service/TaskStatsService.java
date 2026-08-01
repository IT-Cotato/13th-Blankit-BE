package com.cotato.blankit.domain.task.service;

import com.cotato.blankit.domain.feedback.entity.DailyElapsedTime;
import com.cotato.blankit.domain.feedback.entity.Feedback;
import com.cotato.blankit.domain.feedback.repository.DailyElapsedTimeRepository;
import com.cotato.blankit.domain.feedback.repository.FeedbackRepository;
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
                                        t.getStatus().name()
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

        int totalRecommendedMinutes = calcRecommendedMinutes(
                taskRepository.findNonDoneTasksWithEstimatedTime(userId), date);

        List<TaskDailyStatsResponse.FeedbackTaskItem> feedbackTasks = List.of();
        if (!date.isAfter(today)) {
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
            feedbackTasks = feedbackRepository
                    .findSubmittedByUserIdAndDateRange(userId, startOfDay, endOfDay)
                    .stream()
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
                .collect(Collectors.toMap(DailyElapsedTime::getDate, r -> (long) r.getElapsedSeconds()));

        List<Task> nonDoneTasks = taskRepository.findNonDoneTasksWithEstimatedTime(userId);

        List<TaskMonthlyStatsResponse.DayStatsItem> dailyStats = new ArrayList<>();
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = LocalDate.of(year, month, day);

            Integer actualMinutes = null;
            if (!date.isAfter(today)) {
                long elapsedSeconds = elapsedByDate.getOrDefault(date, 0L);
                actualMinutes = (int) Math.round(elapsedSeconds / 60.0);
            }

            int recommendedMinutes = calcRecommendedMinutes(nonDoneTasks, date);
            dailyStats.add(new TaskMonthlyStatsResponse.DayStatsItem(date, actualMinutes, recommendedMinutes));
        }

        return new TaskMonthlyStatsResponse(year, month, dailyStats);
    }

    private int calcRecommendedMinutes(List<Task> tasks, LocalDate date) {
        return (int) Math.round(
                tasks.stream()
                        .filter(t -> ChronoUnit.DAYS.between(date, t.getDeadline()) > 0)
                        .mapToDouble(t -> (double) t.getEstimatedTime() / ChronoUnit.DAYS.between(date, t.getDeadline()))
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
                f.isCompleted()
        );
    }
}
