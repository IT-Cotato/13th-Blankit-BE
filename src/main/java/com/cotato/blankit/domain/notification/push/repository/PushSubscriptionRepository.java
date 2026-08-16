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
                user_id = :userId,
                firebase_installation_id = :installationId,
                fcm_token = :fcmToken,
                device_name = :deviceName,
                browser = :browser,
                active = TRUE,
                last_registered_at = :now,
                failure_count = 0,
                updated_at = :now
            """, nativeQuery = true)
    int upsert(
            @Param("userId") Long userId,
            @Param("installationId") String installationId,
            @Param("fcmToken") String fcmToken,
            @Param("deviceName") String deviceName,
            @Param("browser") String browser,
            @Param("now") LocalDateTime now
    );
}
