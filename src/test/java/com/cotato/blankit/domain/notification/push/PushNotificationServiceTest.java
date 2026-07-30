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
        PushSubscription subscription = PushSubscriptionFixture.create(user, "expired-fid");
        when(preference.isEnabled(1L, PushNotificationType.SERVICE)).thenReturn(true);
        when(repository.findByUserIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(subscription));
        when(repository.findByFirebaseInstallationIdIn(List.of("expired-fid")))
                .thenReturn(List.of(subscription));
        when(gateway.send(any(), any())).thenReturn(new PushDeliveryResult(List.of(
                PushDeliveryResult.Item.failure("expired-fid", "UNREGISTERED", PushErrorType.PERMANENT_TARGET))));
        doAnswer(invocation -> {
            Consumer<Object> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        var service = new PushNotificationService(repository, preference, gateway,
                Clock.systemUTC(), transactionTemplate);

        service.send(1L, PushNotificationType.SERVICE, payload(), List.of());

        assertThat(subscription.isActive()).isFalse();
        assertThat(subscription.getFailureCount()).isEqualTo(1);
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
        PushSubscription successful = PushSubscriptionFixture.create(user, "successful-fid");
        PushSubscription retryable = PushSubscriptionFixture.create(user, "retryable-fid");
        when(preference.isEnabled(1L, PushNotificationType.SERVICE)).thenReturn(true);
        when(repository.findByUserIdAndActiveTrueOrderByIdAsc(1L))
                .thenReturn(List.of(successful, retryable));
        when(repository.findByUserIdAndActiveTrueAndFirebaseInstallationIdInOrderByIdAsc(
                1L, List.of("retryable-fid")))
                .thenReturn(List.of(retryable));
        when(repository.findByFirebaseInstallationIdIn(anyList())).thenAnswer(invocation -> {
            List<String> requested = invocation.getArgument(0);
            return List.of(successful, retryable).stream()
                    .filter(subscription -> requested.contains(subscription.getFirebaseInstallationId()))
                    .toList();
        });
        when(gateway.send(any(), any()))
                .thenReturn(
                        new PushDeliveryResult(List.of(
                                PushDeliveryResult.Item.success("successful-fid"),
                                PushDeliveryResult.Item.failure(
                                        "retryable-fid", "UNAVAILABLE", PushErrorType.RETRYABLE))),
                        new PushDeliveryResult(List.of(
                                PushDeliveryResult.Item.success("retryable-fid")))
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
                first.retryInstallationIds()
        );

        assertThat(first.outcome()).isEqualTo(PushNotificationService.PushSendOutcome.RETRYABLE_FAILURE);
        assertThat(first.retryInstallationIds()).containsExactly("retryable-fid");
        assertThat(second.outcome()).isEqualTo(PushNotificationService.PushSendOutcome.SENT);
        org.mockito.ArgumentCaptor<List<String>> targets = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(gateway, times(2)).send(targets.capture(), any());
        assertThat(targets.getAllValues().get(1)).containsExactly("retryable-fid");
    }

    private PushPayload payload() {
        return new PushPayload("SERVICE", "title", "body", "1", "/", Map.of());
    }
}
