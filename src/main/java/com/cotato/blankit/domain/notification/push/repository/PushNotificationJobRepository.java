package com.cotato.blankit.domain.notification.push.repository;

import com.cotato.blankit.domain.notification.push.entity.PushNotificationJob;
import com.cotato.blankit.domain.notification.push.entity.PushNotificationJobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PushNotificationJobRepository extends JpaRepository<PushNotificationJob, Long> {
    Optional<PushNotificationJob> findByDedupeKey(String dedupeKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select job from PushNotificationJob job
            where (
                    job.status = com.cotato.blankit.domain.notification.push.entity.PushNotificationJobStatus.PENDING
                    and job.scheduledAt <= :now
                    and (job.nextRetryAt is null or job.nextRetryAt <= :now)
                  )
               or (
                    job.status = com.cotato.blankit.domain.notification.push.entity.PushNotificationJobStatus.PROCESSING
                    and job.processingStartedAt <= :leaseExpiredBefore
                  )
            order by job.scheduledAt asc, job.id asc
            """)
    List<PushNotificationJob> findClaimableForUpdate(
            @Param("now") LocalDateTime now,
            @Param("leaseExpiredBefore") LocalDateTime leaseExpiredBefore,
            org.springframework.data.domain.Pageable pageable
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO push_notification_job (
                user_id, type, reference_type, reference_id, title, body, click_url,
                scheduled_at, status, attempts, next_retry_at, processing_started_at,
                retry_fids, failure_type, dedupe_key, sent_at, created_at, updated_at
            ) VALUES (
                :userId, :type, :referenceType, :referenceId, :title, :body, :clickUrl,
                :scheduledAt, 'PENDING', 0, NULL, NULL,
                NULL, NULL, :dedupeKey, NULL, :now, :now
            )
            ON DUPLICATE KEY UPDATE
                dedupe_key = VALUES(dedupe_key)
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("referenceType") String referenceType,
            @Param("referenceId") String referenceId,
            @Param("title") String title,
            @Param("body") String body,
            @Param("clickUrl") String clickUrl,
            @Param("scheduledAt") LocalDateTime scheduledAt,
            @Param("dedupeKey") String dedupeKey,
            @Param("now") LocalDateTime now
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PushNotificationJob job
            set job.status = com.cotato.blankit.domain.notification.push.entity.PushNotificationJobStatus.CANCELLED,
                job.nextRetryAt = null
            where job.user.id = :userId
              and job.type = com.cotato.blankit.domain.notification.push.entity.PushNotificationType.THIRTY_MIN_PACK
              and job.status = com.cotato.blankit.domain.notification.push.entity.PushNotificationJobStatus.PENDING
              and job.scheduledAt >= :from
            """)
    int cancelFutureThirtyMinutePackJobs(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PushNotificationJob job
            set job.status = com.cotato.blankit.domain.notification.push.entity.PushNotificationJobStatus.CANCELLED,
                job.nextRetryAt = null
            where job.user.id = :userId
              and job.type = com.cotato.blankit.domain.notification.push.entity.PushNotificationType.TASK_DEADLINE
              and job.status = com.cotato.blankit.domain.notification.push.entity.PushNotificationJobStatus.PENDING
            """)
    int cancelPendingTaskDeadlineJobs(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PushNotificationJob job
            set job.status = com.cotato.blankit.domain.notification.push.entity.PushNotificationJobStatus.CANCELLED,
                job.nextRetryAt = null
            where job.type = com.cotato.blankit.domain.notification.push.entity.PushNotificationType.TASK_DEADLINE
              and job.referenceType = 'TASK'
              and job.referenceId = :taskId
              and job.status = com.cotato.blankit.domain.notification.push.entity.PushNotificationJobStatus.PENDING
            """)
    int cancelPendingTaskDeadlineJob(@Param("taskId") String taskId);
}
