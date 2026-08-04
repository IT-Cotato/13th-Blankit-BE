package com.cotato.blankit.domain.notification.push.scheduler;

import com.cotato.blankit.domain.notification.push.service.TaskDeadlineNotificationScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "blankit.push.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TaskDeadlineScheduleRefreshScheduler {
    private final TaskDeadlineNotificationScheduleService scheduleService;

    @Scheduled(cron = "${blankit.push.task-deadline.refresh-cron:0 20 0 * * *}",
            zone = "${blankit.time-zone:Asia/Seoul}")
    public void refreshSchedules() {
        scheduleService.synchronizeServiceAlarmUsers();
    }
}
