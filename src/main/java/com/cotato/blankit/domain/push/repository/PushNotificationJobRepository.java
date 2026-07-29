package com.cotato.blankit.domain.push.repository;

import com.cotato.blankit.domain.push.entity.PushNotificationJob;
import com.cotato.blankit.domain.push.entity.PushNotificationJobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PushNotificationJobRepository extends JpaRepository<PushNotificationJob, Long> {
    Optional<PushNotificationJob> findByDedupeKey(String dedupeKey);

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
}
