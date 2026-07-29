package com.cotato.blankit.domain.push.entity;

import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "push_notification_job",
        indexes = @Index(name = "idx_push_job_due", columnList = "status,scheduled_at,next_retry_at"),
        uniqueConstraints = @UniqueConstraint(name = "uk_push_job_dedupe_key", columnNames = "dedupe_key"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushNotificationJob extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "push_notification_job_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private PushNotificationType type;
    @Column(name = "reference_type", nullable = false, length = 50)
    private String referenceType;
    @Column(name = "reference_id", nullable = false, length = 100)
    private String referenceId;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, length = 500)
    private String body;
    @Column(name = "click_url", length = 500)
    private String clickUrl;
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PushNotificationJobStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;
    @Column(name = "dedupe_key", nullable = false, length = 255)
    private String dedupeKey;
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public static PushNotificationJob create(User user, PushNotificationType type, String referenceType,
                                              String referenceId, String title, String body, String clickUrl,
                                              LocalDateTime scheduledAt, String dedupeKey) {
        PushNotificationJob job = new PushNotificationJob();
        job.user = user; job.type = type; job.referenceType = referenceType; job.referenceId = referenceId;
        job.title = title; job.body = body; job.clickUrl = clickUrl; job.scheduledAt = scheduledAt;
        job.dedupeKey = dedupeKey; job.status = PushNotificationJobStatus.PENDING;
        return job;
    }

    public void reschedule(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt; this.nextRetryAt = null; this.attempts = 0;
        this.status = PushNotificationJobStatus.PENDING;
    }
    public void cancel() { if (status == PushNotificationJobStatus.PENDING) status = PushNotificationJobStatus.CANCELLED; }
    public void claim() { status = PushNotificationJobStatus.PROCESSING; attempts++; }
    public void markSent(LocalDateTime now) { status = PushNotificationJobStatus.SENT; sentAt = now; nextRetryAt = null; }
    public void retryAt(LocalDateTime retryAt) { status = PushNotificationJobStatus.PENDING; nextRetryAt = retryAt; }
    public void fail() { status = PushNotificationJobStatus.FAILED; nextRetryAt = null; }
    public void cancelAfterClaim() { status = PushNotificationJobStatus.CANCELLED; nextRetryAt = null; }
}
