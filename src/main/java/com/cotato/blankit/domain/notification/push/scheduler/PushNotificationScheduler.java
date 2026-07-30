package com.cotato.blankit.domain.notification.push.scheduler;

import com.cotato.blankit.domain.notification.push.gateway.PushPayload;
import com.cotato.blankit.domain.notification.push.service.PushNotificationJobService;
import com.cotato.blankit.domain.notification.push.service.PushNotificationService;
import com.cotato.blankit.global.config.PushSchedulerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "blankit.push.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PushNotificationScheduler {
    private final PushNotificationJobService jobService;
    private final PushNotificationService notificationService;
    private final PushSchedulerProperties schedulerProperties;

    @Scheduled(fixedDelayString = "${blankit.push.scheduler.fixed-delay:60000}")
    public void processDueJobs() {
        for (int count = 0; count < schedulerProperties.maxJobsPerTick(); count++) {
            var claimed = jobService.claimNext();
            if (claimed.isEmpty()) return;
            var job = claimed.get();
            PushPayload payload = new PushPayload(job.type().name(), job.title(), job.body(),
                    job.referenceId(), job.clickUrl(), Map.of("referenceType", job.referenceType()));
            PushNotificationService.PushSendResult outcome;
            try {
                outcome = notificationService.send(
                        job.userId(),
                        job.type(),
                        payload,
                        job.retryInstallationIds()
                );
            } catch (RuntimeException exception) {
                log.error("Push job execution failed: jobId={}, attempt={}", job.id(), job.attempts(), exception);
                outcome = PushNotificationService.PushSendResult.retryAll(job.retryInstallationIds());
            }
            jobService.complete(job.id(), outcome);
        }
    }
}
