package com.cotato.blankit.domain.notification.push.gateway;

public record PushDeliveryTarget(Long subscriptionId, String fcmToken) {
}
