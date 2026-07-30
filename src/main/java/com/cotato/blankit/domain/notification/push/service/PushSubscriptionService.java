package com.cotato.blankit.domain.notification.push.service;

import com.cotato.blankit.domain.notification.push.dto.request.PushSubscriptionRequest;
import com.cotato.blankit.domain.notification.push.dto.response.PushSubscriptionResponse;
import com.cotato.blankit.domain.notification.push.entity.PushSubscription;
import com.cotato.blankit.domain.notification.push.repository.PushSubscriptionRepository;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public PushSubscriptionResponse register(Long userId, PushSubscriptionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        PushSubscription subscription = repository.findByFirebaseInstallationId(request.installationId())
                .map(existing -> {
                    existing.register(user, request.deviceName(), request.browser(), now);
                    return existing;
                })
                .orElseGet(() -> repository.save(PushSubscription.create(
                        user, request.installationId(), request.deviceName(), request.browser(), now)));
        try {
            repository.flush();
        } catch (DataIntegrityViolationException race) {
            subscription = repository.findByFirebaseInstallationId(request.installationId()).orElseThrow(() -> race);
            subscription.register(user, request.deviceName(), request.browser(), now);
        }
        return PushSubscriptionResponse.from(subscription);
    }

    @Transactional
    public void deactivate(Long userId, Long subscriptionId) {
        PushSubscription subscription = repository.findByIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PUSH_SUBSCRIPTION_NOT_FOUND));
        subscription.deactivate();
    }
}
