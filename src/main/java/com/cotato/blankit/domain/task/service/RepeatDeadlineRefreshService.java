package com.cotato.blankit.domain.task.service;

import com.cotato.blankit.domain.task.entity.RepeatRule;
import com.cotato.blankit.domain.task.entity.NotificationSetting;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.entity.TaskStatus;
import com.cotato.blankit.domain.task.repository.NotificationSettingRepository;
import com.cotato.blankit.domain.task.repository.RepeatRuleRepository;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
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
        List<RepeatRule> targets = repeatRuleRepository.findOccurrenceGenerationTargets(
                today,
                List.of(TaskStatus.DONE)
        );
        int createdCount = 0;
        for (RepeatRule repeatRule : targets) {
            Task sourceTask = repeatRule.getTask();
            if (!repeatDeadlineCalculator.matches(repeatRule, today)
                    || taskRepository.existsBySourceTaskIdAndDeadline(sourceTask.getId(), today)) {
                continue;
            }
            Task occurrence = taskRepository.save(Task.createRepeatedOccurrence(sourceTask, today));
            notificationSettingRepository.findByTaskId(sourceTask.getId())
                    .map(setting -> NotificationSetting.create(
                            occurrence,
                            setting.getNotifyBefore(),
                            setting.isEnabled()
                    ))
                    .ifPresent(notificationSettingRepository::save);
            createdCount++;
        }
        return createdCount;
    }
}
