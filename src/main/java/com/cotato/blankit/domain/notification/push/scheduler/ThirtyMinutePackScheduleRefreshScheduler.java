package com.cotato.blankit.domain.notification.push.scheduler;

import com.cotato.blankit.domain.notification.push.service.ThirtyMinutePackScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "blankit.push.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ThirtyMinutePackScheduleRefreshScheduler {
    private final ThirtyMinutePackScheduleService scheduleService;

    @Scheduled(cron = "${blankit.push.thirty-minute-pack.refresh-cron:0 10 0 * * *}",
            zone = "${blankit.push.thirty-minute-pack.zone:Asia/Seoul}")
    public void refreshScheduleHorizon() {
        scheduleService.synchronizeEnabledUsers();
    }
}
