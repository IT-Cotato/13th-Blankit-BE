package com.cotato.blankit.domain.push;

import com.cotato.blankit.domain.push.entity.PushNotificationType;
import com.cotato.blankit.domain.push.entity.PushSubscription;
import com.cotato.blankit.domain.push.gateway.*;
import com.cotato.blankit.domain.push.repository.PushSubscriptionRepository;
import com.cotato.blankit.domain.push.service.NotificationPreferenceService;
import com.cotato.blankit.domain.push.service.PushNotificationService;
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

        var outcome = service.send(1L, PushNotificationType.SERVICE, payload());

        assertThat(outcome).isEqualTo(PushNotificationService.PushSendOutcome.SKIPPED_PREFERENCE);
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

        var outcome = service.send(1L, PushNotificationType.SERVICE, payload());

        assertThat(outcome).isEqualTo(PushNotificationService.PushSendOutcome.SKIPPED_NO_SUBSCRIPTION);
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
        PushSubscription subscription = PushSubscription.create(user, "expired-fid", null, null, LocalDateTime.now());
        when(preference.isEnabled(1L, PushNotificationType.SERVICE)).thenReturn(true);
        when(repository.findByUserIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(subscription));
        when(repository.findByFirebaseInstallationId("expired-fid")).thenReturn(java.util.Optional.of(subscription));
        when(gateway.send(any(), any())).thenReturn(new PushDeliveryResult(List.of(
                PushDeliveryResult.Item.failure("expired-fid", "UNREGISTERED", PushErrorType.PERMANENT_TARGET))));
        doAnswer(invocation -> {
            Consumer<Object> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        var service = new PushNotificationService(repository, preference, gateway,
                Clock.systemUTC(), transactionTemplate);

        service.send(1L, PushNotificationType.SERVICE, payload());

        assertThat(subscription.isActive()).isFalse();
        assertThat(subscription.getFailureCount()).isEqualTo(1);
    }

    private PushPayload payload() {
        return new PushPayload("SERVICE", "title", "body", "1", "/", Map.of());
    }
}
