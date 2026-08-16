package com.cotato.blankit.domain.notification.push.repository;

import com.cotato.blankit.domain.notification.push.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    Optional<PushSubscription> findByFirebaseInstallationId(String firebaseInstallationId);
    Optional<PushSubscription> findByFcmToken(String fcmToken);
    List<PushSubscription> findByFcmTokenIn(List<String> fcmTokens);
    Optional<PushSubscription> findByIdAndUserId(Long id, Long userId);
    List<PushSubscription> findByUserIdAndActiveTrueOrderByIdAsc(Long userId);
    List<PushSubscription> findByUserIdAndActiveTrueAndFcmTokenInOrderByIdAsc(
            Long userId,
            List<String> fcmTokens
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO push_subscription (
                user_id, firebase_installation_id, fcm_token, device_name, browser, active,
                last_registered_at, last_success_at, failure_count, created_at, updated_at
            ) VALUES (
                :userId, :installationId, :fcmToken, :deviceName, :browser, TRUE,
                :now, NULL, 0, :now, :now
            )
            ON DUPLICATE KEY UPDATE
                user_id = CASE WHEN firebase_installation_id = :installationId THEN :userId ELSE user_id END,
                fcm_token = CASE WHEN firebase_installation_id = :installationId THEN :fcmToken ELSE fcm_token END,
                device_name = CASE WHEN firebase_installation_id = :installationId THEN :deviceName ELSE device_name END,
                browser = CASE WHEN firebase_installation_id = :installationId THEN :browser ELSE browser END,
                active = CASE WHEN firebase_installation_id = :installationId THEN TRUE ELSE active END,
                last_registered_at = CASE WHEN firebase_installation_id = :installationId THEN :now ELSE last_registered_at END,
                failure_count = CASE WHEN firebase_installation_id = :installationId THEN 0 ELSE failure_count END,
                updated_at = CASE WHEN firebase_installation_id = :installationId THEN :now ELSE updated_at END
            """, nativeQuery = true)
    int upsert(
            @Param("userId") Long userId,
            @Param("installationId") String installationId,
            @Param("fcmToken") String fcmToken,
            @Param("deviceName") String deviceName,
            @Param("browser") String browser,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("""
            update PushSubscription subscription
            set subscription.lastSuccessAt = :now,
                subscription.failureCount = 0,
                subscription.updatedAt = :now
            where subscription.id = :subscriptionId
              and subscription.fcmToken = :fcmToken
            """)
    int markSuccessIfTokenMatches(
            @Param("subscriptionId") Long subscriptionId,
            @Param("fcmToken") String fcmToken,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("""
            update PushSubscription subscription
            set subscription.failureCount = subscription.failureCount + 1,
                subscription.updatedAt = :now
            where subscription.id = :subscriptionId
              and subscription.fcmToken = :fcmToken
            """)
    int markFailureIfTokenMatches(
            @Param("subscriptionId") Long subscriptionId,
            @Param("fcmToken") String fcmToken,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("""
            update PushSubscription subscription
            set subscription.failureCount = subscription.failureCount + 1,
                subscription.active = false,
                subscription.updatedAt = :now
            where subscription.id = :subscriptionId
              and subscription.fcmToken = :fcmToken
            """)
    int deactivateAfterFailureIfTokenMatches(
            @Param("subscriptionId") Long subscriptionId,
            @Param("fcmToken") String fcmToken,
            @Param("now") LocalDateTime now
    );
}
