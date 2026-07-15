package com.cotato.blankit.domain.task.scheduler;

import com.cotato.blankit.domain.task.service.RepeatDeadlineRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RepeatDeadlineRefreshScheduler {

    private final RepeatDeadlineRefreshService repeatDeadlineRefreshService;

    @Scheduled(
            cron = "${blankit.task.repeat-deadline.cron:0 5 0 * * *}",
            zone = "${blankit.task.repeat-deadline.zone:Asia/Seoul}"
    )
    public void refreshExpiredRepeatDeadlines() {
        repeatDeadlineRefreshService.refreshExpiredDeadlines();
    }
}
