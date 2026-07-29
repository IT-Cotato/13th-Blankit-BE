package com.cotato.blankit.domain.push.dto.response;

import com.cotato.blankit.domain.push.entity.PushSubscription;

public record PushSubscriptionResponse(Long subscriptionId, boolean active) {
    public static PushSubscriptionResponse from(PushSubscription subscription) {
        return new PushSubscriptionResponse(subscription.getId(), subscription.isActive());
    }
}
