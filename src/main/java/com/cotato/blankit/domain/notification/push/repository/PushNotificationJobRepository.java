package com.cotato.blankit.domain.notification.push.repository;

import com.cotato.blankit.domain.notification.push.entity.PushNotificationJob;
import com.cotato.blankit.domain.notification.push.entity.PushNotificationJobStatus;
import com.cotato.blankit.domain.notification.push.entity.PushNotificationType;
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
    List<PushNotificationJob> findByUserIdAndTypeOrderByScheduledAtAsc(Long userId, PushNotificationType type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select job from PushNotificationJob job
            where job.status = :status
              and job.scheduledAt <= :now
              and (job.nextRetryAt is null or job.nextRetryAt <= :now)
            order by job.scheduledAt asc, job.id asc
            """)
    List<PushNotificationJob> findDueForUpdate(@Param("status") PushNotificationJobStatus status,
                                                @Param("now") LocalDateTime now,
                                                org.springframework.data.domain.Pageable pageable);

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
