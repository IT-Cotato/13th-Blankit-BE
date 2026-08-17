package com.cotato.blankit.domain.notification.push.gateway;

import java.util.List;

public record PushDeliveryResult(List<Item> items) {
    public record Item(Long subscriptionId, String fcmToken, boolean success, String errorCode, PushErrorType errorType) {
        public static Item success(PushDeliveryTarget target) {
            return new Item(target.subscriptionId(), target.fcmToken(), true, null, null);
        }

        public static Item failure(PushDeliveryTarget target, String code, PushErrorType type) {
            return new Item(target.subscriptionId(), target.fcmToken(), false, code, type);
        }
    }
}
