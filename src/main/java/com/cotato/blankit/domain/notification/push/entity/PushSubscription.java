package com.cotato.blankit.domain.notification.push.entity;

import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "push_subscription",
        indexes = @Index(name = "idx_push_subscription_user_active", columnList = "user_id,active"),
        uniqueConstraints = @UniqueConstraint(name = "uk_push_subscription_fid", columnNames = "firebase_installation_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "push_subscription_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "firebase_installation_id", nullable = false, length = 255)
    private String firebaseInstallationId;
    @Column(name = "device_name", length = 100)
    private String deviceName;
    @Column(length = 100)
    private String browser;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "last_registered_at", nullable = false)
    private LocalDateTime lastRegisteredAt;
    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;
    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    public static PushSubscription create(User user, String fid, String deviceName, String browser, LocalDateTime now) {
        PushSubscription subscription = new PushSubscription();
        subscription.firebaseInstallationId = fid;
        subscription.register(user, deviceName, browser, now);
        return subscription;
    }

    public void register(User user, String deviceName, String browser, LocalDateTime now) {
        this.user = user;
        this.deviceName = deviceName;
        this.browser = browser;
        this.active = true;
        this.lastRegisteredAt = now;
        this.failureCount = 0;
    }

    public void deactivate() { this.active = false; }
    public void markSuccess(LocalDateTime now) { this.lastSuccessAt = now; this.failureCount = 0; }
    public void markFailure(boolean permanent) { this.failureCount++; if (permanent) this.active = false; }
}
