package com.cotato.blankit.domain.notification.push.dto.response;

import com.cotato.blankit.domain.notification.push.entity.PushSubscription;

public record PushSubscriptionResponse(Long subscriptionId, boolean active) {
    public static PushSubscriptionResponse from(PushSubscription subscription) {
        return new PushSubscriptionResponse(subscription.getId(), subscription.isActive());
    }
}
