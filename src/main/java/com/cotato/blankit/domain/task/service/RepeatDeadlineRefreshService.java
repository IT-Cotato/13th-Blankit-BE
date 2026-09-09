package com.cotato.blankit.domain.task.service;

import com.cotato.blankit.domain.notification.push.service.TaskDeadlineNotificationScheduleService;
import com.cotato.blankit.domain.task.entity.NotificationSetting;
import com.cotato.blankit.domain.task.entity.RepeatRule;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.entity.TaskStep;
import com.cotato.blankit.domain.task.repository.NotificationSettingRepository;
import com.cotato.blankit.domain.task.repository.RepeatRuleRepository;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.task.repository.TaskStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RepeatDeadlineRefreshService {

    private final RepeatRuleRepository repeatRuleRepository;
    private final TaskRepository taskRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final TaskStepRepository taskStepRepository;
    private final RepeatDeadlineCalculator repeatDeadlineCalculator;
    private final TaskDeadlineNotificationScheduleService taskDeadlineNotificationScheduleService;
    private final Clock clock;

    @Transactional
    public int generateDueOccurrences() {
        LocalDate today = LocalDate.now(clock);
        List<RepeatRule> targets = repeatRuleRepository.findOccurrenceGenerationTargets(today);
        List<Long> createdTaskIds = new ArrayList<>();
        int createdCount = 0;
        for (RepeatRule repeatRule : targets) {
            createdCount += ensureNextFutureOccurrence(repeatRule, today, createdTaskIds);
        }
        scheduleDeadlineNotifications(createdTaskIds);
        return createdCount;
    }

    @Transactional
    public int generateNextOccurrencesForTask(Long sourceTaskId) {
        LocalDate today = LocalDate.now(clock);
        Optional<RepeatRule> repeatRule =
                repeatRuleRepository.findByTaskIdForOccurrenceGeneration(sourceTaskId);
        if (repeatRule.isEmpty()) {
            return 0;
        }
        List<Long> createdTaskIds = new ArrayList<>();
        int createdCount = ensureNextFutureOccurrence(repeatRule.get(), today, createdTaskIds);
        scheduleDeadlineNotifications(createdTaskIds);
        return createdCount;
    }

    private int ensureNextFutureOccurrence(
            RepeatRule repeatRule,
            LocalDate today,
            List<Long> createdTaskIds
    ) {
        Task sourceTask = repeatRule.getTask();
        LocalDate latestDeadline = taskRepository
                .findTopBySourceTaskIdOrderByDeadlineDescIdDesc(sourceTask.getId())
                .map(Task::getDeadline)
                .filter(deadline -> deadline.isAfter(sourceTask.getDeadline()))
                .orElse(sourceTask.getDeadline());
        int createdCount = 0;

        while (!latestDeadline.isAfter(today)) {
            LocalDate nextDeadline = repeatDeadlineCalculator
                    .calculateNextDeadline(repeatRule, latestDeadline.plusDays(1))
                    .orElse(null);
            if (nextDeadline == null) {
                break;
            }
            if (!taskRepository.existsBySourceTaskIdAndDeadline(sourceTask.getId(), nextDeadline)) {
                Task occurrence = taskRepository.saveAndFlush(
                        Task.createRepeatedOccurrence(sourceTask, nextDeadline)
                );
                copyNotificationSetting(sourceTask, occurrence);
                copyChapters(sourceTask, occurrence);
                createdTaskIds.add(occurrence.getId());
                createdCount++;
            }
            latestDeadline = nextDeadline;
        }
        return createdCount;
    }

    private void copyNotificationSetting(Task sourceTask, Task occurrence) {
        notificationSettingRepository.findByTaskId(sourceTask.getId())
                .map(setting -> NotificationSetting.create(
                        occurrence,
                        setting.getNotifyBefore(),
                        setting.isEnabled()
                ))
                .ifPresent(notificationSettingRepository::save);
    }

    private void copyChapters(Task sourceTask, Task occurrence) {
        List<TaskStep> chapters = taskStepRepository
                .findByTaskIdOrderBySortOrderAscTaskStepIdAsc(sourceTask.getId())
                .stream()
                .map(source -> TaskStep.create(occurrence, source.getTitle(), source.getSortOrder()))
                .toList();
        taskStepRepository.saveAll(chapters);
    }

    private void scheduleDeadlineNotifications(List<Long> createdTaskIds) {
        if (createdTaskIds.isEmpty()) {
            return;
        }
        notificationSettingRepository.flush();
        createdTaskIds.forEach(taskDeadlineNotificationScheduleService::synchronizeTask);
    }
}
