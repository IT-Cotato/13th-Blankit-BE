package com.cotato.blankit.domain.notification.push.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "blankit.firebase", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledPushGateway implements PushGateway {
    @Override
    public PushDeliveryResult send(List<PushDeliveryTarget> targets, PushPayload payload) {
        return new PushDeliveryResult(targets.stream()
                .map(target -> PushDeliveryResult.Item.failure(
                        target, "FIREBASE_DISABLED", PushErrorType.CONFIGURATION))
                .toList());
    }
}
