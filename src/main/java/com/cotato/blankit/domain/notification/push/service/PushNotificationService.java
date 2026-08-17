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

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {
    private final PushSubscriptionRepository repository;
    private final NotificationPreferenceService preferenceService;
    private final PushGateway gateway;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public PushSendResult send(
            Long userId,
            PushNotificationType type,
            PushPayload payload,
            List<String> retryFcmTokens
    ) {
        if (!preferenceService.isEnabled(userId, type)) {
            return PushSendResult.of(PushSendOutcome.SKIPPED_PREFERENCE);
        }
        List<PushSubscription> subscriptions = findActive(userId, retryFcmTokens);
        if (subscriptions.isEmpty()) {
            return PushSendResult.of(PushSendOutcome.SKIPPED_NO_SUBSCRIPTION);
        }

        // 실제 외부 호출 직전에 다시 정책을 확인한다.
        if (!preferenceService.isEnabled(userId, type)) {
            return PushSendResult.of(PushSendOutcome.SKIPPED_PREFERENCE);
        }
        PushDeliveryResult result = gateway.send(
                subscriptions.stream()
                        .map(subscription -> new PushDeliveryTarget(
                                subscription.getId(), subscription.getFcmToken()))
                        .toList(),
                payload
        );
        transactionTemplate.executeWithoutResult(ignored -> applyResults(result));
        List<String> retryTargets = result.items().stream()
                .filter(item -> !item.success() && item.errorType() != PushErrorType.PERMANENT_TARGET)
                .map(PushDeliveryResult.Item::fcmToken)
                .distinct()
                .toList();
        boolean anySuccess = result.items().stream().anyMatch(PushDeliveryResult.Item::success);
        if (!retryTargets.isEmpty()) {
            return new PushSendResult(
                    PushSendOutcome.RETRYABLE_FAILURE,
                    retryTargets,
                    selectFailureType(result)
            );
        }
        return anySuccess
                ? PushSendResult.of(PushSendOutcome.SENT)
                : PushSendResult.failed(selectFailureType(result));
    }

    private PushErrorType selectFailureType(PushDeliveryResult result) {
        List<PushErrorType> failureTypes = result.items().stream()
                .filter(item -> !item.success())
                .map(PushDeliveryResult.Item::errorType)
                .toList();
        if (failureTypes.contains(PushErrorType.CONFIGURATION)) return PushErrorType.CONFIGURATION;
        if (failureTypes.contains(PushErrorType.UNKNOWN)) return PushErrorType.UNKNOWN;
        if (failureTypes.contains(PushErrorType.RETRYABLE)) return PushErrorType.RETRYABLE;
        return PushErrorType.PERMANENT_TARGET;
    }

    private List<PushSubscription> findActive(Long userId, List<String> retryFcmTokens) {
        if (retryFcmTokens != null && !retryFcmTokens.isEmpty()) {
            return repository
                    .findByUserIdAndActiveTrueAndFcmTokenInOrderByIdAsc(
                            userId,
                            retryFcmTokens
                    );
        }
        return repository.findByUserIdAndActiveTrueOrderByIdAsc(userId);
    }

    private void applyResults(PushDeliveryResult result) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (PushDeliveryResult.Item item : result.items()) {
            if (item.success()) {
                repository.markSuccessIfTokenMatches(item.subscriptionId(), item.fcmToken(), now);
            } else {
                int affected = item.errorType() == PushErrorType.PERMANENT_TARGET
                        ? repository.deactivateAfterFailureIfTokenMatches(
                                item.subscriptionId(), item.fcmToken(), now)
                        : repository.markFailureIfTokenMatches(item.subscriptionId(), item.fcmToken(), now);
                if (affected == 0) continue;
                log.warn("FCM delivery failed: subscriptionId={}, code={}, category={}",
                        item.subscriptionId(), item.errorCode(), item.errorType());
            }
        }
    }

    public enum PushSendOutcome {
        SENT, SKIPPED_PREFERENCE, SKIPPED_NO_SUBSCRIPTION, RETRYABLE_FAILURE, FAILED
    }

    public record PushSendResult(
            PushSendOutcome outcome,
            List<String> retryFcmTokens,
            PushErrorType failureType
    ) {
        public PushSendResult {
            retryFcmTokens = retryFcmTokens == null
                    ? List.of()
                    : List.copyOf(retryFcmTokens);
        }

        public static PushSendResult of(PushSendOutcome outcome) {
            return new PushSendResult(outcome, List.of(), null);
        }

        public static PushSendResult retryAll(List<String> retryFcmTokens) {
            return new PushSendResult(PushSendOutcome.RETRYABLE_FAILURE, retryFcmTokens, PushErrorType.UNKNOWN);
        }

        public static PushSendResult failed(PushErrorType failureType) {
            return new PushSendResult(PushSendOutcome.FAILED, List.of(), failureType);
        }
    }
}
