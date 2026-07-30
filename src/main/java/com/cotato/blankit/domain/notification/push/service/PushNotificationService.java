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
                subscriptions.stream().map(PushSubscription::getFirebaseInstallationId).toList(), payload);
        transactionTemplate.executeWithoutResult(ignored -> applyResults(result));
        List<String> retryTargets = result.items().stream()
                .filter(item -> !item.success() && item.errorType() != PushErrorType.PERMANENT_TARGET)
                .map(PushDeliveryResult.Item::installationId)
                .distinct()
                .toList();
        boolean anySuccess = result.items().stream().anyMatch(PushDeliveryResult.Item::success);
        if (!retryTargets.isEmpty()) {
            return new PushSendResult(PushSendOutcome.RETRYABLE_FAILURE, retryTargets);
        }
        return PushSendResult.of(anySuccess ? PushSendOutcome.SENT : PushSendOutcome.FAILED);
    }

    private List<PushSubscription> findActive(Long userId, List<String> retryInstallationIds) {
        if (retryInstallationIds != null && !retryInstallationIds.isEmpty()) {
            return repository
                    .findByUserIdAndActiveTrueAndFirebaseInstallationIdInOrderByIdAsc(
                            userId,
                            retryInstallationIds
                    );
        }
        return repository.findByUserIdAndActiveTrueOrderByIdAsc(userId);
    }

    private void applyResults(PushDeliveryResult result) {
        List<String> installationIds = result.items().stream()
                .map(PushDeliveryResult.Item::installationId)
                .distinct()
                .toList();
        Map<String, PushSubscription> byFid =
                repository.findByFirebaseInstallationIdIn(installationIds).stream()
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

    public record PushSendResult(
            PushSendOutcome outcome,
            List<String> retryInstallationIds
    ) {
        public PushSendResult {
            retryInstallationIds = retryInstallationIds == null
                    ? List.of()
                    : List.copyOf(retryInstallationIds);
        }

        public static PushSendResult of(PushSendOutcome outcome) {
            return new PushSendResult(outcome, List.of());
        }

        public static PushSendResult retryAll(List<String> installationIds) {
            return new PushSendResult(PushSendOutcome.RETRYABLE_FAILURE, installationIds);
        }
    }
}
