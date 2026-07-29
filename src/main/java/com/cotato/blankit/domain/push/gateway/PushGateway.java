package com.cotato.blankit.domain.push.gateway;

import java.util.List;

public interface PushGateway {
    PushDeliveryResult send(List<String> installationIds, PushPayload payload);
}
