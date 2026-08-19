package com.cotato.blankit.domain.notification.push.service;

import com.cotato.blankit.domain.notification.push.entity.PushNotificationType;
import com.cotato.blankit.domain.notification.repository.UserNotificationSettingRepository;
import com.cotato.blankit.domain.task.entity.NotificationSetting;
import com.cotato.blankit.domain.task.entity.NotifyBeforeOption;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.entity.TaskStatus;
import com.cotato.blankit.domain.task.repository.NotificationSettingRepository;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;

@Service
@RequiredArgsConstructor
public class TaskDeadlineNotificationScheduleService {
    private static final long TEMP_FCM_TEST_DELAY_MINUTES = 2L;

    private final TaskRepository taskRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final UserNotificationSettingRepository userSettingRepository;
    private final PushNotificationJobService jobService;
    private final PushNotificationContentFactory contentFactory;
    private final Clock clock;

    @Value("${blankit.push.task-deadline.deadline-time:09:00}")
    private LocalTime deadlineTime;

    @Transactional
    public void synchronizeTask(Long taskId) {
        jobService.cancelPendingTaskDeadlineJob(taskId);
        taskRepository.findById(taskId).ifPresent(task ->
                scheduleIfEligible(task, isServiceAlarmEnabled(task.getUser().getId())));
    }

    @Transactional
    public void synchronizeUser(Long userId) {
        jobService.cancelPendingTaskDeadlineJobs(userId);
        if (!isServiceAlarmEnabled(userId)) return;
        LocalDate today = LocalDate.now(clock);
        taskRepository.findFutureActiveTasksForNotification(userId, today)
                .forEach(task -> scheduleIfEligible(task, true));
    }

    public void synchronizeServiceAlarmUsers() {
        userSettingRepository.findServiceNotificationRecipientUserIds().forEach(this::synchronizeUser);
    }

    @Transactional
    public void cancelTask(Long taskId) {
        jobService.cancelPendingTaskDeadlineJob(taskId);
    }

    private void scheduleIfEligible(Task task, boolean serviceAlarmEnabled) {
        if (task.getStatus() == TaskStatus.DONE || !serviceAlarmEnabled) return;
        NotificationSetting setting = notificationSettingRepository.findByTaskId(task.getId()).orElse(null);
        if (setting == null || !setting.isEnabled()) return;

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime scheduledAt = LocalDateTime.of(task.getDeadline(), deadlineTime)
                .minusMinutes(setting.getNotifyBefore());
        if (setting.getNotifyBefore().equals(NotifyBeforeOption.ONE_DAY.getMinutes())) {
            // TODO: TEMP FCM integration test. Restore the original 1-day-before policy after verification.
            scheduledAt = now.plusMinutes(TEMP_FCM_TEST_DELAY_MINUTES);
        }
        if (!scheduledAt.isAfter(now)) return;

        PushNotificationContentFactory.Content content =
                contentFactory.taskDeadline(task, setting.getNotifyBefore());
        String dedupeKey = "TASK_DEADLINE:TASK:" + task.getId() + ":"
                + task.getDeadline() + ":" + setting.getNotifyBefore();
        jobService.schedule(task.getUser().getId(), PushNotificationType.TASK_DEADLINE,
                "TASK", String.valueOf(task.getId()),
                content.title(), content.body(), content.clickUrl(), scheduledAt, dedupeKey);
    }

    private boolean isServiceAlarmEnabled(Long userId) {
        return userSettingRepository.findByUserId(userId)
                .map(setting -> setting.isServiceAlarmEnabled())
                .orElse(false);
    }
}
