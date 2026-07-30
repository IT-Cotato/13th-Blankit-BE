package com.cotato.blankit.domain.notification.push.service;

import com.cotato.blankit.domain.notification.push.entity.*;
import com.cotato.blankit.domain.notification.push.repository.PushNotificationJobRepository;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1));
    private final PushNotificationJobRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public PushNotificationJob schedule(Long userId, PushNotificationType type, String referenceType,
                                        String referenceId, String title, String body, String clickUrl,
                                        LocalDateTime scheduledAt, String dedupeKey) {
        Optional<PushNotificationJob> existing = repository.findByDedupeKey(dedupeKey);
        if (existing.isPresent()) {
            existing.get().refresh(title, body, clickUrl, scheduledAt);
            return existing.get();
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        try {
            return repository.saveAndFlush(PushNotificationJob.create(user, type, referenceType, referenceId,
                    title, body, clickUrl, scheduledAt, dedupeKey));
        } catch (DataIntegrityViolationException race) {
            return repository.findByDedupeKey(dedupeKey).orElseThrow(() -> race);
        }
    }

    @Transactional
    public void cancel(String dedupeKey) {
        repository.findByDedupeKey(dedupeKey).ifPresent(PushNotificationJob::cancel);
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

    @Transactional
    public void reschedule(String dedupeKey, LocalDateTime scheduledAt) {
        PushNotificationJob job = repository.findByDedupeKey(dedupeKey)
                .orElseThrow(() -> new CustomException(ErrorCode.PUSH_JOB_NOT_FOUND));
        job.reschedule(scheduledAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedPushJob> claimNext() {
        LocalDateTime now = LocalDateTime.now(clock);
        return repository.findDueForUpdate(PushNotificationJobStatus.PENDING, now, PageRequest.of(0, 1))
                .stream().findFirst().map(job -> {
                    job.claim();
                    return ClaimedPushJob.from(job);
                });
    }

    @Transactional
    public void complete(Long jobId, PushNotificationService.PushSendOutcome outcome) {
        PushNotificationJob job = repository.findById(jobId)
                .orElseThrow(() -> new CustomException(ErrorCode.PUSH_JOB_NOT_FOUND));
        switch (outcome) {
            case SENT, SKIPPED_NO_SUBSCRIPTION -> job.markSent(LocalDateTime.now(clock));
            case SKIPPED_PREFERENCE -> job.cancelAfterClaim();
            case RETRYABLE_FAILURE -> retryOrFail(job);
            case FAILED -> job.fail();
        }
    }

    private void retryOrFail(PushNotificationJob job) {
        int retryIndex = job.getAttempts() - 1;
        if (retryIndex >= RETRY_DELAYS.size()) {
            job.fail();
        } else {
            job.retryAt(LocalDateTime.now(clock).plus(RETRY_DELAYS.get(retryIndex)));
        }
    }

    public record ClaimedPushJob(Long id, Long userId, PushNotificationType type, String referenceType,
                                 String referenceId, String title, String body, String clickUrl, int attempts) {
        static ClaimedPushJob from(PushNotificationJob job) {
            return new ClaimedPushJob(job.getId(), job.getUser().getId(), job.getType(), job.getReferenceType(),
                    job.getReferenceId(), job.getTitle(), job.getBody(), job.getClickUrl(), job.getAttempts());
        }
    }
}
