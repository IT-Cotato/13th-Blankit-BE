package com.cotato.blankit.domain.push.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "blankit.firebase", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledPushGateway implements PushGateway {
    @Override
    public PushDeliveryResult send(List<String> installationIds, PushPayload payload) {
        return new PushDeliveryResult(installationIds.stream()
                .map(fid -> PushDeliveryResult.Item.failure(fid, "FIREBASE_DISABLED", PushErrorType.CONFIGURATION))
                .toList());
    }
}
