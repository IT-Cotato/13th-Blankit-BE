package com.cotato.blankit.domain.notification.push;

import com.cotato.blankit.domain.notification.push.gateway.FcmPushGateway;
import com.cotato.blankit.domain.notification.push.gateway.PushPayload;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FcmPushGatewayTest {
    @Test
    void splitsMoreThanFiveHundredFids() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        BatchResponse first = successfulBatch(500);
        BatchResponse second = successfulBatch(1);
        when(messaging.sendEachForMulticast(any())).thenReturn(first, second);
        List<String> fids = IntStream.range(0, 501).mapToObj(i -> "fid-" + i).toList();

        var result = new FcmPushGateway(messaging).send(fids, payload());

        verify(messaging, times(2)).sendEachForMulticast(any());
        assertThat(result.items()).hasSize(501).allMatch(item -> item.success());
        assertThat(result.items().get(500).installationId()).isEqualTo("fid-500");
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

        var result = new FcmPushGateway(messaging).send(List.of("fid-a", "fid-b", "fid-c"), payload());

        assertThat(result.items().get(0).success()).isTrue();
        assertThat(result.items().get(1).installationId()).isEqualTo("fid-b");
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

        var result = new FcmPushGateway(messaging).send(List.of("expired-fid"), payload());

        assertThat(result.items().get(0).errorType())
                .isEqualTo(com.cotato.blankit.domain.notification.push.gateway.PushErrorType.PERMANENT_TARGET);
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
}
