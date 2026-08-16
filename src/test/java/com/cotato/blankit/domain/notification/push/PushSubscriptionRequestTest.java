package com.cotato.blankit.domain.notification.push;

import com.cotato.blankit.domain.notification.push.dto.request.PushSubscriptionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PushSubscriptionRequestTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsInstallationIdAndFcmTokenSeparately() throws Exception {
        PushSubscriptionRequest request = objectMapper.readValue(
                "{\"installationId\":\"fid\",\"fcmToken\":\"fcm-token\",\"deviceName\":\"Mac\",\"browser\":\"Chrome\"}",
                PushSubscriptionRequest.class
        );

        assertThat(request.installationId()).isEqualTo("fid");
        assertThat(request.fcmToken()).isEqualTo("fcm-token");
    }
}
