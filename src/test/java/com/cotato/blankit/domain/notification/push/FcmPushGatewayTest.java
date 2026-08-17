package com.cotato.blankit.domain.notification.push;

import com.cotato.blankit.domain.notification.push.gateway.FcmPushGateway;
import com.cotato.blankit.domain.notification.push.gateway.PushDeliveryTarget;
import com.cotato.blankit.domain.notification.push.gateway.PushPayload;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FcmPushGatewayTest {
    @Test
    void putsFcmRegistrationTokenInMessageTokenTarget() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        BatchResponse response = successfulBatch(1);
        when(messaging.sendEachForMulticast(any())).thenReturn(response);

        new FcmPushGateway(messaging).send(targets("fcm-token"), payload());

        ArgumentCaptor<MulticastMessage> captor = ArgumentCaptor.forClass(MulticastMessage.class);
        verify(messaging).sendEachForMulticast(captor.capture());
        List<?> messages = ReflectionTestUtils.invokeMethod(captor.getValue(), "getMessageList");
        assertThat(messages).hasSize(1);
        String token = ReflectionTestUtils.invokeMethod(messages.get(0), "getToken");
        String fid = ReflectionTestUtils.invokeMethod(messages.get(0), "getFid");
        assertThat(token).isEqualTo("fcm-token");
        assertThat(fid).isNull();
    }

    @Test
    void splitsMoreThanFiveHundredFcmTokens() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        BatchResponse first = successfulBatch(500);
        BatchResponse second = successfulBatch(1);
        when(messaging.sendEachForMulticast(any())).thenReturn(first, second);
        List<PushDeliveryTarget> targets = IntStream.range(0, 501)
                .mapToObj(i -> new PushDeliveryTarget((long) i, "token-" + i))
                .toList();

        var result = new FcmPushGateway(messaging).send(targets, payload());

        verify(messaging, times(2)).sendEachForMulticast(any());
        assertThat(result.items()).hasSize(501).allMatch(item -> item.success());
        assertThat(result.items().get(500).fcmToken()).isEqualTo("token-500");
    }

    @Test
    void mapsPartialFailureByResponseIndex() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        SendResponse ok = mock(SendResponse.class);
        SendResponse failed = mock(SendResponse.class);
        when(ok.isSuccessful()).thenReturn(true);
        when(failed.isSuccessful()).thenReturn(false);
        when(failed.getException()).thenReturn(mock(com.google.firebase.messaging.FirebaseMessagingException.class));
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getResponses()).thenReturn(List.of(ok, failed, ok));
        when(messaging.sendEachForMulticast(any())).thenReturn(batch);

        var result = new FcmPushGateway(messaging).send(
                targets("token-a", "token-b", "token-c"), payload());

        assertThat(result.items().get(0).success()).isTrue();
        assertThat(result.items().get(1).fcmToken()).isEqualTo("token-b");
        assertThat(result.items().get(1).success()).isFalse();
        assertThat(result.items().get(2).success()).isTrue();
    }

    @Test
    void classifiesUnregisteredAsPermanentTargetFailure() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        SendResponse failed = mock(SendResponse.class);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(failed.isSuccessful()).thenReturn(false);
        when(failed.getException()).thenReturn(exception);
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getResponses()).thenReturn(List.of(failed));
        when(messaging.sendEachForMulticast(any())).thenReturn(batch);

        var result = new FcmPushGateway(messaging).send(targets("expired-token"), payload());

        assertThat(result.items().get(0).errorType())
                .isEqualTo(com.cotato.blankit.domain.notification.push.gateway.PushErrorType.PERMANENT_TARGET);
    }

    @Test
    void invalidArgumentDoesNotDeactivateSubscription() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        SendResponse failed = mock(SendResponse.class);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);
        when(failed.isSuccessful()).thenReturn(false);
        when(failed.getException()).thenReturn(exception);
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getResponses()).thenReturn(List.of(failed));
        when(messaging.sendEachForMulticast(any())).thenReturn(batch);

        var result = new FcmPushGateway(messaging).send(targets("valid-token"), payload());

        assertThat(result.items().get(0).errorType())
                .isEqualTo(com.cotato.blankit.domain.notification.push.gateway.PushErrorType.CONFIGURATION);
    }

    private BatchResponse successfulBatch(int size) {
        SendResponse response = mock(SendResponse.class);
        when(response.isSuccessful()).thenReturn(true);
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getResponses()).thenReturn(java.util.Collections.nCopies(size, response));
        return batch;
    }

    private PushPayload payload() {
        return new PushPayload("SERVICE", "title", "body", "1", "/tasks/1", Map.of());
    }

    private List<PushDeliveryTarget> targets(String... fcmTokens) {
        return IntStream.range(0, fcmTokens.length)
                .mapToObj(index -> new PushDeliveryTarget((long) index, fcmTokens[index]))
                .toList();
    }
}
