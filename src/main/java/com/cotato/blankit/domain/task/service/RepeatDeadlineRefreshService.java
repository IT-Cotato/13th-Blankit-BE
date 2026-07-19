package com.cotato.blankit.domain.task.service;

import com.cotato.blankit.domain.task.entity.RepeatRule;
import com.cotato.blankit.domain.task.entity.NotificationSetting;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.repository.NotificationSettingRepository;
import com.cotato.blankit.domain.task.repository.RepeatRuleRepository;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RepeatDeadlineRefreshService {

    private final RepeatRuleRepository repeatRuleRepository;
    private final TaskRepository taskRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final RepeatDeadlineCalculator repeatDeadlineCalculator;
    private final Clock clock;

    @Transactional
    public int generateDueOccurrences() {
        LocalDate today = LocalDate.now(clock);
        List<RepeatRule> targets = repeatRuleRepository.findOccurrenceGenerationTargets(today);
        int createdCount = 0;
        for (RepeatRule repeatRule : targets) {
            Task sourceTask = repeatRule.getTask();
            if (!repeatDeadlineCalculator.matches(repeatRule, today)) {
                continue;
            }
            if (createOccurrenceIfAbsent(sourceTask, today)) {
                createdCount++;
            }
        }
        return createdCount;
    }

    private boolean createOccurrenceIfAbsent(Task sourceTask, LocalDate today) {
        if (taskRepository.existsBySourceTaskIdAndDeadline(sourceTask.getId(), today)) {
            return false;
        }
        try {
            Task occurrence = taskRepository.saveAndFlush(Task.createRepeatedOccurrence(sourceTask, today));
            notificationSettingRepository.findByTaskId(sourceTask.getId())
                    .map(setting -> NotificationSetting.create(
                            occurrence,
                            setting.getNotifyBefore(),
                            setting.isEnabled()
                    ))
                    .ifPresent(notificationSettingRepository::save);
            return true;
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateOccurrenceConstraintViolation(e)
                    && taskRepository.existsBySourceTaskIdAndDeadline(sourceTask.getId(), today)) {
                return false;
            }
            throw e;
        }
    }

    private boolean isDuplicateOccurrenceConstraintViolation(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase().contains("uk_task_source_deadline");
    }
}
