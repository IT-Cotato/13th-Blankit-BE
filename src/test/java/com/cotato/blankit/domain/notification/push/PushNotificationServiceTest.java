package com.cotato.blankit.domain.notification.push;

import com.cotato.blankit.domain.notification.push.entity.PushNotificationType;
import com.cotato.blankit.domain.notification.push.entity.PushSubscription;
import com.cotato.blankit.domain.notification.push.gateway.*;
import com.cotato.blankit.domain.notification.push.repository.PushSubscriptionRepository;
import com.cotato.blankit.domain.notification.push.service.NotificationPreferenceService;
import com.cotato.blankit.domain.notification.push.service.PushNotificationService;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PushNotificationServiceTest {
    @Test
    void preferenceOffDoesNotCallGateway() {
        PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
        NotificationPreferenceService preference = mock(NotificationPreferenceService.class);
        PushGateway gateway = mock(PushGateway.class);
        when(preference.isEnabled(1L, PushNotificationType.SERVICE)).thenReturn(false);
        var service = new PushNotificationService(repository, preference, gateway,
                Clock.systemUTC(), mock(TransactionTemplate.class));

        var outcome = service.send(1L, PushNotificationType.SERVICE, payload(), List.of());

        assertThat(outcome.outcome()).isEqualTo(PushNotificationService.PushSendOutcome.SKIPPED_PREFERENCE);
        verifyNoInteractions(gateway);
    }

    @Test
    void noActiveSubscriptionIsNormalSkip() {
        PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
        NotificationPreferenceService preference = mock(NotificationPreferenceService.class);
        PushGateway gateway = mock(PushGateway.class);
        when(preference.isEnabled(1L, PushNotificationType.SERVICE)).thenReturn(true);
        when(repository.findByUserIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of());
        var service = new PushNotificationService(repository, preference, gateway,
                Clock.systemUTC(), mock(TransactionTemplate.class));

        var outcome = service.send(1L, PushNotificationType.SERVICE, payload(), List.of());

        assertThat(outcome.outcome()).isEqualTo(PushNotificationService.PushSendOutcome.SKIPPED_NO_SUBSCRIPTION);
        verifyNoInteractions(gateway);
    }

    @Test
    @SuppressWarnings("unchecked")
    void unregisteredDeactivatesOnlyThatSubscription() {
        PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
        NotificationPreferenceService preference = mock(NotificationPreferenceService.class);
        PushGateway gateway = mock(PushGateway.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        User user = User.create(SocialProvider.KAKAO, "social", "a@example.com", "name", null, 60);
        PushSubscription subscription = PushSubscriptionFixture.create(
                user, 10L, "expired-fid", "expired-token");
        when(preference.isEnabled(1L, PushNotificationType.SERVICE)).thenReturn(true);
        when(repository.findByUserIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(subscription));
        when(repository.deactivateAfterFailureIfTokenMatches(any(), eq("expired-token"), any()))
                .thenReturn(1);
        when(gateway.send(any(), any())).thenReturn(new PushDeliveryResult(List.of(
                PushDeliveryResult.Item.failure(
                        new PushDeliveryTarget(10L, "expired-token"),
                        "UNREGISTERED",
                        PushErrorType.PERMANENT_TARGET))));
        doAnswer(invocation -> {
            Consumer<Object> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        var service = new PushNotificationService(repository, preference, gateway,
                Clock.systemUTC(), transactionTemplate);

        service.send(1L, PushNotificationType.SERVICE, payload(), List.of());

        verify(repository).deactivateAfterFailureIfTokenMatches(
                eq(subscription.getId()), eq("expired-token"), any(LocalDateTime.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void partialFailureRetriesOnlyFailedInstallation() {
        PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
        NotificationPreferenceService preference = mock(NotificationPreferenceService.class);
        PushGateway gateway = mock(PushGateway.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        User user = User.create(SocialProvider.KAKAO, "partial-social",
                "partial@example.com", "name", null, 60);
        PushSubscription successful = PushSubscriptionFixture.create(
                user, 11L, "successful-fid", "successful-token");
        PushSubscription retryable = PushSubscriptionFixture.create(
                user, 12L, "retryable-fid", "retryable-token");
        when(preference.isEnabled(1L, PushNotificationType.SERVICE)).thenReturn(true);
        when(repository.findByUserIdAndActiveTrueOrderByIdAsc(1L))
                .thenReturn(List.of(successful, retryable));
        when(repository.findByUserIdAndActiveTrueAndFcmTokenInOrderByIdAsc(
                1L, List.of("retryable-token")))
                .thenReturn(List.of(retryable));
        when(repository.markSuccessIfTokenMatches(any(), anyString(), any())).thenReturn(1);
        when(repository.markFailureIfTokenMatches(any(), anyString(), any())).thenReturn(1);
        when(gateway.send(any(), any()))
                .thenReturn(
                        new PushDeliveryResult(List.of(
                                PushDeliveryResult.Item.success(
                                        new PushDeliveryTarget(11L, "successful-token")),
                                PushDeliveryResult.Item.failure(
                                        new PushDeliveryTarget(12L, "retryable-token"),
                                        "UNAVAILABLE",
                                        PushErrorType.RETRYABLE))),
                        new PushDeliveryResult(List.of(
                                PushDeliveryResult.Item.success(
                                        new PushDeliveryTarget(12L, "retryable-token"))))
                );
        doAnswer(invocation -> {
            Consumer<Object> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        var service = new PushNotificationService(repository, preference, gateway,
                Clock.systemUTC(), transactionTemplate);

        var first = service.send(1L, PushNotificationType.SERVICE, payload(), List.of());
        var second = service.send(
                1L,
                PushNotificationType.SERVICE,
                payload(),
                first.retryFcmTokens()
        );

        assertThat(first.outcome()).isEqualTo(PushNotificationService.PushSendOutcome.RETRYABLE_FAILURE);
        assertThat(first.retryFcmTokens()).containsExactly("retryable-token");
        assertThat(first.failureType()).isEqualTo(PushErrorType.RETRYABLE);
        assertThat(second.outcome()).isEqualTo(PushNotificationService.PushSendOutcome.SENT);
        org.mockito.ArgumentCaptor<List<PushDeliveryTarget>> targets =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(gateway, times(2)).send(targets.capture(), any());
        assertThat(targets.getAllValues().get(0))
                .extracting(PushDeliveryTarget::fcmToken)
                .containsExactly("successful-token", "retryable-token");
        assertThat(targets.getAllValues().get(1))
                .extracting(PushDeliveryTarget::fcmToken)
                .containsExactly("retryable-token");
    }

    private PushPayload payload() {
        return new PushPayload("SERVICE", "title", "body", "1", "/", Map.of());
    }
}
