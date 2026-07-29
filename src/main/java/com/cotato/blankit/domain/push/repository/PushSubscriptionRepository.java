package com.cotato.blankit.domain.push.repository;

import com.cotato.blankit.domain.push.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    Optional<PushSubscription> findByFirebaseInstallationId(String firebaseInstallationId);
    Optional<PushSubscription> findByIdAndUserId(Long id, Long userId);
    List<PushSubscription> findByUserIdAndActiveTrueOrderByIdAsc(Long userId);
}
