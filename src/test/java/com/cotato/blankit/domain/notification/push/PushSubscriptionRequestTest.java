package com.cotato.blankit.domain.notification.push;

import com.cotato.blankit.domain.notification.push.dto.request.PushSubscriptionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PushSubscriptionRequestTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void readsInstallationIdAndFcmTokenSeparately() throws Exception {
        PushSubscriptionRequest request = objectMapper.readValue(
                "{\"installationId\":\"fid\",\"fcmToken\":\"fcm-token\",\"deviceName\":\"Mac\",\"browser\":\"Chrome\"}",
                PushSubscriptionRequest.class
        );

        assertThat(request.installationId()).isEqualTo("fid");
        assertThat(request.fcmToken()).isEqualTo("fcm-token");
    }

    @Test
    void rejectsBlankOrNullRequiredIdentifiers() {
        assertThat(validator.validate(new PushSubscriptionRequest("", "token", null, null)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("installationId");
        assertThat(validator.validate(new PushSubscriptionRequest("fid", "", null, null)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("fcmToken");
        assertThat(validator.validate(new PushSubscriptionRequest("fid", null, null, null)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("fcmToken");
    }

    @Test
    void missingFcmTokenIsDeserializedAsNullAndRejected() throws Exception {
        PushSubscriptionRequest request = objectMapper.readValue(
                "{\"installationId\":\"fid\"}",
                PushSubscriptionRequest.class
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("fcmToken");
    }

    @Test
    void acceptsFcmTokenAtMaximumLength() {
        PushSubscriptionRequest request = new PushSubscriptionRequest("fid", "a".repeat(512), null, null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsFcmTokenOverMaximumLength() {
        PushSubscriptionRequest request = new PushSubscriptionRequest("fid", "a".repeat(513), null, null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("fcmToken");
    }
}
