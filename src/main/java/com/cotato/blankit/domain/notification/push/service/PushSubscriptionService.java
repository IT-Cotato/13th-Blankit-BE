package com.cotato.blankit.domain.notification.push.service;

import com.cotato.blankit.domain.notification.push.dto.request.PushSubscriptionRequest;
import com.cotato.blankit.domain.notification.push.dto.response.PushSubscriptionResponse;
import com.cotato.blankit.domain.notification.push.entity.PushSubscription;
import com.cotato.blankit.domain.notification.push.repository.PushSubscriptionRepository;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PushSubscriptionService {
    private final PushSubscriptionRepository repository;
    private final Clock clock;

    @Transactional
    public PushSubscriptionResponse register(Long userId, PushSubscriptionRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            repository.upsert(
                    userId,
                    request.installationId(),
                    request.fcmToken(),
                    request.deviceName(),
                    request.browser(),
                    now
            );
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(ErrorCode.PUSH_SUBSCRIPTION_CONFLICT, exception);
        }
        PushSubscription subscription = repository.findByFirebaseInstallationId(request.installationId())
                .orElseThrow(() -> new CustomException(ErrorCode.PUSH_SUBSCRIPTION_CONFLICT));
        return PushSubscriptionResponse.from(subscription);
    }

    @Transactional
    public void deactivate(Long userId, Long subscriptionId) {
        PushSubscription subscription = repository.findByIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PUSH_SUBSCRIPTION_NOT_FOUND));
        subscription.deactivate();
    }
}
