package com.cotato.blankit.domain.notification.push.service;

import com.cotato.blankit.domain.notification.push.entity.PushNotificationType;
import com.cotato.blankit.domain.notification.push.entity.PushSubscription;
import com.cotato.blankit.domain.notification.push.gateway.*;
import com.cotato.blankit.domain.notification.push.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {
    private final PushSubscriptionRepository repository;
    private final NotificationPreferenceService preferenceService;
    private final PushGateway gateway;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public PushSendOutcome send(Long userId, PushNotificationType type, PushPayload payload) {
        if (!preferenceService.isEnabled(userId, type)) return PushSendOutcome.SKIPPED_PREFERENCE;
        List<PushSubscription> subscriptions = findActive(userId);
        if (subscriptions.isEmpty()) return PushSendOutcome.SKIPPED_NO_SUBSCRIPTION;

        // 실제 외부 호출 직전에 다시 정책을 확인한다.
        if (!preferenceService.isEnabled(userId, type)) return PushSendOutcome.SKIPPED_PREFERENCE;
        PushDeliveryResult result = gateway.send(
                subscriptions.stream().map(PushSubscription::getFirebaseInstallationId).toList(), payload);
        transactionTemplate.executeWithoutResult(ignored -> applyResults(result));
        boolean retryable = result.items().stream().anyMatch(item -> !item.success()
                && (item.errorType() == PushErrorType.RETRYABLE || item.errorType() == PushErrorType.CONFIGURATION));
        boolean anySuccess = result.items().stream().anyMatch(PushDeliveryResult.Item::success);
        return retryable ? PushSendOutcome.RETRYABLE_FAILURE
                : anySuccess ? PushSendOutcome.SENT : PushSendOutcome.FAILED;
    }

    private List<PushSubscription> findActive(Long userId) {
        return repository.findByUserIdAndActiveTrueOrderByIdAsc(userId);
    }

    private void applyResults(PushDeliveryResult result) {
        Map<String, PushSubscription> byFid = result.items().stream()
                .map(PushDeliveryResult.Item::installationId).distinct()
                .map(repository::findByFirebaseInstallationId).flatMap(java.util.Optional::stream)
                .collect(Collectors.toMap(PushSubscription::getFirebaseInstallationId, Function.identity()));
        LocalDateTime now = LocalDateTime.now(clock);
        for (PushDeliveryResult.Item item : result.items()) {
            PushSubscription subscription = byFid.get(item.installationId());
            if (subscription == null) continue;
            if (item.success()) {
                subscription.markSuccess(now);
            } else {
                subscription.markFailure(item.errorType() == PushErrorType.PERMANENT_TARGET);
                log.warn("FCM delivery failed: subscriptionId={}, code={}, category={}",
                        subscription.getId(), item.errorCode(), item.errorType());
            }
        }
    }

    public enum PushSendOutcome {
        SENT, SKIPPED_PREFERENCE, SKIPPED_NO_SUBSCRIPTION, RETRYABLE_FAILURE, FAILED
    }
}
