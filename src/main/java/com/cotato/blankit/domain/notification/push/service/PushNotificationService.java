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

    public PushSendResult send(
            Long userId,
            PushNotificationType type,
            PushPayload payload,
            List<String> retryInstallationIds
    ) {
        if (!preferenceService.isEnabled(userId, type)) {
            return PushSendResult.of(PushSendOutcome.SKIPPED_PREFERENCE);
        }
        List<PushSubscription> subscriptions = findActive(userId, retryInstallationIds);
        if (subscriptions.isEmpty()) {
            return PushSendResult.of(PushSendOutcome.SKIPPED_NO_SUBSCRIPTION);
        }

        // 실제 외부 호출 직전에 다시 정책을 확인한다.
        if (!preferenceService.isEnabled(userId, type)) {
            return PushSendResult.of(PushSendOutcome.SKIPPED_PREFERENCE);
        }
        PushDeliveryResult result = gateway.send(
                subscriptions.stream().map(PushSubscription::getFcmToken).toList(), payload);
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

    private List<PushSubscription> findActive(Long userId, List<String> retryInstallationIds) {
        if (retryInstallationIds != null && !retryInstallationIds.isEmpty()) {
            return repository
                    .findByUserIdAndActiveTrueAndFcmTokenInOrderByIdAsc(
                            userId,
                            retryInstallationIds
                    );
        }
        return repository.findByUserIdAndActiveTrueOrderByIdAsc(userId);
    }

    private void applyResults(PushDeliveryResult result) {
        List<String> fcmTokens = result.items().stream()
                .map(PushDeliveryResult.Item::fcmToken)
                .distinct()
                .toList();
        Map<String, PushSubscription> byToken =
                repository.findByFcmTokenIn(fcmTokens).stream()
                .collect(Collectors.toMap(PushSubscription::getFcmToken, Function.identity()));
        LocalDateTime now = LocalDateTime.now(clock);
        for (PushDeliveryResult.Item item : result.items()) {
            PushSubscription subscription = byToken.get(item.fcmToken());
            if (subscription == null) continue;
            if (item.success()) {
                repository.markSuccessIfTokenMatches(subscription.getId(), item.fcmToken(), now);
            } else {
                int affected = item.errorType() == PushErrorType.PERMANENT_TARGET
                        ? repository.deactivateAfterFailureIfTokenMatches(subscription.getId(), item.fcmToken(), now)
                        : repository.markFailureIfTokenMatches(subscription.getId(), item.fcmToken(), now);
                if (affected == 0) continue;
                log.warn("FCM delivery failed: subscriptionId={}, code={}, category={}",
                        subscription.getId(), item.errorCode(), item.errorType());
            }
        }
    }

    public enum PushSendOutcome {
        SENT, SKIPPED_PREFERENCE, SKIPPED_NO_SUBSCRIPTION, RETRYABLE_FAILURE, FAILED
    }

    public record PushSendResult(
            PushSendOutcome outcome,
            List<String> retryInstallationIds,
            PushErrorType failureType
    ) {
        public PushSendResult {
            retryInstallationIds = retryInstallationIds == null
                    ? List.of()
                    : List.copyOf(retryInstallationIds);
        }

        public static PushSendResult of(PushSendOutcome outcome) {
            return new PushSendResult(outcome, List.of(), null);
        }

        public static PushSendResult retryAll(List<String> fcmTokens) {
            return new PushSendResult(PushSendOutcome.RETRYABLE_FAILURE, fcmTokens, PushErrorType.UNKNOWN);
        }

        public static PushSendResult failed(PushErrorType failureType) {
            return new PushSendResult(PushSendOutcome.FAILED, List.of(), failureType);
        }
    }
}
