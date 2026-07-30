package com.cotato.blankit.domain.notification.push.service;

import com.cotato.blankit.domain.notification.push.entity.*;
import com.cotato.blankit.domain.notification.push.repository.PushNotificationJobRepository;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.config.PushSchedulerProperties;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PushNotificationJobService {
    private final PushNotificationJobRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final PushSchedulerProperties schedulerProperties;

    @Transactional
    public PushNotificationJob schedule(Long userId, PushNotificationType type, String referenceType,
                                        String referenceId, String title, String body, String clickUrl,
                                        LocalDateTime scheduledAt, String dedupeKey) {
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        repository.insertIfAbsent(
                user.getId(),
                type.name(),
                referenceType,
                referenceId,
                title,
                body,
                clickUrl,
                scheduledAt,
                dedupeKey,
                now
        );
        PushNotificationJob job = repository.findByDedupeKey(dedupeKey)
                .orElseThrow(() -> new CustomException(ErrorCode.PUSH_JOB_NOT_FOUND));
        job.refresh(title, body, clickUrl, scheduledAt);
        return job;
    }

    @Transactional
    public int cancelFutureThirtyMinutePackJobs(Long userId, LocalDateTime from) {
        return repository.cancelFutureThirtyMinutePackJobs(userId, from);
    }

    @Transactional
    public int cancelPendingTaskDeadlineJobs(Long userId) {
        return repository.cancelPendingTaskDeadlineJobs(userId);
    }

    @Transactional
    public int cancelPendingTaskDeadlineJob(Long taskId) {
        return repository.cancelPendingTaskDeadlineJob(String.valueOf(taskId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedPushJob> claimNext() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime leaseExpiredBefore = now.minus(Duration.ofMillis(schedulerProperties.processingLeaseMillis()));
        return repository.findClaimableForUpdate(now, leaseExpiredBefore, PageRequest.of(0, 1))
                .stream().findFirst().map(job -> {
                    job.claim(now);
                    return ClaimedPushJob.from(job);
                });
    }

    @Transactional
    public void complete(Long jobId, PushNotificationService.PushSendResult result) {
        PushNotificationJob job = repository.findById(jobId)
                .orElseThrow(() -> new CustomException(ErrorCode.PUSH_JOB_NOT_FOUND));
        switch (result.outcome()) {
            case SENT, SKIPPED_NO_SUBSCRIPTION -> job.markSent(LocalDateTime.now(clock));
            case SKIPPED_PREFERENCE -> job.cancelAfterClaim();
            case RETRYABLE_FAILURE -> retryOrFail(job, result.retryInstallationIds());
            case FAILED -> job.fail();
        }
    }

    private void retryOrFail(PushNotificationJob job, List<String> retryInstallationIds) {
        int retryIndex = job.getAttempts() - 1;
        if (retryIndex >= schedulerProperties.retryDelays().size()) {
            job.fail();
        } else {
            job.retryAt(
                    LocalDateTime.now(clock).plus(schedulerProperties.retryDelays().get(retryIndex)),
                    FidListCodec.encode(retryInstallationIds)
            );
        }
    }

    public record ClaimedPushJob(Long id, Long userId, PushNotificationType type, String referenceType,
                                 String referenceId, String title, String body, String clickUrl, int attempts,
                                 List<String> retryInstallationIds) {
        static ClaimedPushJob from(PushNotificationJob job) {
            return new ClaimedPushJob(job.getId(), job.getUser().getId(), job.getType(), job.getReferenceType(),
                    job.getReferenceId(), job.getTitle(), job.getBody(), job.getClickUrl(), job.getAttempts(),
                    FidListCodec.decode(job.getRetryFids()));
        }
    }
}
