package com.cotato.blankit.domain.notification.push.gateway;

import java.util.List;

public interface PushGateway {
    PushDeliveryResult send(List<String> fcmTokens, PushPayload payload);
}
