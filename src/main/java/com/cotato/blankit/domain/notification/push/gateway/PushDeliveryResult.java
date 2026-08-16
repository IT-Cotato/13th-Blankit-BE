package com.cotato.blankit.domain.notification.push.gateway;

import java.util.List;

public record PushDeliveryResult(List<Item> items) {
    public record Item(String fcmToken, boolean success, String errorCode, PushErrorType errorType) {
        public static Item success(String fcmToken) { return new Item(fcmToken, true, null, null); }
        public static Item failure(String fcmToken, String code, PushErrorType type) {
            return new Item(fcmToken, false, code, type);
        }
    }
}
