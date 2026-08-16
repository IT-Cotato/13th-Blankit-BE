package com.cotato.blankit.domain.notification.push.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "blankit.firebase", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledPushGateway implements PushGateway {
    @Override
    public PushDeliveryResult send(List<String> fcmTokens, PushPayload payload) {
        return new PushDeliveryResult(fcmTokens.stream()
                .map(token -> PushDeliveryResult.Item.failure(token, "FIREBASE_DISABLED", PushErrorType.CONFIGURATION))
                .toList());
    }
}
